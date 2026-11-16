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
 * Fast, display-aware fuzzy matcher for contacts, bookmarks, folders, apps, and files.
 * Handles punctuation, parenthetical aliases (`Downloads (dl)`), emojis (`Kali budi`),
 * hyphenated phone numbers (`986-0515496`), and multi-token queries without false-positive noise.
 */
object FuzzyMatcher {

    /**
     * Normalizes text by lowercasing, turning separators (`-`, `_`, `/`, `.`, `(`, `)`, `[`, `]`)
     * into spaces, stripping emojis/symbols, and collapsing whitespace.
     */
    fun normalizeForSearch(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        var lastWasSpace = true
        for (ch in raw) {
            val lower = ch.lowercaseChar()
            when {
                lower in 'a'..'z' || lower in '0'..'9' || ch.isLetterOrDigit() -> {
                    sb.append(lower)
                    lastWasSpace = false
                }
                ch == ' ' || ch == '-' || ch == '_' || ch == '.' || ch == '/' ||
                    ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == ':' -> {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * Zero-allocation / display-normalized fuzzy scorer operating on pre-lowercased [q] and [c].
     */
    fun scorePreLowered(q: String, c: String): Int {
        if (q.isEmpty()) return 1
        if (c.isEmpty()) return 0

        if (c == q) return 1000
        if (c.startsWith(q)) return 900 - (c.length - q.length).coerceAtMost(80)

        // Word-boundary prefix check on raw lowered strings
        val wordMatch = checkWordBoundaryMatch(q, c)
        if (wordMatch > 0) return wordMatch

        // Contiguous substring match
        val subIdx = c.indexOf(q)
        if (subIdx >= 0) return 680 - subIdx.coerceAtMost(60)

        // Normalized comparison (handles `Kali budi❤️`, `Downloads (dl)`, `986-051`, `sasu_ama.jpg`)
        val normQ = normalizeForSearch(q)
        val normC = normalizeForSearch(c)
        if (normQ.isNotEmpty() && normC.isNotEmpty()) {
            if (normC == normQ) return 960
            if (normC.startsWith(normQ)) return 870 - (normC.length - normQ.length).coerceAtMost(70)
            val normWord = checkWordBoundaryMatch(normQ, normC)
            if (normWord > 0) return normWord
            val normSub = normC.indexOf(normQ)
            if (normSub >= 0) return 650 - normSub.coerceAtMost(50)

            if (normQ.indexOf(' ') >= 0) {
                val multiScore = scoreMultiWordTokens(normQ, normC)
                if (multiScore > 0) return multiScore
            }
        }

        val hasSpace = q.indexOf(' ') >= 0
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

    /**
     * Computes a character-level boolean mask of length [text.length] marking which characters in [text]
     * matched [rawFilterQuery] (supporting exact word-boundary prefixes, contiguous substrings, multi-word
     * token queries like `citi pdf`, and verified subsequence hits). Used by fzf-style UI highlighting.
     */
    fun findMatchedIndices(text: String, rawFilterQuery: String): BooleanArray {
        val mask = BooleanArray(text.length)
        if (text.isEmpty()) return mask
        val cleanQuery = rawFilterQuery.trim().removePrefix("#").lowercase()
        if (cleanQuery.isEmpty()) return mask

        val lowerText = text.lowercase()
        val tokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return mask

        for (token in tokens) {
            val tLen = token.length
            if (tLen > lowerText.length) continue

            // 1. Prefer word-boundary prefix match
            var wbStart = -1
            var i = 0
            while (i <= lowerText.length - tLen) {
                if (i == 0 || isWordSep(lowerText[i - 1])) {
                    if (lowerText.regionMatches(i, token, 0, tLen, ignoreCase = false)) {
                        wbStart = i
                        break
                    }
                }
                i++
            }
            if (wbStart >= 0) {
                for (k in wbStart until (wbStart + tLen)) {
                    mask[k] = true
                }
                continue
            }

            // 2. Contiguous substring match
            val subIdx = lowerText.indexOf(token)
            if (subIdx >= 0) {
                for (k in subIdx until (subIdx + tLen)) {
                    mask[k] = true
                }
                continue
            }

            // 3. Subsequence match (only when scoreSingleTokenSubsequence > 0)
            if (tLen >= 2 && scoreSingleTokenSubsequence(token, lowerText) > 0) {
                var qIdx = 0
                for (cIdx in lowerText.indices) {
                    if (qIdx < tLen && lowerText[cIdx] == token[qIdx]) {
                        mask[cIdx] = true
                        qIdx++
                        if (qIdx == tLen) break
                    }
                }
            }
        }
        return mask
    }

    private fun isWordSep(ch: Char): Boolean =
        ch == ' ' || ch == '_' || ch == '-' || ch == '.' || ch == '/' || ch == '(' || ch == ')' || ch == ':'

    private fun checkWordBoundaryMatch(q: String, c: String): Int {
        val qLen = q.length
        val cLen = c.length
        if (qLen > cLen) return 0
        var i = 0
        while (i <= cLen - qLen) {
            if (i == 0 || isWordSep(c[i - 1])) {
                if (c.regionMatches(i, q, 0, qLen, ignoreCase = false)) {
                    val endIdx = i + qLen
                    return if (endIdx == cLen || isWordSep(c[endIdx])) 820 else 750
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
            val s = scoreSingleWordInMulti(token, c)
            if (s <= 0) return 0
            total += s
            count++
            start = end
        }
        return if (count > 0) total / count else 0
    }

    private fun scoreSingleWordInMulti(token: String, c: String): Int {
        if (c == token) return 950
        if (c.startsWith(token)) return 860
        val wb = checkWordBoundaryMatch(token, c)
        if (wb > 0) return wb
        val sub = c.indexOf(token)
        if (sub >= 0) return 640 - sub.coerceAtMost(50)
        return scoreSingleTokenSubsequence(token, c)
    }

    private fun scoreSingleTokenSubsequence(q: String, c: String): Int {
        val qLen = q.length
        val cLen = c.length
        if (qLen < 2 || qLen > cLen) return 0
        val firstPos = c.indexOf(q[0])
        if (firstPos < 0 || cLen - firstPos < qLen) return 0

        var qIdx = 0
        var consecutive = 0
        var maxConsecutive = 0
        var boundaryHits = 0
        var bonus = 0
        var lastMatchIdx = -1

        for (cIdx in firstPos until cLen) {
            if (qIdx < qLen && c[cIdx] == q[qIdx]) {
                consecutive++
                if (consecutive > maxConsecutive) maxConsecutive = consecutive
                bonus += consecutive * 18
                if (cIdx == 0 || isWordSep(c[cIdx - 1])) {
                    boundaryHits++
                    bonus += 30
                }
                lastMatchIdx = cIdx
                qIdx++
                if (qIdx == qLen) break
            } else {
                consecutive = 0
            }
        }
        if (qIdx < qLen) return 0
        // Reject loose accidental scattered subsequences across long strings
        val span = (lastMatchIdx - firstPos + 1).coerceAtLeast(qLen)
        if (maxConsecutive < 2 && boundaryHits < qLen) return 0
        if (span > qLen * 3 + 4 && boundaryHits < 2) return 0

        val compactness = (160 - (span - qLen) * 12).coerceIn(10, 160)
        val startPenalty = (firstPos * 4).coerceAtMost(60)
        return (140 + bonus + compactness - startPenalty).coerceAtLeast(1)
    }

    fun scoreContact(rawQuery: String, item: ContactItem): Int {
        val clean = rawQuery.trim().lowercase()
        if (clean.isEmpty()) return 100
        val nameScore = scorePreLowered(clean, item.nameLower)
        val qDigits = clean.filter { it.isDigit() }
        val phoneScore = if (qDigits.isNotEmpty()) {
            maxOf(
                scorePreLowered(qDigits, item.cleanPhone),
                scorePreLowered(clean, item.phoneNumber.lowercase()),
            )
        } else 0
        return maxOf(nameScore, phoneScore)
    }

    fun filterContacts(query: String, contacts: List<ContactItem>, limit: Int = 10): List<ContactItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return contacts.take(limit)
        return contacts
            .mapNotNull { item ->
                val best = scoreContact(clean, item)
                if (best > 0) item to best else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun scoreBookmark(rawQuery: String, bm: PipeBookmark): Int {
        val clean = rawQuery.trim().lowercase().removePrefix("#")
        if (clean.isEmpty()) return 200
        val aliasLower = bm.alias.lowercase()
        val titleLower = bm.title.lowercase()
        val combinedDisplay = if (aliasLower.isNotEmpty()) "$titleLower ($aliasLower)" else titleLower
        val aliasScore = when {
            aliasLower == clean -> 2200
            aliasLower.startsWith(clean) -> 950
            else -> scorePreLowered(clean, aliasLower)
        }
        val titleScore = scorePreLowered(clean, titleLower)
        val displayScore = scorePreLowered(clean, combinedDisplay)
        val pathScore = scorePreLowered(clean, bm.target.substringAfterLast('/').lowercase())
        val tagScore = if (bm.tagsLower.isNotEmpty()) scorePreLowered(clean, bm.tagsLower) + 80 else 0
        return maxOf(aliasScore, titleScore, displayScore, pathScore, tagScore)
    }

    fun filterBookmarks(query: String, bookmarks: List<PipeBookmark>, limit: Int = 10): List<PipeBookmark> {
        val clean = query.trim()
        if (clean.isEmpty()) return bookmarks.take(limit)
        return bookmarks
            .mapNotNull { bm ->
                val best = scoreBookmark(clean, bm)
                if (best > 0) bm to best else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Display-aware file/folder scorer using `nameLower`, `parentLower/nameLower`, and `pathLower`.
     */
    fun scoreFileItemPreLowered(cleanLowerQuery: String, item: LocalFileItem): Int {
        if (cleanLowerQuery.isEmpty()) return if (item.isDirectory) 220 else 100
        val nameScore = scorePreLowered(cleanLowerQuery, item.nameLower)
        val parentScore = scorePreLowered(cleanLowerQuery, item.parentLower)
        val effectiveParentScore = if (parentScore >= 600) (parentScore - 80).coerceAtLeast(150) else 0
        val combinedScore = if (cleanLowerQuery.indexOf(' ') >= 0 || cleanLowerQuery.indexOf('/') >= 0) {
            scorePreLowered(cleanLowerQuery, "${item.parentLower}/${item.nameLower}")
        } else 0
        val best = maxOf(nameScore, effectiveParentScore, combinedScore)
        if (best <= 0) return 0
        val dirBonus = if (item.isDirectory && nameScore > 0) 90 else 0
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

enum class QuickAddKind(val tag: String, val label: String) {
    CONTACT("+c", "Contact"),
    BOOKMARK("+b", "Bookmark"),
    PIPE("+s", "Search Pipe"),
}

enum class PromptIntentMode(val scopeBadge: String) {
    ALL("all"),
    CONTACTS_ONLY("c:contacts"),
    FILES_ONLY("f:files"),
    DIRS_ONLY("d:folders"),
    BOOKMARKS_ONLY("b:bookmarks"),
    PIPES_LIST("s:pipes"),
    APPS_ONLY("app:launcher"),
    SCOPED_FOLDER("b:folder"),
    ENGINE_ONLY("s:pipe"),
    ADD_COMMAND("+add"),
    RM_COMMAND("-del"),
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
 * Every item has a `usageKey` used to track usage frequency (`usageCount`) and `rankScore` for relevance.
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

    data class QuickAddAction(
        val kind: QuickAddKind,
        val title: String,
        val subtitle: String,
        val isReadyToCommit: Boolean,
        val arg1: String = "",
        val arg2: String = "",
        val arg3: String = "",
        val bookmarkKind: BookmarkKind = BookmarkKind.LINK,
        val editingId: String? = null,
        override val usageKey: String = "add:${kind.name}",
        override val rankScore: Int = 9000,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class QuickRemoveAction(
        val isBookmark: Boolean,
        val targetId: String,
        val aliasOrKeyword: String,
        val displayTitle: String,
        val isHistory: Boolean = false,
        override val usageKey: String = "rm:$targetId",
        override val rankScore: Int = 9000,
        override val usageCount: Int = 0,
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
     * Parses the user's input with strict prefix separation (`b` for Bookmarks, `s` for Search Pipes)
     * and trailing-space locking so typing normal words (`g`, `docs`, `download`, `abb`) never hijacks
     * the search before you press space!
     *
     * Supported scoped prefixes (activated after a space):
     * - `b <query>` / `bm <query>` -> `BOOKMARKS_ONLY` (e.g. `b abb` matches ONLY the `abb` Bookmark; `b dl invoice` scopes into folder `dl`)
     * - `s <kw> [query]` / `pipe <kw> [query]` -> `PIPES_LIST` or `ENGINE_ONLY` (e.g. `s abb` or `s abb dune` matches ONLY the `abb` Search Pipe)
     * - `c <query> [wa|sms|call]` -> `CONTACTS_ONLY`
     * - `f <query>` -> `FILES_ONLY`
     * - `d <query>` -> `DIRS_ONLY`
     * - `app <query>` -> `APPS_ONLY`
     * - `+ [args]` / `add [args]` -> `ADD_COMMAND`
     * - `- [args]` / `del [args]` / `rm [args]` -> `RM_COMMAND`
     */
    fun parsePromptCommand(
        rawInput: String,
        bookmarks: List<PipeBookmark>,
        engines: List<SearchEngine> = DefaultEngines.LIST,
    ): ParsedPromptCommand {
        val trimmedStart = rawInput.trimStart()
        val trimmed = trimmedStart.trimEnd()
        if (trimmed.isEmpty()) {
            return ParsedPromptCommand(PromptIntentMode.ALL, "")
        }

        val firstToken = trimmedStart.takeWhile { !it.isWhitespace() }
        val firstLower = firstToken.lowercase()
        val hasSpaceAfterFirst = trimmedStart.length > firstToken.length
        val remainder = if (hasSpaceAfterFirst) trimmedStart.substring(firstToken.length).trim() else ""

        // 0. Quick Add (`+`, `+ ...`, `add`, `add ...`) and Quick Delete (`- ...`, `del`, `rm`)
        if (firstLower == "+" || firstLower == "add") {
            return ParsedPromptCommand(PromptIntentMode.ADD_COMMAND, remainder)
        }
        if (firstLower.startsWith("+") && firstLower.length > 1) {
            val inlineRem = (firstToken.drop(1) + (if (remainder.isNotEmpty()) " $remainder" else "")).trim()
            return ParsedPromptCommand(PromptIntentMode.ADD_COMMAND, inlineRem)
        }
        if (firstLower == "del" || firstLower == "rm" || firstLower == "remove" || (firstLower == "-" && hasSpaceAfterFirst)) {
            return ParsedPromptCommand(PromptIntentMode.RM_COMMAND, remainder)
        }

        // 1. Dedicated Bookmark Prefix: `b <query>` or `bm <query>` (requires space after `b` / `bm`)
        if ((firstLower == "b" || firstLower == "bm") && hasSpaceAfterFirst) {
            if (remainder.isNotEmpty()) {
                val subTokens = remainder.split(WHITESPACE_REGEX, limit = 2)
                val subFirst = subTokens[0].lowercase()
                val subHasSpace = remainder.length > subTokens[0].length
                val subRem = if (subTokens.size > 1) subTokens[1].trim() else ""
                val folderBm = bookmarks.firstOrNull {
                    it.kind == BookmarkKind.FOLDER && it.alias.equals(subFirst, ignoreCase = true)
                }
                if (folderBm != null && (subHasSpace || subRem.isNotEmpty())) {
                    return ParsedPromptCommand(
                        mode = PromptIntentMode.SCOPED_FOLDER,
                        filterQuery = subRem,
                        exactBookmark = if (subRem.isEmpty()) folderBm else null,
                        scopedFolderBookmark = folderBm,
                    )
                }
                val exactBm = bookmarks.firstOrNull { it.alias.equals(remainder, ignoreCase = true) }
                return ParsedPromptCommand(
                    mode = PromptIntentMode.BOOKMARKS_ONLY,
                    filterQuery = remainder,
                    exactBookmark = exactBm,
                )
            }
            return ParsedPromptCommand(PromptIntentMode.BOOKMARKS_ONLY, "")
        }

        // 2. Dedicated Search Pipe Prefix: `s <keyword> [query]` or `pipe <keyword> [query]` (requires space after `s` / `pipe`)
        if ((firstLower == "s" || firstLower == "pipe" || firstLower == "pipes") && hasSpaceAfterFirst) {
            if (remainder.isEmpty()) {
                return ParsedPromptCommand(PromptIntentMode.PIPES_LIST, "")
            }
            val subTokens = remainder.split(WHITESPACE_REGEX, limit = 2)
            val subFirst = subTokens[0].lowercase()
            val subHasSpace = remainder.length > subTokens[0].length
            val subRem = if (subTokens.size > 1) subTokens[1].trim() else ""
            val matchedEng = engines.firstOrNull { it.keyword.equals(subFirst, ignoreCase = true) }
            if (matchedEng != null) {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.ENGINE_ONLY,
                    filterQuery = subRem,
                    matchedEngine = matchedEng,
                )
            }
            if (!subHasSpace) {
                return ParsedPromptCommand(PromptIntentMode.PIPES_LIST, subFirst)
            }
            val fallbackEng = engines.firstOrNull {
                it.keyword.lowercase().startsWith(subFirst) || it.name.lowercase().startsWith(subFirst)
            }
            if (fallbackEng != null) {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.ENGINE_ONLY,
                    filterQuery = subRem,
                    matchedEngine = fallbackEng,
                )
            }
            return ParsedPromptCommand(PromptIntentMode.PIPES_LIST, remainder)
        }

        // 3. Contact alias: `c <name>` (requires space after `c`, with optional `wa` / `sms` / `call` chaining)
        if ((firstLower == "c" || firstLower == "call" || firstLower == "contact") && hasSpaceAfterFirst) {
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

        // 4. Direct App Launcher alias: `app <query>` (requires space)
        if ((firstLower == "app" || firstLower == "apps" || firstLower == "ap") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.APPS_ONLY, remainder)
        }

        // 5. File + Folder search alias: `f <query>` (requires space)
        if ((firstLower == "f" || firstLower == "file" || firstLower == "files") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.FILES_ONLY, remainder)
        }

        // 6. Directory-only alias: `d <query>` (requires space)
        if ((firstLower == "d" || firstLower == "dir" || firstLower == "folder" || firstLower == "folders") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.DIRS_ONLY, remainder)
        }

        // 7. Direct Folder Bookmark or Engine Keyword when followed by a space (e.g. `dl invoice`, `yt lofi`, `abb dune`)
        if (hasSpaceAfterFirst) {
            val matchedBookmark = bookmarks.firstOrNull {
                it.alias.isNotBlank() && it.alias.equals(firstLower, ignoreCase = true)
            }
            val matchedEngine = engines.firstOrNull {
                it.keyword.equals(firstLower, ignoreCase = true)
            }

            if (matchedBookmark != null && matchedEngine != null) {
                return when {
                    remainder.isNotEmpty() && matchedBookmark.kind != BookmarkKind.FOLDER ->
                        ParsedPromptCommand(PromptIntentMode.ENGINE_ONLY, remainder, matchedEngine = matchedEngine)
                    remainder.isNotEmpty() && matchedBookmark.kind == BookmarkKind.FOLDER ->
                        ParsedPromptCommand(PromptIntentMode.SCOPED_FOLDER, remainder, scopedFolderBookmark = matchedBookmark)
                    else ->
                        ParsedPromptCommand(PromptIntentMode.ALL, firstLower)
                }
            }

            if (matchedBookmark != null && matchedBookmark.kind == BookmarkKind.FOLDER) {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.SCOPED_FOLDER,
                    filterQuery = remainder,
                    exactBookmark = if (remainder.isEmpty()) matchedBookmark else null,
                    scopedFolderBookmark = matchedBookmark,
                )
            }

            if (matchedEngine != null) {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.ENGINE_ONLY,
                    filterQuery = remainder,
                    matchedEngine = matchedEngine,
                )
            }
        }

        return ParsedPromptCommand(PromptIntentMode.ALL, trimmed)
    }

    /**
     * Builds the unified Raycast-style command list (strictly capped at [MAX_PALETTE_RESULTS] = 10).
     * - Deduplicates `storageFiles` against bookmarked paths so `Downloads (dl)` and `Download` never repeat.
     * - When idle (`q.isEmpty()`): ranks by `usageCount` first.
     * - When searching (`q.isNotEmpty()`): ranks strictly by `rankScore` (match quality) first!
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
        clipboardText: String = "",
    ): List<PaletteItem> {
        val cmd = parsePromptCommand(rawInput, bookmarks, engines)
        fun usageOf(key: String): Int = usageMap[key] ?: 0
        fun usageBonus(key: String): Int = (usageMap[key] ?: 0) * 35

        return when (cmd.mode) {
            PromptIntentMode.ADD_COMMAND -> buildQuickAddSuggestions(cmd.filterQuery, clipboardText)

            PromptIntentMode.RM_COMMAND -> buildQuickRemoveSuggestions(cmd.filterQuery, bookmarks, engines, history)

            PromptIntentMode.CONTACTS_ONLY -> {
                val q = cmd.filterQuery.trim()
                contacts.mapNotNull { c ->
                    val uKey = "contact:${c.cleanPhone}"
                    val uCount = usageOf(uKey)
                    val fScore = FuzzyMatcher.scoreContact(q, c)
                    if (fScore > 0) {
                        PaletteItem.ContactAction(
                            contact = c,
                            preferredAction = cmd.contactAction,
                            usageKey = uKey,
                            rankScore = fScore * 10 + usageBonus(uKey),
                            usageCount = uCount,
                        )
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.ContactAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.ContactAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
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
                        PaletteItem.AppAction(app, uKey, fScore * 10 + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.AppAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.AppAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.FILES_ONLY -> {
                val q = cmd.filterQuery.trim().lowercase()
                storageFiles.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val fScore = FuzzyMatcher.scoreFileItemPreLowered(q, f)
                    if (fScore > 0) {
                        PaletteItem.FileAction(f, uKey, fScore * 10 + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.FileAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.FileAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.DIRS_ONLY -> {
                val q = cmd.filterQuery.trim().lowercase()
                storageFiles.asSequence().filter { it.isDirectory }.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val fScore = if (q.isEmpty()) 200 else FuzzyMatcher.scoreFileItemPreLowered(q, f)
                    if (fScore > 0) {
                        PaletteItem.FileAction(f, uKey, fScore * 10 + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.FileAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.FileAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS).toList()
            }

            PromptIntentMode.BOOKMARKS_ONLY -> {
                val q = cmd.filterQuery.trim()
                bookmarks.mapNotNull { bm ->
                    val uKey = "bm:${bm.id}"
                    val uCount = usageOf(uKey)
                    val fScore = FuzzyMatcher.scoreBookmark(q, bm)
                    if (fScore > 0) {
                        PaletteItem.BookmarkAction(bm, uKey, fScore * 10 + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.BookmarkAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.BookmarkAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.PIPES_LIST -> {
                val q = cmd.filterQuery.trim().lowercase()
                engines.mapNotNull { eng ->
                    val uKey = "eng:${eng.id}"
                    val uCount = usageOf(uKey)
                    val fScore = if (q.isEmpty()) {
                        if (eng.isDefault) 300 else 200
                    } else {
                        val kwScore = if (eng.keyword.equals(q, ignoreCase = true)) 2200 else FuzzyMatcher.scorePreLowered(q, eng.keyword.lowercase())
                        val nameScore = FuzzyMatcher.scorePreLowered(q, eng.name.lowercase())
                        maxOf(kwScore, nameScore)
                    }
                    if (fScore > 0) {
                        PaletteItem.EngineAction(
                            engine = eng,
                            query = "",
                            targetUrl = eng.homeUrl,
                            isHome = true,
                            usageKey = uKey,
                            rankScore = fScore * 10 + usageBonus(uKey),
                            usageCount = uCount,
                        )
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.EngineAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.EngineAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.SCOPED_FOLDER -> {
                val folderBm = cmd.scopedFolderBookmark
                val q = cmd.filterQuery.trim().lowercase()
                val out = mutableListOf<PaletteItem>()
                if (folderBm != null && q.isEmpty()) {
                    val uKey = "bm:${folderBm.id}"
                    out.add(PaletteItem.BookmarkAction(folderBm, uKey, 50_000, usageOf(uKey)))
                }
                val fileMatches = scopedFolderFiles.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val fScore = if (q.isEmpty()) {
                        if (f.isDirectory) 220 else 100
                    } else {
                        FuzzyMatcher.scoreFileItemPreLowered(q, f)
                    }
                    if (fScore > 0) {
                        PaletteItem.FileAction(f, uKey, fScore * 10 + usageBonus(uKey), uCount)
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.FileAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.FileAction> { it.rankScore }.thenByDescending { it.usageCount }
                )

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
                    rankScore = 100_000,
                    usageCount = uCount,
                )
                val relatedHistory = history
                    .filter { it.engineKeyword.equals(eng.keyword, ignoreCase = true) }
                    .mapNotNull { h ->
                        val hKey = "hist:${h.rawInput.lowercase()}"
                        val hScore = if (q.isEmpty()) 200 else FuzzyMatcher.score(q, h.queryText)
                        if (hScore > 0) {
                            PaletteItem.HistoryAction(h, hKey, hScore * 10 + usageBonus(hKey), usageOf(hKey))
                        } else null
                    }
                    .sortedByDescending { it.rankScore }
                    .take(MAX_PALETTE_RESULTS - 1)
                listOf(primary) + relatedHistory
            }

            PromptIntentMode.ALL -> {
                val q = cmd.filterQuery.trim()
                val pool = mutableListOf<PaletteItem>()
                val bookmarkedPaths = bookmarks
                    .filter { it.kind != BookmarkKind.LINK }
                    .map { it.target.trimEnd('/').lowercase() }
                    .toSet()

                if (q.isEmpty()) {
                    bookmarks.forEachIndexed { idx, bm ->
                        val uKey = "bm:${bm.id}"
                        val uCount = usageOf(uKey)
                        pool.add(PaletteItem.BookmarkAction(bm, uKey, 90 - idx + uCount * 180, uCount))
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
                                rankScore = (if (eng.isDefault) 95 else 80 - idx) + uCount * 180,
                                usageCount = uCount,
                            )
                        )
                    }
                    contacts.forEach { c ->
                        val uKey = "contact:${c.cleanPhone}"
                        val uCount = usageOf(uKey)
                        if (uCount > 0) {
                            pool.add(PaletteItem.ContactAction(c, ContactQuickAction.CALL, uKey, 70 + uCount * 180, uCount))
                        }
                    }
                    installedApps.forEach { app ->
                        val uKey = "app:${app.packageName}"
                        val uCount = usageOf(uKey)
                        if (uCount > 0) {
                            pool.add(PaletteItem.AppAction(app, uKey, 72 + uCount * 180, uCount))
                        }
                    }
                    storageFiles.forEach { f ->
                        if (f.pathLower.trimEnd('/') !in bookmarkedPaths) {
                            val uKey = "file:${f.absolutePath}"
                            val uCount = usageOf(uKey)
                            if (uCount > 0) {
                                pool.add(PaletteItem.FileAction(f, uKey, 75 + uCount * 180, uCount))
                            }
                        }
                    }
                    history.take(10).forEachIndexed { idx, h ->
                        val hKey = "hist:${h.rawInput.lowercase()}"
                        val uCount = usageOf(hKey).coerceAtLeast(1)
                        pool.add(PaletteItem.HistoryAction(h, hKey, 60 - idx + uCount * 180, uCount))
                    }

                    pool.sortedWith(
                        compareByDescending<PaletteItem> { it.usageCount }
                            .thenByDescending { it.rankScore }
                    ).take(MAX_PALETTE_RESULTS)
                } else {
                    val qLower = q.lowercase()
                    bookmarks.forEach { bm ->
                        val s = FuzzyMatcher.scoreBookmark(q, bm)
                        if (s > 0) {
                            val uKey = "bm:${bm.id}"
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.BookmarkAction(bm, uKey, s * 10 + 40 + tieBonus, usageOf(uKey)))
                        }
                    }
                    engines.forEach { eng ->
                        val isExactKw = eng.keyword.equals(qLower, ignoreCase = true)
                        val s = if (isExactKw) 2150 else maxOf(
                            FuzzyMatcher.scorePreLowered(qLower, eng.keyword.lowercase()),
                            FuzzyMatcher.scorePreLowered(qLower, eng.name.lowercase()),
                        )
                        if (s > 0) {
                            val uKey = "eng:${eng.id}"
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(
                                PaletteItem.EngineAction(
                                    engine = eng,
                                    query = if (isExactKw) "" else q,
                                    targetUrl = if (isExactKw) eng.homeUrl else buildSearchUrl(eng.urlTemplate, q),
                                    isHome = isExactKw,
                                    usageKey = uKey,
                                    rankScore = s * 10 + tieBonus,
                                    usageCount = usageOf(uKey),
                                )
                            )
                        }
                    }
                    contacts.forEach { c ->
                        val s = FuzzyMatcher.scoreContact(qLower, c)
                        if (s > 0) {
                            val uKey = "contact:${c.cleanPhone}"
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.ContactAction(c, ContactQuickAction.CALL, uKey, s * 10 + 30 + tieBonus, usageOf(uKey)))
                        }
                    }
                    installedApps.forEach { app ->
                        val s = maxOf(
                            FuzzyMatcher.scorePreLowered(qLower, app.labelLower),
                            FuzzyMatcher.scorePreLowered(qLower, app.pkgLower.substringAfterLast('.')),
                        )
                        if (s > 0) {
                            val uKey = "app:${app.packageName}"
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.AppAction(app, uKey, s * 10 + 25 + tieBonus, usageOf(uKey)))
                        }
                    }
                    storageFiles.forEach { f ->
                        if (f.pathLower.trimEnd('/') !in bookmarkedPaths) {
                            val s = FuzzyMatcher.scoreFileItemPreLowered(qLower, f)
                            if (s > 0) {
                                val uKey = "file:${f.absolutePath}"
                                val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                                pool.add(PaletteItem.FileAction(f, uKey, s * 10 + tieBonus, usageOf(uKey)))
                            }
                        }
                    }

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
                                rankScore = 100,
                                usageCount = usageOf(uKey),
                            )
                        )
                    }

                    pool.sortedWith(
                        compareByDescending<PaletteItem> { it.rankScore }
                            .thenByDescending { it.usageCount }
                    ).take(MAX_PALETTE_RESULTS)
                }
            }
        }
    }

    private fun buildQuickAddSuggestions(rawSub: String, clipboardText: String): List<PaletteItem> {
        val sub = rawSub.trim()
        if (sub.isEmpty()) {
            return listOf(
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.CONTACT,
                    title = "Add Contact",
                    subtitle = "tap for form  |  + Name Phone",
                    isReadyToCommit = false,
                ),
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.BOOKMARK,
                    title = "Add Bookmark (b)",
                    subtitle = "tap for form  |  + b <alias> <url/path>",
                    isReadyToCommit = false,
                    arg3 = clipboardText,
                ),
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.PIPE,
                    title = "Add Search Pipe (s)",
                    subtitle = "tap for form  |  + s <keyword> <search-url>",
                    isReadyToCommit = false,
                    arg3 = clipboardText,
                ),
            )
        }

        val subTokens = sub.split(WHITESPACE_REGEX, limit = 2)
        val subType = subTokens[0].lowercase()
        val subArgs = if (subTokens.size > 1) subTokens[1].trim() else ""

        return when (subType) {
            "c", "contact", "call" -> {
                val parsed = parseQuickAddContact(subArgs)
                if (parsed != null) {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.CONTACT,
                            title = "Save Contact: \"${parsed.first}\"",
                            subtitle = "phone: ${parsed.second} (tap or press RET)",
                            isReadyToCommit = true,
                            arg1 = parsed.first,
                            arg2 = parsed.second,
                        )
                    )
                } else {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.CONTACT,
                            title = "Add Contact (tap for form)",
                            subtitle = "+ c <Name> <Phone>",
                            isReadyToCommit = false,
                            arg1 = subArgs,
                        )
                    )
                }
            }
            "b", "bm", "bookmark" -> {
                val parsed = parseQuickAddBookmark(subArgs, clipboardText)
                if (parsed != null) {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.BOOKMARK,
                            title = "Save Bookmark [b ${parsed.first}]: \"${parsed.second}\"",
                            subtitle = "${parsed.third.second.badge} -> ${parsed.third.first}",
                            isReadyToCommit = true,
                            arg1 = parsed.first,
                            arg2 = parsed.second,
                            arg3 = parsed.third.first,
                            bookmarkKind = parsed.third.second,
                        )
                    )
                } else {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.BOOKMARK,
                            title = "Add Bookmark (tap for form)",
                            subtitle = "+ b <alias> [Title] <url/path>",
                            isReadyToCommit = false,
                            arg1 = subArgs,
                            arg3 = clipboardText,
                        )
                    )
                }
            }
            "s", "p", "pipe", "eng", "engine" -> {
                val parsed = parseQuickAddPipe(subArgs, clipboardText)
                if (parsed != null) {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.PIPE,
                            title = "Save Search Pipe [s ${parsed.first}]: \"${parsed.second}\"",
                            subtitle = "template: ${parsed.third}",
                            isReadyToCommit = true,
                            arg1 = parsed.first,
                            arg2 = parsed.second,
                            arg3 = parsed.third,
                        )
                    )
                } else {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.PIPE,
                            title = "Add Search Pipe (tap for form)",
                            subtitle = "+ s <keyword> [Name] <search-url>",
                            isReadyToCommit = false,
                            arg1 = subArgs,
                            arg3 = clipboardText,
                        )
                    )
                }
            }
            else -> {
                val out = mutableListOf<PaletteItem>()
                val autoContact = parseQuickAddContact(sub)
                if (autoContact != null && !sub.contains("http") && !sub.contains('/') && !sub.contains('.')) {
                    out.add(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.CONTACT,
                            title = "Save Contact: \"${autoContact.first}\"",
                            subtitle = "phone: ${autoContact.second} (tap or press RET)",
                            isReadyToCommit = true,
                            arg1 = autoContact.first,
                            arg2 = autoContact.second,
                        )
                    )
                }
                val autoPipe = parseQuickAddPipe(sub, clipboardText)
                val autoBm = parseQuickAddBookmark(sub, clipboardText)
                val looksLikeSearchUrl = sub.contains("{query}") || sub.contains("?") || sub.contains("=")
                if (looksLikeSearchUrl && autoPipe != null) {
                    out.add(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.PIPE,
                            title = "Save Search Pipe [s ${autoPipe.first}]: \"${autoPipe.second}\"",
                            subtitle = "template: ${autoPipe.third}",
                            isReadyToCommit = true,
                            arg1 = autoPipe.first,
                            arg2 = autoPipe.second,
                            arg3 = autoPipe.third,
                        )
                    )
                }
                if (autoBm != null) {
                    out.add(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.BOOKMARK,
                            title = "Save Bookmark [b ${autoBm.first}]: \"${autoBm.second}\"",
                            subtitle = "${autoBm.third.second.badge} -> ${autoBm.third.first}",
                            isReadyToCommit = true,
                            arg1 = autoBm.first,
                            arg2 = autoBm.second,
                            arg3 = autoBm.third.first,
                            bookmarkKind = autoBm.third.second,
                        )
                    )
                }
                if (!looksLikeSearchUrl && autoPipe != null) {
                    out.add(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.PIPE,
                            title = "Save Search Pipe [s ${autoPipe.first}]: \"${autoPipe.second}\"",
                            subtitle = "template: ${autoPipe.third}",
                            isReadyToCommit = true,
                            arg1 = autoPipe.first,
                            arg2 = autoPipe.second,
                            arg3 = autoPipe.third,
                        )
                    )
                }
                if (out.isEmpty()) {
                    out.add(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.CONTACT,
                            title = "Open Quick Add Form for \"$sub\"",
                            subtitle = "tap to choose Contact, Bookmark (b), or Pipe (s)",
                            isReadyToCommit = false,
                            arg1 = sub,
                            arg3 = clipboardText,
                        )
                    )
                }
                out
            }
        }
    }

    private fun buildQuickRemoveSuggestions(
        rawSub: String,
        bookmarks: List<PipeBookmark>,
        engines: List<SearchEngine>,
        history: List<SearchHistoryEntry>,
    ): List<PaletteItem> {
        val sub = rawSub.trim().lowercase()
        val tokens = sub.split(WHITESPACE_REGEX, limit = 2)
        val first = tokens.firstOrNull().orEmpty()
        val onlyBookmarks = first == "b" || first == "bm"
        val onlyPipes = first == "s" || first == "p" || first == "pipe"
        val targetToken = if ((onlyBookmarks || onlyPipes) && tokens.size > 1) tokens[1].trim() else if (onlyBookmarks || onlyPipes) "" else sub

        val out = mutableListOf<PaletteItem>()
        if (!onlyPipes) {
            bookmarks.filter {
                targetToken.isEmpty() || it.alias.lowercase().contains(targetToken) || it.title.lowercase().contains(targetToken)
            }.forEach { bm ->
                out.add(
                    PaletteItem.QuickRemoveAction(
                        isBookmark = true,
                        targetId = bm.id,
                        aliasOrKeyword = bm.alias,
                        displayTitle = "Delete Bookmark [b ${bm.alias}] ${bm.title}",
                    )
                )
            }
        }
        if (!onlyBookmarks && engines.size > 1) {
            engines.filter {
                targetToken.isEmpty() || it.keyword.lowercase().contains(targetToken) || it.name.lowercase().contains(targetToken)
            }.forEach { eng ->
                out.add(
                    PaletteItem.QuickRemoveAction(
                        isBookmark = false,
                        targetId = eng.id,
                        aliasOrKeyword = eng.keyword,
                        displayTitle = "Delete Search Pipe [s ${eng.keyword}] ${eng.name}",
                    )
                )
            }
        }
        if (!onlyBookmarks && !onlyPipes) {
            history.filter {
                targetToken.isEmpty() || it.rawInput.lowercase().contains(targetToken)
            }.take(4).forEach { h ->
                out.add(
                    PaletteItem.QuickRemoveAction(
                        isBookmark = false,
                        targetId = h.id.toString(),
                        aliasOrKeyword = h.rawInput,
                        displayTitle = "Delete History: \\\"${h.rawInput}\\\"",
                        isHistory = true,
                    )
                )
            }
        }
        return out.take(MAX_PALETTE_RESULTS)
    }

    /**
     * Converts a sample search URL (e.g. `https://site.com/search?q=hello`) into a `{query}` template
     * (`https://site.com/search?q={query}`) if `{query}` is not already present.
     */
    fun normalizePipeUrlTemplate(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        if (withScheme.contains("{query}")) return withScheme
        val paramRegex = Regex("([?&](?:q|query|search_query|search|s|k|p)=)([^&#]+)", RegexOption.IGNORE_CASE)
        if (paramRegex.containsMatchIn(withScheme)) {
            return paramRegex.replaceFirst(withScheme, "$1{query}")
        }
        return if (withScheme.contains("?")) "$withScheme&q={query}" else "$withScheme?q={query}"
    }

    /**
     * Infers a clean title from a URL or file path when the user only types `+ b abb https://audiobookbay.lu`.
     */
    fun inferTitleFromTarget(alias: String, target: String): String {
        val cleanTarget = target.trim().trimEnd('/')
        if (cleanTarget.startsWith("/")) {
            return cleanTarget.substringAfterLast('/').ifEmpty { alias.uppercase() }
        }
        val host = cleanTarget
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
        val domainMain = host.substringBefore('.')
        return if (domainMain.length >= 2) {
            domainMain.replaceFirstChar { it.uppercaseChar() }
        } else {
            alias.uppercase()
        }
    }

    /**
     * Parses `add c <Name> <Phone>` or `+ <Name> <Phone>` into `(name, phone)`.
     */
    fun parseQuickAddContact(rawArgs: String): Pair<String, String>? {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(WHITESPACE_REGEX)
        if (tokens.size < 2) return null
        val last = tokens.last()
        val digits = last.filter { it.isDigit() }
        if (digits.length < 3) return null
        val name = tokens.dropLast(1).joinToString(" ").trim()
        if (name.isEmpty()) return null
        return name to last
    }

    /**
     * Parses `+ b <alias> [Title] [target]` (falling back to [clipboardText] if target is omitted).
     */
    fun parseQuickAddBookmark(rawArgs: String, clipboardText: String = ""): Triple<String, String, Pair<String, BookmarkKind>>? {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(WHITESPACE_REGEX)
        val alias = tokens[0].lowercase()
        val lastToken = if (tokens.size >= 2) tokens.last() else ""
        val lastLooksLikeTarget = lastToken.startsWith("http://") ||
            lastToken.startsWith("https://") ||
            lastToken.startsWith("/storage/") ||
            lastToken.startsWith("/sdcard/") ||
            (lastToken.contains('.') && !lastToken.startsWith("."))

        val rawTarget = when {
            lastLooksLikeTarget -> lastToken
            clipboardText.trim().let { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("/") } ->
                clipboardText.trim()
            else -> ""
        }
        if (rawTarget.isEmpty()) return null
        val target = if (!rawTarget.startsWith("/") && !rawTarget.startsWith("http://") && !rawTarget.startsWith("https://")) {
            "https://$rawTarget"
        } else {
            rawTarget
        }

        val titleTokens = if (lastLooksLikeTarget && tokens.size >= 3) tokens.drop(1).dropLast(1) else if (!lastLooksLikeTarget) tokens.drop(1) else emptyList()
        val title = titleTokens.joinToString(" ").trim().ifEmpty { inferTitleFromTarget(alias, target) }
        val kind = when {
            target.startsWith("http://") || target.startsWith("https://") -> BookmarkKind.LINK
            target.substringAfterLast('/').contains('.') -> BookmarkKind.FILE
            else -> BookmarkKind.FOLDER
        }
        return Triple(alias, title, target to kind)
    }

    /**
     * Parses `+ s <keyword> [Name] [url]` (falling back to [clipboardText] if url is omitted).
     */
    fun parseQuickAddPipe(rawArgs: String, clipboardText: String = ""): Triple<String, String, String>? {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(WHITESPACE_REGEX)
        val keyword = tokens[0].lowercase()
        val lastToken = if (tokens.size >= 2) tokens.last() else ""
        val lastLooksLikeUrl = lastToken.startsWith("http://") || lastToken.startsWith("https://") || lastToken.contains('.')

        val rawUrl = when {
            lastLooksLikeUrl && tokens.size >= 2 -> lastToken
            clipboardText.trim().let { it.startsWith("http://") || it.startsWith("https://") } -> clipboardText.trim()
            else -> ""
        }
        if (rawUrl.isEmpty()) return null
        val urlTemplate = normalizePipeUrlTemplate(rawUrl)
        val nameTokens = if (lastLooksLikeUrl && tokens.size >= 3) tokens.drop(1).dropLast(1) else if (!lastLooksLikeUrl) tokens.drop(1) else emptyList()
        val name = nameTokens.joinToString(" ").trim().ifEmpty { inferTitleFromTarget(keyword, urlTemplate) }
        return Triple(keyword, name, urlTemplate)
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

        // Support `s <keyword> [query]` in `resolve()` as well!
        if ((firstWord.equals("s", ignoreCase = true) || firstWord.equals("pipe", ignoreCase = true)) && remainder.isNotEmpty()) {
            val subTokens = remainder.split(WHITESPACE_REGEX, limit = 2)
            val subKw = subTokens[0]
            val subQuery = if (subTokens.size > 1) subTokens[1].trim().replace(WHITESPACE_REGEX, " ") else ""
            val subEng = activeEngines.firstOrNull { it.keyword.equals(subKw, ignoreCase = true) }
            if (subEng != null) {
                return if (subQuery.isEmpty()) {
                    RouteResolution(subEng, true, subEng.keyword.lowercase(), "", subEng.homeUrl, true)
                } else {
                    RouteResolution(
                        engine = subEng,
                        matchedByKeyword = true,
                        extractedKeyword = subEng.keyword.lowercase(),
                        query = subQuery,
                        targetUrl = buildSearchUrl(subEng.urlTemplate, subQuery),
                        isHomeLaunch = false,
                    )
                }
            }
        }

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
