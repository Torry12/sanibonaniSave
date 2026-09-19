package com.sanibonani.save.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sanibonani.save.domain.model.Group
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun SaOsmMap(
    groups: List<Group>,
    onGroupClick: (String) -> Unit,
    onGroupsVisible: ((List<Group>) -> Unit)? = null,
    userLocation: Location? = null,
    onMapReady: (() -> Unit)? = null,
    autoCenterOnGroups: Boolean = true,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = {
            mapView.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(6.0)

                // Set initial center to South Africa
                controller.setCenter(GeoPoint(-28.4793, 24.6727))

                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                locationOverlay.enableMyLocation()
                overlays.add(locationOverlay)

                onMapReady?.invoke()
            }
        },
        modifier = modifier,
        update = { mv ->
            mv.overlays.removeIf { it is Marker }

            groups.forEach { group ->
                val lat = group.latitude
                val lon = group.longitude
                if (lat != null && lon != null) {
                    val marker = Marker(mv)
                    marker.position = GeoPoint(lat, lon)
                    marker.title = group.name
                    marker.snippet = group.type.displayName
                    marker.icon = createMarkerIcon(context, group.logoEmoji)
                    marker.setOnMarkerClickListener { m, _ ->
                        onGroupClick(group.id ?: "")
                        m.showInfoWindow()
                        true
                    }
                    mv.overlays.add(marker)
                }
            }

            if (autoCenterOnGroups && groups.isNotEmpty()) {
                val first = groups.first()
                if (first.latitude != null && first.longitude != null) {
                    mv.controller.animateTo(GeoPoint(first.latitude!!, first.longitude!!))
                    mv.controller.setZoom(10.0)
                }
            }

            mv.invalidate()
        }
    )
}

fun createMarkerIcon(context: Context, emoji: String): Drawable {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        textSize = 80f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(emoji, size / 2f, size / 1.3f, paint)
    return BitmapDrawable(context.resources, bitmap)
}
