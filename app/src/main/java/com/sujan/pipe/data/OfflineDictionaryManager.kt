package com.sujan.pipe.data

import android.content.Context
import android.net.Uri
import com.sujan.pipe.model.DictionaryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * On-demand external offline dictionary engine with **0 MB idle RAM**.
 *
 * Architecture:
 * - Converts any user-supplied dictionary file (`.tsv`, `.csv`, `.txt`, Webster `{ "word": "def" }` `.json`,
 *   OPTED `[ { "word": ..., "type": ..., "description": ... } ]` `.json`, or Wordset `allwords_wordset.json.gz`)
 *   into a sorted, line-oriented local file (`filesDir/pipe_dict.tsv`) plus a tiny 2-character prefix byte-offset
 *   index (`filesDir/pipe_dict.idx`, ~4-8 KB).
 * - Never loads the dictionary into memory during normal `pipe` operation.
 * - Only when the user invokes `def <word>` (or custom dictionary prefix), opens a `RandomAccessFile`,
 *   seeks directly to the 2-letter prefix byte offset on disk, and streams up to 10 matches in <3ms!
 */
object OfflineDictionaryManager {

    private const val DICT_TSV_FILENAME = "pipe_dict.tsv"
    private const val DICT_IDX_FILENAME = "pipe_dict.idx"
    private const val MAX_SCAN_LINES_PER_QUERY = 2500

    @Volatile
    private var cachedPrefixOffsets: Map<String, Long>? = null

    fun getDictFile(context: Context): File = File(context.filesDir, DICT_TSV_FILENAME)
    fun getIdxFile(context: Context): File = File(context.filesDir, DICT_IDX_FILENAME)

    fun isDictionaryInstalled(context: Context): Boolean {
        val f = getDictFile(context)
        return f.exists() && f.length() > 32L
    }

    /**
     * Auto-discovers a dictionary file in `/storage/emulated/0/Download/` if the user dropped one there
     * (`pipe-dict.tsv`, `dictionary_compact.json`, `dictionary.json`, `allwords_wordset.json.gz`, `dict.tsv`, `dict.csv`).
     */
    fun findCandidateInDownloads(): File? {
        val dlDir = File("/storage/emulated/0/Download")
        if (!dlDir.exists() || !dlDir.isDirectory) return null
        val candidates = listOf(
            "pipe-dict.tsv",
            "pipe_dict.tsv",
            "dict.tsv",
            "dictionary_compact.json",
            "dictionary.json",
            "allwords_wordset.json.gz",
            "EDMTDictionary.json",
            "dict.csv",
            "dictionary.csv",
        )
        for (name in candidates) {
            val f = File(dlDir, name)
            if (f.exists() && f.isFile && f.length() > 100L) return f
        }
        return null
    }

    /**
     * Imports a dictionary from a SAF [Uri] and builds the sorted `pipe_dict.tsv` + `pipe_dict.idx`.
     * Returns the number of indexed words.
     */
    fun importDictionaryFromUri(context: Context, uri: Uri, displayNameHint: String = ""): Int {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("! could not open selected file")
        stream.use { rawIn ->
            return importFromStream(context, rawIn, displayNameHint.ifEmpty { uri.lastPathSegment.orEmpty() })
        }
    }

    /**
     * Imports a dictionary from a local storage [File] and builds `pipe_dict.tsv` + `pipe_dict.idx`.
     */
    fun importDictionaryFromFile(context: Context, file: File): Int {
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("! cannot read ${file.name}")
        }
        FileInputStream(file).use { rawIn ->
            return importFromStream(context, rawIn, file.name)
        }
    }

    fun removeDictionary(context: Context) {
        cachedPrefixOffsets = null
        runCatching { getDictFile(context).delete() }
        runCatching { getIdxFile(context).delete() }
    }

    /**
     * Performs an on-demand disk seek lookup for [rawQuery] using `RandomAccessFile`.
     * Uses 0 MB of heap when idle and reads only the matching prefix slice on disk.
     */
    fun searchOnDemand(context: Context, rawQuery: String, limit: Int = 10): List<DictionaryEntry> {
        val dictFile = getDictFile(context)
        if (!dictFile.exists() || dictFile.length() == 0L) return emptyList()

        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) {
            return readSliceFromOffset(dictFile, 0L, "", limit)
        }

        val offsets = loadOrBuildIndexMap(context, dictFile)
        val prefix2 = q.take(2)
        val prefix1 = q.take(1)
        val startOffset = offsets[prefix2]
            ?: offsets[prefix1]
            ?: 0L

        return readSliceFromOffset(dictFile, startOffset, q, limit)
    }

    private fun readSliceFromOffset(
        dictFile: File,
        startOffset: Long,
        queryLower: String,
        limit: Int,
    ): List<DictionaryEntry> {
        val exactAndPrefix = mutableListOf<Pair<DictionaryEntry, Int>>()
        val prefix1 = queryLower.take(1)
        try {
            RandomAccessFile(dictFile, "r").use { raf ->
                val safeOffset = startOffset.coerceIn(0L, raf.length())
                raf.seek(safeOffset)
                var scanned = 0
                while (scanned < MAX_SCAN_LINES_PER_QUERY) {
                    val rawLine = raf.readLine() ?: break
                    scanned++
                    val line = String(rawLine.toByteArray(Charsets.ISO_8859_1), StandardCharsets.UTF_8)
                    val entry = parseTsvLine(line) ?: continue
                    val w = entry.wordLower

                    if (queryLower.isEmpty()) {
                        exactAndPrefix.add(entry to (100 - scanned))
                        if (exactAndPrefix.size >= limit) break
                        continue
                    }

                    if (prefix1.isNotEmpty() && w.isNotEmpty() && w.take(1) > prefix1 && scanned > 60) {
                        break
                    }

                    val score = when {
                        w == queryLower -> 5000
                        w.startsWith(queryLower) -> 2500 - (w.length - queryLower.length).coerceIn(0, 200)
                        w.contains(queryLower) -> 900 - w.indexOf(queryLower).coerceIn(0, 100)
                        else -> 0
                    }
                    if (score > 0) {
                        exactAndPrefix.add(entry to score)
                        if (exactAndPrefix.count { it.second >= 2300 } >= limit * 2) {
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return exactAndPrefix
            .sortedWith(compareByDescending<Pair<DictionaryEntry, Int>> { it.second }.thenBy { it.first.wordLower.length })
            .take(limit)
            .map { it.first }
    }

    private fun parseTsvLine(line: String): DictionaryEntry? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        return when {
            parts.size >= 3 -> DictionaryEntry(
                word = parts[0].trim(),
                partOfSpeech = parts[1].trim().ifEmpty { "def" },
                definition = parts[2].trim(),
                synonyms = parts.getOrNull(3)?.trim().orEmpty(),
                example = parts.getOrNull(4)?.trim().orEmpty(),
            )
            parts.size == 2 -> DictionaryEntry(
                word = parts[0].trim(),
                partOfSpeech = "def",
                definition = parts[1].trim(),
            )
            else -> null
        }
    }

    private fun loadOrBuildIndexMap(context: Context, dictFile: File): Map<String, Long> {
        cachedPrefixOffsets?.let { return it }
        val idxFile = getIdxFile(context)
        if (idxFile.exists() && idxFile.length() > 0L) {
            val map = mutableMapOf<String, Long>()
            runCatching {
                idxFile.forEachLine(StandardCharsets.UTF_8) { line ->
                    val sep = line.indexOf('\t')
                    if (sep > 0) {
                        val key = line.substring(0, sep)
                        val offset = line.substring(sep + 1).toLongOrNull()
                        if (offset != null) map[key] = offset
                    }
                }
            }
            if (map.isNotEmpty()) {
                cachedPrefixOffsets = map
                return map
            }
        }
        val rebuilt = buildByteOffsetIndex(dictFile, idxFile)
        cachedPrefixOffsets = rebuilt
        return rebuilt
    }

    private fun buildByteOffsetIndex(dictFile: File, idxFile: File): Map<String, Long> {
        val offsets = LinkedHashMap<String, Long>()
        try {
            RandomAccessFile(dictFile, "r").use { raf ->
                var lineStartOffset = 0L
                while (true) {
                    val rawLine = raf.readLine() ?: break
                    val nextOffset = raf.filePointer
                    val tabIdx = rawLine.indexOf('\t')
                    if (tabIdx > 0) {
                        val word = rawLine.substring(0, tabIdx).trim().lowercase()
                        if (word.isNotEmpty()) {
                            val p1 = word.take(1)
                            if (p1 !in offsets) offsets[p1] = lineStartOffset
                            if (word.length >= 2) {
                                val p2 = word.take(2)
                                if (p2 !in offsets) offsets[p2] = lineStartOffset
                            }
                        }
                    }
                    lineStartOffset = nextOffset
                }
            }
            BufferedWriter(OutputStreamWriter(FileOutputStream(idxFile), StandardCharsets.UTF_8)).use { out ->
                for ((k, v) in offsets) {
                    out.write("$k\t$v\n")
                }
            }
        } catch (_: Exception) {
        }
        return offsets
    }

    private fun importFromStream(context: Context, rawStream: InputStream, nameHint: String): Int {
        val buffered = BufferedInputStream(rawStream)
        buffered.mark(4)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()

        val isGzip = (b0 == 0x1f && b1 == 0x8b) || nameHint.lowercase().endsWith(".gz")
        val effectiveStream = if (isGzip) GZIPInputStream(buffered) else buffered
        val reader = BufferedReader(InputStreamReader(effectiveStream, StandardCharsets.UTF_8))

        reader.mark(2048)
        val previewChars = CharArray(1024)
        val readCount = reader.read(previewChars)
        reader.reset()
        val head = if (readCount > 0) String(previewChars, 0, readCount).trimStart() else ""

        val parsedEntries = when {
            head.startsWith("{") || head.startsWith("[") -> parseJsonDictionary(reader.readText())
            else -> parseDelimitedDictionary(reader)
        }

        if (parsedEntries.isEmpty()) {
            throw IllegalArgumentException("! no valid dictionary definitions found in file")
        }

        val sorted = parsedEntries
            .distinctBy { "${it.wordLower}\t${it.partOfSpeech}\t${it.definition.take(40)}" }
            .sortedBy { it.wordLower }

        val dictFile = getDictFile(context)
        val idxFile = getIdxFile(context)
        BufferedWriter(OutputStreamWriter(FileOutputStream(dictFile), StandardCharsets.UTF_8)).use { writer ->
            for (item in sorted) {
                val cleanWord = item.word.replace('\t', ' ').replace('\n', ' ').trim()
                val cleanPos = item.partOfSpeech.replace('\t', ' ').replace('\n', ' ').trim().ifEmpty { "def" }
                val cleanDef = item.definition.replace('\t', ' ').replace('\n', ' ').trim()
                val cleanSyn = item.synonyms.replace('\t', ' ').replace('\n', ' ').trim()
                val cleanEx = item.example.replace('\t', ' ').replace('\n', ' ').trim()
                if (cleanWord.isNotEmpty() && cleanDef.isNotEmpty()) {
                    writer.write("$cleanWord\t$cleanPos\t$cleanDef\t$cleanSyn\t$cleanEx\n")
                }
            }
        }
        cachedPrefixOffsets = buildByteOffsetIndex(dictFile, idxFile)
        return sorted.size
    }

    private fun parseDelimitedDictionary(reader: BufferedReader): List<DictionaryEntry> {
        val out = mutableListOf<DictionaryEntry>()
        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine

            if (line.contains('\t')) {
                val parts = line.split('\t')
                if (parts.size >= 3) {
                    val w = parts[0].trim().trim('"')
                    val pos = parts[1].trim().trim('"').ifEmpty { "def" }
                    val def = parts[2].trim().trim('"')
                    val syn = parts.getOrNull(3)?.trim()?.trim('"').orEmpty()
                    val ex = parts.getOrNull(4)?.trim()?.trim('"').orEmpty()
                    if (w.isNotEmpty() && def.isNotEmpty() && !w.equals("word", ignoreCase = true)) {
                        out.add(DictionaryEntry(w, pos, def, syn, ex))
                    }
                } else if (parts.size == 2) {
                    val w = parts[0].trim().trim('"')
                    val def = parts[1].trim().trim('"')
                    if (w.isNotEmpty() && def.isNotEmpty() && !w.equals("word", ignoreCase = true)) {
                        out.add(DictionaryEntry(w, "def", def))
                    }
                }
            } else if (line.contains(',')) {
                val cols = parseCsvRow(line)
                if (cols.size >= 3) {
                    val w = cols[0].trim()
                    val pos = cols[1].trim().ifEmpty { "def" }
                    val def = cols.drop(2).joinToString(", ").trim()
                    if (w.isNotEmpty() && def.isNotEmpty() && !w.equals("word", ignoreCase = true)) {
                        out.add(DictionaryEntry(w, pos, def))
                    }
                } else if (cols.size == 2) {
                    val w = cols[0].trim()
                    val def = cols[1].trim()
                    if (w.isNotEmpty() && def.isNotEmpty() && !w.equals("word", ignoreCase = true)) {
                        out.add(DictionaryEntry(w, "def", def))
                    }
                }
            }
        }
        return out
    }

    private fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun parseJsonDictionary(rawJson: String): List<DictionaryEntry> {
        val out = mutableListOf<DictionaryEntry>()
        val trimmed = rawJson.trim()
        try {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val w = obj.optString("word", "").trim()
                    val pos = obj.optString("type", obj.optString("pos", "def")).trim().ifEmpty { "def" }
                    val def = obj.optString("description", obj.optString("definition", obj.optString("meaning", ""))).trim()
                    if (w.isNotEmpty() && def.isNotEmpty()) {
                        out.add(DictionaryEntry(w, pos, def))
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = root.opt(key) ?: continue
                    when (value) {
                        is String -> {
                            val cleanDef = value.trim()
                            if (key.isNotBlank() && cleanDef.isNotEmpty()) {
                                out.add(DictionaryEntry(key.trim(), "def", cleanDef.take(500)))
                            }
                        }
                        is JSONObject -> {
                            val word = value.optString("word", key).trim()
                            val meanings = value.optJSONArray("meanings")
                            if (meanings != null && meanings.length() > 0) {
                                for (mIdx in 0 until meanings.length().coerceAtMost(3)) {
                                    val m = meanings.optJSONObject(mIdx) ?: continue
                                    val pos = m.optString("speech_part", "def").trim().ifEmpty { "def" }
                                    val def = m.optString("def", "").trim()
                                    val ex = m.optString("example", "").trim()
                                    val synArr = m.optJSONArray("synonyms")
                                    val syns = buildList {
                                        if (synArr != null) {
                                            for (sIdx in 0 until synArr.length().coerceAtMost(5)) {
                                                val s = synArr.optString(sIdx, "").trim()
                                                if (s.isNotEmpty()) add(s)
                                            }
                                        }
                                    }.joinToString(", ")
                                    if (word.isNotEmpty() && def.isNotEmpty()) {
                                        out.add(DictionaryEntry(word, pos, def, syns, ex))
                                    }
                                }
                            } else {
                                val def = value.optString("definition", value.optString("meaning", "")).trim()
                                if (word.isNotEmpty() && def.isNotEmpty()) {
                                    out.add(DictionaryEntry(word, "def", def))
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
