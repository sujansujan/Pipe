package com.sujan.pipe.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Process-wide singleton `MediaSessionManager` tracker for YouTube, YouTube Morphe, ReVanced,
 * RVX, YT Music, NewPipe, PipePipe, LibreTube, Grayjay, and browsers playing YouTube.
 *
 * Key reliability guarantees:
 * 1. Attaches `MediaSessionManager.addOnActiveSessionsChangedListener` directly from Pipe's process
 *    using our `NotificationListenerService` component — works even on Xiaomi HyperOS when the OS
 *    defers or skips binding `YouTubeWatchListenerService`.
 * 2. Properly handles YouTube Morphe's exact exit sequence (where Morphe emits `metadata = null`
 *    and `state = STOPPED, position = 0` when closing the player): flushes the last tracked live
 *    position BEFORE clearing the snapshot, never overwriting a watched timestamp with 0.
 * 3. Polls live `controller.playbackState` every 4 seconds while playing (cancelled immediately
 *    when paused/stopped for 0% idle battery) so live watch progress is continuously persisted.
 */
object YouTubeMediaSessionTracker {

    private const val TAG = "PipeYT"
    private const val CHECKPOINT_INTERVAL_SECONDS = 4L

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
            YouTubeWatchListenerService.estimatePositionMs(
                basePositionMs = basePositionMs,
                lastUpdateElapsedMs = lastUpdateElapsedMs,
                playbackSpeed = playbackSpeed,
                isPlaying = isPlaying,
                durationMs = durationMs,
                nowElapsedMs = nowElapsedMs,
            )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val trackedControllers = ConcurrentHashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val activeSnapshots = ConcurrentHashMap<MediaSession.Token, ActiveVideoSnapshot>()
    private val browserYouTubeHints = ConcurrentHashMap<String, Long>()
    private val lastLoggedPosition = ConcurrentHashMap<String, Long>()
    private val resolvingIds = ConcurrentHashMap.newKeySet<String>()

    private val attachLock = Any()
    private val checkpointLock = Any()
    private var checkpointFuture: ScheduledFuture<*>? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var isListenerRegistered: Boolean = false

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    val isActivelyTracking: Boolean
        get() = isListenerRegistered || YouTubeWatchListenerService.isServiceConnected

    fun markBrowserYouTubeHint(pkg: String) {
        browserYouTubeHints[pkg] = System.currentTimeMillis()
    }

    /**
     * Attaches the process-wide `MediaSessionManager` listener (idempotent) and immediately
     * snapshots any active or recently stopped YouTube / Morphe sessions.
     */
    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!OfflineYouTubeHistoryManager.isNotificationListenerEnabled(ctx)) return
        val repo = PipeRepository(ctx)
        if (!repo.loadYouTubeHistoryEnabled()) return

        val component = ComponentName(ctx, YouTubeWatchListenerService::class.java)
        if (!YouTubeWatchListenerService.isServiceConnected) {
            runCatching { NotificationListenerService.requestRebind(component) }
        }

        synchronized(attachLock) {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            mediaSessionManager = msm

            if (!isListenerRegistered) {
                val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    syncControllers(ctx, controllers.orEmpty())
                }
                sessionsListener = listener
                runCatching {
                    msm.addOnActiveSessionsChangedListener(listener, component, mainHandler)
                    isListenerRegistered = true
                    Log.d(TAG, "YouTubeMediaSessionTracker attached OnActiveSessionsChangedListener")
                }.onFailure { err ->
                    Log.w(TAG, "addOnActiveSessionsChangedListener failed: ${err.message}")
                }
            }

            runCatching {
                val current = msm.getActiveSessions(component)
                Log.d(TAG, "YouTubeMediaSessionTracker poll: ${current.size} active sessions")
                syncControllers(ctx, current)
            }.onFailure { err ->
                Log.w(TAG, "getActiveSessions failed: ${err.message}")
            }
        }
    }

    fun attachControllerFromNotification(
        context: Context,
        token: MediaSession.Token,
        forceBrowserYt: Boolean = false,
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        runCatching {
            val controller = MediaController(ctx, token)
            registerController(ctx, controller)
            captureFromController(ctx, controller, forceBrowserYt = forceBrowserYt)
        }
    }

    fun flushAll() {
        val ctx = appContext ?: return
        for (snap in activeSnapshots.values) {
            flushSnapshotPosition(ctx, snap, force = true)
        }
    }

    fun snapshotNow(context: Context): Int {
        val ctx = context.applicationContext
        start(ctx)
        val component = ComponentName(ctx, YouTubeWatchListenerService::class.java)
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return 0
        val controllers = runCatching { msm.getActiveSessions(component) }.getOrNull().orEmpty()
        var count = 0
        for (c in controllers) {
            val pkg = c.packageName.orEmpty()
            if (YouTubeWatchListenerService.isKnownYouTubePackage(pkg) || YouTubeWatchListenerService.isKnownBrowserPackage(pkg)) {
                registerController(ctx, c)
                if (captureFromController(ctx, c)) count++
            }
        }
        return count
    }

    private fun syncControllers(ctx: Context, active: List<MediaController>) {
        val activeTokens = active.map { it.sessionToken }.toSet()
        val staleTokens = trackedControllers.keys.filter { it !in activeTokens }
        for (token in staleTokens) {
            activeSnapshots.remove(token)?.let { flushSnapshotPosition(ctx, it, force = true) }
            trackedControllers.remove(token)?.let { (ctrl, cb) ->
                runCatching { ctrl.unregisterCallback(cb) }
            }
        }
        for (controller in active) {
            val pkg = controller.packageName.orEmpty()
            if (YouTubeWatchListenerService.isKnownYouTubePackage(pkg) || YouTubeWatchListenerService.isKnownBrowserPackage(pkg)) {
                registerController(ctx, controller)
                captureFromController(ctx, controller)
            }
        }
        refreshCheckpointTicker()
    }

    private fun registerController(ctx: Context, controller: MediaController) {
        val token = controller.sessionToken
        if (trackedControllers.containsKey(token)) return
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (metadata == null) {
                    // YouTube Morphe wipes metadata to null when dismissing player -> flush final position!
                    activeSnapshots.remove(token)?.let { flushSnapshotPosition(ctx, it, force = true) }
                    refreshCheckpointTicker()
                } else {
                    captureFromController(ctx, controller)
                }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val st = state?.state ?: PlaybackState.STATE_NONE
                if (st == PlaybackState.STATE_NONE || st == PlaybackState.STATE_ERROR) {
                    handleSessionEnded(ctx, token, controller, state)
                } else {
                    captureFromController(ctx, controller)
                }
            }

            override fun onSessionDestroyed() {
                activeSnapshots.remove(token)?.let { flushSnapshotPosition(ctx, it, force = true) }
                trackedControllers.remove(token)?.let { (c, cb) ->
                    runCatching { c.unregisterCallback(cb) }
                }
                refreshCheckpointTicker()
            }
        }
        trackedControllers[token] = controller to callback
        runCatching { controller.registerCallback(callback, mainHandler) }
    }

    private fun handleSessionEnded(
        ctx: Context,
        token: MediaSession.Token,
        controller: MediaController,
        state: PlaybackState?,
    ) {
        val prev = activeSnapshots[token]
        if (prev == null) {
            captureFromController(ctx, controller)
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        val explicitPos = state?.position?.takeIf { it > 0L }
        val finalPos = explicitPos ?: prev.currentEstimatedPositionMs(nowElapsed)
        val endedSnap = prev.copy(
            basePositionMs = finalPos,
            lastUpdateElapsedMs = nowElapsed,
            playbackSpeed = 0f,
            isPlaying = false,
        )
        activeSnapshots[token] = endedSnap
        flushSnapshotPosition(ctx, endedSnap, force = true)
        refreshCheckpointTicker()
    }

    private fun captureFromController(
        ctx: Context,
        controller: MediaController,
        forceBrowserYt: Boolean = false,
    ): Boolean {
        val repo = PipeRepository(ctx)
        if (!repo.loadYouTubeHistoryEnabled()) return false

        val token = controller.sessionToken
        val pkg = controller.packageName.orEmpty()
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val stateCode = playbackState?.state ?: PlaybackState.STATE_NONE

        val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

        // If YouTube Morphe cleared metadata to empty/null upon dismissing player, flush previous video's position!
        if (rawTitle.isBlank()) {
            activeSnapshots.remove(token)?.let { prev ->
                val explicitPos = playbackState?.position?.takeIf { it > 0L }
                val finalSnap = if (explicitPos != null) {
                    prev.copy(basePositionMs = explicitPos, playbackSpeed = 0f, isPlaying = false)
                } else {
                    prev
                }
                flushSnapshotPosition(ctx, finalSnap, force = true)
            }
            refreshCheckpointTicker()
            return false
        }

        val rawChannel = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""

        val mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
        val mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
        val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

        val extractedVideoId = OfflineYouTubeHistoryManager.extractYouTubeVideoId(mediaUri)
            ?: OfflineYouTubeHistoryManager.extractYouTubeVideoId(artUri)
            ?: OfflineYouTubeHistoryManager.extractYouTubeVideoId(mediaId, allowBareId = YouTubeWatchListenerService.isKnownYouTubePackage(pkg))
            ?: extractVideoIdFromMetadataStrings(metadata)
            ?: extractVideoIdFromBundle(controller.extras, allowBareForIdKeys = YouTubeWatchListenerService.isKnownYouTubePackage(pkg))
            ?: extractVideoIdFromBundle(playbackState?.extras, allowBareForIdKeys = YouTubeWatchListenerService.isKnownYouTubePackage(pkg))
            ?: ""

        val isYtApp = YouTubeWatchListenerService.isKnownYouTubePackage(pkg)
        val hasRecentBrowserHint = (System.currentTimeMillis() - (browserYouTubeHints[pkg] ?: 0L)) in 0L..120_000L
        val isBrowserYt = forceBrowserYt ||
            extractedVideoId.isNotBlank() ||
            OfflineYouTubeHistoryManager.isYouTubeUrl(mediaUri) ||
            OfflineYouTubeHistoryManager.isYouTubeUrl(artUri) ||
            album.contains("youtube", ignoreCase = true) ||
            rawTitle.endsWith(" - YouTube", ignoreCase = true) ||
            (YouTubeWatchListenerService.isKnownBrowserPackage(pkg) && hasRecentBrowserHint)

        if (!isYtApp && !isBrowserYt) return false

        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val nowElapsed = SystemClock.elapsedRealtime()
        val isPlaying = stateCode == PlaybackState.STATE_PLAYING
        val isTerminalOrPause = stateCode == PlaybackState.STATE_PAUSED ||
            stateCode == PlaybackState.STATE_STOPPED ||
            stateCode == PlaybackState.STATE_NONE

        val rawPos = playbackState?.position?.coerceAtLeast(0L) ?: 0L
        val lastUpdateElapsed = playbackState?.lastPositionUpdateTime?.takeIf { it > 0L } ?: nowElapsed
        val speed = if (isPlaying) (playbackState?.playbackSpeed ?: 1f) else 0f

        val prevSnapshotForToken = activeSnapshots[token]
        val isSameVideoAsPrev = prevSnapshotForToken != null &&
            prevSnapshotForToken.title.equals(rawTitle, ignoreCase = true) &&
            (prevSnapshotForToken.channel.equals(rawChannel, ignoreCase = true) || prevSnapshotForToken.channel.isBlank() || rawChannel.isBlank())

        // Never lose tracked position when YouTube Morphe emits position=0 during stop/pause/dismiss!
        val estimatedPosMs = if (isTerminalOrPause && rawPos == 0L && isSameVideoAsPrev && prevSnapshotForToken != null) {
            prevSnapshotForToken.currentEstimatedPositionMs(nowElapsed)
        } else {
            YouTubeWatchListenerService.estimatePositionMs(
                basePositionMs = rawPos,
                lastUpdateElapsedMs = lastUpdateElapsed,
                playbackSpeed = speed,
                isPlaying = isPlaying,
                durationMs = durationMs,
                nowElapsedMs = nowElapsed,
            )
        }

        val sourceLabel = YouTubeWatchListenerService.friendlyAppLabel(pkg)
        val carryVideoId = extractedVideoId.ifBlank {
            if (isSameVideoAsPrev) prevSnapshotForToken?.videoId.orEmpty() else ""
        }
        val resolvedUrl = if (carryVideoId.isNotBlank()) {
            "https://www.youtube.com/watch?v=$carryVideoId"
        } else if (OfflineYouTubeHistoryManager.isYouTubeUrl(mediaUri)) {
            mediaUri
        } else {
            ""
        }

        val newSnapshot = ActiveVideoSnapshot(
            packageName = pkg,
            title = rawTitle,
            channel = rawChannel,
            videoId = carryVideoId,
            url = resolvedUrl,
            durationMs = durationMs,
            sourceApp = sourceLabel,
            basePositionMs = if (isPlaying) rawPos else estimatedPosMs,
            lastUpdateElapsedMs = if (isPlaying) lastUpdateElapsed else nowElapsed,
            playbackSpeed = speed,
            isPlaying = isPlaying,
        )

        val prevSnapshot = activeSnapshots.put(token, newSnapshot)
        val isVideoSwitch = prevSnapshot != null && prevSnapshot.identityKey != newSnapshot.identityKey
        if (isVideoSwitch && prevSnapshot != null) {
            flushSnapshotPosition(ctx, prevSnapshot, force = true)
        }
        refreshCheckpointTicker()

        val throttleKey = newSnapshot.identityKey
        val prevLoggedPos = lastLoggedPosition[throttleKey]
        val isFirstForVideo = prevLoggedPos == null || isVideoSwitch
        if (prevLoggedPos != null && !isVideoSwitch && !isTerminalOrPause &&
            kotlin.math.abs(estimatedPosMs - prevLoggedPos) < 3_000L
        ) {
            return true
        }
        lastLoggedPosition[throttleKey] = estimatedPosMs

        val artBitmap = if (isFirstForVideo) {
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } else {
            null
        }

        Log.d(TAG, "capture [$sourceLabel] state=$stateCode pos=${estimatedPosMs / 1000}s/${durationMs / 1000}s vid='$carryVideoId' title='${rawTitle.take(40)}'")

        scheduler.execute {
            if (isFirstForVideo) {
                val saved = OfflineYouTubeHistoryManager.recordWatch(
                    context = ctx,
                    title = rawTitle,
                    channel = rawChannel,
                    videoId = carryVideoId,
                    url = resolvedUrl,
                    durationMs = durationMs,
                    positionMs = estimatedPosMs,
                    sourceApp = sourceLabel,
                    thumbnailBitmap = artBitmap,
                )
                if (saved != null && !saved.hasDirectVideoId && repo.loadYouTubeResolveVideoId()) {
                    resolveInBackgroundOnce(ctx, token, newSnapshot.identityKey, saved)
                }
            } else {
                OfflineYouTubeHistoryManager.updateWatchPosition(
                    context = ctx,
                    title = rawTitle,
                    channel = rawChannel,
                    videoId = carryVideoId,
                    url = resolvedUrl,
                    positionMs = estimatedPosMs,
                    durationMs = durationMs,
                    sourceApp = sourceLabel,
                )
            }
        }
        return true
    }

    private fun resolveInBackgroundOnce(
        ctx: Context,
        token: MediaSession.Token,
        identityKey: String,
        entry: com.sujan.pipe.model.YouTubeWatchEntry,
    ) {
        if (!resolvingIds.add(entry.id)) return
        try {
            val upgraded = OfflineYouTubeHistoryManager.resolveAndAttachVideoId(ctx, entry)
            Log.d(TAG, "bg resolve '${entry.title.take(32)}' -> vid='${upgraded.videoId}'")
            if (upgraded.hasDirectVideoId) {
                val cur = activeSnapshots[token]
                if (cur != null && cur.identityKey == identityKey && cur.videoId.isBlank()) {
                    activeSnapshots[token] = cur.copy(
                        videoId = upgraded.videoId,
                        url = upgraded.watchUrl,
                    )
                }
            }
        } finally {
            resolvingIds.remove(entry.id)
        }
    }

    private fun flushSnapshotPosition(ctx: Context, snapshot: ActiveVideoSnapshot, force: Boolean) {
        val repo = PipeRepository(ctx)
        if (!repo.loadYouTubeHistoryEnabled()) return
        val estimatedPos = snapshot.currentEstimatedPositionMs()
        if (estimatedPos <= 0L) return

        val throttleKey = snapshot.identityKey
        val prevLogged = lastLoggedPosition[throttleKey]
        if (!force && prevLogged != null && kotlin.math.abs(estimatedPos - prevLogged) < 3_000L) {
            return
        }
        lastLoggedPosition[throttleKey] = estimatedPos
        Log.d(TAG, "flush [${snapshot.sourceApp}] pos=${estimatedPos / 1000}s/${snapshot.durationMs / 1000}s vid='${snapshot.videoId}' title='${snapshot.title.take(36)}'")

        runCatching {
            scheduler.execute {
                OfflineYouTubeHistoryManager.updateWatchPosition(
                    context = ctx,
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
                checkpointFuture?.cancel(false)
                checkpointFuture = null
            }
        }
    }

    private fun runPlayingCheckpoints() {
        val ctx = appContext ?: return
        val repo = PipeRepository(ctx)
        if (!repo.loadYouTubeHistoryEnabled()) return
        val nowElapsed = SystemClock.elapsedRealtime()
        var stillAnyPlaying = false

        for ((token, snap) in activeSnapshots.entries) {
            val controller = trackedControllers[token]?.first
            val liveState = runCatching { controller?.playbackState }.getOrNull()
            val liveStateCode = liveState?.state
            val livePlaying = if (liveStateCode != null) liveStateCode == PlaybackState.STATE_PLAYING else snap.isPlaying

            val livePos = if (liveState != null && liveState.position > 0L) {
                YouTubeWatchListenerService.estimatePositionMs(
                    basePositionMs = liveState.position,
                    lastUpdateElapsedMs = liveState.lastPositionUpdateTime.takeIf { it > 0L } ?: nowElapsed,
                    playbackSpeed = if (livePlaying) liveState.playbackSpeed else 0f,
                    isPlaying = livePlaying,
                    durationMs = snap.durationMs,
                    nowElapsedMs = nowElapsed,
                )
            } else {
                snap.currentEstimatedPositionMs(nowElapsed)
            }

            if (livePlaying) {
                stillAnyPlaying = true
                if (liveState != null && liveState.position >= 0L) {
                    activeSnapshots[token] = snap.copy(
                        basePositionMs = liveState.position,
                        lastUpdateElapsedMs = liveState.lastPositionUpdateTime.takeIf { it > 0L } ?: nowElapsed,
                        playbackSpeed = liveState.playbackSpeed,
                        isPlaying = true,
                    )
                }
            } else if (snap.isPlaying) {
                activeSnapshots[token] = snap.copy(
                    basePositionMs = livePos,
                    lastUpdateElapsedMs = nowElapsed,
                    playbackSpeed = 0f,
                    isPlaying = false,
                )
            }

            val prev = lastLoggedPosition[snap.identityKey]
            if (livePos > 0L && (prev == null || kotlin.math.abs(livePos - prev) >= 3_000L || !livePlaying)) {
                lastLoggedPosition[snap.identityKey] = livePos
                Log.d(TAG, "checkpoint [${snap.sourceApp}] pos=${livePos / 1000}s/${snap.durationMs / 1000}s title='${snap.title.take(32)}'")
                OfflineYouTubeHistoryManager.updateWatchPosition(
                    context = ctx,
                    title = snap.title,
                    channel = snap.channel,
                    videoId = snap.videoId,
                    url = snap.url,
                    positionMs = livePos,
                    durationMs = snap.durationMs,
                    sourceApp = snap.sourceApp,
                )
            }
        }

        if (!stillAnyPlaying) {
            synchronized(checkpointLock) {
                checkpointFuture?.cancel(false)
                checkpointFuture = null
            }
        }
    }

    private val SAFE_STRING_METADATA_KEYS = listOf(
        MediaMetadata.METADATA_KEY_MEDIA_URI,
        MediaMetadata.METADATA_KEY_MEDIA_ID,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
        MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
    )

    private fun extractVideoIdFromMetadataStrings(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        for (key in SAFE_STRING_METADATA_KEYS) {
            val str = runCatching { metadata.getString(key) }.getOrNull() ?: continue
            val allowBare = key == MediaMetadata.METADATA_KEY_MEDIA_ID
            OfflineYouTubeHistoryManager.extractYouTubeVideoId(str, allowBareId = allowBare)?.let { return it }
        }
        return null
    }

    private fun extractVideoIdFromBundle(bundle: Bundle?, allowBareForIdKeys: Boolean): String? {
        if (bundle == null) return null
        val keys = runCatching { bundle.keySet() }.getOrNull() ?: return null
        for (key in keys) {
            val rawVal = runCatching { @Suppress("DEPRECATION") bundle.get(key) }.getOrNull() ?: continue
            if (rawVal !is CharSequence && rawVal !is android.net.Uri) continue
            val str = rawVal.toString()
            if (str.isBlank()) continue
            val allowBare = allowBareForIdKeys && (key.contains("video", ignoreCase = true) || key.contains("media_id", ignoreCase = true))
            OfflineYouTubeHistoryManager.extractYouTubeVideoId(str, allowBareId = allowBare)?.let { return it }
        }
        return null
    }
}
