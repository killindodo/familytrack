package com.killindodo.familytrack.watcher.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class MapMarker(val lat: Double, val lng: Double, val color: Int, val label: String)

data class MapCircle(val lat: Double, val lng: Double, val radiusM: Double, val color: Int)

data class MapTrail(val points: List<Pair<Double, Double>>, val color: Int)

/**
 * OpenStreetMap (osmdroid) — free tiles, no API key, offline cache included.
 */
@Composable
fun OsmMap(
    markers: List<MapMarker>,
    circles: List<MapCircle> = emptyList(),
    trails: List<MapTrail> = emptyList(),
    focus: Pair<Double, Double>? = null,
    focusZoom: Double = 15.0,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val mapView = androidx.compose.runtime.remember {
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onDetach()
        }
    }

    LaunchedEffect(focus) {
        focus?.let { (lat, lng) ->
            mapView.controller.animateTo(GeoPoint(lat, lng))
            mapView.controller.setZoom(focusZoom)
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            circles.forEach { map.overlays.add(circleOverlay(map, it)) }
            trails.forEach { map.overlays.add(trailOverlay(map, it)) }
            markers.forEach { map.overlays.add(markerOverlay(map, it)) }
            map.invalidate()
        },
    )
}

private fun markerOverlay(map: MapView, m: MapMarker): Marker = Marker(map).apply {
    position = GeoPoint(m.lat, m.lng)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = BitmapDrawable(map.context.resources, circleBitmap(m.color, m.label))
    title = m.label
}

private fun circleBitmap(color: Int, label: String, size: Int = 48): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(size / 2f, size / 2f, size / 2f - 1f, p)
    p.color = color
    c.drawCircle(size / 2f, size / 2f, size / 2f - 4f, p)
    p.color = android.graphics.Color.WHITE
    p.textSize = size * 0.45f
    p.textAlign = Paint.Align.CENTER
    val yPos = size / 2f - (p.descent() + p.ascent()) / 2f
    c.drawText(label.take(1).uppercase(), size / 2f, yPos, p)
    return bmp
}

private fun circleOverlay(map: MapView, c: MapCircle): Polygon {
    val poly = Polygon(map)
    val lat1 = Math.toRadians(c.lat)
    val lon1 = Math.toRadians(c.lng)
    val d = c.radiusM / 6_371_000.0
    val pts = ArrayList<GeoPoint>(73)
    for (i in 0..72) {
        val brg = Math.toRadians(i * 5.0)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lon2 = lon1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        pts.add(GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2)))
    }
    poly.setPoints(pts)
    poly.fillPaint.color = withAlpha(c.color, 40)
    poly.outlinePaint.color = c.color
    poly.outlinePaint.strokeWidth = 3f
    return poly
}

private fun trailOverlay(map: MapView, t: MapTrail): Polyline {
    val line = Polyline(map)
    line.setPoints(t.points.map { GeoPoint(it.first, it.second) })
    line.outlinePaint.color = t.color
    line.outlinePaint.strokeWidth = 6f
    return line
}

private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)
