package com.sujan.pipe.service

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
import android.util.Log
import com.sujan.pipe.MainActivity
import com.sujan.pipe.R
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository

/**
 * Lightweight, silent (`IMPORTANCE_MIN`) foreground service that prevents aggressive OEM battery
 * managers (Xiaomi HyperOS SmartPower, Samsung, ColorOS) from freezing `com.sujan.pipe` while
 * you watch YouTube / YouTube Morphe / ReVanced, guaranteeing 100% reliable live watch-history
 * and timestamp capture.
 */
class YouTubeWatchKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "YouTubeWatchKeepAliveService created")
        promoteToForeground()
        YouTubeMediaSessionTracker.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        YouTubeMediaSessionTracker.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        YouTubeMediaSessionTracker.flushAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        ensureNotificationChannel()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_pipe)
            .setContentTitle("pipe offline youtube history")
            .setContentText("passively saving watch history & timestamps locally")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }.onFailure { err ->
            Log.w(TAG, "startForeground failed: ${err.message}")
        }
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Offline YouTube History Monitor",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps Pipe unfrozen on HyperOS/MIUI so YouTube watch timestamps are never missed"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val TAG = "PipeYT"
        private const val CHANNEL_ID = "pipe_yt_watch_keepalive"
        private const val NOTIF_ID = 4107

        @Volatile
        var isRunning: Boolean = false
            private set

        fun syncRunningState(context: Context) {
            val appCtx = context.applicationContext
            val repo = PipeRepository(appCtx)
            val shouldRun = repo.loadYouTubeHistoryEnabled() &&
                repo.loadYouTubeKeepAliveEnabled() &&
                OfflineYouTubeHistoryManager.isNotificationListenerEnabled(appCtx)

            val intent = Intent(appCtx, YouTubeWatchKeepAliveService::class.java)
            if (shouldRun) {
                runCatching {
                    appCtx.startForegroundService(intent)
                }.onFailure { err ->
                    Log.w(TAG, "startForegroundService skipped: ${err.message}")
                }
            } else if (isRunning) {
                runCatching { appCtx.stopService(intent) }
            }
        }
    }
}
