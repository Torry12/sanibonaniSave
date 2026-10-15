package com.sanibonani.save.ui.components

import com.sanibonani.save.domain.utils.safeRunCatching
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.CustomZoomButtonsController.Visibility
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import java.util.concurrent.ConcurrentHashMap
import com.sanibonani.save.domain.config.SaReferenceData

import kotlin.time.Duration.Companion.seconds

@Composable
fun SaOsmMap(
    groups: List<Group>,
    modifier: Modifier = Modifier,
    onGroupClick: (String) -> Unit,
    onLocationTap: ((List<Group>) -> Unit)? = null,
    userLocation: Location? = null,
    onMapReady: (() -> Unit)? = null,
    autoCenterOnGroups: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var zoomControlsVisible by remember { mutableStateOf(true) }

    // Use a reference to the MapView so it can be controlled by the floating zoom buttons
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val locationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val clustererRef = remember { mutableStateOf<RadiusMarkerClusterer?>(null) }

    val mapEntries = remember(groups) {
        groups.mapNotNull { group ->
            val lat = group.latitude
            val lon = group.longitude
            if (lat != null && lon != null) {
                GeoPoint(lat, lon) to group
            } else {
                SaReferenceData.PROVINCE_CENTERS[group.province]?.let { center ->
                    GeoPoint(center.first, center.second) to group
                }
            }
        }
    }

    LaunchedEffect(zoomControlsVisible) {
        if (zoomControlsVisible) {
            kotlinx.coroutines.delay(3.seconds)
            zoomControlsVisible = false
        }
    }

    LaunchedEffect(userLocation, autoCenterOnGroups, mapViewRef.value) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        if (userLocation != null && !autoCenterOnGroups) {
            val lat = userLocation.latitude
            val lon = userLocation.longitude
            val radiusKm = 50.0
            val latDelta = radiusKm / 111.0
            val cosLat = kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.01)
            val lonDelta = radiusKm / (111.0 * cosLat)

            val bounds = BoundingBox(lat + latDelta, lon + lonDelta, lat - latDelta, lon - lonDelta)
            safeRunCatching { mv.zoomToBoundingBox(bounds, true, 64) }
        }
    }

    LaunchedEffect(mapEntries, autoCenterOnGroups, mapViewRef.value, clustererRef.value) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        val clusterer = clustererRef.value ?: return@LaunchedEffect

        // 1. Clear existing markers in clusterer
        clusterer.items.clear()

        // 2. Add new markers to clusterer
        mapEntries.forEach { (pos, group) ->
            val marker = Marker(mv).apply {
                position = pos
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = group.name
                snippet = group.type.displayName
                icon = getCachedMarkerIcon(context, group.logoEmoji, group.name)
                relatedObject = group

                setOnMarkerClickListener { _, _ ->
                    onGroupClick(group.id ?: "")
                    true
                }

                infoWindow = SaMarkerInfoWindow(mv, title ?: "", snippet ?: "")
            }
            clusterer.add(marker)
        }

        // 3. Auto-center/fit if requested
        if (autoCenterOnGroups && mapEntries.isNotEmpty()) {
            val lats = mapEntries.map { it.first.latitude }
            val lons = mapEntries.map { it.first.longitude }

            if (mapEntries.size == 1) {
                mv.controller.animateTo(GeoPoint(lats[0], lons[0]))
                mv.controller.setZoom(14.0)
            } else {
                val minLat = lats.min()
                val maxLat = lats.max()
                val minLon = lons.min()
                val maxLon = lons.max()

                // Add some padding
                val latBuffer = (maxLat - minLat).coerceAtLeast(0.01) * 0.2
                val lonBuffer = (maxLon - minLon).coerceAtLeast(0.01) * 0.2

                val bounds = BoundingBox(
                    maxLat + latBuffer,
                    maxLon + lonBuffer,
                    minLat - latBuffer,
                    minLon - lonBuffer
                )
                safeRunCatching {
                    mv.zoomToBoundingBox(bounds, true, 100)
                }
            }
        }

        safeRunCatching {
            clusterer.invalidate()
            mv.invalidate()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef.value ?: return@LifecycleEventObserver
            val lo = locationOverlayRef.value
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mv.onResume()
                    lo?.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    lo?.disableMyLocation()
                    mv.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationOverlayRef.value?.let { lo ->
                lo.disableMyLocation()
                lo.myLocationProvider?.stopLocationProvider()
            }
            mapViewRef.value?.onDetach()
            mapViewRef.value = null
            locationOverlayRef.value = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    safeRunCatching { zoomController.setVisibility(Visibility.NEVER) }
                    setBuiltInZoomControls(false)

                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(-28.4793, 24.6727))

                    // Add location overlay immediately with safety check
                    safeRunCatching {
                        val provider = GpsMyLocationProvider(ctx.applicationContext)
                        val locationOverlay = MyLocationNewOverlay(provider, this)
                        locationOverlay.enableMyLocation()
                        overlays.add(locationOverlay)
                        locationOverlayRef.value = locationOverlay
                    }

                    // Initialize Clusterer
                    val clusterer = RadiusMarkerClusterer(ctx)
                    val clusterIcon = createMarkerIcon(ctx, "ðŸ“", "...")
                    clusterer.setIcon((clusterIcon as BitmapDrawable).bitmap)

                    overlays.add(clusterer)
                    clustererRef.value = clusterer

                    mapViewRef.value = this
                    onMapReady?.invoke()
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { _ ->
                // Empty as everything is handled in LaunchedEffects for performance
            }
        )

        // Floating Zoom Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = zoomControlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = { mapViewRef.value?.controller?.zoomIn(); zoomControlsVisible = true },
                        containerColor = Color.White.copy(alpha = 0.85f),
                        contentColor = Forest
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in")
                    }

                    SmallFloatingActionButton(
                        onClick = { mapViewRef.value?.controller?.zoomOut(); zoomControlsVisible = true },
                        containerColor = Color.White.copy(alpha = 0.85f),
                        contentColor = Forest
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out")
                    }
                }
            }

            AnimatedVisibility(visible = !zoomControlsVisible, enter = fadeIn(), exit = fadeOut()) {
                SmallFloatingActionButton(
                    onClick = { zoomControlsVisible = true },
                    containerColor = Color.White.copy(alpha = 0.75f),
                    contentColor = Forest
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Show zoom controls")
                }
            }
        }
    }
}

@Composable
fun SaLocationPickerMap(
    selectedLat: Double?,
    selectedLon: Double?,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Reference to MapView for lifecycle management
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val locationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    LaunchedEffect(selectedLat, selectedLon, mapViewRef.value) {
        val mv = mapViewRef.value ?: return@LaunchedEffect
        mv.overlays.removeAll { it is Marker }
        if (selectedLat != null && selectedLon != null) {
            val marker = Marker(mv).apply {
                position = GeoPoint(selectedLat, selectedLon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = getCachedMarkerIcon(context, "ðŸ“", "Selected")
                title = "Selected Location"
            }
            mv.overlays.add(marker)
            mv.controller.animateTo(GeoPoint(selectedLat, selectedLon))
        }
        safeRunCatching { mv.invalidate() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef.value ?: return@LifecycleEventObserver
            val lo = locationOverlayRef.value
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mv.onResume()
                    lo?.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    lo?.disableMyLocation()
                    mv.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationOverlayRef.value?.let { lo ->
                lo.disableMyLocation()
                lo.myLocationProvider?.stopLocationProvider()
            }
            mapViewRef.value?.onDetach()
            mapViewRef.value = null
            locationOverlayRef.value = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    safeRunCatching { zoomController.setVisibility(Visibility.NEVER) }
                    setBuiltInZoomControls(false)
                    controller.setZoom(13.0)

                    // Default center if no location selected
                    if (selectedLat == null || selectedLon == null) {
                        controller.setCenter(GeoPoint(-26.2041, 28.0473)) // JHB
                    } else {
                        controller.setCenter(GeoPoint(selectedLat, selectedLon))
                    }

                    val provider = GpsMyLocationProvider(ctx.applicationContext)
                    val locationOverlay = MyLocationNewOverlay(provider, this)
                    locationOverlay.enableMyLocation()
                    overlays.add(locationOverlay)

                    val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onLocationPicked(p.latitude, p.longitude)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }
                    overlays.add(org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver))

                    locationOverlayRef.value = locationOverlay
                    mapViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { _ ->
                // Empty as everything is handled in LaunchedEffects for performance
            }
        )

        // Marker instructions
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape
        ) {
            Text(
                "Tap map to pick location",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
