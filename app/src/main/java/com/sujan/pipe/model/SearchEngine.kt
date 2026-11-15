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
enum class ContactQuickAction(val badge: String) {
    CALL("call"),
    SMS("sms"),
    WHATSAPP("wa"),
}

data class PipeBookmark(
    val id: String,
    val alias: String,
    val title: String,
    val target: String,
    val kind: BookmarkKind,
    val tags: String = "",
) {
    val tagsLower: String = tags.trim().lowercase()
}

data class InstalledAppItem(
    val label: String,
    val packageName: String,
) {
    val labelLower: String = label.trim().lowercase()
    val pkgLower: String = packageName.trim().lowercase()
}

/**
 * Device contact entry for live fuzzy search (`c kali` -> call, sms, whatsapp).
 */
data class ContactItem(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String? = null,
) {
    val nameLower: String = displayName.trim().lowercase()
    val cleanPhone: String = phoneNumber.filter { it.isDigit() }
    val monogram: String = run {
        val parts = displayName.trim().split(Regex("\s+")).filter { it.isNotEmpty() }
        when {
            parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
            parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
            parts.size == 1 -> parts[0].uppercase()
            else -> "@"
        }
    }
}

/**
 * Local file or directory discovered on device storage.
 * Pre-computes lowercase strings once at index load time so keystroke search allocates zero strings.
 */
data class LocalFileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val parentName: String,
    val sizeBytes: Long = 0L,
) {
    val nameLower: String = name.trim().lowercase()
    val parentLower: String = parentName.trim().lowercase()
    val pathLower: String = absolutePath.trim().lowercase()

    val isImageFile: Boolean = !isDirectory && (
        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") ||
            nameLower.endsWith(".png") || nameLower.endsWith(".webp") ||
            nameLower.endsWith(".bmp") || nameLower.endsWith(".gif")
        )

    val formattedSize: String
        get() {
            if (isDirectory) return "dir"
            if (sizeBytes <= 0L) return "file"
            val kb = sizeBytes / 1024.0
            if (kb < 1024.0) return "${kb.toInt().coerceAtLeast(1)} KB"
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.1f MB", mb)
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
