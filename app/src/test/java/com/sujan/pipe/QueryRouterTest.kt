package com.sujan.pipe

import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
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

        // Verify strict alias scoping (`f` -> only FileAction items; `c` -> only ContactAction items)
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

        // Verify empty / no-alias input combines all categories, caps at max 10, and sorts by most used
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

        // Verify multi-word file ("sasu ama.jpg") and nested folder-path matching ("Nagarikata")
        val deepStorage = listOf(
            com.sujan.pipe.model.LocalFileItem("sasu ama.jpg", "/storage/emulated/0/DCIM/Camera/sasu ama.jpg", isDirectory = false, parentName = "DCIM/Camera", sizeBytes = 2_400_000L),
            com.sujan.pipe.model.LocalFileItem("Nagarikata", "/storage/emulated/0/Documents/Legal/Nagarikata", isDirectory = true, parentName = "Documents/Legal", sizeBytes = 0L),
            com.sujan.pipe.model.LocalFileItem("front_scan.jpg", "/storage/emulated/0/Documents/Legal/Nagarikata/front_scan.jpg", isDirectory = false, parentName = "Documents/Legal/Nagarikata", sizeBytes = 850_000L),
            com.sujan.pipe.model.LocalFileItem("back_scan.jpg", "/storage/emulated/0/Documents/Legal/Nagarikata/back_scan.jpg", isDirectory = false, parentName = "Documents/Legal/Nagarikata", sizeBytes = 910_000L),
        )
        val sasuMatches = FuzzyMatcher.filterFiles("sasu ama", deepStorage)
        assertEquals(1, sasuMatches.size)
        assertEquals("sasu ama.jpg", sasuMatches.first().name)

        // Searching "nagarikata" returns BOTH the Nagarikata folder AND the files inside .../Nagarikata/
        val nagarikataMatches = FuzzyMatcher.filterFiles("nagarikata", deepStorage)
        assertEquals(3, nagarikataMatches.size)

        // Verify Feature 1: Contact action chaining (`c kali wa` -> WHATSAPP, filterQuery = "kali")
        val cmdWa = QueryRouter.parsePromptCommand("c kali wa", bookmarks, engines)
        assertEquals(PromptIntentMode.CONTACTS_ONLY, cmdWa.mode)
        assertEquals("kali", cmdWa.filterQuery)
        assertEquals(com.sujan.pipe.model.ContactQuickAction.WHATSAPP, cmdWa.contactAction)

        // Verify Feature 6: Direct App Launcher alias (`app mix` -> APPS_ONLY)
        val cmdApp = QueryRouter.parsePromptCommand("app mix", bookmarks, engines)
        assertEquals(PromptIntentMode.APPS_ONLY, cmdApp.mode)
        assertEquals("mix", cmdApp.filterQuery)
    }
}
