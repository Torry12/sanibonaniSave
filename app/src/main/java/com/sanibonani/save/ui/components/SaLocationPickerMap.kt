package com.sanibonani.save.ui.components

import com.sanibonani.save.domain.utils.safeRunCatching
import android.graphics.*
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sanibonani.save.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.CustomZoomButtonsController.Visibility


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
                icon = getCachedMarkerIcon(context, "📍", "Selected")
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
