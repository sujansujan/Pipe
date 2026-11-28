package com.sujan.pipe

import com.sujan.pipe.data.FloccusBookmarkManager
import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.FloccusBookmarkItem
import com.sujan.pipe.model.FullBackupBundle
import com.sujan.pipe.model.PrefixConfig
import com.sujan.pipe.model.UniversalAlias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloccusBookmarkTest {

    private val engines = DefaultEngines.LIST
    private val bookmarks = DefaultBookmarks.LIST

    private val sampleXbel = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE xbel PUBLIC "+//IDN python.org//DTD XML Bookmark Exchange Language 1.0//EN//XML" "http://pyxml.sourceforge.net/topics/dtds/xbel.dtd">
        <xbel version="1.0">
        <!--- highestId :149: for Floccus bookmark sync browser extension -->
          <folder id="1">
            <title>Bookmarks Bar</title>
            <folder id="2">
              <title>Dev &amp; Tools</title>
              <bookmark id="10" href="https://developer.android.com/jetpack/compose">
                <title>Jetpack Compose Docs</title>
              </bookmark>
              <bookmark id="11" href="https://kotlinlang.org/docs/home.html">
                <title>Kotlin &quot;Official&quot; Guide</title>
              </bookmark>
            </folder>
            <bookmark id="12" href="https://news.ycombinator.com/">
              <title>Hacker News</title>
            </bookmark>
          </folder>
          <bookmark id="13" href="https://floccus.org/">
            <title>Floccus Home</title>
          </bookmark>
        </xbel>
    """.trimIndent()

    private val sampleNetscapeHtml = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
          <DT><H3>Reading List</H3>
          <DL><p>
            <DT><H3>Rust</H3>
            <DL><p>
              <DT><A HREF="https://doc.rust-lang.org/book/">The Rust Book</A>
            </DL><p>
            <DT><A HREF="https://lwn.net/">LWN.net &amp; Kernel</A>
          </DL><p>
        </DL><p>
    """.trimIndent()

    @Test
    fun `parseXbel strips doctype and triple-hyphen Floccus comments and preserves nested folder paths`() {
        val parsed = FloccusBookmarkManager.parseBookmarkFileContent(sampleXbel)
        assertEquals(4, parsed.size)

        val compose = parsed.first { it.id == "10" }
        assertEquals("Jetpack Compose Docs", compose.title)
        assertEquals("https://developer.android.com/jetpack/compose", compose.url)
        assertEquals("Bookmarks Bar / Dev & Tools", compose.folderPath)
        assertEquals("developer.android.com", compose.hostLower)
        assertTrue(compose.subtitleDisplay.contains("Bookmarks Bar / Dev & Tools"))

        val kotlin = parsed.first { it.id == "11" }
        assertEquals("Kotlin \"Official\" Guide", kotlin.title)
        assertEquals("Bookmarks Bar / Dev & Tools", kotlin.folderPath)

        val hn = parsed.first { it.id == "12" }
        assertEquals("Bookmarks Bar", hn.folderPath)

        val root = parsed.first { it.id == "13" }
        assertEquals("", root.folderPath)
    }

    @Test
    fun `parseNetscapeHtml preserves nested H3 folder hierarchy and decodes entities`() {
        val parsed = FloccusBookmarkManager.parseBookmarkFileContent(sampleNetscapeHtml)
        assertEquals(2, parsed.size)

        val rust = parsed.first { it.title == "The Rust Book" }
        assertEquals("https://doc.rust-lang.org/book/", rust.url)
        assertEquals("Reading List / Rust", rust.folderPath)

        val lwn = parsed.first { it.url == "https://lwn.net/" }
        assertEquals("LWN.net & Kernel", lwn.title)
        assertEquals("Reading List", lwn.folderPath)
    }

    @Test
    fun `scoreFloccusBookmark matches title, host, and nested folder path tags`() {
        val parsed = FloccusBookmarkManager.parseXbel(sampleXbel)
        val compose = parsed.first { it.id == "10" }

        assertTrue(FloccusBookmarkManager.scoreFloccusBookmark("compose", compose) > 0)
        assertTrue(FloccusBookmarkManager.scoreFloccusBookmark("developer.android", compose) > 0)
        assertTrue(FloccusBookmarkManager.scoreFloccusBookmark("dev tools", compose) > 0)
        assertTrue(FloccusBookmarkManager.scoreFloccusBookmark("dev compose", compose) > 0)
        assertEquals(0, FloccusBookmarkManager.scoreFloccusBookmark("zzzznotfound", compose))
    }

    @Test
    fun `QueryRouter routes fl and floccus prefixes and shows setup prompt when empty`() {
        val cmd1 = QueryRouter.parsePromptCommand("fl compose", bookmarks, engines)
        assertEquals(PromptIntentMode.FLOCCUS_ONLY, cmd1.mode)
        assertEquals("compose", cmd1.filterQuery)

        val cmd2 = QueryRouter.parsePromptCommand("floccus dev", bookmarks, engines)
        assertEquals(PromptIntentMode.FLOCCUS_ONLY, cmd2.mode)
        assertEquals("dev", cmd2.filterQuery)

        val emptyResults = QueryRouter.buildPaletteResults(
            rawInput = "fl",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = emptyList(),
        )
        assertTrue(emptyResults.first() is PaletteItem.FloccusBookmarkAction)
        assertTrue((emptyResults.first() as PaletteItem.FloccusBookmarkAction).isSetupPrompt)
    }

    @Test
    fun `unified search mode includes Floccus in ALL and b while separated mode restricts Floccus to fl and exact alias`() {
        val floccusItems = listOf(
            FloccusBookmarkItem(
                id = "fl_compose",
                title = "Jetpack Compose Docs",
                url = "https://developer.android.com/jetpack/compose",
                folderPath = "Dev / Android",
            ),
        )

        // Unified ON (default): appears in ALL and `b` and `fl`
        val unifiedAll = QueryRouter.buildPaletteResults(
            rawInput = "compose",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = floccusItems,
            includeFloccusInUnified = true,
        )
        assertTrue(unifiedAll.any { it is PaletteItem.FloccusBookmarkAction && it.bookmark?.id == "fl_compose" })

        val unifiedB = QueryRouter.buildPaletteResults(
            rawInput = "b compose",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = floccusItems,
            includeFloccusInUnified = true,
        )
        assertTrue(unifiedB.any { it is PaletteItem.FloccusBookmarkAction && it.bookmark?.id == "fl_compose" })

        // Separated OFF: hidden from ALL and `b`, still searchable under `fl` and exact `@alias`
        val separatedAll = QueryRouter.buildPaletteResults(
            rawInput = "compose",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = floccusItems,
            includeFloccusInUnified = false,
        )
        assertFalse(separatedAll.any { it is PaletteItem.FloccusBookmarkAction })

        val separatedB = QueryRouter.buildPaletteResults(
            rawInput = "b compose",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = floccusItems,
            includeFloccusInUnified = false,
        )
        assertFalse(separatedB.any { it is PaletteItem.FloccusBookmarkAction })

        val separatedFl = QueryRouter.buildPaletteResults(
            rawInput = "fl compose",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = floccusItems,
            includeFloccusInUnified = false,
        )
        assertTrue(separatedFl.first() is PaletteItem.FloccusBookmarkAction)
        assertEquals("fl_compose", (separatedFl.first() as PaletteItem.FloccusBookmarkAction).bookmark?.id)

        val aliasedSeparated = QueryRouter.buildPaletteResults(
            rawInput = "jcd",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            floccusBookmarks = floccusItems,
            includeFloccusInUnified = false,
            universalAliases = listOf(
                UniversalAlias(
                    targetKey = floccusItems.first().usageKey,
                    alias = "jcd",
                    label = "Jetpack Compose Docs",
                    category = "fl",
                ),
            ),
        )
        assertTrue(aliasedSeparated.first() is PaletteItem.FloccusBookmarkAction)
        assertEquals("fl_compose", (aliasedSeparated.first() as PaletteItem.FloccusBookmarkAction).bookmark?.id)
    }

    @Test
    fun `full backup JSON round-trips custom floccus prefix`() {
        val bundle = FullBackupBundle(
            engines = engines,
            bookmarks = bookmarks,
            prefixes = PrefixConfig(floccus = "fbm"),
        )
        val json = QueryRouter.exportFullBackupToJson(bundle)
        val imported = QueryRouter.importFullBackupFromJson(json)
        assertNotNull(imported)
        assertEquals("fbm", imported!!.prefixes?.floccus)
    }
}
