package com.sujan.pipe.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.model.FloccusBookmarkItem
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 100% Offline live-linked Floccus bookmark engine.
 *
 * - Reads Floccus `.xbel` (XML Bookmark Exchange Language) and Netscape `.html` bookmark exports
 *   directly from local storage (e.g. `/storage/emulated/0/Google Drive/bookmarks.xbel`) or SAF `content://` URIs.
 * - Watches `file.lastModified()` + `file.length()` so whenever DriveSync / FolderSync / Syncthing
 *   updates your Floccus `.xbel` file, Pipe reloads it automatically in <5ms with zero manual re-import.
 * - Preserves nested `<folder>` hierarchy as searchable paths (e.g. `Dev / Android`).
 */
object FloccusBookmarkManager {

    private const val TAG = "PipeFloccus"
    private const val CACHE_TSV_FILENAME = "pipe_floccus_cache_v1.tsv"

    private val syncLock = Any()

    @Volatile
    private var memoryCache: List<FloccusBookmarkItem>? = null

    @Volatile
    private var cachedFilePath: String = ""

    @Volatile
    private var cachedLastModified: Long = -1L

    @Volatile
    private var cachedFileLength: Long = -1L

    fun getCacheFile(context: Context): File = File(context.filesDir, CACHE_TSV_FILENAME)

    fun peekMemoryBookmarks(): List<FloccusBookmarkItem> = memoryCache ?: emptyList()

    /**
     * Auto-discovers synced Floccus `.xbel` (preferred) or `.html` bookmark files on device storage.
     * Picks the largest / most recently modified valid `.xbel` file if multiple exist.
     */
    fun findCandidateFile(): File? {
        val candidateDirs = listOf(
            File("/storage/emulated/0/Google Drive"),
            File("/storage/emulated/0/Drive"),
            File("/storage/emulated/0/Syncthing"),
            File("/storage/emulated/0/Nextcloud"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0"),
        )
        val xbelMatches = mutableListOf<File>()
        val htmlMatches = mutableListOf<File>()

        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!child.isFile || !child.canRead() || child.length() < 64L) continue
                val lower = child.name.lowercase()
                when {
                    lower.endsWith(".xbel") -> xbelMatches.add(child)
                    (lower.contains("floccus") || lower.contains("bookmark")) && lower.endsWith(".html") -> htmlMatches.add(child)
                }
            }
        }

        // Prefer non-empty .xbel with actual content (> 400 bytes), breaking ties by most recently modified then size
        val bestXbel = xbelMatches
            .sortedWith(
                compareByDescending<File> { it.length() >= 400L }
                    .thenByDescending { it.lastModified() }
                    .thenByDescending { it.length() }
            )
            .firstOrNull()
        if (bestXbel != null) return bestXbel

        return htmlMatches.maxByOrNull { it.lastModified() }
    }

    /**
     * Returns cached bookmarks immediately and auto-reloads from the linked `.xbel`/`.html` file
     * whenever its `lastModified` or `length` changed (or if [forceReload] is true).
     * If no source is linked yet and storage permission is available, auto-links a discovered `.xbel` file.
     */
    fun syncIfNeeded(
        context: Context,
        forceReload: Boolean = false,
        autoDiscoverIfUnlinked: Boolean = true,
    ): List<FloccusBookmarkItem> {
        val appCtx = context.applicationContext
        val repo = PipeRepository(appCtx)
        var sourcePath = repo.loadFloccusSourcePath().trim()

        // Auto-discover synced Floccus file on first run if storage permission is granted
        if (sourcePath.isEmpty() && autoDiscoverIfUnlinked && DeviceIntegrationManager.hasAllFilesPermission(appCtx)) {
            val found = findCandidateFile()
            if (found != null) {
                sourcePath = found.absolutePath
                repo.saveFloccusSourcePath(sourcePath)
                repo.saveFloccusSourceLabel(found.name)
                Log.d(TAG, "Auto-linked Floccus file: $sourcePath")
            }
        }

        if (sourcePath.isEmpty()) {
            return memoryCache ?: emptyList()
        }

        synchronized(syncLock) {
            // Direct filesystem path: fast stat check (`lastModified` + `length`) for instant auto-sync!
            if (!sourcePath.startsWith("content://")) {
                val file = File(sourcePath)
                if (file.exists() && file.isFile && file.canRead()) {
                    val mtime = file.lastModified()
                    val len = file.length()
                    if (!forceReload &&
                        memoryCache != null &&
                        cachedFilePath == sourcePath &&
                        cachedLastModified == mtime &&
                        cachedFileLength == len
                    ) {
                        return memoryCache!!
                    }
                    val parsed = runCatching {
                        val raw = file.readText(StandardCharsets.UTF_8)
                        parseBookmarkFileContent(raw)
                    }.getOrNull()
                    if (parsed != null) {
                        memoryCache = parsed
                        cachedFilePath = sourcePath
                        cachedLastModified = mtime
                        cachedFileLength = len
                        writeCacheFileLocked(getCacheFile(appCtx), parsed)
                        repo.saveFloccusLastSyncedMs(System.currentTimeMillis())
                        Log.d(TAG, "Synced ${parsed.size} Floccus bookmarks from ${file.name}")
                        return parsed
                    }
                }
            } else {
                // SAF content:// URI
                val uri = runCatching { Uri.parse(sourcePath) }.getOrNull()
                if (uri != null && (forceReload || memoryCache == null)) {
                    val parsed = runCatching {
                        appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                            val raw = stream.readBytes().toString(StandardCharsets.UTF_8)
                            parseBookmarkFileContent(raw)
                        }
                    }.getOrNull()
                    if (parsed != null) {
                        memoryCache = parsed
                        cachedFilePath = sourcePath
                        cachedLastModified = System.currentTimeMillis()
                        cachedFileLength = parsed.size.toLong()
                        writeCacheFileLocked(getCacheFile(appCtx), parsed)
                        repo.saveFloccusLastSyncedMs(System.currentTimeMillis())
                        Log.d(TAG, "Synced ${parsed.size} Floccus bookmarks from SAF URI")
                        return parsed
                    }
                }
            }

            // Fallback to local TSV mirror cache if file temporarily unavailable
            memoryCache?.let { return it }
            val diskCached = readCacheFileLocked(getCacheFile(appCtx))
            memoryCache = diskCached
            return diskCached
        }
    }

    /**
     * Links a direct local file (e.g. `/storage/emulated/0/Google Drive/bookmarks.xbel`) and parses it immediately.
     */
    fun linkFile(context: Context, file: File): List<FloccusBookmarkItem> {
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("! cannot read ${file.name}")
        }
        val raw = file.readText(StandardCharsets.UTF_8)
        val parsed = parseBookmarkFileContent(raw)
        if (parsed.isEmpty()) {
            throw IllegalArgumentException("! no bookmarks found in ${file.name}")
        }
        val appCtx = context.applicationContext
        val repo = PipeRepository(appCtx)
        synchronized(syncLock) {
            repo.saveFloccusSourcePath(file.absolutePath)
            repo.saveFloccusSourceLabel(file.name)
            repo.saveFloccusLastSyncedMs(System.currentTimeMillis())
            memoryCache = parsed
            cachedFilePath = file.absolutePath
            cachedLastModified = file.lastModified()
            cachedFileLength = file.length()
            writeCacheFileLocked(getCacheFile(appCtx), parsed)
        }
        return parsed
    }

    /**
     * Links a SAF [Uri] (taking persistable read permission if supported) and parses it immediately.
     */
    fun linkUri(context: Context, uri: Uri, displayNameHint: String = ""): List<FloccusBookmarkItem> {
        val appCtx = context.applicationContext
        runCatching {
            appCtx.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val raw = appCtx.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        } ?: throw IllegalArgumentException("! could not open selected Floccus file")

        val parsed = parseBookmarkFileContent(raw)
        if (parsed.isEmpty()) {
            throw IllegalArgumentException("! no bookmarks found in selected file (.xbel / .html)")
        }

        // If the SAF URI points to primary external storage (`primary:...`), resolve direct file path so
        // `lastModified()` auto-sync works seamlessly even after app restarts!
        val resolvedLocalFile = tryResolvePrimaryFile(uri)
        val effectivePath = resolvedLocalFile?.absolutePath ?: uri.toString()
        val label = resolvedLocalFile?.name
            ?: displayNameHint.ifBlank { uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "bookmarks.xbel" }

        val repo = PipeRepository(appCtx)
        synchronized(syncLock) {
            repo.saveFloccusSourcePath(effectivePath)
            repo.saveFloccusSourceLabel(label)
            repo.saveFloccusLastSyncedMs(System.currentTimeMillis())
            memoryCache = parsed
            cachedFilePath = effectivePath
            cachedLastModified = resolvedLocalFile?.lastModified() ?: System.currentTimeMillis()
            cachedFileLength = resolvedLocalFile?.length() ?: parsed.size.toLong()
            writeCacheFileLocked(getCacheFile(appCtx), parsed)
        }
        return parsed
    }

    fun unlink(context: Context) {
        val appCtx = context.applicationContext
        val repo = PipeRepository(appCtx)
        synchronized(syncLock) {
            memoryCache = emptyList()
            cachedFilePath = ""
            cachedLastModified = -1L
            cachedFileLength = -1L
            repo.saveFloccusSourcePath("")
            repo.saveFloccusSourceLabel("not linked")
            repo.saveFloccusLastSyncedMs(0L)
            runCatching { getCacheFile(appCtx).delete() }
        }
    }

    private fun tryResolvePrimaryFile(uri: Uri): File? {
        val last = uri.lastPathSegment ?: return null
        if (last.startsWith("primary:")) {
            val rel = last.removePrefix("primary:")
            val candidate = File("/storage/emulated/0/$rel")
            if (candidate.exists() && candidate.isFile && candidate.canRead()) return candidate
        }
        return null
    }

    /**
     * Fuzzy-scores a [FloccusBookmarkItem] against [rawQuery] across title, folder path, host, and URL.
     */
    fun scoreFloccusBookmark(rawQuery: String, item: FloccusBookmarkItem): Int {
        val clean = rawQuery.trim().lowercase().removePrefix("#")
        if (clean.isEmpty()) return 180
        val titleScore = FuzzyMatcher.scorePreLowered(clean, item.titleLower)
        val hostScore = if (item.hostLower.isNotEmpty()) FuzzyMatcher.scorePreLowered(clean, item.hostLower) else 0
        val folderScore = if (item.folderPathLower.isNotEmpty()) {
            val fs = FuzzyMatcher.scorePreLowered(clean, item.folderPathLower)
            if (fs > 0) (fs - 60).coerceAtLeast(1) else 0
        } else 0
        val combinedScore = if (clean.indexOf(' ') >= 0 && item.folderPathLower.isNotEmpty()) {
            FuzzyMatcher.scorePreLowered(clean, "${item.folderPathLower} ${item.titleLower}")
        } else 0
        val urlScore = if (titleScore == 0 && hostScore == 0 && folderScore == 0 && item.urlLower.contains(clean)) 420 else 0
        return maxOf(titleScore, hostScore, folderScore, combinedScore, urlScore)
    }

    /**
     * Auto-detects XBEL (`.xbel` / XML) vs Netscape HTML bookmark export and parses all bookmarks
     * with their nested folder hierarchy intact.
     */
    fun parseBookmarkFileContent(rawText: String): List<FloccusBookmarkItem> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.contains("<xbel", ignoreCase = true) || trimmed.contains("<bookmark", ignoreCase = true)) {
            parseXbel(trimmed)
        } else if (trimmed.contains("<a ", ignoreCase = true) || trimmed.contains("NETSCAPE-Bookmark-file", ignoreCase = true)) {
            parseNetscapeHtml(trimmed)
        } else {
            emptyList()
        }
    }

    private val XML_COMMENT_REGEX = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
    private val XML_DOCTYPE_REGEX = Regex("<!DOCTYPE[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val XML_TOKEN_REGEX = Regex(
        "<(/?)(folder|bookmark|title)\\b([^>]*)>(?:([^<]*)</title>)?",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val ATTR_HREF_REGEX = Regex("href\\s*=\\s*\"([^\"]+)\"|href\\s*=\\s*'([^']+)'", RegexOption.IGNORE_CASE)
    private val ATTR_ID_REGEX = Regex("\\bid\\s*=\\s*\"([^\"]+)\"|\\bid\\s*=\\s*'([^']+)'", RegexOption.IGNORE_CASE)

    /**
     * Pure Kotlin streaming XBEL 1.0 parser (immune to Floccus's `<!--- ... -->` comments and external DTDs).
     */
    fun parseXbel(rawXml: String): List<FloccusBookmarkItem> {
        val cleaned = XML_DOCTYPE_REGEX.replace(XML_COMMENT_REGEX.replace(rawXml, ""), "")
        val results = ArrayList<FloccusBookmarkItem>()
        val folderStack = ArrayDeque<String>()

        var inBookmark = false
        var currentBmHref = ""
        var currentBmId = ""
        var currentBmTitle = ""
        var awaitingFolderTitle = false

        var pos = 0
        val len = cleaned.length
        while (pos < len) {
            val lt = cleaned.indexOf('<', pos)
            if (lt < 0) break
            val gt = cleaned.indexOf('>', lt + 1)
            if (gt < 0) break

            val tagBody = cleaned.substring(lt + 1, gt).trim()
            pos = gt + 1
            if (tagBody.isEmpty() || tagBody.startsWith("?") || tagBody.startsWith("!")) continue

            val isClosing = tagBody.startsWith("/")
            val isSelfClosing = tagBody.endsWith("/")
            val bodyNoSlash = tagBody.removePrefix("/").removeSuffix("/").trim()
            val spaceIdx = bodyNoSlash.indexOfFirst { it.isWhitespace() }
            val tagName = (if (spaceIdx >= 0) bodyNoSlash.substring(0, spaceIdx) else bodyNoSlash).lowercase()
            val attrs = if (spaceIdx >= 0) bodyNoSlash.substring(spaceIdx + 1) else ""

            when (tagName) {
                "folder" -> {
                    if (isClosing) {
                        if (folderStack.isNotEmpty()) folderStack.removeLast()
                        awaitingFolderTitle = false
                    } else if (!isSelfClosing) {
                        folderStack.addLast("")
                        awaitingFolderTitle = true
                    }
                }
                "bookmark" -> {
                    if (isClosing) {
                        if (inBookmark && isValidBookmarkUrl(currentBmHref)) {
                            val folderPath = folderStack.filter { it.isNotBlank() }.joinToString(" / ")
                            val title = currentBmTitle.ifBlank { inferTitleFromUrl(currentBmHref) }
                            val id = currentBmId.ifBlank { "xbel_${results.size}_${currentBmHref.hashCode().toUInt()}" }
                            results.add(FloccusBookmarkItem(id = id, title = title, url = currentBmHref, folderPath = folderPath))
                        }
                        inBookmark = false
                        currentBmHref = ""
                        currentBmId = ""
                        currentBmTitle = ""
                    } else {
                        val hrefMatch = ATTR_HREF_REGEX.find(attrs)
                        val href = decodeEntities((hrefMatch?.groupValues?.get(1).orEmpty().ifEmpty { hrefMatch?.groupValues?.get(2).orEmpty() }).trim())
                        val idMatch = ATTR_ID_REGEX.find(attrs)
                        val id = (idMatch?.groupValues?.get(1).orEmpty().ifEmpty { idMatch?.groupValues?.get(2).orEmpty() }).trim()
                        if (isSelfClosing) {
                            if (isValidBookmarkUrl(href)) {
                                val folderPath = folderStack.filter { it.isNotBlank() }.joinToString(" / ")
                                results.add(
                                    FloccusBookmarkItem(
                                        id = id.ifBlank { "xbel_${results.size}_${href.hashCode().toUInt()}" },
                                        title = inferTitleFromUrl(href),
                                        url = href,
                                        folderPath = folderPath,
                                    )
                                )
                            }
                        } else {
                            inBookmark = true
                            currentBmHref = href
                            currentBmId = id
                            currentBmTitle = ""
                            awaitingFolderTitle = false
                        }
                    }
                }
                "title" -> {
                    if (!isClosing && !isSelfClosing) {
                        val closeIdx = cleaned.indexOf("</title>", pos, ignoreCase = true)
                        if (closeIdx >= pos) {
                            val titleText = decodeEntities(cleaned.substring(pos, closeIdx)).trim()
                            pos = closeIdx + 8
                            if (inBookmark) {
                                currentBmTitle = titleText
                            } else if (awaitingFolderTitle && folderStack.isNotEmpty()) {
                                folderStack.removeLast()
                                folderStack.addLast(titleText)
                                awaitingFolderTitle = false
                            }
                        }
                    }
                }
            }
        }
        return results.distinctBy { it.url.lowercase() }
    }

    private val HTML_TOKEN_REGEX = Regex(
        "<(h3|a)\\b([^>]*)>(.*?)</\\1>|<(/?dl)\\b[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Pure Kotlin Netscape Bookmark HTML parser (supports Floccus `.export.html` and browser exports).
     */
    fun parseNetscapeHtml(rawHtml: String): List<FloccusBookmarkItem> {
        val results = ArrayList<FloccusBookmarkItem>()
        val folderStack = ArrayDeque<String>()
        var pendingFolderName: String? = null

        for (m in HTML_TOKEN_REGEX.findAll(rawHtml)) {
            val dlTag = m.groupValues[4].lowercase()
            if (dlTag == "dl") {
                val pushed = pendingFolderName
                if (pushed != null) {
                    folderStack.addLast(pushed)
                    pendingFolderName = null
                } else if (folderStack.isNotEmpty()) {
                    folderStack.addLast("")
                }
                continue
            }
            if (dlTag == "/dl") {
                if (folderStack.isNotEmpty()) folderStack.removeLast()
                pendingFolderName = null
                continue
            }

            val tag = m.groupValues[1].lowercase()
            val attrs = m.groupValues[2]
            val innerText = decodeEntities(m.groupValues[3].replace(Regex("<[^>]+>"), "")).trim()
            if (tag == "h3") {
                pendingFolderName = innerText
            } else if (tag == "a") {
                val hrefMatch = ATTR_HREF_REGEX.find(attrs)
                val href = decodeEntities((hrefMatch?.groupValues?.get(1).orEmpty().ifEmpty { hrefMatch?.groupValues?.get(2).orEmpty() }).trim())
                if (isValidBookmarkUrl(href)) {
                    val folderPath = folderStack.filter { it.isNotBlank() }.joinToString(" / ")
                    val title = innerText.ifBlank { inferTitleFromUrl(href) }
                    results.add(
                        FloccusBookmarkItem(
                            id = "html_${results.size}_${href.hashCode().toUInt()}",
                            title = title,
                            url = href,
                            folderPath = folderPath,
                        )
                    )
                }
            }
        }
        return results.distinctBy { it.url.lowercase() }
    }

    private fun isValidBookmarkUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun inferTitleFromUrl(url: String): String {
        val host = url.substringAfter("://", url).substringBefore('/').removePrefix("www.")
        return host.ifBlank { url }
    }

    private fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        var s = raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull(16)
            if (cp != null && cp in 32..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        s = Regex("&#(\\d+);").replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull()
            if (cp != null && cp in 32..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        return s
    }

    private fun writeCacheFileLocked(file: File, items: List<FloccusBookmarkItem>) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.bufferedWriter(StandardCharsets.UTF_8).use { w ->
                for (item in items) {
                    val id = item.id.replace('\t', ' ').replace('\n', ' ').trim()
                    val title = item.title.replace('\t', ' ').replace('\n', ' ').trim()
                    val url = item.url.replace('\t', ' ').replace('\n', ' ').trim()
                    val folder = item.folderPath.replace('\t', ' ').replace('\n', ' ').trim()
                    w.write("$id\t$title\t$url\t$folder\n")
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun readCacheFileLocked(file: File): List<FloccusBookmarkItem> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            file.useLines(StandardCharsets.UTF_8) { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 3 && parts[2].isNotBlank()) {
                        FloccusBookmarkItem(
                            id = parts[0],
                            title = parts[1].ifBlank { inferTitleFromUrl(parts[2]) },
                            url = parts[2],
                            folderPath = parts.getOrNull(3).orEmpty(),
                        )
                    } else null
                }.toList()
            }
        }.getOrDefault(emptyList())
    }
}
