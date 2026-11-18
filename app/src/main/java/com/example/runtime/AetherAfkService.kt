package com.example.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class AetherAfkService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                onStopAllRequested?.invoke()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1) ?: 1
                val cycles = intent?.getLongExtra(EXTRA_CYCLES, 0L) ?: 0L
                val uptimeSec = intent?.getLongExtra(EXTRA_UPTIME_SEC, 0L) ?: 0L
                acquireWakeLock()
                val notification = buildNotification(activeCount, cycles, uptimeSec)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AetherPhone::AfkWakeLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 12h renewable safety lease
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun buildNotification(activeCount: Int, cycles: Long, uptimeSec: Long): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherAfkService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hrs = uptimeSec / 3600
        val mins = (uptimeSec % 3600) / 60
        val secs = uptimeSec % 60
        val formattedTime = String.format("%02d:%02d:%02d", hrs, mins, secs)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AetherPhone AFK Runtime ($activeCount active)")
            .setContentText("Uptime $formattedTime (Unlimited) • $cycles macro cycles")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop All", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.afk_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.afk_service_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "aether_afk_runtime"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START_OR_UPDATE = "com.example.runtime.ACTION_START_OR_UPDATE"
        const val ACTION_STOP_ALL = "com.example.runtime.ACTION_STOP_ALL"
        const val EXTRA_ACTIVE_COUNT = "extra_active_count"
        const val EXTRA_CYCLES = "extra_cycles"
        const val EXTRA_UPTIME_SEC = "extra_uptime_sec"

        @Volatile
        var onStopAllRequested: (() -> Unit)? = null

        fun stop(context: Context) {
            context.stopService(Intent(context, AetherAfkService::class.java))
        }
    }
}
