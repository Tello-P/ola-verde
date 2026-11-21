package es.olaverde.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.BufferedWriter
import java.io.FileWriter

/** Servicio en primer plano que graba el GPS a 1 Hz mientras dura el viaje. */
class TripRecorderService : Service() {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var writer: BufferedWriter? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val w = writer ?: return
            for (loc in result.locations) {
                val speed = if (loc.hasSpeed()) loc.speed.toString() else ""
                w.write("${loc.time},${loc.latitude},${loc.longitude},$speed,${loc.accuracy}\n")
                points.intValue++
            }
            w.flush()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (writer != null) return START_STICKY

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val file = TripStore(this).newTrip(System.currentTimeMillis())
        writer = BufferedWriter(FileWriter(file, true))
        points.intValue = 0
        recording.value = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        writer?.close()
        writer = null
        recording.value = false
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Grabación de viaje", NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, TripRecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ola Verde")
            .setContentText("Grabando el viaje para aprender los semáforos")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(0, "Detener", stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "es.olaverde.app.STOP"

        val recording = mutableStateOf(false)
        val points = mutableIntStateOf(0)

        fun start(context: Context) =
            context.startForegroundService(Intent(context, TripRecorderService::class.java))

        fun stop(context: Context) =
            context.startService(Intent(context, TripRecorderService::class.java).setAction(ACTION_STOP))
    }
}
