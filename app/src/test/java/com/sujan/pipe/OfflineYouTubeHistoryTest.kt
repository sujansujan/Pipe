package com.sujan.pipe

import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.FullBackupBundle
import com.sujan.pipe.model.PrefixConfig
import com.sujan.pipe.model.YouTubeWatchEntry
import com.sujan.pipe.service.YouTubeWatchListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineYouTubeHistoryTest {

    private val engines = DefaultEngines.LIST
    private val bookmarks = DefaultBookmarks.LIST

    @Test
    fun `extractYouTubeVideoId handles watch, short, shorts, live, embed, music, and ytimg thumbnail URLs`() {
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://i9.ytimg.com/vi_webp/dQw4w9WgXcQ/mqdefault.webp"))
        assertEquals("dQw4w9WgXcQ", OfflineYouTubeHistoryManager.extractYouTubeVideoId("dQw4w9WgXcQ", allowBareId = true))
        assertNull(OfflineYouTubeHistoryManager.extractYouTubeVideoId("https://example.com/article"))
    }

    @Test
    fun `extractTimestampSeconds parses seconds and hms YouTube URL query parameters`() {
        assertEquals(42L, OfflineYouTubeHistoryManager.extractTimestampSeconds("https://youtu.be/dQw4w9WgXcQ?t=42"))
        assertEquals(95L, OfflineYouTubeHistoryManager.extractTimestampSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=95s"))
        assertEquals(90L, OfflineYouTubeHistoryManager.extractTimestampSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1m30s"))
        assertEquals(3723L, OfflineYouTubeHistoryManager.extractTimestampSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1h2m3s"))
        assertEquals(0L, OfflineYouTubeHistoryManager.extractTimestampSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `parseYouTubeHistoryContent imports Google Takeout JSON cleanly and filters searches and removed items`() {
        val takeoutJson = """
            [
              {
                "header": "YouTube",
                "title": "Watched Rust Async Deep Dive",
                "titleUrl": "https://www.youtube.com/watch?v=abc123DEF45",
                "subtitles": [{"name": "Jon Gjengset", "url": "https://www.youtube.com/channel/UC_foo"}],
                "time": "2026-11-20T14:30:12.123456Z"
              },
              {
                "header": "YouTube",
                "title": "Searched for compose layout",
                "titleUrl": "https://www.youtube.com/results?search_query=compose+layout",
                "time": "2026-11-20T14:20:00Z"
              },
              {
                "header": "YouTube",
                "title": "Watched a video that has been removed",
                "time": "2026-11-19T10:00:00Z"
              }
            ]
        """.trimIndent()

        val parsed = OfflineYouTubeHistoryManager.parseYouTubeHistoryContent(takeoutJson, "Takeout")
        assertEquals(1, parsed.size)
        val item = parsed.first()
        assertEquals("abc123DEF45", item.videoId)
        assertEquals("Rust Async Deep Dive", item.title)
        assertEquals("Jon Gjengset", item.channel)
        assertEquals("Takeout", item.sourceApp)
        assertTrue(item.watchedAtMs > 0L)
        assertEquals("https://www.youtube.com/watch?v=abc123DEF45", item.watchUrl)
    }

    @Test
    fun `parseYouTubeHistoryContent supports Takeout HTML, FreeTube NDJSON, and Pipe JSON export round-trip`() {
        val takeoutHtml = """
            <!DOCTYPE html><html><body>
            <div class="content-cell">Watched <a href="https://www.youtube.com/watch?v=xyz987UVW12">Kotlin Coroutines &amp; Flows</a><br>
            <a href="https://www.youtube.com/channel/UC123">Philipp Lackner</a><br>Nov 25, 2026</div>
            </body></html>
        """.trimIndent()
        val htmlParsed = OfflineYouTubeHistoryManager.parseYouTubeHistoryContent(takeoutHtml)
        assertEquals(1, htmlParsed.size)
        assertEquals("xyz987UVW12", htmlParsed.first().videoId)
        assertEquals("Kotlin Coroutines & Flows", htmlParsed.first().title)
        assertEquals("Philipp Lackner", htmlParsed.first().channel)

        val freetubeNdjson = """
            {"videoId":"ft123456789","title":"Linux Kernel Internals","author":"Low Level","lengthSeconds":1240,"watchProgress":320,"timeWatched":1732500000000}
            {"videoId":"ft987654321","title":"Neovim Lua Setup","author":"TJ DeVries","lengthSeconds":900,"watchProgress":45,"timeWatched":1732600000000}
        """.trimIndent()
        val ftParsed = OfflineYouTubeHistoryManager.parseYouTubeHistoryContent(freetubeNdjson)
        assertEquals(2, ftParsed.size)
        assertEquals("ft123456789", ftParsed[0].videoId)
        assertEquals(1_240_000L, ftParsed[0].durationMs)
        assertEquals(320_000L, ftParsed[0].positionMs)
        assertTrue(ftParsed[0].hasResumePosition)
        assertEquals("https://www.youtube.com/watch?v=ft123456789&t=320s", ftParsed[0].resumeUrl)

        val exportedJson = OfflineYouTubeHistoryManager.exportHistoryToJson(ftParsed)
        val roundTripped = OfflineYouTubeHistoryManager.parseYouTubeHistoryContent(exportedJson)
        assertEquals(2, roundTripped.size)
        assertEquals("Linux Kernel Internals", roundTripped[0].title)
        assertEquals("Low Level", roundTripped[0].channel)
        assertEquals(320_000L, roundTripped[0].positionMs)
    }

    @Test
    fun `upsertSingleEntry upgrades placeholder titles, updates playback position without inflating watchCount, and increments on rewatch`() {
        val t0 = 1_700_000_000_000L
        val linkEntry = YouTubeWatchEntry(
            id = "dQw4w9WgXcQ",
            videoId = "dQw4w9WgXcQ",
            title = "YouTube Video (dQw4w9WgXcQ)",
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            watchedAtMs = t0,
            sourceApp = "Link",
        )
        val mediaEntry = YouTubeWatchEntry(
            id = "dQw4w9WgXcQ",
            videoId = "dQw4w9WgXcQ",
            title = "Rick Astley - Never Gonna Give You Up",
            channel = "Rick Astley",
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            watchedAtMs = t0 + 5_000L,
            durationMs = 213_000L,
            positionMs = 45_000L,
            sourceApp = "ReVanced",
        )
        val afterUpgrade = OfflineYouTubeHistoryManager.upsertSingleEntry(listOf(linkEntry), mediaEntry)
        assertEquals(1, afterUpgrade.size)
        assertEquals("Rick Astley - Never Gonna Give You Up", afterUpgrade.first().title)
        assertEquals("Rick Astley", afterUpgrade.first().channel)
        assertEquals(1, afterUpgrade.first().watchCount)
        assertEquals(45_000L, afterUpgrade.first().positionMs)

        // Same-session position update (2 minutes later) should NOT increment watchCount
        val posUpdate = mediaEntry.copy(watchedAtMs = t0 + 120_000L, positionMs = 110_000L)
        val afterPosUpdate = OfflineYouTubeHistoryManager.upsertSingleEntry(afterUpgrade, posUpdate)
        assertEquals(1, afterPosUpdate.first().watchCount)
        assertEquals(110_000L, afterPosUpdate.first().positionMs)

        // Rewatch 1 hour later SHOULD increment watchCount
        val rewatch = mediaEntry.copy(watchedAtMs = t0 + 3_600_000L, positionMs = 15_000L)
        val afterRewatch = OfflineYouTubeHistoryManager.upsertSingleEntry(afterPosUpdate, rewatch)
        assertEquals(2, afterRewatch.first().watchCount)
        assertEquals(15_000L, afterRewatch.first().positionMs)
    }

    @Test
    fun `TSV serialization round-trips all fields accurately`() {
        val original = YouTubeWatchEntry(
            id = "abc123DEF45",
            videoId = "abc123DEF45",
            title = "Building Offline Apps",
            channel = "Android Dev",
            url = "https://www.youtube.com/watch?v=abc123DEF45",
            watchedAtMs = 1_732_600_000_000L,
            durationMs = 600_000L,
            positionMs = 125_000L,
            watchCount = 3,
            sourceApp = "NewPipe",
            thumbnailPath = "/data/files/yt_thumbs/abc123DEF45.jpg",
        )
        val line = OfflineYouTubeHistoryManager.serializeTsvLine(original)
        val restored = OfflineYouTubeHistoryManager.parseTsvLine(line)
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `QueryRouter routes yth commands, shows setup when empty, surfaces matches under yt pipe, and keeps ytw intact`() {
        val cmdBare = QueryRouter.parsePromptCommand("yth", bookmarks, engines)
        assertEquals(PromptIntentMode.YOUTUBE_HISTORY_ONLY, cmdBare.mode)
        assertEquals("", cmdBare.filterQuery)

        val cmdQuery = QueryRouter.parsePromptCommand("yth rust async", bookmarks, engines)
        assertEquals(PromptIntentMode.YOUTUBE_HISTORY_ONLY, cmdQuery.mode)
        assertEquals("rust async", cmdQuery.filterQuery)

        // Ensure `ytw` (YouTube Last Week) is NOT hijacked
        val cmdYtw = QueryRouter.parsePromptCommand("ytw kotlin", bookmarks, engines)
        assertEquals(PromptIntentMode.ENGINE_ONLY, cmdYtw.mode)
        assertEquals("ytw", cmdYtw.matchedEngine?.keyword)

        // Custom prefix
        val customPrefixes = PrefixConfig(youtubeHistory = "wh")
        assertEquals(
            PromptIntentMode.YOUTUBE_HISTORY_ONLY,
            QueryRouter.parsePromptCommand("wh lofi", bookmarks, engines, customPrefixes).mode,
        )

        // Setup prompt when empty
        val emptyResults = QueryRouter.buildPaletteResults(
            rawInput = "yth",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            youtubeHistoryMatches = emptyList(),
            youtubeHistoryTotalCount = 0,
            isYouTubeListenerEnabled = false,
        )
        assertTrue(emptyResults.first() is PaletteItem.YouTubeWatchAction)
        assertTrue((emptyResults.first() as PaletteItem.YouTubeWatchAction).isSetupPrompt)

        // Populated results in yth mode & related matches under yt mode
        val sampleWatch = YouTubeWatchEntry(
            id = "abc123DEF45",
            videoId = "abc123DEF45",
            title = "Rust Async Deep Dive",
            channel = "Jon Gjengset",
            watchedAtMs = 1_732_600_000_000L,
            durationMs = 3_600_000L,
            positionMs = 420_000L,
            sourceApp = "YouTube",
        )
        val ythResults = QueryRouter.buildPaletteResults(
            rawInput = "yth rust",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            youtubeHistoryMatches = listOf(sampleWatch),
            youtubeHistoryTotalCount = 1,
            isYouTubeListenerEnabled = true,
        )
        assertTrue(ythResults.first() is PaletteItem.YouTubeWatchAction)
        assertEquals("Rust Async Deep Dive", (ythResults.first() as PaletteItem.YouTubeWatchAction).entry?.title)
        assertTrue(ythResults.any { it is PaletteItem.EngineAction && it.engine.keyword == "yt" })

        val ytEngineResults = QueryRouter.buildPaletteResults(
            rawInput = "yt rust",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            youtubeHistoryMatches = listOf(sampleWatch),
            youtubeHistoryTotalCount = 1,
            isYouTubeListenerEnabled = true,
        )
        assertTrue(ytEngineResults[0] is PaletteItem.EngineAction)
        assertTrue(ytEngineResults[1] is PaletteItem.YouTubeWatchAction)
        assertEquals("abc123DEF45", (ytEngineResults[1] as PaletteItem.YouTubeWatchAction).entry?.videoId)

        // Full v2 backup round-trip preserves youtubeHistory prefix
        val json = QueryRouter.exportFullBackupToJson(FullBackupBundle(engines = engines, prefixes = customPrefixes))
        val restored = QueryRouter.importFullBackupFromJson(json)
        assertEquals("wh", restored.prefixes?.youtubeHistory)
    }

    @Test
    fun `YouTubeWatchListenerService recognizes YouTube, ReVanced, NewPipe, LibreTube, Grayjay, and browsers`() {
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("com.google.android.youtube"))
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("app.revanced.android.youtube"))
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("app.rvx.android.youtube"))
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("org.schabi.newpipe"))
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("InfinityLoop1309.NewPipeEnhanced"))
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("com.github.libretube"))
        assertTrue(YouTubeWatchListenerService.isKnownYouTubePackage("com.futo.platformplayer"))
        assertFalse(YouTubeWatchListenerService.isKnownYouTubePackage("com.spotify.music"))

        assertTrue(YouTubeWatchListenerService.isKnownBrowserPackage("com.brave.browser"))
        assertTrue(YouTubeWatchListenerService.isKnownBrowserPackage("org.mozilla.firefox"))
        assertEquals("ReVanced", YouTubeWatchListenerService.friendlyAppLabel("app.revanced.android.youtube"))
        assertEquals("PipePipe", YouTubeWatchListenerService.friendlyAppLabel("InfinityLoop1309.NewPipeEnhanced"))
    }

    @Test
    fun `estimatePositionMs extrapolates live playback at 1x and 2x speed, respects pause, and clamps to duration`() {
        // 1x speed: base 30s + 45s elapsed = 75s
        assertEquals(
            75_000L,
            YouTubeWatchListenerService.estimatePositionMs(
                basePositionMs = 30_000L,
                lastUpdateElapsedMs = 100_000L,
                playbackSpeed = 1.0f,
                isPlaying = true,
                durationMs = 300_000L,
                nowElapsedMs = 145_000L,
            ),
        )
        // 2x speed: base 10s + 20s*2 = 50s
        assertEquals(
            50_000L,
            YouTubeWatchListenerService.estimatePositionMs(
                basePositionMs = 10_000L,
                lastUpdateElapsedMs = 100_000L,
                playbackSpeed = 2.0f,
                isPlaying = true,
                durationMs = 300_000L,
                nowElapsedMs = 120_000L,
            ),
        )
        // Paused: stays at base position regardless of elapsed time
        assertEquals(
            42_000L,
            YouTubeWatchListenerService.estimatePositionMs(
                basePositionMs = 42_000L,
                lastUpdateElapsedMs = 100_000L,
                playbackSpeed = 1.0f,
                isPlaying = false,
                durationMs = 300_000L,
                nowElapsedMs = 500_000L,
            ),
        )
        // Clamped to duration when playback overshoots
        assertEquals(
            180_000L,
            YouTubeWatchListenerService.estimatePositionMs(
                basePositionMs = 150_000L,
                lastUpdateElapsedMs = 100_000L,
                playbackSpeed = 1.5f,
                isPlaying = true,
                durationMs = 180_000L,
                nowElapsedMs = 200_000L,
            ),
        )
    }

    @Test
    fun `applyPositionUpdate updates watch position in place without inflating watchCount or reordering`() {
        val videoB = YouTubeWatchEntry(
            id = "vidBBBBBBBB",
            videoId = "vidBBBBBBBB",
            title = "Video B (Current)",
            channel = "Channel B",
            watchedAtMs = 2_000_000L,
            durationMs = 600_000L,
            positionMs = 5_000L,
            watchCount = 1,
        )
        val videoA = YouTubeWatchEntry(
            id = "vidAAAAAAAA",
            videoId = "vidAAAAAAAA",
            title = "Video A (Previous)",
            channel = "Channel A",
            watchedAtMs = 1_000_000L,
            durationMs = 900_000L,
            positionMs = 0L,
            watchCount = 2,
        )
        val (updated, found) = OfflineYouTubeHistoryManager.applyPositionUpdate(
            existing = listOf(videoB, videoA),
            videoId = "vidAAAAAAAA",
            title = "Video A (Previous)",
            channel = "Channel A",
            positionMs = 420_000L,
            durationMs = 900_000L,
        )
        assertTrue(found)
        // Video B stays #1, Video A stays #2 with updated position and unchanged watchCount
        assertEquals("vidBBBBBBBB", updated[0].videoId)
        assertEquals("vidAAAAAAAA", updated[1].videoId)
        assertEquals(420_000L, updated[1].positionMs)
        assertEquals(2, updated[1].watchCount)
        assertTrue(updated[1].hasResumePosition)
        assertEquals("https://www.youtube.com/watch?v=vidAAAAAAAA&t=420s", updated[1].resumeUrl)
    }
}
