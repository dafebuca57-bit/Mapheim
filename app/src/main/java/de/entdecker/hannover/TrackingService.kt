package de.entdecker.hannover

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.entdecker.hannover.GameState.GameEvent

/** Sigue el GPS con la pantalla apagada y avisa de cada descubrimiento. */
class TrackingService : Service(), GameState.Listener {

    companion object {
        const val ACTION_STOP = "de.entdecker.hannover.STOP"
        private const val CH_TRACK = "tracking"
        private const val CH_DISC = "discover"
        private const val NOTIF_ID = 1
    }

    private lateinit var client: FusedLocationProviderClient
    private var running = false
    private var nextId = 100
    private var lastNotifUpdate = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (l in result.locations) {
                GameState.onLocation(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 30f, l.time)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startUpdates()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
            running = true
            GameState.addListener(this)
            GameState.tracking = true
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (running) client.removeLocationUpdates(callback)
        running = false
        GameState.removeListener(this)
        GameState.tracking = false
        super.onDestroy()
    }

    override fun onGameUpdate(ev: GameEvent) {
        val now = System.currentTimeMillis()
        if (now - lastNotifUpdate > 15_000 || ev.discovered.isNotEmpty()) {
            lastNotifUpdate = now
            notifySafe(NOTIF_ID, buildNotification())
        }
        if (GameState.uiVisible) return // la pantalla ya lo muestra
        for (p in ev.discovered) {
            notifySafe(nextId++, alert("Descubriste: ${p.name}", p.cat.label))
        }
        for (a in ev.achievements) {
            notifySafe(nextId++, alert("Logro desbloqueado: ${a.title}", a.desc))
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CH_TRACK)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("Explorando Hannover")
        .setContentText(GameState.stats().let { "${fmt(it.hPct, 2)} % explorado · ${fmt(it.walkedKm, 1)} km" })
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(openApp())
        .addAction(
            R.drawable.ic_stop, "Detener",
            PendingIntent.getService(
                this, 1,
                Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun alert(title: String, text: String) = NotificationCompat.Builder(this, CH_DISC)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setColor(ContextCompat.getColor(this, R.color.gold))
        .setContentIntent(openApp())
        .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission")
    private fun notifySafe(id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(id, n)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "Exploración activa", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DISC, "Descubrimientos y logros", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
