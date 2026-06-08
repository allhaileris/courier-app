package org.eu.manekineko.courier.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.eu.manekineko.courier.R
import org.eu.manekineko.courier.data.api.DeliveryApiService
import org.eu.manekineko.courier.data.api.MockDeliveryApiService
import org.eu.manekineko.courier.data.api.TrackUploadService
import org.eu.manekineko.courier.data.database.TrackDatabase
import org.eu.manekineko.courier.data.model.DeliveryPoint
import org.eu.manekineko.courier.data.model.PointStatus
import org.eu.manekineko.courier.data.model.TrackEntity
import android.util.Log
import com.google.gson.Gson
import org.eu.manekineko.courier.databinding.FragmentCommentSheetBinding
import org.eu.manekineko.courier.databinding.FragmentMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var locationMarker: Marker? = null
    private var trackLine: Polyline? = null
    private val trackPoints = mutableListOf<GeoPoint>()

    private var isRecordingTrack = false
    private var trackStartTime: Long = 0
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    // Для отображения длительности записи
    private val durationTimer = Timer()
    private var durationTimerTask: TimerTask? = null

    // Для вычисления скорости
    private var lastLocationTime: Long = 0
    private var lastLocationPoint: GeoPoint? = null
    private var lastSpeedKmh: Float = 0f

    private val apiService: DeliveryApiService = MockDeliveryApiService()
    private val deliveryMarkers = mutableListOf<Marker>()
    private var fetchPointsJob: Job? = null
    private var lastFetchLocation: GeoPoint? = null

    private var pendingComment: String = ""

    private val database by lazy { TrackDatabase.getInstance(requireContext()) }
    private val trackDao by lazy { database.trackDao() }
    private val gson by lazy { Gson() }

    companion object {
        private const val MIN_DISTANCE_TO_REFETCH_METERS = 200.0
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupMap()
        setupLocationButton()
        setupZoomButtons()
        setupCommentButton()
        setupStartTrackButton()
        checkAndRequestPermissions()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)
        binding.mapView.controller.setCenter(GeoPoint(59.9426, 30.3183))
        // Disable built-in zoom controls (buttons are replaced with FABs)
        // setBuiltInZoomControls is deprecated, zoom controls are hidden by default in newer versions
        applyMapTheme()
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
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(
                ColorMatrixColorFilter(invertMatrix)
            )
        } else {
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
    }

    private fun setupLocationButton() {
        binding.fabMyLocation.setOnClickListener {
            if (hasLocationPermission()) {
                centerOnCurrentLocation()
            } else {
                requestLocationPermissions()
            }
        }
    }

    private fun setupZoomButtons() {
        binding.fabZoomIn.setOnClickListener {
            binding.mapView.controller.zoomIn()
        }
        binding.fabZoomOut.setOnClickListener {
            binding.mapView.controller.zoomOut()
        }
    }

    private fun setupCommentButton() {
        binding.fabAddComment.setOnClickListener {
            showCommentBottomSheet()
        }
    }

    private fun setupStartTrackButton() {
        binding.fabStartTrack.setOnClickListener {
            onTrackButtonClick()
        }
    }

    private fun onTrackButtonClick() {
        if (isRecordingTrack) {
            isRecordingTrack = false
            binding.fabStartTrack.setImageResource(R.drawable.ic_start_track)
            stopDurationTimer()

            saveTrackToDatabase()
            pendingComment = ""
            // Не останавливаем локацию - курсор должен продолжать двигаться
        } else {
            isRecordingTrack = true
            binding.fabStartTrack.setImageResource(R.drawable.ic_stop_track)

            trackPoints.clear()
            pendingComment = ""
            updateTrackLine()

            // Remove old markers before starting new track
            removeStartMarker()
            removeEndMarker()

            trackStartTime = System.currentTimeMillis()
            startDurationTimer()

            if (hasLocationPermission()) {
                centerOnCurrentLocation()
                notifyTrackStarted()
            }
        }
    }

    private fun saveTrackToDatabase() {
        val pointsArray = trackPoints.map { doubleArrayOf(it.latitude, it.longitude) }.toTypedArray()
        val pointsJson = gson.toJson(pointsArray)

        Log.d("MapFragment", "Saving track with ${trackPoints.size} points")
        Log.d("MapFragment", "Points JSON: $pointsJson")

        // Add end marker at the last point
        if (trackPoints.isNotEmpty()) {
            addEndMarker(trackPoints.last())
        }

        val trackEntity = TrackEntity(
            pointsJson = pointsJson,
            startTime = System.currentTimeMillis(),
            comment = pendingComment
        )

        viewLifecycleOwner.lifecycleScope.launch {
            trackDao.insertTrack(trackEntity)
        }

        Toast.makeText(requireContext(), "Трек сохранён", Toast.LENGTH_SHORT).show()
    }

    private fun updateTrackLine() {
        if (trackLine == null) {
            trackLine = Polyline().apply {
                outlinePaint.strokeWidth = 8f
                outlinePaint.color = ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                setPoints(trackPoints)
            }
            binding.mapView.overlays.add(trackLine)
        } else {
            trackLine?.setPoints(trackPoints)
        }
        binding.mapView.invalidate()
    }

    private fun updateLocationOnMap(point: GeoPoint, bearing: Float = 0f) {
        if (locationMarker == null) {
            locationMarker = Marker(binding.mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Вы здесь"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_navigation_arrow)
                setInfoWindow(null)
            }
            binding.mapView.overlays.add(locationMarker)
        }
        locationMarker?.position = point
        locationMarker?.rotation = -bearing

        binding.mapView.controller.animateTo(point)

        if (isRecordingTrack) {
            trackPoints.add(point)
            if (trackPoints.size == 1) {
                addStartMarker()
            }
            updateTrackLine()
        }

        binding.mapView.invalidate()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notifyTrackStarted() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    TrackUploadService.notifyTrackStarted(
                        it.latitude,
                        it.longitude,
                        System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun showCommentBottomSheet() {
        val sheetBinding = FragmentCommentSheetBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext()).apply {
            setCancelable(true)
            setContentView(sheetBinding.root)
        }

        sheetBinding.etCommentInput.setText(pendingComment)

        sheetBinding.btnCancelComment.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnSaveComment.setOnClickListener {
            val commentText = sheetBinding.etCommentInput.text.toString().trim()
            if (commentText.isNotEmpty()) {
                pendingComment = commentText
                Toast.makeText(requireContext(), "Комментарий сохранён", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkAndRequestPermissions() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMinUpdateIntervalMillis(2000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val currentPoint = GeoPoint(location.latitude, location.longitude)
                    updateLocationOnMap(currentPoint, location.bearing)
                    fetchDeliveryPointsIfNeeded(currentPoint)
                    calculateAndDisplaySpeed(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback!!, requireActivity().mainLooper
            )

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val point = GeoPoint(it.latitude, it.longitude)
                    updateLocationOnMap(point)
                    binding.mapView.controller.animateTo(point)
                    fetchDeliveryPointsIfNeeded(point)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun fetchDeliveryPointsIfNeeded(currentLocation: GeoPoint) {
        val lastLocation = lastFetchLocation
        if (lastLocation != null) {
            val distance = currentLocation.distanceToAsDouble(lastLocation)
            if (distance < MIN_DISTANCE_TO_REFETCH_METERS) return
        }

        lastFetchLocation = currentLocation
        fetchPointsJob?.cancel()
        fetchPointsJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val points = apiService.getDeliveryPoints(
                    currentLocation.latitude,
                    currentLocation.longitude
                )
                updateDeliveryMarkers(points)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateDeliveryMarkers(points: List<DeliveryPoint>) {
        deliveryMarkers.forEach { marker ->
            binding.mapView.overlays.remove(marker)
        }
        deliveryMarkers.clear()

        points.forEach { point ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(point.latitude, point.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = point.address
                snippet = "${point.clientName} • ${point.estimatedTime}"
                icon = ContextCompat.getDrawable(
                    requireContext(),
                    getMarkerIcon(point.status)
                )
            }
            deliveryMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }

        binding.mapView.invalidate()
    }

    private fun getMarkerIcon(status: PointStatus): Int {
        return when (status) {
            PointStatus.PENDING -> R.drawable.ic_delivery_point
            PointStatus.IN_PROGRESS -> R.drawable.ic_delivery_active
            PointStatus.DELIVERED -> R.drawable.ic_delivery_done
        }
    }

    private fun centerOnCurrentLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val point = GeoPoint(it.latitude, it.longitude)
                    binding.mapView.controller.animateTo(point)
                    binding.mapView.controller.setZoom(17.0)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun calculateAndDisplaySpeed(location: android.location.Location) {
        val currentTime = System.currentTimeMillis()

        if (lastLocationTime == 0L || lastLocationPoint == null) {
            lastLocationTime = currentTime
            lastLocationPoint = GeoPoint(location.latitude, location.longitude)
            return
        }

        val currentPoint = GeoPoint(location.latitude, location.longitude)
        val distanceMeters = lastLocationPoint!!.distanceToAsDouble(currentPoint)
        val timeDiffSeconds = (currentTime - lastLocationTime) / 1000.0

        if (timeDiffSeconds < 0.5) return

        lastSpeedKmh = if (distanceMeters > 0.5 && location.accuracy < 100) {
            (distanceMeters / timeDiffSeconds).toFloat() * 3.6f
        } else {
            0f
        }

        updateSpeedDisplay()
        lastLocationTime = currentTime
        lastLocationPoint = currentPoint
    }

    private fun updateSpeedDisplay() {
        binding.tvSpeed.text = String.format("%.0f км/ч", lastSpeedKmh)
    }

    private fun addStartMarker() {
        startMarker = Marker(binding.mapView).apply {
            position = trackPoints.firstOrNull()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Старт"
            icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_delivery_point
            )
        }
        binding.mapView.overlays.add(startMarker!!)
        binding.mapView.invalidate()
    }

    private fun addEndMarker(point: GeoPoint) {
        endMarker = Marker(binding.mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Финиш"
            icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_delivery_done
            )
        }
        binding.mapView.overlays.add(endMarker!!)
        binding.mapView.invalidate()
    }

    private fun removeStartMarker() {
        startMarker?.let { marker ->
            binding.mapView.overlays.remove(marker)
            binding.mapView.invalidate()
            startMarker = null
        }
    }

    private fun removeEndMarker() {
        endMarker?.let { marker ->
            binding.mapView.overlays.remove(marker)
            binding.mapView.invalidate()
            endMarker = null
        }
    }

    private fun startDurationTimer() {
        binding.tvDuration.visibility = android.view.View.VISIBLE
        durationTimerTask?.cancel()
        durationTimerTask = object : TimerTask() {
            override fun run() {
                val elapsedMillis = System.currentTimeMillis() - trackStartTime
                val seconds = (elapsedMillis / 1000).toInt()
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                binding.tvDuration.post {
                    binding.tvDuration.text = String.format("%02d:%02d:%02d", hours, minutes, secs)
                }
            }
        }
        durationTimer.scheduleAtFixedRate(durationTimerTask, 0, 1000)
    }

    private fun stopDurationTimer() {
        durationTimerTask?.cancel()
        durationTimerTask = null
        binding.tvDuration.text = "00:00:00"
        binding.tvDuration.visibility = android.view.View.GONE
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        updateSpeedDisplay()
        if (isRecordingTrack) {
            startDurationTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        stopDurationTimer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchPointsJob?.cancel()
        stopDurationTimer()
        removeStartMarker()
        removeEndMarker()
        _binding = null
    }
}
