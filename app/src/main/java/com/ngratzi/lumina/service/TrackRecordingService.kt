package com.ngratzi.lumina.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ngratzi.lumina.MainActivity
import com.ngratzi.lumina.data.model.Track
import com.ngratzi.lumina.data.model.TrackColors
import com.ngratzi.lumina.data.model.TrackPoint
import com.ngratzi.lumina.data.repository.TrackRepository
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.*

data class RecordingState(
    val isRecording: Boolean = false,
    val trackId: Long? = null,
    val distanceNm: Double = 0.0,
    val pointCount: Int = 0,
)

@AndroidEntryPoint
class TrackRecordingService : Service() {

    @Inject lateinit var trackRepository: TrackRepository
    @Inject lateinit var locationHelper: LocationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_START = "com.ngratzi.lumina.TRACK_START"
        const val ACTION_STOP  = "com.ngratzi.lumina.TRACK_STOP"
        const val CHANNEL_ID   = "lumina_tracking"
        const val NOTIF_ID     = 200

        private val _state = MutableStateFlow(RecordingState())
        val state: StateFlow<RecordingState> = _state.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP  -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_state.value.isRecording) return
        startForeground(NOTIF_ID, buildNotification("Starting…"))

        scope.launch {
            val now  = System.currentTimeMillis()
            val name = "Track " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(now))
            var track = Track(name = name, color = TrackColors[0], startTime = now)
            val trackId = trackRepository.createTrack(track)
            track = track.copy(id = trackId)

            _state.value = RecordingState(isRecording = true, trackId = trackId)

            var lastLat: Double? = null
            var lastLon: Double? = null
            var totalDist = 0.0
            var count = 0

            while (_state.value.isRecording) {
                val loc = locationHelper.getCurrentLocation()
                if (loc != null) {
                    val lat = loc.latitude
                    val lon = loc.longitude
                    if (lastLat != null && lastLon != null) {
                        totalDist += haversineNm(lastLat!!, lastLon!!, lat, lon)
                    }
                    lastLat = lat
                    lastLon = lon
                    count++

                    trackRepository.addPoint(
                        TrackPoint(trackId = trackId, lat = lat, lon = lon, timestamp = System.currentTimeMillis())
                    )
                    trackRepository.updateTrack(track.copy(totalDistanceNm = totalDist))

                    _state.value = _state.value.copy(distanceNm = totalDist, pointCount = count)
                    updateNotification("Recording · ${"%.2f".format(totalDist)} nm · $count pts")
                }
                delay(10_000L)
            }

            // Finalize: stamp endTime
            trackRepository.updateTrack(
                track.copy(totalDistanceNm = totalDist, endTime = System.currentTimeMillis())
            )
            _state.value = RecordingState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        // Signal the loop to exit; it will finalize the track and stop itself
        _state.value = _state.value.copy(isRecording = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        _state.value = RecordingState()
        scope.cancel()
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Lumina — Track Recording")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}

private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R    = 3440.065
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = sin(dLat / 2).pow(2) +
               cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}
