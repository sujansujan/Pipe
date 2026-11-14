package com.sujan.pipe.domain

import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.SearchEngine
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class RouteResolution(
    val engine: SearchEngine,
    val matchedByKeyword: Boolean,
    val extractedKeyword: String?,
    val query: String,
    val targetUrl: String,
    val isHomeLaunch: Boolean,
) {
    /** Formats the resolution as a Unix pipeline preview: `"lofi music" | yt -> youtube` */
    val pipelinePreview: String
        get() = when {
            isHomeLaunch -> "echo '' | ${engine.keyword} -> ${engine.homeUrl}"
            matchedByKeyword -> "\"$query\" | ${engine.keyword} -> ${engine.name.lowercase()}"
            else -> "\"$query\" | ${engine.keyword} (default) -> ${engine.name.lowercase()}"
        }
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

    private val WHITESPACE_REGEX = Regex("\\s+")

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
        val matches = Regex("\\{[^{}]*\\}").findAll(trimmed).toList()
        if (matches.isEmpty()) throw IllegalArgumentException("! no engine objects found in json")

        val parsed = mutableListOf<SearchEngine>()
        for ((idx, match) in matches.withIndex()) {
            val block = match.value
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
