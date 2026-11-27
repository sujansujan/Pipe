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
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Passive, 100% offline YouTube Watch History listener using Android's `NotificationListenerService`
 * and `MediaSessionManager`.
 *
 * - Observes active `MediaSession` controllers from YouTube, YouTube ReVanced / RVX, YT Music,
 *   NewPipe, PipePipe, LibreTube, Grayjay, SmartTube, and browsers playing YouTube tabs.
 * - Extracts exact title, channel, duration, playback position (extrapolated via
 *   `position + (elapsedRealtime - lastPositionUpdateTime) * playbackSpeed`), video ID
 *   (from `MEDIA_URI`, `MEDIA_ID`, `ART_URI` / `i.ytimg.com/vi/<ID>/...`, or recent Pipe link correlation),
 *   and saves the in-memory `MediaMetadata` thumbnail bitmap locally with zero network requests.
 * - Automatically flushes live watch position on video switches, pause, stop, session destruction
 *   (e.g. swiping YouTube away without pausing), and lightweight 15-second checkpoints while playing
 *   (checkpoint timer is cancelled immediately when nothing is playing -> 0% idle battery).
 */
class YouTubeWatchListenerService : NotificationListenerService() {

    private data class ActiveVideoSnapshot(
        val packageName: String,
        val title: String,
        val channel: String,
        val videoId: String,
        val url: String,
        val durationMs: Long,
        val sourceApp: String,
        val basePositionMs: Long,
        val lastUpdateElapsedMs: Long,
        val playbackSpeed: Float,
        val isPlaying: Boolean,
    ) {
        val identityKey: String
            get() = "$packageName|${videoId.ifBlank { "${title.lowercase()}|${channel.lowercase()}" }}"

        fun currentEstimatedPositionMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long =
            estimatePositionMs(
                basePositionMs = basePositionMs,
                lastUpdateElapsedMs = lastUpdateElapsedMs,
                playbackSpeed = playbackSpeed,
                isPlaying = isPlaying,
                durationMs = durationMs,
                nowElapsedMs = nowElapsedMs,
            )
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val trackedControllers = ConcurrentHashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val activeSnapshots = ConcurrentHashMap<MediaSession.Token, ActiveVideoSnapshot>()
    private val browserYouTubeHints = ConcurrentHashMap<String, Long>()
    private val lastLoggedPosition = ConcurrentHashMap<String, Long>()

    private val checkpointLock = Any()
    private var checkpointFuture: ScheduledFuture<*>? = null

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        attachMediaSessionManager()
    }

    override fun onListenerDisconnected() {
        flushAllActiveSnapshots()
        detachMediaSessionManager()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        flushAllActiveSnapshots()
        detachMediaSessionManager()
        stopCheckpointTicker()
        scheduler.shutdown()
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
            val repo = PipeRepository(applicationContext)
            if (!repo.loadYouTubeHistoryEnabled()) return
            val source = friendlyAppLabel(pkg)
            val fallbackChannel = text.takeIf { !it.contains("youtube.com", ignoreCase = true) }.orEmpty()
            scheduler.execute {
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
        activeSnapshots.clear()
        stopCheckpointTicker()
    }

    private fun syncControllers(active: List<MediaController>) {
        val activeTokens = active.map { it.sessionToken }.toSet()
        val staleTokens = trackedControllers.keys.filter { it !in activeTokens }
        for (token in staleTokens) {
            activeSnapshots.remove(token)?.let { flushSnapshotPosition(it, force = true) }
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
        refreshCheckpointTicker()
    }

    private fun registerController(controller: MediaController) {
        val token = controller.sessionToken
        if (trackedControllers.containsKey(token)) return
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                captureFromController(controller)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val st = state?.state ?: PlaybackState.STATE_NONE
                if (st == PlaybackState.STATE_STOPPED || st == PlaybackState.STATE_NONE) {
                    handleSessionStopped(token, state)
                } else if (st == PlaybackState.STATE_PLAYING ||
                    st == PlaybackState.STATE_PAUSED ||
                    st == PlaybackState.STATE_BUFFERING
                ) {
                    captureFromController(controller)
                }
            }

            override fun onSessionDestroyed() {
                activeSnapshots.remove(token)?.let { flushSnapshotPosition(it, force = true) }
                trackedControllers.remove(token)?.let { (c, cb) ->
                    runCatching { c.unregisterCallback(cb) }
                }
                refreshCheckpointTicker()
            }
        }
        trackedControllers[token] = controller to callback
        runCatching { controller.registerCallback(callback) }
    }

    private fun handleSessionStopped(token: MediaSession.Token, state: PlaybackState?) {
        val prev = activeSnapshots[token] ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        val explicitPos = state?.position?.takeIf { it > 0L }
        val finalPos = explicitPos ?: prev.currentEstimatedPositionMs(nowElapsed)
        val stoppedSnap = prev.copy(
            basePositionMs = finalPos,
            lastUpdateElapsedMs = nowElapsed,
            playbackSpeed = 0f,
            isPlaying = false,
        )
        activeSnapshots[token] = stoppedSnap
        flushSnapshotPosition(stoppedSnap, force = true)
        refreshCheckpointTicker()
    }

    private fun captureFromController(
        controller: MediaController,
        forceBrowserYt: Boolean = false,
    ) {
        val repo = PipeRepository(applicationContext)
        if (!repo.loadYouTubeHistoryEnabled()) return

        val token = controller.sessionToken
        val pkg = controller.packageName.orEmpty()
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState
        val stateCode = playbackState?.state ?: PlaybackState.STATE_NONE

        if (stateCode == PlaybackState.STATE_STOPPED || stateCode == PlaybackState.STATE_NONE) {
            handleSessionStopped(token, playbackState)
            return
        }
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
            ?: extractVideoIdFromMetadata(metadata)
            ?: extractVideoIdFromBundle(controller.extras, allowBareForIdKeys = isKnownYouTubePackage(pkg))
            ?: extractVideoIdFromBundle(playbackState?.extras, allowBareForIdKeys = isKnownYouTubePackage(pkg))
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
        val nowElapsed = SystemClock.elapsedRealtime()
        val isPlaying = stateCode == PlaybackState.STATE_PLAYING
        val basePos = playbackState?.position?.coerceAtLeast(0L) ?: 0L
        val lastUpdateElapsed = playbackState?.lastPositionUpdateTime?.takeIf { it > 0L } ?: nowElapsed
        val speed = playbackState?.playbackSpeed ?: if (isPlaying) 1f else 0f
        val estimatedPosMs = estimatePositionMs(
            basePositionMs = basePos,
            lastUpdateElapsedMs = lastUpdateElapsed,
            playbackSpeed = speed,
            isPlaying = isPlaying,
            durationMs = durationMs,
            nowElapsedMs = nowElapsed,
        )

        val sourceLabel = friendlyAppLabel(pkg)
        val resolvedUrl = if (extractedVideoId.isNotBlank()) {
            "https://www.youtube.com/watch?v=$extractedVideoId"
        } else if (OfflineYouTubeHistoryManager.isYouTubeUrl(mediaUri)) {
            mediaUri
        } else {
            ""
        }

        val newSnapshot = ActiveVideoSnapshot(
            packageName = pkg,
            title = rawTitle,
            channel = rawChannel,
            videoId = extractedVideoId,
            url = resolvedUrl,
            durationMs = durationMs,
            sourceApp = sourceLabel,
            basePositionMs = basePos,
            lastUpdateElapsedMs = lastUpdateElapsed,
            playbackSpeed = speed,
            isPlaying = isPlaying,
        )

        // If the user switched directly from another video without pausing, flush the previous video's final position first!
        val prevSnapshot = activeSnapshots.put(token, newSnapshot)
        val isVideoSwitch = prevSnapshot != null && prevSnapshot.identityKey != newSnapshot.identityKey
        if (isVideoSwitch && prevSnapshot != null) {
            flushSnapshotPosition(prevSnapshot, force = true)
        }
        refreshCheckpointTicker()

        val throttleKey = newSnapshot.identityKey
        val prevLoggedPos = lastLoggedPosition[throttleKey]
        val isFirstForVideo = prevLoggedPos == null || isVideoSwitch
        if (prevLoggedPos != null && !isVideoSwitch && stateCode == PlaybackState.STATE_PLAYING && kotlin.math.abs(estimatedPosMs - prevLoggedPos) < 5_000L) {
            return
        }
        lastLoggedPosition[throttleKey] = estimatedPosMs

        val artBitmap = if (isFirstForVideo) {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } else {
            null
        }

        scheduler.execute {
            if (isFirstForVideo) {
                val saved = OfflineYouTubeHistoryManager.recordWatch(
                    context = applicationContext,
                    title = rawTitle,
                    channel = rawChannel,
                    videoId = extractedVideoId,
                    url = resolvedUrl,
                    durationMs = durationMs,
                    positionMs = estimatedPosMs,
                    sourceApp = sourceLabel,
                    thumbnailBitmap = artBitmap,
                )
                // If YouTube / ReVanced omitted videoId in MediaSession, resolve & cache it once in the background
                // so future offline opens jump straight to `watch?v=<id>&t=<sec>s`!
                if (saved != null && !saved.hasDirectVideoId && repo.loadYouTubeResolveVideoId()) {
                    val upgraded = OfflineYouTubeHistoryManager.resolveAndAttachVideoId(applicationContext, saved)
                    if (upgraded.hasDirectVideoId) {
                        val cur = activeSnapshots[token]
                        if (cur != null && cur.identityKey == newSnapshot.identityKey && cur.videoId.isBlank()) {
                            activeSnapshots[token] = cur.copy(
                                videoId = upgraded.videoId,
                                url = upgraded.watchUrl,
                            )
                        }
                    }
                }
            } else {
                OfflineYouTubeHistoryManager.updateWatchPosition(
                    context = applicationContext,
                    title = rawTitle,
                    channel = rawChannel,
                    videoId = extractedVideoId,
                    url = resolvedUrl,
                    positionMs = estimatedPosMs,
                    durationMs = durationMs,
                    sourceApp = sourceLabel,
                )
            }
        }
    }

    private fun extractVideoIdFromMetadata(metadata: MediaMetadata): String? {
        val keys = runCatching { metadata.keySet() }.getOrNull() ?: return null
        for (key in keys) {
            val str = runCatching { metadata.getString(key) }.getOrNull() ?: continue
            val allowBare = key.contains("id", ignoreCase = true) || key.contains("video", ignoreCase = true)
            OfflineYouTubeHistoryManager.extractYouTubeVideoId(str, allowBareId = allowBare)?.let { return it }
        }
        return null
    }

    private fun extractVideoIdFromBundle(bundle: Bundle?, allowBareForIdKeys: Boolean): String? {
        if (bundle == null) return null
        val keys = runCatching { bundle.keySet() }.getOrNull() ?: return null
        for (key in keys) {
            val rawVal = runCatching { @Suppress("DEPRECATION") bundle.get(key) }.getOrNull() ?: continue
            val str = rawVal.toString()
            if (str.isBlank()) continue
            val allowBare = allowBareForIdKeys && (key.contains("video", ignoreCase = true) || key.contains("media_id", ignoreCase = true))
            OfflineYouTubeHistoryManager.extractYouTubeVideoId(str, allowBareId = allowBare)?.let { return it }
        }
        return null
    }

    private fun flushSnapshotPosition(snapshot: ActiveVideoSnapshot, force: Boolean) {
        val repo = PipeRepository(applicationContext)
        if (!repo.loadYouTubeHistoryEnabled()) return
        val estimatedPos = snapshot.currentEstimatedPositionMs()
        if (estimatedPos <= 0L) return

        val throttleKey = snapshot.identityKey
        val prevLogged = lastLoggedPosition[throttleKey]
        if (!force && prevLogged != null && kotlin.math.abs(estimatedPos - prevLogged) < 5_000L) {
            return
        }
        lastLoggedPosition[throttleKey] = estimatedPos

        runCatching {
            scheduler.execute {
                OfflineYouTubeHistoryManager.updateWatchPosition(
                    context = applicationContext,
                    title = snapshot.title,
                    channel = snapshot.channel,
                    videoId = snapshot.videoId,
                    url = snapshot.url,
                    positionMs = estimatedPos,
                    durationMs = snapshot.durationMs,
                    sourceApp = snapshot.sourceApp,
                )
            }
        }
    }

    private fun flushAllActiveSnapshots() {
        for (snap in activeSnapshots.values) {
            flushSnapshotPosition(snap, force = true)
        }
    }

    private fun refreshCheckpointTicker() {
        val anyPlaying = activeSnapshots.values.any { it.isPlaying }
        synchronized(checkpointLock) {
            if (anyPlaying) {
                if (checkpointFuture == null || checkpointFuture?.isCancelled == true || checkpointFuture?.isDone == true) {
                    checkpointFuture = runCatching {
                        scheduler.scheduleAtFixedRate(
                            { runPlayingCheckpoints() },
                            CHECKPOINT_INTERVAL_SECONDS,
                            CHECKPOINT_INTERVAL_SECONDS,
                            TimeUnit.SECONDS,
                        )
                    }.getOrNull()
                }
            } else {
                stopCheckpointTickerLocked()
            }
        }
    }

    private fun stopCheckpointTicker() {
        synchronized(checkpointLock) {
            stopCheckpointTickerLocked()
        }
    }

    private fun stopCheckpointTickerLocked() {
        checkpointFuture?.cancel(false)
        checkpointFuture = null
    }

    private fun runPlayingCheckpoints() {
        val repo = PipeRepository(applicationContext)
        if (!repo.loadYouTubeHistoryEnabled()) return
        val playing = activeSnapshots.values.filter { it.isPlaying }
        if (playing.isEmpty()) {
            stopCheckpointTicker()
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        for (snap in playing) {
            val est = snap.currentEstimatedPositionMs(nowElapsed)
            val prev = lastLoggedPosition[snap.identityKey]
            if (est > 0L && (prev == null || kotlin.math.abs(est - prev) >= 5_000L)) {
                lastLoggedPosition[snap.identityKey] = est
                OfflineYouTubeHistoryManager.updateWatchPosition(
                    context = applicationContext,
                    title = snap.title,
                    channel = snap.channel,
                    videoId = snap.videoId,
                    url = snap.url,
                    positionMs = est,
                    durationMs = snap.durationMs,
                    sourceApp = snap.sourceApp,
                )
            }
        }
    }

    companion object {
        private const val CHECKPOINT_INTERVAL_SECONDS = 15L

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
