package org.eu.manekineko.courier.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.eu.manekineko.courier.MainActivity
import org.eu.manekineko.courier.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val trackPoints = mutableListOf<GeoPoint>()
    private var isTracking = false

    var onLocationUpdate: ((GeoPoint) -> Unit)? = null

    // Для вычисления скорости
    private var lastLocationTime: Long = 0
    private var lastLocationPoint: GeoPoint? = null
    private var lastSpeedKmh: Float = 0f

    fun getLastSpeed(): Float = lastSpeedKmh

    fun handleLocationUpdate(location: android.location.Location) {
        if (!isTracking) return

        val currentPoint = GeoPoint(location.latitude, location.longitude)
        trackPoints.add(currentPoint)
        onLocationUpdate?.invoke(currentPoint)
        sendSpeedUpdate(location)
        updateNotification()
    }

    private val binder = TrackingBinder()

    inner class TrackingBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SPEED = "speed"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startTracking() {
        if (isTracking) return
        isTracking = true
        trackPoints.clear()

        startForeground(NOTIFICATION_ID, createNotification())
        requestLocationUpdates()
    }

    private fun stopTracking() {
        isTracking = false
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun getTrackPoints(): List<GeoPoint> = trackPoints.toList()

    fun isCurrentlyTracking(): Boolean = isTracking

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMinUpdateIntervalMillis(2000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val point = GeoPoint(location.latitude, location.longitude)
                    trackPoints.add(point)
                    onLocationUpdate?.invoke(point)
                    
                    // Вычисляем и отправляем скорость
                    sendSpeedUpdate(location)
                    
                    updateNotification()
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback!!, mainLooper
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Запись маршрута",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление о записи маршрута"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Запись маршрута")
            .setContentText("Записано точек: ${trackPoints.size}")
            .setSmallIcon(R.drawable.ic_start_track)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun sendSpeedUpdate(location: android.location.Location) {
        val currentTime = System.currentTimeMillis()

        if (lastLocationTime == 0L || lastLocationPoint == null) {
            lastLocationTime = currentTime
            lastLocationPoint = GeoPoint(location.latitude, location.longitude)
            return
        }

        val currentPoint = GeoPoint(location.latitude, location.longitude)
        val distanceMeters = lastLocationPoint!!.distanceToAsDouble(currentPoint)
        val timeDiffSeconds = (currentTime - lastLocationTime) / 1000.0

        // Порог времени снижен до 0.5 секунды для более частых обновлений
        if (timeDiffSeconds < 0.5) return

        // Условия скорости упрощены: рассчитываем скорость при любом разумном расстоянии
        val speedKmh = if (distanceMeters > 0.5 && location.accuracy < 100) {
            (distanceMeters / timeDiffSeconds) * 3.6f
        } else {
            0f
        }

        // Отправляем скорость через broadcast
        val intent = Intent("ACTION_SPEED_UPDATE").apply {
            putExtra(EXTRA_SPEED, speedKmh)
        }
        sendBroadcast(intent)

        lastSpeedKmh = speedKmh.toFloat()
        lastLocationTime = currentTime
        lastLocationPoint = currentPoint
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}
