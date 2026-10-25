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
import androidx.compose.ui.graphics.toArgb
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

// Global cache for marker icons to prevent OOM due to repeated Bitmap creation
private val markerIconCache = ConcurrentHashMap<String, Drawable>()

class SaMarkerInfoWindow(
    mapView: MapView,
    private val titleStr: String,
    private val snippetStr: String
) : InfoWindow(R.layout.sa_marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val currentMapView = mapView ?: return
        val context = currentMapView.context ?: return

        // Ensure no other info windows are open to prevent crashes/overlaps
        closeAllInfoWindowsOn(currentMapView)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 24)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(MapMarkerNavy.toArgb())
                cornerRadius = 32f
                setStroke(3, android.graphics.Color.WHITE)
            }
            elevation = 12f
        }

        val titleView = TextView(context).apply {
            text = titleStr
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }
        layout.addView(titleView)

        if (snippetStr.isNotEmpty()) {
            val snippetView = TextView(context).apply {
                text = snippetStr
                setTextColor(MapMarkerText.toArgb())
                textSize = 11f
                gravity = Gravity.CENTER
            }
            layout.addView(snippetView)
        }

        mView = layout
        layout.setOnClickListener { close() }
    }

    override fun onClose() {
        // Nothing
    }
}

fun getCachedMarkerIcon(context: Context, emoji: String?, label: String? = null): Drawable {
    val key = "${emoji ?: "default"}_${label ?: ""}"
    return markerIconCache.getOrPut(key) {
        createMarkerIcon(context, emoji, label)
    }
}

fun createMarkerIcon(context: Context, emoji: String?, label: String? = null): Drawable {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val displayLabel = if (label != null && label.length > 18) label.take(15) + "..." else label
    val labelPadding = 24f
    val textWidth = if (displayLabel != null) textPaint.measureText(displayLabel) else 0f
    val labelWidth = (textWidth + labelPadding * 2).coerceAtLeast(120f)

    val pinWidth = 120
    val pinHeight = 160
    val labelHeight = 48f
    val gap = 8f

    val bitmapWidth = labelWidth.toInt().coerceAtLeast(pinWidth)
    val bitmapHeight = (pinHeight + (if (displayLabel != null) labelHeight + gap else 0f)).toInt()

    return try {
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = bitmapWidth / 2f

        // 1. Draw Label at top if present
        if (displayLabel != null) {
            val labelRect = RectF(
                centerX - labelWidth / 2f,
                0f,
                centerX + labelWidth / 2f,
                labelHeight
            )
            val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1B3A4B.toInt() // Navy
                style = Paint.Style.FILL
                setShadowLayer(8f, 0f, 4f, 0x40000000)
            }
            canvas.drawRoundRect(labelRect, 24f, 24f, labelBgPaint)

            val labelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(labelRect, 24f, 24f, labelBorderPaint)

            canvas.drawText(displayLabel, centerX, labelHeight / 2f + 10f, textPaint)
        }

        // 2. Draw Pin below label
        val pinTopOffset = if (displayLabel != null) labelHeight + gap else 0f

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B3A4B.toInt()
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40000000
            setShadowLayer(12f, 0f, 6f, 0x60000000)
        }

        val mainCircleRadius = pinWidth / 2.2f
        val topY = pinTopOffset + mainCircleRadius + 10f

        val path = Path().apply {
            addCircle(centerX, topY, mainCircleRadius, Path.Direction.CW)
            moveTo(centerX - mainCircleRadius * 0.8f, topY + mainCircleRadius * 0.5f)
            lineTo(centerX, bitmapHeight - 10f)
            lineTo(centerX + mainCircleRadius * 0.8f, topY + mainCircleRadius * 0.5f)
            close()
        }

        canvas.drawPath(path, shadowPaint)
        canvas.drawPath(path, pinPaint)
        canvas.drawPath(path, borderPaint)

        val innerCircleRadius = mainCircleRadius * 0.75f
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, topY, innerCircleRadius, innerPaint)

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = innerCircleRadius * 1.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(emoji ?: "🤝", centerX, topY + (emojiPaint.textSize / 3f), emojiPaint)

        BitmapDrawable(context.resources, bitmap)
    } catch (e: Throwable) {
        // Safe fallback for low-memory devices — covers both OutOfMemoryError and
        // any drawable/bitmap failure so a marker is always produced.
        ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
            ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
    }
}
