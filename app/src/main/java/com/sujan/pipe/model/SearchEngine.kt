package com.sujan.pipe.model

enum class EngineColorRole(val label: String) {
    ACCENT("lime"),
    ACCENT2("peach"),
    ACCENT3("sky"),
    GOOD("mint"),
    WARN("amber"),
    FG("ink");

    companion object {
        fun fromString(raw: String?): EngineColorRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACCENT2
    }
}

object EngineGlyphs {
    val ALL = listOf("|", ">", "*", "#", "@", "?", "$", "~")
}

data class SearchEngine(
    val id: String,
    val name: String,
    val keyword: String,
    val urlTemplate: String,
    val glyph: String = "|",
    val colorRole: EngineColorRole = EngineColorRole.ACCENT2,
    val isDefault: Boolean = false,
) {
    val homeUrl: String
        get() {
            val trimmed = urlTemplate.trim()
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) return trimmed
            val authorityStart = schemeEnd + 3
            val pathStart = trimmed.indexOf('/', authorityStart)
            if (pathStart < 0) return trimmed
            val origin = trimmed.substring(0, pathStart)
            val pathPart = trimmed.substring(pathStart)
            return if (pathPart.startsWith("/maps/")) "$origin/maps" else origin
        }
}

data class SearchHistoryEntry(
    val id: Long,
    val rawInput: String,
    val engineName: String,
    val engineKeyword: String,
    val queryText: String,
    val resolvedUrl: String,
    val timestampEpochMs: Long,
)

object DefaultEngines {
    val LIST: List<SearchEngine> = listOf(
        SearchEngine("eng_google", "Google", "g", "https://www.google.com/search?q={query}", "|", EngineColorRole.ACCENT, isDefault = true),
        SearchEngine("eng_youtube", "YouTube", "yt", "https://www.youtube.com/results?search_query={query}", "|", EngineColorRole.ACCENT2),
        SearchEngine("eng_wikipedia", "Wikipedia", "w", "https://en.wikipedia.org/w/index.php?search={query}", "|", EngineColorRole.FG),
        SearchEngine("eng_maps", "Maps", "maps", "https://www.google.com/maps/search/{query}", "|", EngineColorRole.GOOD),
        SearchEngine("eng_github", "GitHub", "gh", "https://github.com/search?q={query}", "|", EngineColorRole.ACCENT3),
        SearchEngine("eng_amazon", "Amazon", "a", "https://www.amazon.com/s?k={query}", "|", EngineColorRole.WARN),
        SearchEngine("eng_reddit", "Reddit", "r", "https://www.reddit.com/search/?q={query}", "|", EngineColorRole.ACCENT2),
    )
}
