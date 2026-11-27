package com.sujan.pipe.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository

/**
 * Android `NotificationListenerService` endpoint that grants `MediaSessionManager` access
 * and forwards media notification events to [YouTubeMediaSessionTracker].
 */
class YouTubeWatchListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.d(TAG, "YouTubeWatchListenerService onListenerConnected")
        YouTubeMediaSessionTracker.start(applicationContext)
        YouTubeWatchKeepAliveService.syncRunningState(applicationContext)
    }

    override fun onListenerDisconnected() {
        isServiceConnected = false
        Log.d(TAG, "YouTubeWatchListenerService onListenerDisconnected")
        YouTubeMediaSessionTracker.flushAll()
        runCatching {
            requestRebind(ComponentName(applicationContext, YouTubeWatchListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        val looksLikeBrowserYouTube = isKnownBrowserPackage(pkg) && (
            subText.contains("youtube", ignoreCase = true) ||
                text.contains("youtube.com", ignoreCase = true) ||
                infoText.contains("youtube", ignoreCase = true) ||
                title.endsWith(" - YouTube", ignoreCase = true)
            )
        if (looksLikeBrowserYouTube) {
            YouTubeMediaSessionTracker.markBrowserYouTubeHint(pkg)
        }

        if (!isKnownYouTubePackage(pkg) && !looksLikeBrowserYouTube) return

        val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }

        if (token != null) {
            YouTubeMediaSessionTracker.attachControllerFromNotification(
                context = applicationContext,
                token = token,
                forceBrowserYt = looksLikeBrowserYouTube,
            )
        } else if (title.isNotBlank()) {
            val repo = PipeRepository(applicationContext)
            if (!repo.loadYouTubeHistoryEnabled()) return
            val source = friendlyAppLabel(pkg)
            val fallbackChannel = text.takeIf { !it.contains("youtube.com", ignoreCase = true) }.orEmpty()
            OfflineYouTubeHistoryManager.recordWatch(
                context = applicationContext,
                title = title,
                channel = fallbackChannel,
                sourceApp = source,
            )
        }
    }

    companion object {
        private const val TAG = "PipeYT"

        @Volatile
        var isServiceConnected: Boolean = false
            private set

        fun ensureBoundAndCaptureActive(context: Context): Int {
            YouTubeWatchKeepAliveService.syncRunningState(context)
            return YouTubeMediaSessionTracker.snapshotNow(context)
        }

        /**
         * Pure helper that extrapolates live playback position from Android's `PlaybackState`
         * `(position, lastPositionUpdateTime, playbackSpeed)` and clamps cleanly to `[0, durationMs]`.
         */
        fun estimatePositionMs(
            basePositionMs: Long,
            lastUpdateElapsedMs: Long,
            playbackSpeed: Float,
            isPlaying: Boolean,
            durationMs: Long,
            nowElapsedMs: Long,
        ): Long {
            val safeBase = basePositionMs.coerceAtLeast(0L)
            val estimated = if (isPlaying && lastUpdateElapsedMs > 0L && nowElapsedMs > lastUpdateElapsedMs) {
                val effectiveSpeed = if (playbackSpeed > 0f) playbackSpeed else 1f
                val delta = ((nowElapsedMs - lastUpdateElapsedMs) * effectiveSpeed).toLong().coerceAtLeast(0L)
                safeBase + delta
            } else {
                safeBase
            }
            return if (durationMs > 0L) estimated.coerceIn(0L, durationMs) else estimated.coerceAtLeast(0L)
        }

        private val KNOWN_YT_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.youtube.kids",
            "app.morphe.android.youtube",
            "app.morphe.android.apps.youtube.music",
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "app.revanced.android.apps.youtube.music",
            "app.rvx.android.apps.youtube.music",
            "com.vanced.android.youtube",
            "com.vanced.android.apps.youtube.music",
            "org.schabi.newpipe",
            "org.schabi.newpipe.debug",
            "InfinityLoop1309.NewPipeEnhanced",
            "com.github.libretube",
            "com.futo.platformplayer",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videomanager.tv",
            "com.google.android.youtube.tv",
        )

        private val KNOWN_BROWSER_HINTS = listOf(
            "chrome",
            "brave",
            "firefox",
            "fenix",
            "vivaldi",
            "opera",
            "kiwi",
            "bromite",
            "cromite",
            "duckduckgo",
            "emmx",
            "sbrowser",
            "via",
            "soul",
            "mull",
            "iceraven",
            "falkon",
            "browser",
        )

        fun isKnownYouTubePackage(pkg: String): Boolean {
            if (pkg.isBlank()) return false
            if (pkg in KNOWN_YT_PACKAGES) return true
            val lower = pkg.lowercase()
            return lower.contains("youtube") ||
                lower.contains("morphe") ||
                lower.contains("newpipe") ||
                lower.contains("pipepipe") ||
                lower.contains("libretube") ||
                lower.contains("smarttube") ||
                lower.contains("revanced") ||
                lower.contains("grayjay")
        }

        fun isKnownBrowserPackage(pkg: String): Boolean {
            if (pkg.isBlank()) return false
            val lower = pkg.lowercase()
            return KNOWN_BROWSER_HINTS.any { lower.contains(it) }
        }

        fun friendlyAppLabel(pkg: String): String {
            val lower = pkg.lowercase()
            return when {
                lower.contains("morphe") && lower.contains("music") -> "Morphe Music"
                lower.contains("morphe") -> "Morphe"
                lower.contains("rvx") -> "RVX"
                lower.contains("revanced") && lower.contains("music") -> "YT Music"
                lower.contains("revanced") -> "ReVanced"
                lower.contains("youtube.music") -> "YT Music"
                lower.contains("youtube") -> "YouTube"
                lower.contains("pipepipe") || lower.contains("newpipeenhanced") -> "PipePipe"
                lower.contains("newpipe") -> "NewPipe"
                lower.contains("libretube") -> "LibreTube"
                lower.contains("platformplayer") || lower.contains("grayjay") -> "Grayjay"
                lower.contains("brave") -> "Brave"
                lower.contains("firefox") || lower.contains("fenix") -> "Firefox"
                lower.contains("chrome") -> "Chrome"
                lower.contains("cromite") -> "Cromite"
                lower.contains("vivaldi") -> "Vivaldi"
                else -> "Browser"
            }
        }
    }
}

/**
 * Automatically starts [YouTubeMediaSessionTracker], requests `NotificationListenerService` rebind,
 * and launches [YouTubeWatchKeepAliveService] after reboot or APK update.
 */
class PipeBootRebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (OfflineYouTubeHistoryManager.isNotificationListenerEnabled(context)) {
                runCatching {
                    NotificationListenerService.requestRebind(
                        ComponentName(context.applicationContext, YouTubeWatchListenerService::class.java)
                    )
                }
                YouTubeMediaSessionTracker.start(context.applicationContext)
                YouTubeWatchKeepAliveService.syncRunningState(context.applicationContext)
            }
        }
    }
}
