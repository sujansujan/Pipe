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

enum class BookmarkKind(val badge: String) {
    FOLDER("dir"),
    FILE("file"),
    LINK("url");

    companion object {
        fun fromString(raw: String?): BookmarkKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LINK
    }
}

/**
 * User bookmark supporting Links (`https://...`), Folders (`/storage/emulated/0/Download`),
 * and Files (`/storage/emulated/0/Documents/notes.txt`), triggered by [alias] or fuzzy search.
 */
data class PipeBookmark(
    val id: String,
    val alias: String,
    val title: String,
    val target: String,
    val kind: BookmarkKind,
)

/**
 * Device contact entry for live fuzzy search (`c kali` -> call, sms, whatsapp).
 */
data class ContactItem(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
)

/**
 * Local file or directory discovered on device storage.
 */
data class LocalFileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val parentName: String,
)

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

object DefaultBookmarks {
    val LIST: List<PipeBookmark> = listOf(
        PipeBookmark("bm_dl", "dl", "Downloads", "/storage/emulated/0/Download", BookmarkKind.FOLDER),
        PipeBookmark("bm_docs", "docs", "Documents", "/storage/emulated/0/Documents", BookmarkKind.FOLDER),
        PipeBookmark("bm_dcim", "dcim", "DCIM / Camera", "/storage/emulated/0/DCIM", BookmarkKind.FOLDER),
        PipeBookmark("bm_pic", "pic", "Pictures", "/storage/emulated/0/Pictures", BookmarkKind.FOLDER),
        PipeBookmark("bm_music", "music", "Music", "/storage/emulated/0/Music", BookmarkKind.FOLDER),
        PipeBookmark("bm_sd", "sd", "Internal Storage", "/storage/emulated/0", BookmarkKind.FOLDER),
    )
}
