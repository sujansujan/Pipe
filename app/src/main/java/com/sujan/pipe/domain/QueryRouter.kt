package com.sujan.pipe.domain

import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fast, zero-dependency fuzzy matcher for contacts, bookmarks, folders, and files.
 * Scores exact matches highest, followed by prefix matches, substring matches,
 * word-boundary matches, and ordered character-subsequence matches.
 */
object FuzzyMatcher {

    /**
     * Returns a positive score if [query] fuzzy-matches [candidate], or 0 if it does not match.
     */
    fun score(query: String, candidate: String): Int {
        val q = query.trim().lowercase()
        val c = candidate.trim().lowercase()
        if (q.isEmpty()) return 1
        if (c.isEmpty()) return 0

        if (c == q) return 1000
        if (c.startsWith(q)) return 850 - (c.length - q.length).coerceAtMost(100)

        // Word-prefix match (e.g. "prasad" in "Kali Prasad")
        val words = c.split(Regex("[\\s_\\-./]+"))
        if (words.any { it == q }) return 780
        if (words.any { it.startsWith(q) }) return 700

        // Contiguous substring match
        val subIdx = c.indexOf(q)
        if (subIdx >= 0) return 600 - subIdx.coerceAtMost(80)

        // Multi-token check: every token in query must match candidate
        if (q.contains(' ')) {
            val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val tokenScores = tokens.map { scoreSingleTokenSubsequence(it, c) }
            return if (tokenScores.all { it > 0 }) tokenScores.sum() / tokens.size else 0
        }

        return scoreSingleTokenSubsequence(q, c)
    }

    private fun scoreSingleTokenSubsequence(q: String, c: String): Int {
        var qIdx = 0
        var consecutive = 0
        var bonus = 0
        var firstMatchIdx = -1

        for (cIdx in c.indices) {
            if (qIdx < q.length && c[cIdx] == q[qIdx]) {
                if (firstMatchIdx < 0) firstMatchIdx = cIdx
                consecutive++
                bonus += consecutive * 15
                if (cIdx == 0 || !c[cIdx - 1].isLetterOrDigit()) {
                    bonus += 25 // Word boundary bonus
                }
                qIdx++
            } else {
                consecutive = 0
            }
        }
        if (qIdx < q.length) return 0
        val compactness = (200 - (c.length - q.length).coerceAtMost(150)).coerceAtLeast(10)
        val startPenalty = firstMatchIdx.coerceAtMost(50)
        return (150 + bonus + compactness - startPenalty).coerceAtLeast(1)
    }

    fun filterContacts(query: String, contacts: List<ContactItem>, limit: Int = 8): List<ContactItem> {
        val clean = query.trim()
        if (clean.isEmpty()) return contacts.take(limit)
        return contacts
            .mapNotNull { item ->
                val nameScore = score(clean, item.displayName)
                val phoneScore = score(clean, item.phoneNumber.replace(" ", "").replace("-", ""))
                val best = maxOf(nameScore, phoneScore)
                if (best > 0) item to best else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun filterBookmarks(query: String, bookmarks: List<PipeBookmark>, limit: Int = 8): List<PipeBookmark> {
        val clean = query.trim()
        if (clean.isEmpty()) return bookmarks.take(limit)
        return bookmarks
            .mapNotNull { bm ->
                val aliasScore = if (bm.alias.equals(clean, ignoreCase = true)) {
                    2000
                } else {
                    score(clean, bm.alias) + 120
                }
                val titleScore = score(clean, bm.title)
                val pathScore = score(clean, bm.target.substringAfterLast('/'))
                val best = maxOf(aliasScore, titleScore, pathScore)
                if (best > 0) bm to best else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun filterFiles(query: String, files: List<LocalFileItem>, limit: Int = 12): List<LocalFileItem> {
        val clean = query.trim()
        if (clean.isEmpty()) return files.take(limit)
        return files
            .mapNotNull { f ->
                val s = score(clean, f.name)
                if (s > 0) f to (s + if (f.isDirectory) 20 else 0) else null
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

/**
 * High-level command mode parsed from the prompt input:
 * - `c <query>` -> Contact search mode
 * - `f <query>` -> Local file/folder search mode
 * - `bm <query>` -> Bookmark search mode
 * - `<bookmark_alias>` or `<folder_alias> <subfilter>` -> Direct bookmark or folder-scoped file search
 * - Standard search engine pipe (`yt lofi`, `g phones`) + ambient fuzzy matches
 */
enum class PromptIntentMode {
    GENERAL,
    CONTACTS_ONLY,
    FILES_ONLY,
    BOOKMARKS_ONLY,
    SCOPED_FOLDER,
}

data class ParsedPromptCommand(
    val mode: PromptIntentMode,
    val filterQuery: String,
    val exactBookmark: PipeBookmark? = null,
    val scopedFolderBookmark: PipeBookmark? = null,
)

data class EngineValidationErrors(
    val nameError: String? = null,
    val keywordError: String? = null,
    val urlTemplateError: String? = null,
) {
    val isValid: Boolean
        get() = nameError == null && keywordError == null && urlTemplateError == null
}

object QueryRouter {

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Parses special command prefixes (`c `, `f `, `bm `, or a bookmark alias like `dl` / `dl pdf`)
     * before falling back to search engine routing.
     */
    fun parsePromptCommand(
        rawInput: String,
        bookmarks: List<PipeBookmark>,
    ): ParsedPromptCommand {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedPromptCommand(PromptIntentMode.GENERAL, "")
        }
        val tokens = trimmed.split(WHITESPACE_REGEX, limit = 2)
        val first = tokens[0].lowercase()
        val remainder = if (tokens.size > 1) tokens[1].trim() else ""

        // 1. Contact alias: `c` or `c <name>`
        if (first == "c" || first == "call" || first == "contact") {
            return ParsedPromptCommand(PromptIntentMode.CONTACTS_ONLY, remainder)
        }

        // 2. File search alias: `f` or `file`
        if (first == "f" || first == "file" || first == "files") {
            return ParsedPromptCommand(PromptIntentMode.FILES_ONLY, remainder)
        }

        // 3. Bookmark search alias: `b` or `bm`
        if (first == "b" || first == "bm") {
            return ParsedPromptCommand(PromptIntentMode.BOOKMARKS_ONLY, remainder)
        }

        // 4. Exact bookmark alias match (e.g. `dl`, `docs`, `pic`, or `dl invoice`)
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
                    mode = PromptIntentMode.GENERAL,
                    filterQuery = trimmed,
                    exactBookmark = matchedBookmark,
                )
            } else {
                ParsedPromptCommand(PromptIntentMode.GENERAL, trimmed)
            }
        }

        return ParsedPromptCommand(PromptIntentMode.GENERAL, trimmed)
    }

    /**
     * Normalizes a phone number into clean digits for WhatsApp (`https://wa.me/<digits>`).
     * Automatically prepends Nepal's `977` country code when given a 10-digit local mobile number
     * starting with `98` or `97`.
     */
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

    /**
     * Extracts a search query when `pipe` is set as Default Browser and receives a search URL
     * from a home-screen search bar, assistant, or custom search engine URL (e.g. `google.com/search?q=yt+lofi`,
     * `duckduckgo.com/?q=gh+compose`, `bing.com/search?q=...`, `pipe://search?q=...`).
     * Returns null if [rawUri] is a normal website navigation URL (like `https://github.com/sujansujan`).
     */
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
