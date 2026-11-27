package com.sujan.pipe.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.model.YouTubeWatchEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * 100% Offline YouTube Watch History Engine for `pipe`.
 *
 * Capabilities:
 * - Zero cloud/account/network dependency: all watched videos, positions, watch counts, and optional
 *   media-session thumbnails live locally in `filesDir/pipe_yt_history_v1.tsv` & `filesDir/yt_thumbs/`.
 * - Live offline capture from `YouTubeWatchListenerService` (`NotificationListenerService` + `MediaSessionManager`),
 *   plus automatic link/share capture from `WebSearchReceiverActivity`, `TextPipeActivity`, and `openUrlExcludingSelf`.
 * - Multi-format offline import: Google Takeout `watch-history.json` / `watch-history.html`,
 *   NewPipe / PipePipe / LibreTube / Grayjay JSON, FreeTube `history.db` (NDJSON), Pipe JSON/TSV, and CSV.
 * - Full offline export to JSON or TSV.
 */
object OfflineYouTubeHistoryManager {

    private const val HISTORY_TSV_FILENAME = "pipe_yt_history_v1.tsv"
    private const val THUMBS_DIR_NAME = "yt_thumbs"
    private const val MAX_STORED_ENTRIES = 50_000
    private const val MAX_CACHED_THUMBS_ON_DISK = 400
    private const val SESSION_REWATCH_WINDOW_MS = 10 * 60 * 1000L
    private const val PENDING_LINK_CORRELATION_MS = 60_000L

    private val writeLock = Any()

    @Volatile
    private var memoryCache: List<YouTubeWatchEntry>? = null

    @Volatile
    private var hasPendingDiskChanges: Boolean = false

    @Volatile
    private var pendingRecentLink: PendingLinkHint? = null

    private val thumbBitmapCache = object : LruCache<String, Bitmap>(48) {}
    private val failedResolveTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val FAILED_RESOLVE_COOLDOWN_MS = 20_000L

    data class YouTubeSearchCandidate(
        val videoId: String,
        val title: String,
        val channel: String = "",
        val durationMs: Long = 0L,
    )

    private data class PendingLinkHint(
        val videoId: String,
        val url: String,
        val titleHint: String,
        val timestampMs: Long,
    )

    private val YT_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    private val YT_WATCH_PARAM_REGEX = Regex("[?&]v=([A-Za-z0-9_-]{11})")
    private val YT_SHORT_URL_REGEX = Regex(
        "(?:youtu\\.be/|youtube\\.com/(?:shorts/|embed/|live/|v/)|ytimg\\.com/vi(?:_webp)?/)([A-Za-z0-9_-]{11})",
        RegexOption.IGNORE_CASE,
    )
    private val YT_ANY_URL_REGEX = Regex(
        "https?://(?:www\\.|m\\.|music\\.)?(?:youtube\\.com/[^\\s\"'<>]+|youtu\\.be/[^\\s\"'<>]+)",
        RegexOption.IGNORE_CASE,
    )
    private val TIMESTAMP_HMS_REGEX = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s?)?", RegexOption.IGNORE_CASE)
    private val HTML_ANCHOR_REGEX = Regex(
        "<a\\s+[^>]*href=\"(https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)[^\"]+)\"[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    private val IGNORED_TITLES = setOf(
        "advertisement",
        "ad",
        "youtube",
        "youtube music",
        "loading",
        "loading...",
        "watched a video that has been removed",
        "a video that has been removed",
        "watched a story that is no longer available",
        "a story that is no longer available",
        "watched a video that is no longer available",
        "a video that is no longer available",
        "a video that is private",
        "deleted video",
        "private video",
    )

    fun getHistoryFile(context: Context): File = File(context.filesDir, HISTORY_TSV_FILENAME)
    fun getThumbsDir(context: Context): File = File(context.filesDir, THUMBS_DIR_NAME)

    fun peekMemoryHistory(): List<YouTubeWatchEntry> = memoryCache ?: emptyList()

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledFlat = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        if (enabledFlat.isBlank()) return false
        val pkg = context.packageName
        return enabledFlat.split(':').any { raw ->
            val cn = ComponentName.unflattenFromString(raw.trim())
            cn?.packageName == pkg
        }
    }

    /**
     * Checks whether [rawUrl] points to a YouTube domain.
     */
    fun isYouTubeUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val lower = rawUrl.trim().lowercase()
        return lower.contains("youtube.com/") ||
            lower.contains("youtu.be/") ||
            lower.contains("ytimg.com/")
    }

    /**
     * Extracts the 11-character YouTube video ID from any YouTube watch/shorts/live/embed/youtu.be
     * or `i.ytimg.com/vi/<ID>/...` thumbnail URI.
     */
    fun extractYouTubeVideoId(raw: String?, allowBareId: Boolean = false): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (allowBareId && YT_ID_REGEX.matches(trimmed)) {
            return trimmed
        }
        YT_WATCH_PARAM_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        YT_SHORT_URL_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    /**
     * Finds the first YouTube URL embedded in arbitrary shared text (e.g. Share Sheet from YouTube app).
     */
    fun extractFirstYouTubeUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return YT_ANY_URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
    }

    /**
     * Parses YouTube `?t=123`, `&t=1m30s`, `&t=1h2m3s`, or `&start=90` into seconds.
     */
    fun extractTimestampSeconds(rawUrl: String?): Long {
        if (rawUrl.isNullOrBlank()) return 0L
        val paramMatch = Regex("[?&#](?:t|start)=([0-9hmsHMS]+)").find(rawUrl) ?: return 0L
        val rawVal = paramMatch.groupValues[1].trim()
        rawVal.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        val m = TIMESTAMP_HMS_REGEX.matchEntire(rawVal) ?: return 0L
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLongOrNull() ?: 0L
        val sec = m.groupValues[3].toLongOrNull() ?: 0L
        return (h * 3600L + min * 60L + sec).coerceAtLeast(0L)
    }

    /**
     * Generates a stable entry ID: the 11-char [videoId] when known, or a normalized `title|channel` key.
     */
    fun buildStableId(videoId: String, title: String, channel: String): String {
        val cleanVid = videoId.trim()
        if (cleanVid.length == 11 && YT_ID_REGEX.matches(cleanVid)) {
            return cleanVid
        }
        val normTitle = FuzzyMatcher.normalizeForSearch(title)
        val normChannel = FuzzyMatcher.normalizeForSearch(channel)
        return "meta:${normTitle}|${normChannel}"
    }

    /**
     * Loads all offline watch history entries (newest first) from RAM or disk TSV.
     */
    fun getCachedOrLoadHistory(context: Context): List<YouTubeWatchEntry> {
        memoryCache?.let { return it }
        synchronized(writeLock) {
            memoryCache?.let { return it }
            val loaded = readHistoryFileLocked(getHistoryFile(context))
            memoryCache = loaded
            return loaded
        }
    }

    /**
     * Fast offline fuzzy search over YouTube watch history (matches video title, channel, and videoId).
     */
    fun searchOnDemand(
        context: Context,
        rawQuery: String,
        limit: Int = 10,
    ): List<YouTubeWatchEntry> {
        val all = getCachedOrLoadHistory(context)
        return searchEntries(all, rawQuery, limit)
    }

    /**
     * Pure Kotlin fuzzy search over [entries] (unit-testable without Android Context).
     */
    fun searchEntries(
        entries: List<YouTubeWatchEntry>,
        rawQuery: String,
        limit: Int = 10,
    ): List<YouTubeWatchEntry> {
        if (entries.isEmpty()) return emptyList()
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) {
            return entries.sortedByDescending { it.watchedAtMs }.take(limit)
        }
        val scored = ArrayList<Pair<YouTubeWatchEntry, Int>>()
        for ((idx, entry) in entries.withIndex()) {
            val titleScore = FuzzyMatcher.scorePreLowered(q, entry.titleLower)
            val channelScore = if (entry.channelLower.isNotEmpty()) {
                val cs = FuzzyMatcher.scorePreLowered(q, entry.channelLower)
                if (cs > 0) (cs - 40).coerceAtLeast(1) else 0
            } else 0
            val idScore = if (entry.videoIdLower.isNotEmpty() && entry.videoIdLower.contains(q)) 920 else 0
            val combinedScore = if (q.indexOf(' ') >= 0 && entry.channelLower.isNotEmpty()) {
                FuzzyMatcher.scorePreLowered(q, "${entry.titleLower} ${entry.channelLower}")
            } else 0
            val best = maxOf(titleScore, channelScore, idScore, combinedScore)
            if (best > 0) {
                // Slight recency boost for newer watches among close fuzzy scores
                val recencyBonus = (120 - (idx / 5)).coerceIn(0, 120)
                val directIdBonus = if (entry.hasDirectVideoId) 15 else 0
                scored.add(entry to (best * 10 + recencyBonus + directIdBonus))
            }
        }
        return scored
            .sortedWith(
                compareByDescending<Pair<YouTubeWatchEntry, Int>> { it.second }
                    .thenByDescending { it.first.watchedAtMs }
            )
            .take(limit)
            .map { it.first }
    }

    /**
     * Records a YouTube link opened via Pipe (Default Browser / WebSearchReceiver / Share Sheet / openUrlExcludingSelf).
     * Also caches it briefly as a [pendingRecentLink] so if `YouTubeWatchListenerService` sees playback start
     * seconds later without an `ART_URI`/`MEDIA_ID`, it automatically attaches the exact `videoId`!
     */
    fun recordYouTubeUrlInteraction(
        context: Context,
        rawUrl: String,
        titleHint: String = "",
        channelHint: String = "",
        sourceApp: String = "Pipe",
    ): YouTubeWatchEntry? {
        val repo = PipeRepository(context)
        if (!repo.loadYouTubeHistoryEnabled()) return null
        val videoId = extractYouTubeVideoId(rawUrl) ?: return null
        val now = System.currentTimeMillis()
        val cleanTitle = cleanVideoTitle(titleHint)
        val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
        val startSecs = extractTimestampSeconds(rawUrl)

        pendingRecentLink = PendingLinkHint(
            videoId = videoId,
            url = cleanUrl,
            titleHint = cleanTitle,
            timestampMs = now,
        )

        return recordWatch(
            context = context,
            title = cleanTitle.ifBlank { "YouTube Video ($videoId)" },
            channel = channelHint.trim(),
            videoId = videoId,
            url = cleanUrl,
            durationMs = 0L,
            positionMs = startSecs * 1000L,
            sourceApp = sourceApp,
            watchedAtMs = now,
            thumbnailBitmap = null,
            allowPlaceholderTitle = true,
        )
    }

    /**
     * Records or updates a watched YouTube video in the offline local TSV store.
     */
    fun recordWatch(
        context: Context,
        title: String,
        channel: String = "",
        videoId: String = "",
        url: String = "",
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        sourceApp: String = "YouTube",
        watchedAtMs: Long = System.currentTimeMillis(),
        thumbnailBitmap: Bitmap? = null,
        allowPlaceholderTitle: Boolean = false,
    ): YouTubeWatchEntry? {
        val cleanTitle = cleanVideoTitle(title)
        val cleanChannel = channel.trim()
        if (cleanTitle.isBlank()) return null
        if (!allowPlaceholderTitle && cleanTitle.lowercase() in IGNORED_TITLES) return null

        var resolvedVideoId = extractYouTubeVideoId(videoId, allowBareId = true)
            ?: extractYouTubeVideoId(url)
            ?: ""

        // Correlate with a recently opened YouTube link if the player's MediaSession didn't expose videoId
        if (resolvedVideoId.isBlank()) {
            val hint = pendingRecentLink
            if (hint != null && (watchedAtMs - hint.timestampMs) in 0L..PENDING_LINK_CORRELATION_MS) {
                if (hint.titleHint.isBlank() ||
                    FuzzyMatcher.score(hint.titleHint, cleanTitle) >= 500 ||
                    (watchedAtMs - hint.timestampMs) <= 15_000L
                ) {
                    resolvedVideoId = hint.videoId
                    pendingRecentLink = null
                }
            }
        }

        val resolvedUrl = when {
            resolvedVideoId.isNotBlank() -> "https://www.youtube.com/watch?v=$resolvedVideoId"
            url.isNotBlank() -> url.trim()
            else -> ""
        }
        val stableId = buildStableId(resolvedVideoId, cleanTitle, cleanChannel)

        val saveThumbEnabled = runCatching { PipeRepository(context).loadYouTubeSaveThumbnails() }.getOrDefault(true)
        var savedThumbPath = ""
        if (saveThumbEnabled && thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
            savedThumbPath = saveThumbnailOffline(context, stableId, thumbnailBitmap).orEmpty()
        }

        val incoming = YouTubeWatchEntry(
            id = stableId,
            videoId = resolvedVideoId,
            title = cleanTitle,
            channel = cleanChannel,
            url = resolvedUrl,
            watchedAtMs = watchedAtMs,
            durationMs = durationMs.coerceAtLeast(0L),
            positionMs = positionMs.coerceAtLeast(0L),
            watchCount = 1,
            sourceApp = sourceApp.trim().ifBlank { "YouTube" },
            thumbnailPath = savedThumbPath,
        )

        synchronized(writeLock) {
            val current = memoryCache ?: readHistoryFileLocked(getHistoryFile(context))
            val updated = upsertSingleEntry(current, incoming)
            writeHistoryFileLocked(getHistoryFile(context), updated)
            memoryCache = updated
            hasPendingDiskChanges = false
            return updated.firstOrNull { it.id == stableId || (resolvedVideoId.isNotBlank() && it.videoId == resolvedVideoId) }
                ?: incoming
        }
    }

    /**
     * Updates the saved playback position (and optional duration) of an already-recorded video in place
     * without incrementing `watchCount` or reordering it above a newly started video.
     * When [flushToDisk] is `false`, updates RAM immediately (visible to `yth` right away) and defers
     * disk serialization until the next periodic 30s flush or pause/stop/switch event.
     */
    fun updateWatchPosition(
        context: Context,
        title: String,
        channel: String = "",
        videoId: String = "",
        url: String = "",
        positionMs: Long,
        durationMs: Long = 0L,
        sourceApp: String = "YouTube",
        flushToDisk: Boolean = true,
    ) {
        val cleanPos = positionMs.coerceAtLeast(0L)
        if (cleanPos <= 0L) return
        val cleanTitle = cleanVideoTitle(title)
        val cleanChannel = channel.trim()
        val resolvedVideoId = extractYouTubeVideoId(videoId, allowBareId = true)
            ?: extractYouTubeVideoId(url)
            ?: ""
        if (cleanTitle.isBlank() && resolvedVideoId.isBlank()) return

        synchronized(writeLock) {
            val current = memoryCache ?: readHistoryFileLocked(getHistoryFile(context))
            val (updated, found) = applyPositionUpdate(
                existing = current,
                videoId = resolvedVideoId,
                title = cleanTitle,
                channel = cleanChannel,
                positionMs = cleanPos,
                durationMs = durationMs.coerceAtLeast(0L),
            )
            if (found) {
                memoryCache = updated
                if (flushToDisk) {
                    writeHistoryFileLocked(getHistoryFile(context), updated)
                    hasPendingDiskChanges = false
                } else {
                    hasPendingDiskChanges = true
                }
                return
            }
        }

        if (cleanTitle.isNotBlank()) {
            recordWatch(
                context = context,
                title = cleanTitle,
                channel = cleanChannel,
                videoId = resolvedVideoId,
                url = url,
                durationMs = durationMs,
                positionMs = cleanPos,
                sourceApp = sourceApp,
            )
        }
    }

    fun flushPendingToDisk(context: Context) {
        if (!hasPendingDiskChanges) return
        synchronized(writeLock) {
            if (!hasPendingDiskChanges) return
            val current = memoryCache ?: return
            writeHistoryFileLocked(getHistoryFile(context), current)
            hasPendingDiskChanges = false
        }
    }

    /**
     * Pure helper that updates `positionMs` (and `durationMs` if > 0) for a matching entry in place
     * without changing order or `watchCount`.
     */
    fun applyPositionUpdate(
        existing: List<YouTubeWatchEntry>,
        videoId: String,
        title: String,
        channel: String,
        positionMs: Long,
        durationMs: Long = 0L,
    ): Pair<List<YouTubeWatchEntry>, Boolean> {
        val titleLower = title.trim().lowercase()
        val channelLower = channel.trim().lowercase()
        val stableId = buildStableId(videoId, title, channel)

        val matchIdx = existing.indexOfFirst { cur ->
            (videoId.isNotBlank() && cur.videoId == videoId) ||
                cur.id == stableId ||
                (titleLower.isNotEmpty() && cur.titleLower == titleLower &&
                    (cur.channelLower == channelLower || cur.channelLower.isEmpty() || channelLower.isEmpty()))
        }
        if (matchIdx < 0) return existing to false

        val prev = existing[matchIdx]
        val effectiveDuration = if (durationMs > 0L) durationMs else prev.durationMs
        val clampedPos = if (effectiveDuration > 0L) positionMs.coerceIn(0L, effectiveDuration) else positionMs.coerceAtLeast(0L)
        if (prev.positionMs == clampedPos && prev.durationMs == effectiveDuration) {
            return existing to true
        }
        val mutable = existing.toMutableList()
        mutable[matchIdx] = prev.copy(
            positionMs = clampedPos,
            durationMs = effectiveDuration,
        )
        return mutable to true
    }

    /**
     * Pure helper that attaches a newly resolved 11-char [resolvedVideoId] to an existing metadata-only
     * entry, upgrading its `id` and `url` while preserving `positionMs`, `durationMs`, `watchCount`,
     * `thumbnailPath`, and list position.
     */
    fun applyVideoIdResolution(
        existing: List<YouTubeWatchEntry>,
        targetEntryId: String,
        resolvedVideoId: String,
    ): Pair<List<YouTubeWatchEntry>, YouTubeWatchEntry?> {
        val cleanVid = extractYouTubeVideoId(resolvedVideoId, allowBareId = true) ?: return existing to null
        val matchIdx = existing.indexOfFirst { it.id == targetEntryId || it.videoId == cleanVid }
        if (matchIdx < 0) return existing to null

        val prev = existing[matchIdx]
        val upgraded = prev.copy(
            id = cleanVid,
            videoId = cleanVid,
            url = "https://www.youtube.com/watch?v=$cleanVid",
        )
        val out = ArrayList<YouTubeWatchEntry>(existing.size)
        for (i in existing.indices) {
            if (i == matchIdx) {
                out.add(upgraded)
            } else {
                val other = existing[i]
                if (other.id != cleanVid && other.videoId != cleanVid) {
                    out.add(other)
                }
            }
        }
        return out to upgraded
    }

    /**
     * Resolves the exact 11-char YouTube `videoId` for a metadata-only entry (such as stock YouTube /
     * ReVanced `MediaSession` captures that omit `MEDIA_URI`/`ART_URI`), caches it permanently into
     * `pipe_yt_history_v1.tsv`, and returns the upgraded [YouTubeWatchEntry] so direct video launch
     * and `&t=XXs` resume work immediately.
     */
    fun resolveAndAttachVideoId(
        context: Context,
        entry: YouTubeWatchEntry,
        forceRetry: Boolean = false,
    ): YouTubeWatchEntry {
        if (entry.hasDirectVideoId) return entry
        if (entry.title.isBlank()) return entry
        val repo = runCatching { PipeRepository(context) }.getOrNull()
        if (repo != null && !repo.loadYouTubeResolveVideoId()) return entry

        val failKey = entry.id
        val lastFailMs = failedResolveTimestamps[failKey]
        if (!forceRetry && lastFailMs != null && (System.currentTimeMillis() - lastFailMs) < FAILED_RESOLVE_COOLDOWN_MS) {
            return entry
        }

        val resolvedId = lookupVideoIdOnline(
            title = entry.title,
            channel = entry.channel,
            durationMs = entry.durationMs,
        )
        if (resolvedId.isNullOrBlank()) {
            failedResolveTimestamps[failKey] = System.currentTimeMillis()
            return entry
        }
        failedResolveTimestamps.remove(failKey)

        synchronized(writeLock) {
            val current = memoryCache ?: readHistoryFileLocked(getHistoryFile(context))
            val (updated, upgraded) = applyVideoIdResolution(current, entry.id, resolvedId)
            if (upgraded != null) {
                writeHistoryFileLocked(getHistoryFile(context), updated)
                memoryCache = updated
                return upgraded
            }
        }
        return entry.copy(
            id = resolvedId,
            videoId = resolvedId,
            url = "https://www.youtube.com/watch?v=$resolvedId",
        )
    }

    private fun lookupVideoIdOnline(
        title: String,
        channel: String,
        durationMs: Long,
    ): String? {
        val query = if (channel.isNotBlank()) "$title $channel" else title
        // 1. Lightweight InnerTube JSON search (no API key, no cookies)
        val innerTubeCandidates = fetchInnerTubeCandidates(query)
        pickBestVideoIdCandidate(innerTubeCandidates, title, channel, durationMs)?.let { return it }

        // 2. Fallback to standard YouTube search HTML `ytInitialData`
        val htmlCandidates = fetchHtmlSearchCandidates(query)
        return pickBestVideoIdCandidate(htmlCandidates, title, channel, durationMs)
    }

    private fun fetchInnerTubeCandidates(query: String): List<YouTubeSearchCandidate> = runCatching {
        val payload = JSONObject().apply {
            put(
                "context",
                JSONObject().apply {
                    put(
                        "client",
                        JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20240726.00.00")
                            put("hl", "en")
                            put("gl", "US")
                        }
                    )
                }
            )
            put("query", query)
        }.toString()

        val conn = (URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 6_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
        }
        try {
            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return@runCatching emptyList()
            val rawStream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            val body = BufferedReader(InputStreamReader(rawStream, StandardCharsets.UTF_8)).use { it.readText() }
            parseInnerTubeSearchCandidates(body)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(emptyList())

    private fun fetchHtmlSearchCandidates(query: String): List<YouTubeSearchCandidate> = runCatching {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val conn = (URL("https://www.youtube.com/results?search_query=$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 6_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching emptyList()
            val rawStream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            val html = BufferedReader(InputStreamReader(rawStream, StandardCharsets.UTF_8)).use { it.readText() }
            parseHtmlSearchCandidates(html)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(emptyList())

    fun parseHtmlSearchCandidates(html: String): List<YouTubeSearchCandidate> {
        val marker = "var ytInitialData = "
        val idx = html.indexOf(marker)
        if (idx < 0) return emptyList()
        val start = idx + marker.length
        val end = html.indexOf(";</script>", start)
        if (end <= start) return emptyList()
        return parseInnerTubeSearchCandidates(html.substring(start, end))
    }

    fun parseInnerTubeSearchCandidates(rawJson: String): List<YouTubeSearchCandidate> {
        val out = ArrayList<YouTubeSearchCandidate>()
        runCatching {
            val root = JSONObject(rawJson)
            val sections = root
                .optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: return emptyList()

            for (s in 0 until sections.length()) {
                val items = sections.optJSONObject(s)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
                    ?: continue
                for (i in 0 until items.length()) {
                    val vr = items.optJSONObject(i)?.optJSONObject("videoRenderer") ?: continue
                    val vid = vr.optString("videoId", "").trim()
                    if (vid.length != 11 || !YT_ID_REGEX.matches(vid)) continue
                    val title = extractRunsText(vr.optJSONObject("title"))
                    if (title.isBlank()) continue
                    val channel = extractRunsText(vr.optJSONObject("ownerText"))
                        .ifBlank { extractRunsText(vr.optJSONObject("longBylineText")) }
                        .ifBlank { extractRunsText(vr.optJSONObject("shortBylineText")) }
                    val durText = vr.optJSONObject("lengthText")?.optString("simpleText", "").orEmpty()
                    val durMs = parseDurationTextToMs(durText)
                    out.add(YouTubeSearchCandidate(vid, title, channel, durMs))
                    if (out.size >= 15) return out
                }
            }
        }
        return out
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText", "").trim()
        if (simple.isNotEmpty()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString().trim()
    }

    fun parseDurationTextToMs(text: String): Long {
        val clean = text.trim()
        if (clean.isEmpty() || !clean.contains(':')) return 0L
        val parts = clean.split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }.coerceAtLeast(0L)
    }

    /**
     * Picks the best matching `videoId` from YouTube search [candidates] using title, channel,
     * and duration proximity.
     */
    fun pickBestVideoIdCandidate(
        candidates: List<YouTubeSearchCandidate>,
        targetTitle: String,
        targetChannel: String,
        targetDurationMs: Long,
    ): String? {
        if (candidates.isEmpty()) return null
        val normTargetTitle = FuzzyMatcher.normalizeForSearch(targetTitle)
        val normTargetChannel = FuzzyMatcher.normalizeForSearch(targetChannel)

        var bestVid: String? = null
        var bestScore = Int.MIN_VALUE

        for ((idx, cand) in candidates.withIndex()) {
            val normCandTitle = FuzzyMatcher.normalizeForSearch(cand.title)
            val normCandChannel = FuzzyMatcher.normalizeForSearch(cand.channel)

            var score = (20 - idx).coerceAtLeast(0) * 5

            // Title similarity
            score += when {
                normCandTitle == normTargetTitle && normTargetTitle.isNotEmpty() -> 1_200
                normCandTitle.startsWith(normTargetTitle) || normTargetTitle.startsWith(normCandTitle) -> 900
                else -> FuzzyMatcher.score(targetTitle, cand.title)
            }

            // Channel similarity (strongly distinguishes official uploads from reuploads/lyrics)
            if (normTargetChannel.isNotEmpty() && normCandChannel.isNotEmpty()) {
                score += when {
                    normCandChannel == normTargetChannel -> 900
                    normCandChannel.contains(normTargetChannel) || normTargetChannel.contains(normCandChannel) -> 650
                    else -> {
                        val chScore = FuzzyMatcher.score(targetChannel, cand.channel)
                        if (chScore >= 500) chScore / 2 else -150
                    }
                }
            }

            // Duration proximity (very high signal when MediaSession provided duration)
            if (targetDurationMs > 0L && cand.durationMs > 0L) {
                val diffSec = kotlin.math.abs(targetDurationMs - cand.durationMs) / 1000L
                score += when {
                    diffSec <= 3L -> 500
                    diffSec <= 10L -> 250
                    diffSec <= 30L -> 50
                    diffSec >= 120L -> -250
                    else -> -50
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestVid = cand.videoId
            }
        }
        return if (bestScore >= 300) bestVid else candidates.firstOrNull()?.videoId
    }

    /**
     * Upserts a single live watch event cleanly:
     * - Matches existing entry by `videoId` OR by normalized `title + channel` (upgrading placeholder titles or missing videoIds!).
     * - Only increments `watchCount` when re-watched after [SESSION_REWATCH_WINDOW_MS] (10m) or after another video played in between.
     */
    fun upsertSingleEntry(
        existing: List<YouTubeWatchEntry>,
        incoming: YouTubeWatchEntry,
    ): List<YouTubeWatchEntry> {
        val matchIdx = existing.indexOfFirst { cur ->
            (incoming.videoId.isNotBlank() && cur.videoId == incoming.videoId) ||
                cur.id == incoming.id ||
                (cur.titleLower == incoming.titleLower &&
                    (cur.channelLower == incoming.channelLower || cur.channelLower.isEmpty() || incoming.channelLower.isEmpty()))
        }

        if (matchIdx < 0) {
            return (listOf(incoming) + existing)
                .sortedByDescending { it.watchedAtMs }
                .take(MAX_STORED_ENTRIES)
        }

        val prev = existing[matchIdx]
        val isMostRecentAndSameSession = matchIdx == 0 &&
            (incoming.watchedAtMs - prev.watchedAtMs) in 0L..SESSION_REWATCH_WINDOW_MS
        val prevWasPlaceholder = prev.title.startsWith("YouTube Video (") && prev.title.endsWith(")")
        val incomingIsPlaceholder = incoming.title.startsWith("YouTube Video (") && incoming.title.endsWith(")")

        val bestTitle = when {
            !incomingIsPlaceholder && incoming.title.isNotBlank() -> incoming.title
            !prevWasPlaceholder && prev.title.isNotBlank() -> prev.title
            else -> incoming.title.ifBlank { prev.title }
        }
        val bestChannel = incoming.channel.ifBlank { prev.channel }
        val bestVideoId = incoming.videoId.ifBlank { prev.videoId }
        val bestUrl = when {
            bestVideoId.isNotBlank() -> "https://www.youtube.com/watch?v=$bestVideoId"
            incoming.url.isNotBlank() -> incoming.url
            else -> prev.url
        }
        val bestDuration = if (incoming.durationMs > 0L) incoming.durationMs else prev.durationMs
        val bestPosition = when {
            incoming.positionMs > 0L -> incoming.positionMs
            isMostRecentAndSameSession -> prev.positionMs
            else -> prev.positionMs
        }
        val bestThumb = incoming.thumbnailPath.ifBlank { prev.thumbnailPath }
        val bestSource = if (!incomingIsPlaceholder && incoming.sourceApp.isNotBlank()) incoming.sourceApp else prev.sourceApp
        val nextCount = if (isMostRecentAndSameSession || prevWasPlaceholder) {
            prev.watchCount.coerceAtLeast(1)
        } else {
            prev.watchCount + 1
        }
        val mergedId = buildStableId(bestVideoId, bestTitle, bestChannel)

        val merged = prev.copy(
            id = mergedId,
            videoId = bestVideoId,
            title = bestTitle,
            channel = bestChannel,
            url = bestUrl,
            watchedAtMs = maxOf(prev.watchedAtMs, incoming.watchedAtMs),
            durationMs = bestDuration,
            positionMs = bestPosition,
            watchCount = nextCount,
            sourceApp = bestSource,
            thumbnailPath = bestThumb,
        )

        val out = ArrayList<YouTubeWatchEntry>(existing.size)
        out.add(merged)
        for (i in existing.indices) {
            if (i != matchIdx) {
                val item = existing[i]
                // Also fold any duplicate id/videoId if merging upgraded a meta: key to a real videoId
                val isDup = (bestVideoId.isNotBlank() && item.videoId == bestVideoId) || item.id == mergedId
                if (!isDup) out.add(item)
            }
        }
        return out.sortedByDescending { it.watchedAtMs }.take(MAX_STORED_ENTRIES)
    }

    /**
     * Merges a batch of imported entries with existing local history without losing local metadata.
     */
    fun mergeEntries(
        existing: List<YouTubeWatchEntry>,
        incoming: List<YouTubeWatchEntry>,
    ): List<YouTubeWatchEntry> {
        val byKey = LinkedHashMap<String, YouTubeWatchEntry>()
        fun keyOf(e: YouTubeWatchEntry): String =
            if (e.videoId.isNotBlank()) "vid:${e.videoId}" else "meta:${e.titleLower}|${e.channelLower}"

        for (e in existing) {
            byKey[keyOf(e)] = e
        }
        for (inc in incoming) {
            val k = keyOf(inc)
            val prev = byKey[k]
            if (prev == null) {
                byKey[k] = inc
            } else {
                val prevPlaceholder = prev.title.startsWith("YouTube Video (")
                val incPlaceholder = inc.title.startsWith("YouTube Video (")
                val bestTitle = when {
                    !prevPlaceholder && prev.title.isNotBlank() -> prev.title
                    !incPlaceholder && inc.title.isNotBlank() -> inc.title
                    else -> prev.title.ifBlank { inc.title }
                }
                byKey[k] = prev.copy(
                    id = buildStableId(prev.videoId.ifBlank { inc.videoId }, bestTitle, prev.channel.ifBlank { inc.channel }),
                    videoId = prev.videoId.ifBlank { inc.videoId },
                    title = bestTitle,
                    channel = prev.channel.ifBlank { inc.channel },
                    url = prev.url.ifBlank { inc.url },
                    watchedAtMs = maxOf(prev.watchedAtMs, inc.watchedAtMs),
                    durationMs = maxOf(prev.durationMs, inc.durationMs),
                    positionMs = if (prev.watchedAtMs >= inc.watchedAtMs) prev.positionMs else inc.positionMs,
                    watchCount = maxOf(prev.watchCount, inc.watchCount),
                    sourceApp = prev.sourceApp.ifBlank { inc.sourceApp },
                    thumbnailPath = prev.thumbnailPath.ifBlank { inc.thumbnailPath },
                )
            }
        }
        return byKey.values
            .sortedByDescending { it.watchedAtMs }
            .take(MAX_STORED_ENTRIES)
    }

    fun deleteEntry(context: Context, entryId: String): List<YouTubeWatchEntry> {
        synchronized(writeLock) {
            val current = memoryCache ?: readHistoryFileLocked(getHistoryFile(context))
            val removed = current.firstOrNull { it.id == entryId }
            val updated = current.filterNot { it.id == entryId }
            writeHistoryFileLocked(getHistoryFile(context), updated)
            memoryCache = updated
            if (removed != null && removed.thumbnailPath.isNotBlank()) {
                runCatching { File(removed.thumbnailPath).delete() }
            }
            return updated
        }
    }

    fun clearAll(context: Context) {
        synchronized(writeLock) {
            memoryCache = emptyList()
            runCatching { getHistoryFile(context).delete() }
            runCatching {
                getThumbsDir(context).listFiles()?.forEach { it.delete() }
            }
            thumbBitmapCache.evictAll()
        }
    }

    /**
     * Auto-discovers Google Takeout or exported YouTube watch history files in `/storage/emulated/0/Download/`.
     */
    fun findCandidateInDownloads(): File? {
        val searchDirs = listOf(
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Download/Takeout/YouTube and YouTube Music/history"),
            File("/storage/emulated/0/Download/YouTube and YouTube Music/history"),
            File("/storage/emulated/0/Documents"),
        )
        val candidateNames = listOf(
            "watch-history.json",
            "watch-history.html",
            "pipe-yt-history.json",
            "pipe-yt-history.tsv",
            "pipe_yt_history.tsv",
            "youtube-watch-history.json",
            "history.db",
            "newpipe_history.json",
            "libretube_history.json",
        )
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in candidateNames) {
                val f = File(dir, name)
                if (f.exists() && f.isFile && f.length() > 16L) return f
            }
        }
        return null
    }

    /**
     * Imports YouTube watch history from a SAF [Uri] (Google Takeout JSON/HTML, NewPipe, LibreTube, FreeTube, Pipe TSV/JSON).
     * Returns pair of `(importedCount, totalStoredCount)`.
     */
    fun importFromUri(context: Context, uri: Uri, displayNameHint: String = ""): Pair<Int, Int> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("! could not open selected watch history file")
        stream.use { rawIn ->
            return importFromStream(context, rawIn, displayNameHint.ifEmpty { uri.lastPathSegment.orEmpty() })
        }
    }

    fun importFromFile(context: Context, file: File): Pair<Int, Int> {
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("! cannot read ${file.name}")
        }
        FileInputStream(file).use { rawIn ->
            return importFromStream(context, rawIn, file.name)
        }
    }

    private fun importFromStream(context: Context, rawStream: InputStream, nameHint: String): Pair<Int, Int> {
        val buffered = BufferedInputStream(rawStream)
        buffered.mark(4)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()

        val isGzip = (b0 == 0x1f && b1 == 0x8b) || nameHint.lowercase().endsWith(".gz")
        val effective = if (isGzip) GZIPInputStream(buffered) else buffered
        val text = BufferedReader(InputStreamReader(effective, StandardCharsets.UTF_8)).use { it.readText() }
        val parsed = parseYouTubeHistoryContent(text, sourceHint = inferSourceLabel(nameHint))
        if (parsed.isEmpty()) {
            throw IllegalArgumentException("! no valid YouTube watch history entries found")
        }
        synchronized(writeLock) {
            val current = memoryCache ?: readHistoryFileLocked(getHistoryFile(context))
            val merged = mergeEntries(current, parsed)
            writeHistoryFileLocked(getHistoryFile(context), merged)
            memoryCache = merged
            return parsed.size to merged.size
        }
    }

    private fun inferSourceLabel(nameHint: String): String {
        val lower = nameHint.lowercase()
        return when {
            lower.contains("takeout") || lower.contains("watch-history") -> "Takeout"
            lower.contains("newpipe") -> "NewPipe"
            lower.contains("pipepipe") -> "PipePipe"
            lower.contains("libretube") -> "LibreTube"
            lower.contains("freetube") || lower.endsWith(".db") -> "FreeTube"
            lower.contains("grayjay") -> "Grayjay"
            else -> "Import"
        }
    }

    /**
     * Multi-format offline parser supporting:
     * 1. Google Takeout `watch-history.json` (`[{"header":"YouTube","title":"Watched ...","titleUrl":"...","subtitles":[{"name":"..."}],"time":"..."}]`)
     * 2. Pipe JSON export (`[{"videoId":...,"title":...,"channel":...,"watchedAtMs":...}]` or `{"watchHistory":[...]}`)
     * 3. NewPipe / LibreTube / Grayjay JSON exports (`{"history":[...]}` or `{"watchHistory":[...]}`)
     * 4. FreeTube `history.db` (newline-delimited JSON objects)
     * 5. Google Takeout `watch-history.html`
     * 6. Pipe TSV / generic TSV & CSV
     */
    fun parseYouTubeHistoryContent(
        rawText: String,
        sourceHint: String = "Import",
    ): List<YouTubeWatchEntry> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. HTML (Google Takeout watch-history.html)
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.contains("content-cell", ignoreCase = true)
        ) {
            return parseTakeoutHtml(trimmed, sourceHint = "Takeout")
        }

        // 2. JSON array or object (Google Takeout, Pipe JSON, NewPipe, LibreTube)
        if (trimmed.startsWith("[")) {
            val arrParsed = parseJsonArrayHistory(trimmed, sourceHint)
            if (arrParsed.isNotEmpty()) return arrParsed
        }
        if (trimmed.startsWith("{")) {
            // Check if NDJSON (FreeTube history.db has one JSON object per line)
            val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size > 1 && lines.all { it.startsWith("{") && it.endsWith("}") }) {
                val ndjsonParsed = parseNdjsonHistory(lines, sourceHint = if (sourceHint == "Import") "FreeTube" else sourceHint)
                if (ndjsonParsed.isNotEmpty()) return ndjsonParsed
            }
            val objParsed = parseJsonObjectWrapperHistory(trimmed, sourceHint)
            if (objParsed.isNotEmpty()) return objParsed
            // Single JSON object line
            val single = parseSingleJsonEntry(runCatching { JSONObject(trimmed) }.getOrNull(), sourceHint)
            if (single != null) return listOf(single)
        }

        // 3. TSV / CSV
        return parseDelimitedHistory(trimmed, sourceHint)
    }

    private fun parseJsonArrayHistory(rawJson: String, sourceHint: String): List<YouTubeWatchEntry> {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        val out = ArrayList<YouTubeWatchEntry>(arr.length().coerceAtMost(MAX_STORED_ENTRIES))
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val entry = parseSingleJsonEntry(obj, sourceHint)
            if (entry != null) out.add(entry)
        }
        return out
    }

    private fun parseJsonObjectWrapperHistory(rawJson: String, sourceHint: String): List<YouTubeWatchEntry> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val arrayKeys = listOf("watchHistory", "history", "videos", "streams", "entries", "items")
        for (key in arrayKeys) {
            val arr = root.optJSONArray(key)
            if (arr != null && arr.length() > 0) {
                val out = ArrayList<YouTubeWatchEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val entry = parseSingleJsonEntry(obj, sourceHint)
                    if (entry != null) out.add(entry)
                }
                if (out.isNotEmpty()) return out
            }
        }
        return emptyList()
    }

    private fun parseNdjsonHistory(lines: List<String>, sourceHint: String): List<YouTubeWatchEntry> {
        val out = ArrayList<YouTubeWatchEntry>(lines.size)
        for (line in lines) {
            val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
            val entry = parseSingleJsonEntry(obj, sourceHint)
            if (entry != null) out.add(entry)
        }
        return out
    }

    private fun parseSingleJsonEntry(obj: JSONObject?, sourceHint: String): YouTubeWatchEntry? {
        if (obj == null) return null

        // Skip non-watch Google Takeout search entries if present in mixed exports
        val rawTitle = obj.optString("title", "").trim()
        if (rawTitle.startsWith("Searched for ", ignoreCase = true) ||
            rawTitle.startsWith("Visited ", ignoreCase = true)
        ) {
            return null
        }

        val urlCandidate = obj.optString("titleUrl", "")
            .ifBlank { obj.optString("url", "") }
            .ifBlank { obj.optString("videoUrl", "") }
            .trim()
        val explicitVideoId = obj.optString("videoId", "")
            .ifBlank { obj.optString("video_id", "") }
            .ifBlank { obj.optString("id", "") }
            .trim()

        val videoId = extractYouTubeVideoId(explicitVideoId, allowBareId = true)
            ?: extractYouTubeVideoId(urlCandidate)
            ?: ""

        val cleanTitle = cleanVideoTitle(rawTitle)
        if (cleanTitle.isBlank() || cleanTitle.lowercase() in IGNORED_TITLES) return null
        if (videoId.isBlank() && urlCandidate.isBlank() && !obj.has("watchedAtMs") && !obj.has("time")) {
            return null
        }

        // Channel extraction (Google Takeout subtitles[], FreeTube author, NewPipe uploader, Pipe channel)
        var channel = obj.optString("channel", "")
            .ifBlank { obj.optString("author", "") }
            .ifBlank { obj.optString("uploader", "") }
            .ifBlank { obj.optString("uploaderName", "") }
            .ifBlank { obj.optString("channelName", "") }
            .trim()
        if (channel.isBlank()) {
            val subs = obj.optJSONArray("subtitles")
            if (subs != null && subs.length() > 0) {
                channel = subs.optJSONObject(0)?.optString("name", "")?.trim().orEmpty()
            }
        }

        // Timestamp extraction (epoch ms, epoch sec, or ISO-8601 string)
        val watchedAtMs = when {
            obj.has("watchedAtMs") -> obj.optLong("watchedAtMs", 0L)
            obj.has("timeWatched") -> normalizeEpochMs(obj.optLong("timeWatched", 0L))
            obj.has("lastViewedAt") -> normalizeEpochMs(obj.optLong("lastViewedAt", 0L))
            obj.has("access_date") -> normalizeEpochMs(obj.optLong("access_date", 0L))
            obj.has("timestamp") -> normalizeEpochMs(obj.optLong("timestamp", 0L))
            obj.has("time") -> parseIsoTimestampMs(obj.optString("time", ""))
            else -> System.currentTimeMillis()
        }.let { if (it <= 0L) System.currentTimeMillis() else it }

        val durationMs = when {
            obj.has("durationMs") -> obj.optLong("durationMs", 0L)
            obj.has("lengthSeconds") -> obj.optLong("lengthSeconds", 0L) * 1000L
            obj.has("duration") -> {
                val d = obj.optLong("duration", 0L)
                if (d in 1L..86_400L) d * 1000L else d
            }
            else -> 0L
        }.coerceAtLeast(0L)

        val positionMs = when {
            obj.has("positionMs") -> obj.optLong("positionMs", 0L)
            obj.has("watchProgress") -> obj.optLong("watchProgress", 0L) * 1000L
            obj.has("progress") -> obj.optLong("progress", 0L) * 1000L
            else -> extractTimestampSeconds(urlCandidate) * 1000L
        }.coerceAtLeast(0L)

        val watchCount = obj.optInt("watchCount", obj.optInt("repeat_count", 1)).coerceAtLeast(1)
        val effectiveSource = obj.optString("sourceApp", "").trim().ifBlank {
            if (obj.has("titleUrl") && obj.has("header")) "Takeout" else sourceHint
        }
        val finalUrl = if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else urlCandidate

        return YouTubeWatchEntry(
            id = buildStableId(videoId, cleanTitle, channel),
            videoId = videoId,
            title = cleanTitle,
            channel = channel,
            url = finalUrl,
            watchedAtMs = watchedAtMs,
            durationMs = durationMs,
            positionMs = positionMs,
            watchCount = watchCount,
            sourceApp = effectiveSource,
        )
    }

    private fun parseTakeoutHtml(html: String, sourceHint: String): List<YouTubeWatchEntry> {
        val out = ArrayList<YouTubeWatchEntry>()
        val anchors = HTML_ANCHOR_REGEX.findAll(html).toList()
        var idx = 0
        val baseNow = System.currentTimeMillis()
        while (idx < anchors.size && out.size < MAX_STORED_ENTRIES) {
            val m = anchors[idx]
            val href = decodeHtmlEntities(m.groupValues[1])
            val vid = extractYouTubeVideoId(href)
            if (vid != null) {
                val title = cleanVideoTitle(decodeHtmlEntities(HTML_TAG_REGEX.replace(m.groupValues[2], "")))
                var channel = ""
                if (idx + 1 < anchors.size) {
                    val nextHref = anchors[idx + 1].groupValues[1]
                    if (nextHref.contains("/channel/", ignoreCase = true) ||
                        nextHref.contains("/@", ignoreCase = true) ||
                        nextHref.contains("/c/", ignoreCase = true) ||
                        nextHref.contains("/user/", ignoreCase = true)
                    ) {
                        channel = decodeHtmlEntities(HTML_TAG_REGEX.replace(anchors[idx + 1].groupValues[2], "")).trim()
                        idx++
                    }
                }
                if (title.isNotBlank() && title.lowercase() !in IGNORED_TITLES) {
                    out.add(
                        YouTubeWatchEntry(
                            id = buildStableId(vid, title, channel),
                            videoId = vid,
                            title = title,
                            channel = channel,
                            url = "https://www.youtube.com/watch?v=$vid",
                            watchedAtMs = baseNow - out.size * 1000L,
                            sourceApp = sourceHint,
                        )
                    )
                }
            }
            idx++
        }
        return out
    }

    private fun parseDelimitedHistory(rawText: String, sourceHint: String): List<YouTubeWatchEntry> {
        val out = ArrayList<YouTubeWatchEntry>()
        val now = System.currentTimeMillis()
        for (rawLine in rawText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.contains('\t')) {
                val parts = line.split('\t')
                // Pipe native 11-column TSV format
                if (parts.size >= 10 && parts[5].toLongOrNull() != null) {
                    parseTsvLine(line)?.let { out.add(it) }
                    continue
                }
                // Generic TSV: url \t title [\t channel] OR title \t url [\t channel]
                if (parts.size >= 2) {
                    val p0 = parts[0].trim().trim('"')
                    val p1 = parts[1].trim().trim('"')
                    val p2 = parts.getOrNull(2)?.trim()?.trim('"').orEmpty()
                    if (p0.equals("id", ignoreCase = true) || p0.equals("title", ignoreCase = true)) continue
                    val vid0 = extractYouTubeVideoId(p0, allowBareId = true)
                    val vid1 = extractYouTubeVideoId(p1, allowBareId = true)
                    val (vid, title, channel) = when {
                        vid0 != null -> Triple(vid0, cleanVideoTitle(p1), p2)
                        vid1 != null -> Triple(vid1, cleanVideoTitle(p0), p2)
                        else -> Triple("", cleanVideoTitle(p0), p1)
                    }
                    if (title.isNotBlank()) {
                        out.add(
                            YouTubeWatchEntry(
                                id = buildStableId(vid, title, channel),
                                videoId = vid,
                                title = title,
                                channel = channel,
                                url = if (vid.isNotBlank()) "https://www.youtube.com/watch?v=$vid" else "",
                                watchedAtMs = now - out.size * 1000L,
                                sourceApp = sourceHint,
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * Exports offline YouTube watch history to clean portable JSON.
     */
    fun exportHistoryToJson(entries: List<YouTubeWatchEntry>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAtMs", System.currentTimeMillis())
        root.put("count", entries.size)
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("videoId", e.videoId)
                    put("title", e.title)
                    put("channel", e.channel)
                    put("url", e.watchUrl)
                    put("watchedAtMs", e.watchedAtMs)
                    put("durationMs", e.durationMs)
                    put("positionMs", e.positionMs)
                    put("watchCount", e.watchCount)
                    put("sourceApp", e.sourceApp)
                }
            )
        }
        root.put("watchHistory", arr)
        return root.toString(2)
    }

    /**
     * Loads a locally cached offline thumbnail bitmap (zero network access).
     */
    fun loadLocalThumbnail(path: String): Bitmap? {
        if (path.isBlank()) return null
        thumbBitmapCache.get(path)?.let { return it }
        val f = File(path)
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(path)?.also { bmp ->
                thumbBitmapCache.put(path, bmp)
            }
        }.getOrNull()
    }

    private fun saveThumbnailOffline(context: Context, stableId: String, source: Bitmap): String? {
        return runCatching {
            val dir = getThumbsDir(context)
            if (!dir.exists()) dir.mkdirs()
            val safeName = stableId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64) + ".jpg"
            val target = File(dir, safeName)

            val maxW = 160
            val maxH = 90
            val scaled = if (source.width > maxW || source.height > maxH) {
                val ratio = minOf(maxW.toFloat() / source.width.coerceAtLeast(1), maxH.toFloat() / source.height.coerceAtLeast(1))
                val w = (source.width * ratio).toInt().coerceAtLeast(16)
                val h = (source.height * ratio).toInt().coerceAtLeast(16)
                Bitmap.createScaledBitmap(source, w, h, true)
            } else {
                source
            }
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (scaled !== source) scaled.recycle()
            thumbBitmapCache.remove(target.absolutePath)
            pruneOldThumbnailsIfNeeded(dir)
            target.absolutePath
        }.getOrNull()
    }

    private fun pruneOldThumbnailsIfNeeded(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_CACHED_THUMBS_ON_DISK) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED_THUMBS_ON_DISK)
            .forEach { runCatching { it.delete() } }
    }

    private fun readHistoryFileLocked(file: File): List<YouTubeWatchEntry> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            file.useLines(StandardCharsets.UTF_8) { lines ->
                lines.mapNotNull(::parseTsvLine)
                    .sortedByDescending { it.watchedAtMs }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun writeHistoryFileLocked(file: File, entries: List<YouTubeWatchEntry>) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                for (e in entries.take(MAX_STORED_ENTRIES)) {
                    writer.write(serializeTsvLine(e))
                    writer.write("\n")
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    internal fun serializeTsvLine(e: YouTubeWatchEntry): String {
        fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        return listOf(
            clean(e.id),
            clean(e.videoId),
            clean(e.title),
            clean(e.channel),
            clean(e.url),
            e.watchedAtMs.toString(),
            e.durationMs.toString(),
            e.positionMs.toString(),
            e.watchCount.coerceAtLeast(1).toString(),
            clean(e.sourceApp),
            clean(e.thumbnailPath),
        ).joinToString("\t")
    }

    internal fun parseTsvLine(line: String): YouTubeWatchEntry? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        if (parts.size < 6) return null
        val id = parts[0].trim()
        val videoId = parts[1].trim()
        val title = parts[2].trim()
        if (title.isBlank()) return null
        val channel = parts.getOrNull(3)?.trim().orEmpty()
        val url = parts.getOrNull(4)?.trim().orEmpty()
        val watchedAtMs = parts.getOrNull(5)?.toLongOrNull() ?: 0L
        val durationMs = parts.getOrNull(6)?.toLongOrNull() ?: 0L
        val positionMs = parts.getOrNull(7)?.toLongOrNull() ?: 0L
        val watchCount = (parts.getOrNull(8)?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val sourceApp = parts.getOrNull(9)?.trim().orEmpty().ifBlank { "YouTube" }
        val thumbPath = parts.getOrNull(10)?.trim().orEmpty()
        return YouTubeWatchEntry(
            id = id.ifBlank { buildStableId(videoId, title, channel) },
            videoId = videoId,
            title = title,
            channel = channel,
            url = url,
            watchedAtMs = watchedAtMs,
            durationMs = durationMs,
            positionMs = positionMs,
            watchCount = watchCount,
            sourceApp = sourceApp,
            thumbnailPath = thumbPath,
        )
    }

    private fun cleanVideoTitle(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("Watched ", ignoreCase = false)) {
            t = t.removePrefix("Watched ").trim()
        }
        // Strip browser suffix like " - YouTube"
        if (t.endsWith(" - YouTube", ignoreCase = true) && t.length > 10) {
            t = t.dropLast(10).trim()
        }
        return t
    }

    private fun normalizeEpochMs(raw: Long): Long {
        if (raw <= 0L) return 0L
        // Seconds (< 10^11) -> convert to ms
        return if (raw < 100_000_000_000L) raw * 1000L else raw
    }

    fun parseIsoTimestampMs(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0L
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
        )
        // Truncate fractional microseconds (Google Takeout sometimes has 6 digits) to 3 digits
        val normalized = s.replace(Regex("\\.(\\d{3})\\d+(Z|[+-]\\d\\d:?\\d\\d)$"), ".$1$2")
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(normalized)?.time
            }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return 0L
    }

    private fun decodeHtmlEntities(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()
}
