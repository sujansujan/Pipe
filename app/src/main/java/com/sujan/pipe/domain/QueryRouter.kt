package com.sujan.pipe.domain

import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.ContactQuickAction
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.InstalledAppItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fast, zero-dependency fuzzy matcher for contacts, bookmarks, folders, and files.
 */
object FuzzyMatcher {

    /**
     * Zero-allocation fuzzy scorer operating on pre-lowercased [q] and [c].
     * Uses fast character-Boundary checks instead of `Regex.split` or `String.lowercase`.
     */
    fun scorePreLowered(q: String, c: String): Int {
        if (q.isEmpty()) return 1
        if (c.isEmpty()) return 0

        if (c == q) return 1000
        if (c.startsWith(q)) return 850 - (c.length - q.length).coerceAtMost(100)

        // Fast early exit if single-token query's first character isn't even present in candidate
        val hasSpace = q.indexOf(' ') >= 0
        if (!hasSpace && c.indexOf(q[0]) < 0) return 0

        // Word-boundary prefix check without allocating Regex or List<String>
        val wordMatch = checkWordBoundaryMatch(q, c)
        if (wordMatch > 0) return wordMatch

        // Contiguous substring match
        val subIdx = c.indexOf(q)
        if (subIdx >= 0) return 600 - subIdx.coerceAtMost(80)

        // Multi-word token check without Regex allocation
        if (hasSpace) {
            return scoreMultiWordTokens(q, c)
        }

        return scoreSingleTokenSubsequence(q, c)
    }

    fun score(query: String, candidate: String): Int {
        val q = query.trim().lowercase()
        val c = candidate.trim().lowercase()
        return scorePreLowered(q, c)
    }

    private fun isWordSep(ch: Char): Boolean =
        ch == ' ' || ch == '_' || ch == '-' || ch == '.' || ch == '/'

    private fun checkWordBoundaryMatch(q: String, c: String): Int {
        val qLen = q.length
        val cLen = c.length
        if (qLen > cLen) return 0
        var i = 0
        while (i <= cLen - qLen) {
            if (i == 0 || isWordSep(c[i - 1])) {
                if (c.regionMatches(i, q, 0, qLen, ignoreCase = false)) {
                    val endIdx = i + qLen
                    return if (endIdx == cLen || isWordSep(c[endIdx])) 780 else 700
                }
            }
            i++
        }
        return 0
    }

    private fun scoreMultiWordTokens(q: String, c: String): Int {
        var total = 0
        var count = 0
        var start = 0
        val len = q.length
        while (start < len) {
            while (start < len && q[start] == ' ') start++
            if (start >= len) break
            var end = start
            while (end < len && q[end] != ' ') end++
            val token = q.substring(start, end)
            val s = scoreSingleTokenSubsequence(token, c)
            if (s <= 0) return 0
            total += s
            count++
            start = end
        }
        return if (count > 0) total / count else 0
    }

    private fun scoreSingleTokenSubsequence(q: String, c: String): Int {
        val qLen = q.length
        val cLen = c.length
        if (qLen > cLen) return 0
        val firstPos = c.indexOf(q[0])
        if (firstPos < 0) return 0
        if (cLen - firstPos < qLen) return 0

        var qIdx = 0
        var consecutive = 0
        var bonus = 0

        for (cIdx in firstPos until cLen) {
            if (qIdx < qLen && c[cIdx] == q[qIdx]) {
                consecutive++
                bonus += consecutive * 15
                if (cIdx == 0 || isWordSep(c[cIdx - 1])) {
                    bonus += 25
                }
                qIdx++
                if (qIdx == qLen) break
            } else {
                consecutive = 0
            }
        }
        if (qIdx < qLen) return 0
        val compactness = (200 - (cLen - qLen).coerceAtMost(150)).coerceAtLeast(10)
        val startPenalty = firstPos.coerceAtMost(50)
        return (150 + bonus + compactness - startPenalty).coerceAtLeast(1)
    }

    fun filterContacts(query: String, contacts: List<ContactItem>, limit: Int = 10): List<ContactItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return contacts.take(limit)
        return contacts
            .mapNotNull { item ->
                val nameScore = scorePreLowered(clean, item.nameLower)
                val phoneScore = if (clean[0].isDigit()) scorePreLowered(clean, item.cleanPhone) else 0
                val best = maxOf(nameScore, phoneScore)
                if (best > 0) item to best else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun filterBookmarks(query: String, bookmarks: List<PipeBookmark>, limit: Int = 10): List<PipeBookmark> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return bookmarks.take(limit)
        return bookmarks
            .mapNotNull { bm ->
                val aliasScore = if (bm.alias.equals(clean, ignoreCase = true)) {
                    2000
                } else {
                    scorePreLowered(clean, bm.alias.lowercase()) + 120
                }
                val titleScore = scorePreLowered(clean, bm.title.lowercase())
                val pathScore = scorePreLowered(clean, bm.target.substringAfterLast('/').lowercase())
                val tagScore = if (bm.tagsLower.isNotEmpty()) scorePreLowered(clean.removePrefix("#"), bm.tagsLower) + 80 else 0
                val best = maxOf(aliasScore, titleScore, pathScore, tagScore)
                if (best > 0) bm to best else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Zero-allocation file/folder scorer using pre-lowercased fields (`nameLower`, `parentLower`, `pathLower`).
     */
    fun scoreFileItemPreLowered(cleanLowerQuery: String, item: LocalFileItem): Int {
        if (cleanLowerQuery.isEmpty()) return if (item.isDirectory) 220 else 100
        val nameScore = scorePreLowered(cleanLowerQuery, item.nameLower)
        val parentScore = scorePreLowered(cleanLowerQuery, item.parentLower)
        val effectiveParentScore = if (parentScore >= 550) (parentScore - 80).coerceAtLeast(150) else 0
        val pathScore = if (cleanLowerQuery.indexOf('/') >= 0) scorePreLowered(cleanLowerQuery, item.pathLower) else 0
        val best = maxOf(nameScore, effectiveParentScore, pathScore)
        if (best <= 0) return 0
        val dirBonus = if (item.isDirectory && nameScore > 0) 110 else 0
        return best + dirBonus
    }

    fun scoreFileItem(query: String, item: LocalFileItem): Int =
        scoreFileItemPreLowered(query.trim().lowercase(), item)

    fun filterFiles(query: String, files: List<LocalFileItem>, limit: Int = 10): List<LocalFileItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) {
            return files.sortedByDescending { it.isDirectory }.take(limit)
        }
        return files
            .mapNotNull { f ->
                val s = scoreFileItemPreLowered(clean, f)
                if (s > 0) f to s else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}

data class RouteResolution(
    val engine: SearchEngine,
    val matchedByKeyword: Boolean,
    val extractedKeyword: String?,
    val query: String,
    val targetUrl: String,
    val isHomeLaunch: Boolean,
) {
    val pipelinePreview: String
        get() = when {
            isHomeLaunch -> "echo '' | ${engine.keyword} -> ${engine.homeUrl}"
            matchedByKeyword -> "\"$query\" | ${engine.keyword} -> ${engine.name.lowercase()}"
            else -> "\"$query\" | ${engine.keyword} (default) -> ${engine.name.lowercase()}"
        }
}

enum class PromptIntentMode(val scopeBadge: String) {
    ALL("all"),
    CONTACTS_ONLY("c:contacts"),
    FILES_ONLY("f:files+dirs"),
    DIRS_ONLY("d:folders"),
    BOOKMARKS_ONLY("bm:bookmarks"),
    APPS_ONLY("app:launcher"),
    SCOPED_FOLDER("dir:scoped"),
    ENGINE_ONLY("pipe:web"),
}

data class ParsedPromptCommand(
    val mode: PromptIntentMode,
    val filterQuery: String,
    val exactBookmark: PipeBookmark? = null,
    val scopedFolderBookmark: PipeBookmark? = null,
    val matchedEngine: SearchEngine? = null,
    val contactAction: ContactQuickAction = ContactQuickAction.CALL,
)

/**
 * Unified Raycast-style command palette row item.
 * Every item has a `usageKey` used to track and sort by "most used" (`usageCount`).
 */
sealed interface PaletteItem {
    val usageKey: String
    val rankScore: Int
    val usageCount: Int

    data class EngineAction(
        val engine: SearchEngine,
        val query: String,
        val targetUrl: String,
        val isHome: Boolean,
        override val usageKey: String,
        override val rankScore: Int,
        override val usageCount: Int,
    ) : PaletteItem

    data class BookmarkAction(
        val bookmark: PipeBookmark,
        override val usageKey: String,
        override val rankScore: Int,
        override val usageCount: Int,
    ) : PaletteItem

    data class ContactAction(
        val contact: ContactItem,
        val preferredAction: ContactQuickAction = ContactQuickAction.CALL,
        override val usageKey: String,
        override val rankScore: Int,
        override val usageCount: Int,
    ) : PaletteItem

    data class AppAction(
        val app: InstalledAppItem,
        override val usageKey: String,
        override val rankScore: Int,
        override val usageCount: Int,
    ) : PaletteItem

    data class FileAction(
        val file: LocalFileItem,
        override val usageKey: String,
        override val rankScore: Int,
        override val usageCount: Int,
    ) : PaletteItem

    data class HistoryAction(
        val entry: SearchHistoryEntry,
        override val usageKey: String,
        override val rankScore: Int,
        override val usageCount: Int,
    ) : PaletteItem
}

data class EngineValidationErrors(
    val nameError: String? = null,
    val keywordError: String? = null,
    val urlTemplateError: String? = null,
) {
    val isValid: Boolean
        get() = nameError == null && keywordError == null && urlTemplateError == null
}

object QueryRouter {

    const val MAX_PALETTE_RESULTS = 10
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Parses the user's input to determine whether an explicit alias was typed:
     * - `c` / `c <query>` -> `CONTACTS_ONLY`
     * - `f` / `f <query>` -> `FILES_ONLY` (searches both folders and files)
     * - `d` / `dir <query>` -> `DIRS_ONLY` (searches folders only)
     * - `b` / `bm <query>` -> `BOOKMARKS_ONLY`
     * - `<bookmark_alias>` / `<bookmark_alias> <subfilter>` -> `SCOPED_FOLDER` or exact bookmark
     * - `<engine_keyword>` / `<engine_keyword> <query>` -> `ENGINE_ONLY`
     * - Otherwise (no alias typed) -> `ALL` (shows all categories combined, max 10, ranked by most used!)
     */
    fun parsePromptCommand(
        rawInput: String,
        bookmarks: List<PipeBookmark>,
        engines: List<SearchEngine> = DefaultEngines.LIST,
    ): ParsedPromptCommand {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedPromptCommand(PromptIntentMode.ALL, "")
        }
        val tokens = trimmed.split(WHITESPACE_REGEX, limit = 2)
        val first = tokens[0].lowercase()
        val hasSpace = rawInput.trimStart().length > first.length
        val remainder = if (tokens.size > 1) tokens[1].trim() else ""

        // 1. Contact alias: `c <name>` with optional action chaining (`c kali wa`, `c kali sms`, `c kali call`)
        if (first == "c" || first == "call" || first == "contact") {
            val subTokens = remainder.trim().split(WHITESPACE_REGEX)
            if (subTokens.size >= 2) {
                val lastToken = subTokens.last().lowercase()
                val chainedAction = when (lastToken) {
                    "wa", "whatsapp", "w" -> ContactQuickAction.WHATSAPP
                    "sms", "text", "msg", "s" -> ContactQuickAction.SMS
                    "call", "dial", "tel" -> ContactQuickAction.CALL
                    else -> null
                }
                if (chainedAction != null) {
                    val namePart = subTokens.dropLast(1).joinToString(" ")
                    return ParsedPromptCommand(
                        mode = PromptIntentMode.CONTACTS_ONLY,
                        filterQuery = namePart,
                        contactAction = chainedAction,
                    )
                }
            }
            return ParsedPromptCommand(PromptIntentMode.CONTACTS_ONLY, remainder, contactAction = ContactQuickAction.CALL)
        }

        // 1b. Direct App Launcher alias: `app <query>` or `ap <query>`
        if (first == "app" || first == "apps" || first == "ap") {
            return ParsedPromptCommand(PromptIntentMode.APPS_ONLY, remainder)
        }

        // 2. File + Folder search alias: `f` or `file` or `files`
        if (first == "f" || first == "file" || first == "files") {
            return ParsedPromptCommand(PromptIntentMode.FILES_ONLY, remainder)
        }

        // 3. Directory-only alias: `d` or `dir` or `folder`
        if (first == "d" || first == "dir" || first == "folder" || first == "folders") {
            return ParsedPromptCommand(PromptIntentMode.DIRS_ONLY, remainder)
        }

        // 4. Bookmark search alias: `b` or `bm`
        if (first == "b" || first == "bm") {
            return ParsedPromptCommand(PromptIntentMode.BOOKMARKS_ONLY, remainder)
        }

        // 5. Bookmark alias match (e.g. `dl`, `docs`, `pic`, `dcim`, `sd`, or `dl invoice`)
        val matchedBookmark = bookmarks.firstOrNull {
            it.alias.isNotBlank() && it.alias.equals(first, ignoreCase = true)
        }
        if (matchedBookmark != null) {
            return if (matchedBookmark.kind == BookmarkKind.FOLDER) {
                ParsedPromptCommand(
                    mode = PromptIntentMode.SCOPED_FOLDER,
                    filterQuery = remainder,
                    exactBookmark = if (remainder.isEmpty()) matchedBookmark else null,
                    scopedFolderBookmark = matchedBookmark,
                )
            } else if (remainder.isEmpty()) {
                ParsedPromptCommand(
                    mode = PromptIntentMode.BOOKMARKS_ONLY,
                    filterQuery = first,
                    exactBookmark = matchedBookmark,
                )
            } else {
                ParsedPromptCommand(PromptIntentMode.ALL, trimmed)
            }
        }

        // 6. Search engine keyword match (e.g. `yt lofi`, `g phones`, or `yt` with space)
        val matchedEngine = engines.firstOrNull {
            it.keyword.equals(first, ignoreCase = true)
        }
        if (matchedEngine != null && (hasSpace || remainder.isNotEmpty() || trimmed.equals(matchedEngine.keyword, ignoreCase = true))) {
            return ParsedPromptCommand(
                mode = PromptIntentMode.ENGINE_ONLY,
                filterQuery = remainder,
                matchedEngine = matchedEngine,
            )
        }

        return ParsedPromptCommand(PromptIntentMode.ALL, trimmed)
    }

    /**
     * Builds the unified Raycast-style command list (strictly capped at [MAX_PALETTE_RESULTS] = 10),
     * scoped to the active alias if one was typed, or combining all categories when no alias is typed,
     * and ranked by `usageCount` (most used) + fuzzy match quality.
     */
    fun buildPaletteResults(
        rawInput: String,
        engines: List<SearchEngine>,
        bookmarks: List<PipeBookmark>,
        contacts: List<ContactItem>,
        storageFiles: List<LocalFileItem>,
        scopedFolderFiles: List<LocalFileItem>,
        history: List<SearchHistoryEntry>,
        usageMap: Map<String, Int>,
        installedApps: List<InstalledAppItem> = emptyList(),
    ): List<PaletteItem> {
        val cmd = parsePromptCommand(rawInput, bookmarks, engines)
        fun usageOf(key: String): Int = usageMap[key] ?: 0
        fun usageBonus(key: String): Int = (usageMap[key] ?: 0) * 180

        return when (cmd.mode) {
            PromptIntentMode.CONTACTS_ONLY -> {
                val q = cmd.filterQuery.trim().lowercase()
                contacts.mapNotNull { c ->
                    val uKey = "contact:${c.cleanPhone}"
                    val uCount = usageOf(uKey)
                    val fScore = if (q.isEmpty()) 100 else maxOf(
                        FuzzyMatcher.scorePreLowered(q, c.nameLower),
                        if (q[0].isDigit()) FuzzyMatcher.scorePreLowered(q, c.cleanPhone) else 0,
                    )
                    if (fScore > 0) {
                        PaletteItem.ContactAction(
                            contact = c,
                            preferredAction = cmd.contactAction,
                            usageKey = uKey,
                            rankScore = fScore + usageBonus(uKey),
                            usageCount = uCount,
                        )
                    } else null
                }.sortedWith(compareByDescending<PaletteItem.ContactAction> { it.rankScore }.thenByDescending { it.usageCount })
                    .take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.APPS_ONLY -> {
                val q = cmd.filterQuery.trim().lowercase()
                installedApps.mapNotNull { app ->
                    val uKey = "app:${app.packageName}"
                    val uCount = usageOf(uKey)
                    val fScore = if (q.isEmpty()) 100 else maxOf(
                        FuzzyMatcher.scorePreLowered(q, app.labelLower),
                        FuzzyMatcher.scorePreLowered(q, app.pkgLower.substringAfterLast('.')),
                    )
                    if (fScore > 0) {
                        PaletteItem.AppAction(app, uKey, fScore + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(compareByDescending<PaletteItem.AppAction> { it.rankScore }.thenByDescending { it.usageCount })
                    .take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.FILES_ONLY -> {
                val q = cmd.filterQuery.trim()
                storageFiles.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val fScore = FuzzyMatcher.scoreFileItemPreLowered(q.lowercase(), f)
                    if (fScore > 0) {
                        PaletteItem.FileAction(f, uKey, fScore + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(compareByDescending<PaletteItem.FileAction> { it.rankScore }.thenByDescending { it.usageCount })
                    .take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.DIRS_ONLY -> {
                val q = cmd.filterQuery.trim()
                storageFiles.asSequence().filter { it.isDirectory }.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val fScore = if (q.isEmpty()) 200 else FuzzyMatcher.scorePreLowered(q.lowercase(), f.nameLower)
                    if (fScore > 0) {
                        PaletteItem.FileAction(f, uKey, fScore + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(compareByDescending<PaletteItem.FileAction> { it.rankScore }.thenByDescending { it.usageCount })
                    .take(MAX_PALETTE_RESULTS)
                    .toList()
            }

            PromptIntentMode.BOOKMARKS_ONLY -> {
                val q = cmd.filterQuery.trim()
                bookmarks.mapNotNull { bm ->
                    val uKey = "bm:${bm.id}"
                    val uCount = usageOf(uKey)
                    val qClean = q.removePrefix("#").lowercase()
                    val fScore = if (qClean.isEmpty()) 200 else maxOf(
                        if (bm.alias.equals(qClean, ignoreCase = true)) 2000 else FuzzyMatcher.score(qClean, bm.alias) + 120,
                        FuzzyMatcher.score(qClean, bm.title),
                        FuzzyMatcher.score(qClean, bm.target.substringAfterLast('/')),
                        if (bm.tagsLower.isNotEmpty()) FuzzyMatcher.scorePreLowered(qClean, bm.tagsLower) + 100 else 0,
                    )
                    if (fScore > 0) {
                        PaletteItem.BookmarkAction(bm, uKey, fScore + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(compareByDescending<PaletteItem.BookmarkAction> { it.rankScore }.thenByDescending { it.usageCount })
                    .take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.SCOPED_FOLDER -> {
                val folderBm = cmd.scopedFolderBookmark
                val q = cmd.filterQuery.trim()
                val out = mutableListOf<PaletteItem>()
                if (folderBm != null && q.isEmpty()) {
                    val uKey = "bm:${folderBm.id}"
                    out.add(PaletteItem.BookmarkAction(folderBm, uKey, 5000 + usageBonus(uKey), usageOf(uKey)))
                }
                val fileMatches = scopedFolderFiles.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val dirBoost = if (f.isDirectory) 120 else 0
                    val fScore = if (q.isEmpty()) (100 + dirBoost) else {
                        val s = FuzzyMatcher.scorePreLowered(q.lowercase(), f.nameLower)
                        if (s > 0) s + dirBoost else 0
                    }
                    if (fScore > 0) {
                        PaletteItem.FileAction(f, uKey, fScore + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(compareByDescending<PaletteItem.FileAction> { it.rankScore }.thenByDescending { it.usageCount })

                out.addAll(fileMatches.take(MAX_PALETTE_RESULTS - out.size))
                out.take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.ENGINE_ONLY -> {
                val eng = cmd.matchedEngine ?: engines.firstOrNull { it.isDefault } ?: DefaultEngines.LIST.first()
                val q = cmd.filterQuery.trim()
                val uKey = "eng:${eng.id}"
                val uCount = usageOf(uKey)
                val targetUrl = if (q.isEmpty()) eng.homeUrl else buildSearchUrl(eng.urlTemplate, q)
                val primary = PaletteItem.EngineAction(
                    engine = eng,
                    query = q,
                    targetUrl = targetUrl,
                    isHome = q.isEmpty(),
                    usageKey = uKey,
                    rankScore = 10_000 + usageBonus(uKey),
                    usageCount = uCount,
                )
                val relatedHistory = history
                    .filter { it.engineKeyword.equals(eng.keyword, ignoreCase = true) }
                    .mapNotNull { h ->
                        val hKey = "hist:${h.rawInput.lowercase()}"
                        val hScore = if (q.isEmpty()) 200 else FuzzyMatcher.score(q, h.queryText)
                        if (hScore > 0) {
                            PaletteItem.HistoryAction(h, hKey, hScore + usageBonus(hKey), usageOf(hKey))
                        } else null
                    }
                    .sortedByDescending { it.rankScore }
                    .take(MAX_PALETTE_RESULTS - 1)
                listOf(primary) + relatedHistory
            }

            PromptIntentMode.ALL -> {
                val q = cmd.filterQuery.trim()
                val pool = mutableListOf<PaletteItem>()

                if (q.isEmpty()) {
                    // Empty input: show all categories ranked by MOST USED (`usageCount`)!
                    bookmarks.forEachIndexed { idx, bm ->
                        val uKey = "bm:${bm.id}"
                        val uCount = usageOf(uKey)
                        pool.add(PaletteItem.BookmarkAction(bm, uKey, 90 - idx + usageBonus(uKey), uCount))
                    }
                    engines.forEachIndexed { idx, eng ->
                        val uKey = "eng:${eng.id}"
                        val uCount = usageOf(uKey)
                        pool.add(
                            PaletteItem.EngineAction(
                                engine = eng,
                                query = "",
                                targetUrl = eng.homeUrl,
                                isHome = true,
                                usageKey = uKey,
                                rankScore = (if (eng.isDefault) 95 else 80 - idx) + usageBonus(uKey),
                                usageCount = uCount,
                            )
                        )
                    }
                    contacts.forEach { c ->
                        val uKey = "contact:${c.cleanPhone}"
                        val uCount = usageOf(uKey)
                        if (uCount > 0) {
                            pool.add(PaletteItem.ContactAction(c, ContactQuickAction.CALL, uKey, 70 + usageBonus(uKey), uCount))
                        }
                    }
                    installedApps.forEach { app ->
                        val uKey = "app:${app.packageName}"
                        val uCount = usageOf(uKey)
                        if (uCount > 0) {
                            pool.add(PaletteItem.AppAction(app, uKey, 72 + usageBonus(uKey), uCount))
                        }
                    }
                    storageFiles.forEach { f ->
                        val uKey = "file:${f.absolutePath}"
                        val uCount = usageOf(uKey)
                        if (uCount > 0) {
                            pool.add(PaletteItem.FileAction(f, uKey, 75 + usageBonus(uKey), uCount))
                        }
                    }
                    history.take(10).forEachIndexed { idx, h ->
                        val hKey = "hist:${h.rawInput.lowercase()}"
                        val uCount = usageOf(hKey).coerceAtLeast(1)
                        pool.add(PaletteItem.HistoryAction(h, hKey, 60 - idx + uCount * 180, uCount))
                    }
                } else {
                    // Non-empty query without alias: fuzzy search ALL categories + include default web search fallback!
                    val qLower = q.lowercase()
                    val qTag = qLower.removePrefix("#")
                    bookmarks.forEach { bm ->
                        val s = maxOf(
                            FuzzyMatcher.score(qTag, bm.alias),
                            FuzzyMatcher.score(qTag, bm.title),
                            if (bm.tagsLower.isNotEmpty()) FuzzyMatcher.scorePreLowered(qTag, bm.tagsLower) + 80 else 0,
                        )
                        if (s > 0) {
                            val uKey = "bm:${bm.id}"
                            pool.add(PaletteItem.BookmarkAction(bm, uKey, s + 60 + usageBonus(uKey), usageOf(uKey)))
                        }
                    }
                    contacts.forEach { c ->
                        val s = maxOf(
                            FuzzyMatcher.scorePreLowered(qLower, c.nameLower),
                            if (qLower[0].isDigit()) FuzzyMatcher.scorePreLowered(qLower, c.cleanPhone) else 0,
                        )
                        if (s > 0) {
                            val uKey = "contact:${c.cleanPhone}"
                            pool.add(PaletteItem.ContactAction(c, ContactQuickAction.CALL, uKey, s + 40 + usageBonus(uKey), usageOf(uKey)))
                        }
                    }
                    installedApps.forEach { app ->
                        val s = FuzzyMatcher.scorePreLowered(qLower, app.labelLower)
                        if (s > 0) {
                            val uKey = "app:${app.packageName}"
                            pool.add(PaletteItem.AppAction(app, uKey, s + 45 + usageBonus(uKey), usageOf(uKey)))
                        }
                    }
                    storageFiles.forEach { f ->
                        val s = FuzzyMatcher.scoreFileItemPreLowered(q.lowercase(), f)
                        if (s > 0) {
                            val uKey = "file:${f.absolutePath}"
                            pool.add(PaletteItem.FileAction(f, uKey, s + usageBonus(uKey), usageOf(uKey)))
                        }
                    }
                    engines.forEach { eng ->
                        val s = maxOf(FuzzyMatcher.score(q, eng.keyword), FuzzyMatcher.score(q, eng.name))
                        if (s > 0) {
                            val uKey = "eng:${eng.id}"
                            pool.add(
                                PaletteItem.EngineAction(
                                    engine = eng,
                                    query = q,
                                    targetUrl = buildSearchUrl(eng.urlTemplate, q),
                                    isHome = false,
                                    usageKey = uKey,
                                    rankScore = s + usageBonus(uKey),
                                    usageCount = usageOf(uKey),
                                )
                            )
                        }
                    }
                    // Always include the default fallback web search so pressing Enter can web-search any query
                    val defEng = engines.firstOrNull { it.isDefault } ?: DefaultEngines.LIST.first()
                    if (pool.none { it is PaletteItem.EngineAction && it.engine.id == defEng.id }) {
                        val uKey = "eng:${defEng.id}"
                        pool.add(
                            PaletteItem.EngineAction(
                                engine = defEng,
                                query = q,
                                targetUrl = buildSearchUrl(defEng.urlTemplate, q),
                                isHome = false,
                                usageKey = uKey,
                                rankScore = 300 + usageBonus(uKey),
                                usageCount = usageOf(uKey),
                            )
                        )
                    }
                }

                pool.sortedWith(
                    compareByDescending<PaletteItem> { it.usageCount }
                        .thenByDescending { it.rankScore }
                ).take(MAX_PALETTE_RESULTS)
            }
        }
    }

    fun normalizeWhatsAppPhone(rawPhone: String, defaultCountryCode: String = "977"): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length == 10 && (digits.startsWith("98") || digits.startsWith("97"))) {
            return "$defaultCountryCode$digits"
        }
        if (digits.length == 11 && digits.startsWith("09")) {
            return "$defaultCountryCode${digits.drop(1)}"
        }
        return digits
    }

    fun resolve(
        rawInput: String,
        engines: List<SearchEngine> = DefaultEngines.LIST,
    ): RouteResolution {
        val activeEngines = engines.ifEmpty { DefaultEngines.LIST }
        val defaultEngine = activeEngines.firstOrNull { it.isDefault } ?: activeEngines.first()

        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return RouteResolution(defaultEngine, false, null, "", defaultEngine.homeUrl, true)
        }

        val tokens = trimmed.split(WHITESPACE_REGEX, limit = 2)
        val firstWord = tokens[0]
        val remainder = if (tokens.size > 1) tokens[1].trim().replace(WHITESPACE_REGEX, " ") else ""

        val matchedEngine = activeEngines.firstOrNull { it.keyword.equals(firstWord, ignoreCase = true) }

        return if (matchedEngine != null) {
            if (remainder.isEmpty()) {
                RouteResolution(matchedEngine, true, matchedEngine.keyword.lowercase(), "", matchedEngine.homeUrl, true)
            } else {
                RouteResolution(
                    engine = matchedEngine,
                    matchedByKeyword = true,
                    extractedKeyword = matchedEngine.keyword.lowercase(),
                    query = remainder,
                    targetUrl = buildSearchUrl(matchedEngine.urlTemplate, remainder),
                    isHomeLaunch = false,
                )
            }
        } else {
            val normalizedFullQuery = trimmed.replace(WHITESPACE_REGEX, " ")
            RouteResolution(
                engine = defaultEngine,
                matchedByKeyword = false,
                extractedKeyword = null,
                query = normalizedFullQuery,
                targetUrl = buildSearchUrl(defaultEngine.urlTemplate, normalizedFullQuery),
                isHomeLaunch = false,
            )
        }
    }

    fun extractQueryFromSearchUri(rawUri: String?): String? {
        if (rawUri.isNullOrBlank()) return null
        return try {
            val uri = URI(rawUri.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path?.lowercase().orEmpty()
            val rawQuery = uri.rawQuery ?: return null

            val isKnownSearchUrl = scheme == "pipe" ||
                host == "pipe.local" ||
                host.contains("google.") && (path.startsWith("/search") || path.startsWith("/m") || path.isEmpty() || path == "/") ||
                host.contains("duckduckgo.com") ||
                host.contains("bing.com") && path.startsWith("/search") ||
                host.contains("search.yahoo.com") ||
                host.contains("ecosia.org") && path.startsWith("/search") ||
                host.contains("startpage.com") ||
                host.contains("brave.com") && path.startsWith("/search")

            if (!isKnownSearchUrl) return null

            for (pair in rawQuery.split('&')) {
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val key = pair.substring(0, eq).lowercase()
                    val value = pair.substring(eq + 1)
                    if (key == "q" || key == "query" || key == "s" || key == "text" || key == "p") {
                        return URLDecoder.decode(value, StandardCharsets.UTF_8.name()).trim()
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun buildSearchUrl(urlTemplate: String, query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return urlTemplate.trim().replace("{query}", encoded)
    }

    fun suggestEngines(rawInput: String, engines: List<SearchEngine>): List<SearchEngine> {
        val trimmedStart = rawInput.trimStart()
        if (trimmedStart.isEmpty()) return engines
        val firstToken = trimmedStart.takeWhile { !it.isWhitespace() }.lowercase()
        val hasSpaceAfterFirst = trimmedStart.length > firstToken.length
        if (hasSpaceAfterFirst) {
            val exact = engines.filter { it.keyword.equals(firstToken, ignoreCase = true) }
            if (exact.isNotEmpty()) return exact
        }
        val matches = engines.filter {
            it.keyword.lowercase().startsWith(firstToken) || it.name.lowercase().startsWith(firstToken)
        }
        return matches.ifEmpty { engines }
    }

    fun validateEngine(
        name: String,
        keyword: String,
        urlTemplate: String,
        existingEngines: List<SearchEngine>,
        editingEngineId: String? = null,
    ): EngineValidationErrors {
        val cleanName = name.trim()
        val cleanKeyword = keyword.trim()
        val cleanUrl = urlTemplate.trim()

        val nameErr = if (cleanName.isEmpty()) "! name is required" else null
        val keywordErr = when {
            cleanKeyword.isEmpty() -> "! keyword is required"
            cleanKeyword.any { it.isWhitespace() } -> "! keyword cannot contain spaces"
            else -> {
                val dup = existingEngines.firstOrNull {
                    it.id != editingEngineId && it.keyword.equals(cleanKeyword, ignoreCase = true)
                }
                if (dup != null) "! duplicate keyword (used by ${dup.name.lowercase()})" else null
            }
        }
        val urlErr = when {
            cleanUrl.isEmpty() -> "! url template is required"
            !cleanUrl.contains("{query}") -> "! url template must contain {query}"
            !isValidHttpUrlTemplate(cleanUrl) -> "! invalid url (use https://... with {query})"
            else -> null
        }
        return EngineValidationErrors(nameErr, keywordErr, urlErr)
    }

    private fun isValidHttpUrlTemplate(template: String): Boolean {
        val probeUrl = template.replace("{query}", "test")
        return try {
            val uri = URI(probeUrl)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            (scheme == "http" || scheme == "https") && !host.isNullOrBlank() && host.contains('.')
        } catch (_: Exception) {
            false
        }
    }

    fun exportEnginesToJson(engines: List<SearchEngine>): String {
        val sb = StringBuilder("[\n")
        engines.forEachIndexed { idx, e ->
            sb.append("  {\n")
            sb.append("    \"id\": \"${escapeJson(e.id)}\",\n")
            sb.append("    \"name\": \"${escapeJson(e.name)}\",\n")
            sb.append("    \"keyword\": \"${escapeJson(e.keyword.lowercase())}\",\n")
            sb.append("    \"urlTemplate\": \"${escapeJson(e.urlTemplate)}\",\n")
            sb.append("    \"glyph\": \"${escapeJson(e.glyph)}\",\n")
            sb.append("    \"colorRole\": \"${escapeJson(e.colorRole.name)}\",\n")
            sb.append("    \"isDefault\": ${e.isDefault}\n")
            sb.append("  }")
            if (idx < engines.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun importEnginesFromJson(json: String): List<SearchEngine> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw IllegalArgumentException("! json must be a top-level array [ ... ]")
        }
        val blocks = extractTopLevelJsonObjects(trimmed)
        if (blocks.isEmpty()) throw IllegalArgumentException("! no engine objects found in json")

        val parsed = mutableListOf<SearchEngine>()
        for ((idx, block) in blocks.withIndex()) {
            val name = extractJsonString(block, "name")?.trim()
                ?: throw IllegalArgumentException("! item #${idx + 1} missing \"name\"")
            val keyword = extractJsonString(block, "keyword")?.trim()?.lowercase()
                ?: throw IllegalArgumentException("! item #${idx + 1} missing \"keyword\"")
            val urlTemplate = extractJsonString(block, "urlTemplate")?.trim()
                ?: throw IllegalArgumentException("! item #${idx + 1} missing \"urlTemplate\"")
            val id = extractJsonString(block, "id")?.trim()?.takeIf { it.isNotEmpty() } ?: "eng_${keyword}_${idx}"
            val glyph = extractJsonString(block, "glyph")?.trim()?.takeIf { it.isNotEmpty() } ?: "|"
            val colorRole = EngineColorRole.fromString(extractJsonString(block, "colorRole"))
            val isDefault = extractJsonBoolean(block, "isDefault") ?: false

            val errors = validateEngine(name, keyword, urlTemplate, parsed)
            if (!errors.isValid) {
                val msg = errors.nameError ?: errors.keywordError ?: errors.urlTemplateError ?: "! invalid item"
                throw IllegalArgumentException("$msg (item #${idx + 1})")
            }
            parsed.add(SearchEngine(id, name, keyword, urlTemplate, glyph.take(1), colorRole, isDefault))
        }
        val defaultIdx = parsed.indexOfFirst { it.isDefault }.let { if (it >= 0) it else 0 }
        return parsed.mapIndexed { idx, eng -> eng.copy(isDefault = idx == defaultIdx) }
    }

    private fun extractTopLevelJsonObjects(jsonArray: String): List<String> {
        val objects = mutableListOf<String>()
        var inString = false
        var escaped = false
        var depth = 0
        var objStart = -1

        for (i in jsonArray.indices) {
            val ch = jsonArray[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) objStart = i
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && objStart >= 0) {
                            objects.add(jsonArray.substring(objStart, i + 1))
                            objStart = -1
                        }
                    }
                }
            }
        }
        return objects
    }

    private fun escapeJson(v: String): String =
        v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun extractJsonString(block: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val raw = pattern.find(block)?.groupValues?.get(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
    }

    private fun extractJsonBoolean(block: String, key: String): Boolean? {
        val pattern = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return pattern.find(block)?.groupValues?.get(1)?.equals("true", ignoreCase = true)
    }
}
