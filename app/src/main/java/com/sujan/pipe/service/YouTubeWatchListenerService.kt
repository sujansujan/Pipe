package com.sujan.pipe.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Passive, 100% offline YouTube Watch History listener using Android's `NotificationListenerService`
 * and `MediaSessionManager`.
 *
 * - Observes active `MediaSession` controllers from YouTube, YouTube ReVanced / RVX, YT Music,
 *   NewPipe, PipePipe, LibreTube, Grayjay, SmartTube, and browsers playing YouTube tabs.
 * - Extracts exact title, channel, duration, playback position (for resume `&t=...s`), video ID
 *   (from `MEDIA_URI`, `MEDIA_ID`, `ART_URI` / `i.ytimg.com/vi/<ID>/...`, or recent Pipe link correlation),
 *   and saves the in-memory `MediaMetadata` thumbnail bitmap locally with zero network requests.
 */
class YouTubeWatchListenerService : NotificationListenerService() {

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val trackedControllers = ConcurrentHashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val browserYouTubeHints = ConcurrentHashMap<String, Long>()
    private val lastLoggedPosition = ConcurrentHashMap<String, Long>()

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        attachMediaSessionManager()
    }

    override fun onListenerDisconnected() {
        detachMediaSessionManager()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        detachMediaSessionManager()
        ioExecutor.shutdownNow()
        super.onDestroy()
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
            browserYouTubeHints[pkg] = System.currentTimeMillis()
        }

        if (!isKnownYouTubePackage(pkg) && !looksLikeBrowserYouTube) return

        val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }

        if (token != null) {
            runCatching {
                val controller = MediaController(applicationContext, token)
                registerController(controller)
                captureFromController(controller, forceBrowserYt = looksLikeBrowserYouTube)
            }
        } else if (title.isNotBlank()) {
            // Fallback for minimal media notifications without an attached MediaSession token
            val repo = PipeRepository(applicationContext)
            if (!repo.loadYouTubeHistoryEnabled()) return
            val source = friendlyAppLabel(pkg)
            val fallbackChannel = text.takeIf { !it.contains("youtube.com", ignoreCase = true) }.orEmpty()
            ioExecutor.execute {
                OfflineYouTubeHistoryManager.recordWatch(
                    context = applicationContext,
                    title = title,
                    channel = fallbackChannel,
                    sourceApp = source,
                )
            }
        }
    }

    private fun attachMediaSessionManager() {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        mediaSessionManager = msm
        val component = ComponentName(this, YouTubeWatchListenerService::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            syncControllers(controllers.orEmpty())
        }
        sessionsListener = listener

        runCatching {
            msm.addOnActiveSessionsChangedListener(listener, component)
            val current = msm.getActiveSessions(component)
            syncControllers(current)
        }
    }

    private fun detachMediaSessionManager() {
        sessionsListener?.let { listener ->
            runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionsListener = null
        for ((_, pair) in trackedControllers) {
            runCatching { pair.first.unregisterCallback(pair.second) }
        }
        trackedControllers.clear()
    }

    private fun syncControllers(active: List<MediaController>) {
        val activeTokens = active.map { it.sessionToken }.toSet()
        val staleTokens = trackedControllers.keys.filter { it !in activeTokens }
        for (token in staleTokens) {
            trackedControllers.remove(token)?.let { (ctrl, cb) ->
                runCatching { ctrl.unregisterCallback(cb) }
            }
        }
        for (controller in active) {
            val pkg = controller.packageName.orEmpty()
            if (isKnownYouTubePackage(pkg) || isKnownBrowserPackage(pkg)) {
                registerController(controller)
                captureFromController(controller)
            }
        }
    }

    private fun registerController(controller: MediaController) {
        val token = controller.sessionToken
        if (trackedControllers.containsKey(token)) return
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                captureFromController(controller)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val st = state?.state ?: return
                if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED) {
                    captureFromController(controller)
                }
            }

            override fun onSessionDestroyed() {
                trackedControllers.remove(token)?.let { (c, cb) ->
                    runCatching { c.unregisterCallback(cb) }
                }
            }
        }
        trackedControllers[token] = controller to callback
        runCatching { controller.registerCallback(callback) }
    }

    private fun captureFromController(
        controller: MediaController,
        forceBrowserYt: Boolean = false,
    ) {
        val repo = PipeRepository(applicationContext)
        if (!repo.loadYouTubeHistoryEnabled()) return

        val pkg = controller.packageName.orEmpty()
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState
        val stateCode = playbackState?.state ?: PlaybackState.STATE_NONE

        // Only capture active playback or pause (skip stopped/error/none idle sessions)
        if (stateCode != PlaybackState.STATE_PLAYING &&
            stateCode != PlaybackState.STATE_PAUSED &&
            stateCode != PlaybackState.STATE_BUFFERING
        ) {
            return
        }

        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        if (rawTitle.isBlank()) return

        val rawChannel = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""

        val mediaUri = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
        val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

        val extractedVideoId = OfflineYouTubeHistoryManager.extractYouTubeVideoId(mediaUri)
            ?: OfflineYouTubeHistoryManager.extractYouTubeVideoId(artUri)
            ?: OfflineYouTubeHistoryManager.extractYouTubeVideoId(mediaId, allowBareId = isKnownYouTubePackage(pkg))
            ?: ""

        val isYtApp = isKnownYouTubePackage(pkg)
        val hasRecentBrowserHint = (System.currentTimeMillis() - (browserYouTubeHints[pkg] ?: 0L)) in 0L..120_000L
        val isBrowserYt = forceBrowserYt ||
            extractedVideoId.isNotBlank() ||
            OfflineYouTubeHistoryManager.isYouTubeUrl(mediaUri) ||
            OfflineYouTubeHistoryManager.isYouTubeUrl(artUri) ||
            album.contains("youtube", ignoreCase = true) ||
            rawTitle.endsWith(" - YouTube", ignoreCase = true) ||
            (isKnownBrowserPackage(pkg) && hasRecentBrowserHint)

        if (!isYtApp && !isBrowserYt) return

        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val positionMs = playbackState?.position?.coerceAtLeast(0L) ?: 0L

        // Throttle redundant position-only updates for the exact same video within 8 seconds of playback delta
        val throttleKey = "$pkg|${extractedVideoId.ifBlank { rawTitle.lowercase() }}"
        val prevPos = lastLoggedPosition[throttleKey]
        if (prevPos != null && kotlin.math.abs(positionMs - prevPos) < 8_000L && stateCode == PlaybackState.STATE_PLAYING) {
            return
        }
        lastLoggedPosition[throttleKey] = positionMs

        val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val sourceLabel = friendlyAppLabel(pkg)
        val resolvedUrl = if (extractedVideoId.isNotBlank()) {
            "https://www.youtube.com/watch?v=$extractedVideoId"
        } else if (OfflineYouTubeHistoryManager.isYouTubeUrl(mediaUri)) {
            mediaUri
        } else {
            ""
        }

        ioExecutor.execute {
            OfflineYouTubeHistoryManager.recordWatch(
                context = applicationContext,
                title = rawTitle,
                channel = rawChannel,
                videoId = extractedVideoId,
                url = resolvedUrl,
                durationMs = durationMs,
                positionMs = positionMs,
                sourceApp = sourceLabel,
                thumbnailBitmap = artBitmap,
            )
        }
    }

    companion object {
        private val KNOWN_YT_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.youtube.kids",
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
