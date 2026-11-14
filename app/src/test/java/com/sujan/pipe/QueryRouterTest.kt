package com.sujan.pipe

import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.DefaultEngines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRouterTest {

    private val engines = DefaultEngines.LIST

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
    fun `extractQueryFromSearchUri decodes custom browser search engine URLs`() {
        val q1 = QueryRouter.extractQueryFromSearchUri("pipe://search?q=yt+lofi+music")
        assertEquals("yt lofi music", q1)
        val res1 = QueryRouter.resolve(q1!!, engines)
        assertEquals("https://www.youtube.com/results?search_query=lofi+music", res1.targetUrl)

        val q2 = QueryRouter.extractQueryFromSearchUri("https://pipe.local/search?q=gh%20compose%20ui")
        assertEquals("gh compose ui", q2)
        val res2 = QueryRouter.resolve(q2!!, engines)
        assertEquals("https://github.com/search?q=compose+ui", res2.targetUrl)
    }
}
