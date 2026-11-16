package com.sujan.pipe

import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.DictionaryEntry
import com.sujan.pipe.model.FullBackupBundle
import com.sujan.pipe.model.PrefixConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRouterTest {

    private val engines = DefaultEngines.LIST
    private val bookmarks = DefaultBookmarks.LIST

    @Test
    fun `matches standard lowercase keyword and builds encoded search URL`() {
        val res = QueryRouter.resolve("yt lofi music", engines)
        assertTrue(res.matchedByKeyword)
        assertEquals("yt", res.extractedKeyword)
        assertEquals("YouTube", res.engine.name)
        assertEquals("lofi music", res.query)
        assertFalse(res.isHomeLaunch)
        assertEquals("https://www.youtube.com/results?search_query=lofi+music", res.targetUrl)
    }

    @Test
    fun `matches youtube time-filtered search pipes ytt, ytw, and ytm by default`() {
        val resToday = QueryRouter.resolve("ytt kotlin compose", engines)
        assertTrue(resToday.matchedByKeyword)
        assertEquals("ytt", resToday.extractedKeyword)
        assertEquals("YouTube Today", resToday.engine.name)
        assertEquals("https://www.youtube.com/results?search_query=kotlin+compose&sp=EgIIAg%3D%3D", resToday.targetUrl)

        val resWeek = QueryRouter.resolve("ytw android dev", engines)
        assertTrue(resWeek.matchedByKeyword)
        assertEquals("ytw", resWeek.extractedKeyword)
        assertEquals("YouTube Last Week", resWeek.engine.name)
        assertEquals("https://www.youtube.com/results?search_query=android+dev&sp=EgIIAw%3D%3D", resWeek.targetUrl)

        val resMonth = QueryRouter.resolve("ytm linux terminal", engines)
        assertTrue(resMonth.matchedByKeyword)
        assertEquals("ytm", resMonth.extractedKeyword)
        assertEquals("YouTube This Month", resMonth.engine.name)
        assertEquals("https://www.youtube.com/results?search_query=linux+terminal&sp=EgIIBA%3D%3D", resMonth.targetUrl)
    }

    @Test
    fun `custom PrefixConfig routes custom files, pipes, bookmarks, apps, history, dictionary, and clipboard prefixes`() {
        val customPrefixes = PrefixConfig(
            files = "fl",
            pipes = "sp",
            bookmarks = "bk",
            apps = "la",
            history = "hs",
            contacts = "ct",
            dictionary = "dict",
            clipboard = "cp",
        )
        assertEquals(PromptIntentMode.FILES_ONLY, QueryRouter.parsePromptCommand("fl report", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.ENGINE_ONLY, QueryRouter.parsePromptCommand("sp yt music", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.BOOKMARKS_ONLY, QueryRouter.parsePromptCommand("bk docs", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.APPS_ONLY, QueryRouter.parsePromptCommand("la mix", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.HISTORY_ONLY, QueryRouter.parsePromptCommand("hs compose", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.CONTACTS_ONLY, QueryRouter.parsePromptCommand("ct kalika", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.DICTIONARY_ONLY, QueryRouter.parsePromptCommand("dict serendipity", bookmarks, engines, customPrefixes).mode)
        assertEquals(PromptIntentMode.CLIPBOARD_ONLY, QueryRouter.parsePromptCommand("cp", bookmarks, engines, customPrefixes).mode)
    }

    @Test
    fun `detectDirectUrl recognizes bare domains, http URLs, localhost, and local IPs with ports`() {
        assertEquals("https://audiobookbay.lu", QueryRouter.detectDirectUrl("audiobookbay.lu"))
        assertEquals("https://github.com/sujansujan/Pipe", QueryRouter.detectDirectUrl("github.com/sujansujan/Pipe"))
        assertEquals("http://192.168.1.1:8080/admin", QueryRouter.detectDirectUrl("192.168.1.1:8080/admin"))
        assertEquals("http://localhost:3000", QueryRouter.detectDirectUrl("localhost:3000"))
        assertNull(QueryRouter.detectDirectUrl("yt lofi music"))
        assertNull(QueryRouter.detectDirectUrl("citizenship.pdf"))
    }

    @Test
    fun `stripTrackingParams removes utm, fbclid, igsh, and si query parameters`() {
        val dirty = "https://example.com/article?id=42&utm_source=twitter&utm_medium=social&fbclid=IwAR123"
        val clean = QueryRouter.stripTrackingParams(dirty)
        assertEquals("https://example.com/article?id=42", clean)

        val allDirty = "https://youtu.be/dQw4w9WgXcQ?si=abc123xyz&utm_campaign=share"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", QueryRouter.stripTrackingParams(allDirty))
    }

    @Test
    fun `on-demand dictionary results only surface in DICTIONARY_ONLY mode`() {
        val sampleDict = listOf(
            DictionaryEntry(
                word = "serendipity",
                partOfSpeech = "noun",
                definition = "The occurrence and development of events by chance in a happy or beneficial way.",
                synonyms = "chance, fluke",
            )
        )
        val normalAll = QueryRouter.buildPaletteResults(
            rawInput = "serendipity",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            dictionaryMatches = sampleDict,
            isDictionaryInstalled = true,
        )
        assertFalse(normalAll.any { it is PaletteItem.DictionaryAction })

        val dictScoped = QueryRouter.buildPaletteResults(
            rawInput = "def serendipity",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
            dictionaryMatches = sampleDict,
            isDictionaryInstalled = true,
        )
        assertTrue(dictScoped.first() is PaletteItem.DictionaryAction)
        val firstDict = dictScoped.first() as PaletteItem.DictionaryAction
        assertEquals("serendipity", firstDict.entry?.word)
    }

    @Test
    fun `full v2 backup export and import round-trip preserves engines, bookmarks, prefixes, and excluded folders`() {
        val customPrefixes = PrefixConfig(files = "fl", dictionary = "d", clipboard = "cl")
        val bundle = FullBackupBundle(
            version = 2,
            engines = engines,
            bookmarks = bookmarks,
            prefixes = customPrefixes,
            excludedFolders = listOf("Telegram", "WhatsApp"),
            usageCounts = mapOf("bm:bm_dl" to 9),
        )
        val json = QueryRouter.exportFullBackupToJson(bundle)
        val restored = QueryRouter.importFullBackupFromJson(json)
        assertEquals(2, restored.version)
        assertEquals(engines.size, restored.engines.size)
        assertEquals(bookmarks.size, restored.bookmarks.size)
        assertEquals("fl", restored.prefixes?.files)
        assertEquals("d", restored.prefixes?.dictionary)
        assertEquals("cl", restored.prefixes?.clipboard)
        assertEquals(listOf("Telegram", "WhatsApp"), restored.excludedFolders)
        assertEquals(9, restored.usageCounts["bm:bm_dl"])
    }

    @Test
    fun `no keyword match falls back to default engine with full input as query`() {
        val res = QueryRouter.resolve("best mechanical keyboards 2026", engines)
        assertFalse(res.matchedByKeyword)
        assertNull(res.extractedKeyword)
        assertEquals("Google", res.engine.name)
        assertEquals("best mechanical keyboards 2026", res.query)
        assertFalse(res.isHomeLaunch)
        assertEquals("https://www.google.com/search?q=best+mechanical+keyboards+2026", res.targetUrl)
    }

    @Test
    fun `keyword with empty query opens engine home URL`() {
        val resYt = QueryRouter.resolve("yt", engines)
        assertTrue(resYt.matchedByKeyword)
        assertTrue(resYt.isHomeLaunch)
        assertEquals("", resYt.query)
        assertEquals("https://www.youtube.com", resYt.targetUrl)

        val resMaps = QueryRouter.resolve("maps   ", engines)
        assertTrue(resMaps.matchedByKeyword)
        assertTrue(resMaps.isHomeLaunch)
        assertEquals("", resMaps.query)
        assertEquals("https://www.google.com/maps", resMaps.targetUrl)
    }

    @Test
    fun `special characters in query are properly URL-encoded`() {
        val res = QueryRouter.resolve("g c++ & rust #1 100%?", engines)
        assertTrue(res.matchedByKeyword)
        assertEquals("g", res.extractedKeyword)
        assertEquals("c++ & rust #1 100%?", res.query)
        assertEquals("https://www.google.com/search?q=c%2B%2B+%26+rust+%231+100%25%3F", res.targetUrl)
    }

    @Test
    fun `multiple leading trailing and internal spaces are normalized cleanly`() {
        val res = QueryRouter.resolve("   gh     jetpack    compose   ascii   ", engines)
        assertTrue(res.matchedByKeyword)
        assertEquals("gh", res.extractedKeyword)
        assertEquals("jetpack compose ascii", res.query)
        assertEquals("https://github.com/search?q=jetpack+compose+ascii", res.targetUrl)
    }

    @Test
    fun `uppercase and mixed-case keywords match case-insensitively`() {
        val upperYt = QueryRouter.resolve("YT synthwave mix", engines)
        assertTrue(upperYt.matchedByKeyword)
        assertEquals("yt", upperYt.extractedKeyword)
        assertEquals("synthwave mix", upperYt.query)
        assertEquals("https://www.youtube.com/results?search_query=synthwave+mix", upperYt.targetUrl)

        val mixedWiki = QueryRouter.resolve("W Nepal Rastra Bank", engines)
        assertTrue(mixedWiki.matchedByKeyword)
        assertEquals("w", mixedWiki.extractedKeyword)
        assertEquals("https://en.wikipedia.org/w/index.php?search=Nepal+Rastra+Bank", mixedWiki.targetUrl)
    }

    @Test
    fun `inline validation catches duplicate keyword, spaces in keyword, missing query placeholder, and invalid URL`() {
        val dup = QueryRouter.validateEngine("Second YT", "YT", "https://youtube.com/results?q={query}", engines)
        assertFalse(dup.isValid)
        assertNotNull(dup.keywordError)

        val space = QueryRouter.validateEngine("My Search", "my key", "https://example.com/?q={query}", engines)
        assertFalse(space.isValid)
        assertEquals("! keyword cannot contain spaces", space.keywordError)

        val missing = QueryRouter.validateEngine("Example", "ex", "https://example.com/search", engines)
        assertFalse(missing.isValid)
        assertEquals("! url template must contain {query}", missing.urlTemplateError)

        val badUrl = QueryRouter.validateEngine("Bad", "bad", "ftp://not-http/{query}", engines)
        assertFalse(badUrl.isValid)
        assertNotNull(badUrl.urlTemplateError)
    }

    @Test
    fun `json export and import round-trip preserves all engines`() {
        val json = QueryRouter.exportEnginesToJson(engines)
        val roundTripped = QueryRouter.importEnginesFromJson(json)
        assertEquals(engines.size, roundTripped.size)
        assertEquals(engines.map { it.keyword }, roundTripped.map { it.keyword })
        assertEquals(1, roundTripped.count { it.isDefault })
    }

    @Test
    fun `extractQueryFromSearchUri decodes default browser search URLs and ignores normal websites`() {
        val qGoogle = QueryRouter.extractQueryFromSearchUri("https://www.google.com/search?q=yt+lofi+music&ie=UTF-8")
        assertEquals("yt lofi music", qGoogle)

        val qPipe = QueryRouter.extractQueryFromSearchUri("pipe://search?q=gh+compose")
        assertEquals("gh compose", qPipe)

        val normalSite = QueryRouter.extractQueryFromSearchUri("https://github.com/sujansujan/Pipe")
        assertNull(normalSite)
    }

    @Test
    fun `findMatchedIndices marks word-boundary, multi-token, and subsequence spans accurately`() {
        val mask1 = FuzzyMatcher.findMatchedIndices("Kalika Prasad", "kal")
        assertTrue(mask1[0] && mask1[1] && mask1[2])
        assertFalse(mask1[3])

        val mask2 = FuzzyMatcher.findMatchedIndices("citizenship_scan.pdf", "citi pdf")
        assertTrue(mask2[0] && mask2[1] && mask2[2] && mask2[3])
        assertFalse(mask2[4])
        val len = "citizenship_scan.pdf".length
        assertTrue(mask2[len - 3] && mask2[len - 2] && mask2[len - 1])
    }

    @Test
    fun `contact fuzzy search and folder bookmark aliases parse and rank accurately`() {
        val cmdContact = QueryRouter.parsePromptCommand("c kali", bookmarks)
        assertEquals(PromptIntentMode.CONTACTS_ONLY, cmdContact.mode)
        assertEquals("kali", cmdContact.filterQuery)

        val contacts = listOf(
            ContactItem("1", "Sujan Devkota", "9841000000"),
            ContactItem("2", "Kalika Prasad", "9841234567"),
            ContactItem("3", "Ramesh Kali", "9801112223"),
        )
        val matched = FuzzyMatcher.filterContacts("kali", contacts)
        assertEquals(2, matched.size)
        assertEquals("Kalika Prasad", matched.first().displayName)
        assertEquals("9779841234567", QueryRouter.normalizeWhatsAppPhone(matched.first().phoneNumber))

        val cmdDl = QueryRouter.parsePromptCommand("dl invoice", bookmarks)
        assertEquals(PromptIntentMode.SCOPED_FOLDER, cmdDl.mode)
        assertEquals("invoice", cmdDl.filterQuery)
        assertEquals("/storage/emulated/0/Download", cmdDl.scopedFolderBookmark?.target)

        val cmdFiles = QueryRouter.parsePromptCommand("f doc", bookmarks)
        assertEquals(PromptIntentMode.FILES_ONLY, cmdFiles.mode)
        val sampleItems = listOf(
            com.sujan.pipe.model.LocalFileItem("doc_notes.txt", "/storage/emulated/0/Download/doc_notes.txt", isDirectory = false, parentName = "Download"),
            com.sujan.pipe.model.LocalFileItem("Documents", "/storage/emulated/0/Documents", isDirectory = true, parentName = "storage"),
        )
        val fileMatches = FuzzyMatcher.filterFiles("doc", sampleItems)
        assertEquals(2, fileMatches.size)
        assertTrue(fileMatches.first().isDirectory)
        assertEquals("Documents", fileMatches.first().name)

        val usage = mapOf("file:/storage/emulated/0/Documents" to 5, "bm:bm_dl" to 12)
        val fOnly = QueryRouter.buildPaletteResults(
            rawInput = "f doc",
            engines = engines,
            bookmarks = bookmarks,
            contacts = contacts,
            storageFiles = sampleItems,
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = usage,
        )
        assertTrue(fOnly.all { it is com.sujan.pipe.domain.PaletteItem.FileAction })

        val cOnly = QueryRouter.buildPaletteResults(
            rawInput = "c kali",
            engines = engines,
            bookmarks = bookmarks,
            contacts = contacts,
            storageFiles = sampleItems,
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = usage,
        )
        assertTrue(cOnly.all { it is com.sujan.pipe.domain.PaletteItem.ContactAction })

        val allMostUsed = QueryRouter.buildPaletteResults(
            rawInput = "",
            engines = engines,
            bookmarks = bookmarks,
            contacts = contacts,
            storageFiles = sampleItems,
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = usage,
        )
        assertTrue(allMostUsed.size <= 10)
        assertEquals("bm:bm_dl", allMostUsed.first().usageKey)
    }
}
