package org.eu.manekineko.courier.ui.trackmap

import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.eu.manekineko.courier.databinding.ActivityTrackMapBinding
import android.util.Log
import org.json.JSONArray
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackMapBinding

    companion object {
        const val EXTRA_POINTS_JSON = "points_json"
        const val EXTRA_START_TIME = "start_time"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.trackMapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.trackMapView.setMultiTouchControls(true)
        applyMapTheme()

        val pointsJson = intent.getStringExtra(EXTRA_POINTS_JSON) ?: "[]"
        val startTime = intent.getLongExtra(EXTRA_START_TIME, 0L)

        Log.d("TrackMapActivity", "Points JSON: $pointsJson")
        Log.d("TrackMapActivity", "Start time: $startTime")

        val points = parsePoints(pointsJson)
        Log.d("TrackMapActivity", "Parsed points count: ${points.size}")

        // Откладываем отрисовку до полной инициализации MapView
        binding.trackMapView.post {
            Log.d("TrackMapActivity", "Post running, displaying route")
            displayRoute(points)
            displayInfo(points, pointsJson, startTime)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.trackMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.trackMapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.trackMapView.onDetach()
    }

    private fun parsePoints(json: String): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        try {
            Log.d("TrackMapActivity", "Parsing JSON: $json")
            val arr = JSONArray(json)
            Log.d("TrackMapActivity", "Array length: ${arr.length()}")
            for (i in 0 until arr.length()) {
                val point = arr.getJSONArray(i)
                Log.d("TrackMapActivity", "Point $i: lat=${point.getDouble(0)}, lon=${point.getDouble(1)}")
                result.add(GeoPoint(point.getDouble(0), point.getDouble(1)))
            }
        } catch (e: Exception) {
            Log.e("TrackMapActivity", "Error parsing points", e)
        }
        return result
    }

    private fun displayRoute(points: List<GeoPoint>) {
        if (points.isEmpty()) {
            Log.d("TrackMapActivity", "No points to display")
            return
        }

        Log.d("TrackMapActivity", "Displaying route with ${points.size} points")

        val polyline = Polyline().apply {
            outlinePaint.strokeWidth = 10f
            outlinePaint.color = ContextCompat.getColor(
                this@TrackMapActivity, android.R.color.holo_blue_dark
            )
            setPoints(points)
        }
        binding.trackMapView.overlays.add(polyline)

        val startMarker = Marker(binding.trackMapView).apply {
            position = points.first()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Старт"
            icon = ContextCompat.getDrawable(
                this@TrackMapActivity,
                org.eu.manekineko.courier.R.drawable.ic_delivery_point
            )
        }
        binding.trackMapView.overlays.add(startMarker)

        if (points.size > 1) {
            val endMarker = Marker(binding.trackMapView).apply {
                position = points.last()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Финиш"
                icon = ContextCompat.getDrawable(
                    this@TrackMapActivity,
                    org.eu.manekineko.courier.R.drawable.ic_delivery_done
                )
            }
            binding.trackMapView.overlays.add(endMarker)
        }

        val boundingBox = BoundingBox.fromGeoPointsSafe(points)
        Log.d("TrackMapActivity", "Bounding box: $boundingBox")

        binding.trackMapView.post {
            binding.trackMapView.controller.animateTo(boundingBox.center)
            binding.trackMapView.controller.setZoom(14.0)
            binding.trackMapView.invalidate()
        }
    }

    private fun displayInfo(points: List<GeoPoint>, pointsJson: String, startTime: Long) {
        val endTime = System.currentTimeMillis()
        val durationSeconds = (endTime - startTime) / 1000
        val durationMinutes = durationSeconds / 60
        val durationHours = durationMinutes / 60

        val distanceMeters = points.zipWithNext { a, b -> a.distanceToAsDouble(b) }.sum()

        binding.infoDistance.text = String.format("%.1f км", distanceMeters / 1000)
        binding.infoPoints.text = points.size.toString()
        binding.infoDate.text = when {
            durationHours > 0 -> String.format("%d ч %d мин", durationHours, durationMinutes % 60)
            durationMinutes > 0 -> "$durationMinutes мин"
            else -> "$durationSeconds сек"
        }
    }

    private fun applyMapTheme() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            val invertMatrix = ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            val darkenMatrix = ColorMatrix().apply {
                setScale(0.85f, 0.85f, 0.9f, 1f)
            }
            invertMatrix.postConcat(darkenMatrix)
            binding.trackMapView.overlayManager.tilesOverlay.setColorFilter(
                ColorMatrixColorFilter(invertMatrix)
            )
        } else {
            binding.trackMapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
    }
}
