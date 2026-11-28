package com.sujan.pipe.domain

import com.sujan.pipe.data.FloccusBookmarkManager
import com.sujan.pipe.model.AppShortcutItem
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.FloccusBookmarkItem
import com.sujan.pipe.model.UniversalAlias
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.ContactQuickAction
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.DictionaryEntry
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.FullBackupBundle
import com.sujan.pipe.model.InstalledAppItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.PrefixConfig
import com.sujan.pipe.model.DefaultSystemShortcuts
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.model.SystemSettingShortcut
import com.sujan.pipe.model.WhatsAppFlavor
import com.sujan.pipe.model.YouTubeWatchEntry
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FuzzyMatcher {

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

    fun scorePreLowered(q: String, c: String): Int {
        if (q.isEmpty()) return 1
        if (c.isEmpty()) return 0

        if (c == q) return 1000
        if (c.startsWith(q)) return 900 - (c.length - q.length).coerceAtMost(80)

        val wordMatch = checkWordBoundaryMatch(q, c)
        if (wordMatch > 0) return wordMatch

        val subIdx = c.indexOf(q)
        if (subIdx >= 0) return 680 - subIdx.coerceAtMost(60)

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

            val subIdx = lowerText.indexOf(token)
            if (subIdx >= 0) {
                for (k in subIdx until (subIdx + tLen)) {
                    mask[k] = true
                }
                continue
            }

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

enum class ClipActionKind {
    OPEN_URL,
    COPY_CLEAN_URL,
    SAVE_BOOKMARK,
    CALL_NUMBER,
    WA_NUMBER,
    WAB_NUMBER,
    SAVE_CONTACT,
    COPY_TEXT,
}

enum class DirectNumberPrimaryMode(val badge: String, val label: String, val targetPackage: String?) {
    WHATSAPP_PERSONAL("wa", "WhatsApp", "com.whatsapp"),
    WHATSAPP_BUSINESS("wa.b", "WhatsApp Business", "com.whatsapp.w4b"),
    CALL("call", "Dial", null),
    SMS("sms", "SMS", null),
}

enum class PromptIntentMode(val scopeBadge: String) {
    ALL("all"),
    CONTACTS_ONLY("c:contacts"),
    FILES_ONLY("f:files"),
    DIRS_ONLY("d:folders"),
    BOOKMARKS_ONLY("b:bookmarks"),
    FLOCCUS_ONLY("fl:floccus"),
    PIPES_LIST("s:pipes"),
    APPS_ONLY("app:launcher"),
    HISTORY_ONLY("hist:history"),
    YOUTUBE_HISTORY_ONLY("yth:watch"),
    DICTIONARY_ONLY("def:dict"),
    CLIPBOARD_ONLY("clip:tools"),
    SYSTEM_SETTINGS_ONLY("set:sys"),
    DIRECT_NUMBER_ONLY("wa:num"),
    TIMER_ALARM_ONLY("clk:time"),
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
    val directNumberMode: DirectNumberPrimaryMode = DirectNumberPrimaryMode.WHATSAPP_PERSONAL,
    val isAlarmCommand: Boolean = false,
)

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

    data class FloccusBookmarkAction(
        val bookmark: FloccusBookmarkItem?,
        val query: String = "",
        val isSetupPrompt: Boolean = false,
        val setupTitle: String = "Link Floccus Bookmarks (.xbel / .html)",
        val setupSubtitle: String = "tap to auto-detect bookmarks.xbel or pick file in Settings -> index",
        override val usageKey: String = bookmark?.usageKey ?: "floccus:setup",
        override val rankScore: Int = 9300,
        override val usageCount: Int = 0,
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

    data class AppShortcutAction(
        val shortcut: AppShortcutItem,
        override val usageKey: String = shortcut.usageKey,
        override val rankScore: Int = 8800,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class DictionaryAction(
        val entry: DictionaryEntry?,
        val query: String,
        val isSetupPrompt: Boolean = false,
        val setupTitle: String = "Link Offline Dictionary (.tsv / .json / .gz)",
        val setupSubtitle: String = "tap to auto-detect from Download/ or pick dictionary file",
        override val usageKey: String = "dict:${entry?.wordLower ?: query.lowercase()}",
        override val rankScore: Int = 9500,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class DirectUrlAction(
        val targetUrl: String,
        val displayHost: String,
        override val usageKey: String = "url:$targetUrl",
        override val rankScore: Int = 95_000,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class ClipboardAction(
        val kind: ClipActionKind,
        val title: String,
        val subtitle: String,
        val payloadText: String,
        override val usageKey: String = "clip:${kind.name}",
        override val rankScore: Int = 9000,
        override val usageCount: Int = 0,
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

    data class YouTubeWatchAction(
        val entry: YouTubeWatchEntry?,
        val query: String,
        val isSetupPrompt: Boolean = false,
        val setupTitle: String = "Enable Offline YouTube Watch History",
        val setupSubtitle: String = "grant Notification Access for auto-capture, or import Takeout watch-history.json",
        override val usageKey: String = "ytw:${entry?.id ?: "setup"}",
        override val rankScore: Int = 9400,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class SystemSettingAction(
        val shortcut: SystemSettingShortcut,
        override val usageKey: String = "sys:${shortcut.id}",
        override val rankScore: Int = 9200,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class DirectNumberAction(
        val rawPhone: String,
        val normalizedWhatsAppDigits: String,
        val messageText: String = "",
        val primaryMode: DirectNumberPrimaryMode = DirectNumberPrimaryMode.WHATSAPP_PERSONAL,
        override val usageKey: String = "num:$normalizedWhatsAppDigits:${primaryMode.name}",
        override val rankScore: Int = 96_000,
        override val usageCount: Int = 0,
    ) : PaletteItem

    data class TimerAlarmAction(
        val isAlarm: Boolean,
        val totalSeconds: Int = 0,
        val hour24: Int = 0,
        val minute: Int = 0,
        val label: String = "",
        val title: String,
        val subtitle: String,
        override val usageKey: String = if (isAlarm) "alarm:$hour24:$minute" else "timer:$totalSeconds",
        override val rankScore: Int = 97_000,
        override val usageCount: Int = 0,
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
    private val IPV4_PORT_REGEX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?(/.*)?$")
    private val LOCALHOST_REGEX = Regex("^localhost(:\\d{1,5})?(/.*)?$", RegexOption.IGNORE_CASE)
    private val LOCAL_FILE_EXTS = setOf(
        "pdf", "jpg", "jpeg", "png", "webp", "gif", "bmp", "mp3", "mp4", "mkv", "wav",
        "flac", "ogg", "apk", "zip", "tar", "gz", "7z", "rar", "txt", "md", "csv",
        "tsv", "json", "xml", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "kt", "java", "py",
    )
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "igsh", "si", "mc_cid", "mc_eid", "_hsenc", "rb_clickid", "ref_src",
    )

    /**
     * Feature #3: Auto-detects bare domains (`audiobookbay.lu`, `github.com/user/repo`),
     * full `http(s)://` URLs, and local IPs/ports (`192.168.1.1:8080`, `localhost:3000`).
     */
    fun detectDirectUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val afterScheme = trimmed.substringAfter("://")
            if (afterScheme.isNotEmpty() && (afterScheme.contains('.') || afterScheme.startsWith("localhost", ignoreCase = true))) {
                return trimmed
            }
            return null
        }
        if (IPV4_PORT_REGEX.matches(trimmed) || LOCALHOST_REGEX.matches(trimmed)) {
            return "http://$trimmed"
        }
        val hostPart = trimmed.substringBefore('/').substringBefore('?').substringBefore(':')
        if (!hostPart.contains('.') || hostPart.startsWith('.') || hostPart.endsWith('.')) return null
        val tld = hostPart.substringAfterLast('.').lowercase()
        if (tld.length !in 2..12 || !tld.all { it in 'a'..'z' }) return null
        val hasPathOrQuery = trimmed.contains('/') || trimmed.contains('?')
        if (!hasPathOrQuery && tld in LOCAL_FILE_EXTS) return null
        return "https://$trimmed"
    }

    /**
     * Feature #6: Strips tracking query parameters (`?utm_source=...`, `fbclid`, `igsh`, `si`, etc.) from a URL.
     */
    fun stripTrackingParams(rawUrl: String): String {
        val qIdx = rawUrl.indexOf('?')
        if (qIdx < 0) return rawUrl
        val base = rawUrl.substring(0, qIdx)
        val afterQ = rawUrl.substring(qIdx + 1)
        val hashIdx = afterQ.indexOf('#')
        val queryPart = if (hashIdx >= 0) afterQ.substring(0, hashIdx) else afterQ
        val fragment = if (hashIdx >= 0) afterQ.substring(hashIdx) else ""

        val keptParams = queryPart.split('&').filter { pair ->
            val key = pair.substringBefore('=').lowercase()
            key.isNotEmpty() && key !in TRACKING_PARAMS && !key.startsWith("utm_")
        }
        return if (keptParams.isEmpty()) {
            "$base$fragment"
        } else {
            "$base?${keptParams.joinToString("&")}$fragment"
        }
    }

    /**
     * Feature #3: Detects an unsaved phone number (7..15 digits) with optional trailing message text.
     */
    fun parseDirectPhoneInput(rawInput: String): Pair<String, String>? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        if (IPV4_PORT_REGEX.matches(trimmed)) return null

        val tokens = trimmed.split(WHITESPACE_REGEX)
        val phoneTokens = mutableListOf<String>()
        var msgStartIndex = tokens.size

        for ((idx, tok) in tokens.withIndex()) {
            val isPhonePiece = tok.all { it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' || it == '.' } &&
                tok.any { it.isDigit() }
            if (isPhonePiece && phoneTokens.size < 3) {
                phoneTokens.add(tok)
            } else {
                msgStartIndex = idx
                break
            }
        }

        if (phoneTokens.isEmpty()) return null
        val combinedPhone = phoneTokens.joinToString("")
        val digits = combinedPhone.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        val message = if (msgStartIndex < tokens.size) tokens.subList(msgStartIndex, tokens.size).joinToString(" ").trim() else ""
        return combinedPhone to message
    }

    /**
     * Feature #10: Parses countdown timer input such as `12m`, `1h 30m`, `45s`, `90s`, or `15` (minutes) + optional label.
     */
    fun parseDurationTimer(rawArgs: String): Pair<Int, String>? {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return null

        val unitRegex = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s*(?:h|hr|hrs|hour|hours))?\\s*(?:(\\d+(?:\\.\\d+)?)\\s*(?:m|min|mins|minute|minutes))?\\s*(?:(\\d+)\\s*(?:s|sec|secs|second|seconds))?(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
        val m = unitRegex.find(trimmed)
        if (m != null) {
            val hStr = m.groupValues[1]
            val mStr = m.groupValues[2]
            val sStr = m.groupValues[3]
            val label = m.groupValues[4].trim()
            if (hStr.isNotEmpty() || mStr.isNotEmpty() || sStr.isNotEmpty()) {
                val hours = hStr.toDoubleOrNull() ?: 0.0
                val mins = mStr.toDoubleOrNull() ?: 0.0
                val secs = sStr.toIntOrNull() ?: 0
                val totalSecs = (hours * 3600.0 + mins * 60.0 + secs).toInt()
                if (totalSecs > 0) return totalSecs to label
            }
        }

        val tokens = trimmed.split(WHITESPACE_REGEX, limit = 2)
        val firstNum = tokens[0].toDoubleOrNull()
        if (firstNum != null && firstNum > 0) {
            val totalSecs = (firstNum * 60.0).toInt().coerceAtLeast(1)
            val label = if (tokens.size > 1) tokens[1].trim() else ""
            return totalSecs to label
        }
        return null
    }

    fun formatDurationSummary(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0 || isEmpty()) add("${s}s")
        }.joinToString(" ")
    }

    /**
     * Feature #10: Parses alarm time input such as `06:30`, `6:30am`, `6:30 pm`, `18:45`, `7am` + optional label.
     */
    fun parseAlarmTime(rawArgs: String): Triple<Int, Int, String>? {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return null
        val regex = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val rawMin = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].toIntOrNull() ?: return null else 0
        val amPm = match.groupValues[3].lowercase()
        val label = match.groupValues[4].trim()

        if (rawMin !in 0..59) return null
        val hour24 = when (amPm) {
            "am" -> {
                if (rawHour !in 1..12) return null
                if (rawHour == 12) 0 else rawHour
            }
            "pm" -> {
                if (rawHour !in 1..12) return null
                if (rawHour == 12) 12 else rawHour + 12
            }
            else -> {
                if (rawHour !in 0..23) return null
                rawHour
            }
        }
        return Triple(hour24, rawMin, label)
    }

    fun formatAlarmTime(hour24: Int, minute: Int): String {
        val h24 = String.format(java.util.Locale.US, "%02d:%02d", hour24, minute)
        val period = if (hour24 < 12) "AM" else "PM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val mStr = String.format(java.util.Locale.US, "%02d", minute)
        return "$h24 ($h12:$mStr $period)"
    }

    fun scoreSystemShortcut(rawQuery: String, shortcut: SystemSettingShortcut): Int {
        val clean = rawQuery.trim().lowercase()
        if (clean.isEmpty()) return 200
        val aliasScore = when {
            shortcut.aliasLower == clean -> 2400
            shortcut.aliasLower.startsWith(clean) -> 1100
            else -> FuzzyMatcher.scorePreLowered(clean, shortcut.aliasLower)
        }
        val titleScore = FuzzyMatcher.scorePreLowered(clean, shortcut.titleLower)
        val kwScore = FuzzyMatcher.scorePreLowered(clean, shortcut.keywordsLower)
        return maxOf(aliasScore, titleScore, kwScore)
    }

    fun scoreAppShortcut(rawQuery: String, item: AppShortcutItem): Int {
        val clean = rawQuery.trim().lowercase()
        if (clean.isEmpty()) return 120
        val shortcutScore = FuzzyMatcher.scorePreLowered(clean, item.shortcutLabelLower)
        val combinedScore = FuzzyMatcher.scorePreLowered(clean, item.combinedLower)
        val appScore = FuzzyMatcher.scorePreLowered(clean, item.appLabelLower)
        val effectiveAppScore = if (appScore >= 600) (appScore - 120).coerceAtLeast(150) else 0
        return maxOf(shortcutScore, combinedScore, effectiveAppScore)
    }

    fun parsePromptCommand(
        rawInput: String,
        bookmarks: List<PipeBookmark>,
        engines: List<SearchEngine> = DefaultEngines.LIST,
        prefixes: PrefixConfig = PrefixConfig(),
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

        val bPrefix = prefixes.bookmarksClean
        val sPrefix = prefixes.pipesClean
        val cPrefix = prefixes.contactsClean
        val appPrefix = prefixes.appsClean
        val fPrefix = prefixes.filesClean
        val histPrefix = prefixes.historyClean
        val defPrefix = prefixes.dictionaryClean
        val clipPrefix = prefixes.clipboardClean
        val setPrefix = prefixes.systemSettingsClean
        val ythPrefix = prefixes.youtubeHistoryClean
        val flPrefix = prefixes.floccusClean

        // True Offline YouTube Watch History: `yth` / `yth <query>` / `ytwatch <query>`
        if (firstLower == ythPrefix || firstLower == "yth" || firstLower == "ytwatch" || firstLower == "watchhist") {
            return ParsedPromptCommand(PromptIntentMode.YOUTUBE_HISTORY_ONLY, remainder)
        }

        // Floccus Synced Bookmarks: `fl` / `fl <query>` / `floccus <query>`
        // Guard against user explicitly setting another category's prefix (e.g. files="fl") to the same token.
        val flCollidesWithOtherPrefix = flPrefix == fPrefix || flPrefix == bPrefix || flPrefix == sPrefix ||
            flPrefix == cPrefix || flPrefix == appPrefix || flPrefix == histPrefix ||
            flPrefix == defPrefix || flPrefix == clipPrefix || flPrefix == setPrefix || flPrefix == ythPrefix
        if (firstLower == "floccus" || (!flCollidesWithOtherPrefix && firstLower == flPrefix)) {
            return ParsedPromptCommand(PromptIntentMode.FLOCCUS_ONLY, remainder)
        }

        // On-Demand Offline Dictionary: `def <word>` or custom dictionary prefix
        if ((firstLower == defPrefix || firstLower == "def" || firstLower == "dict" || firstLower == "define") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.DICTIONARY_ONLY, remainder)
        }

        // Clipboard & Text Tools: `clip` (works immediately or with space) or `txt <text>`
        if (firstLower == clipPrefix || firstLower == "clip" || ((firstLower == "txt" || firstLower == "text") && hasSpaceAfterFirst)) {
            return ParsedPromptCommand(PromptIntentMode.CLIPBOARD_ONLY, remainder)
        }

        // Feature #1: Deep Android System Settings (`set <query>` or `sys <query>`)
        if ((firstLower == setPrefix || firstLower == "set" || firstLower == "sys" || firstLower == "settings") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.SYSTEM_SETTINGS_ONLY, remainder)
        }

        // Feature #10: Quick Countdown Timer & Alarm (`timer 12m`, `alarm 06:30`)
        if ((firstLower == "timer" || firstLower == "tmr") && (hasSpaceAfterFirst || trimmed.equals("timer", ignoreCase = true))) {
            return ParsedPromptCommand(PromptIntentMode.TIMER_ALARM_ONLY, remainder, isAlarmCommand = false)
        }
        if ((firstLower == "alarm" || firstLower == "alm") && (hasSpaceAfterFirst || trimmed.equals("alarm", ignoreCase = true))) {
            return ParsedPromptCommand(PromptIntentMode.TIMER_ALARM_ONLY, remainder, isAlarmCommand = true)
        }

        // Feature #3: Direct WhatsApp (`wa <num>`) & WhatsApp Business (`wab <num>`) for unsaved numbers (or saved contacts)
        if ((firstLower == "wa" || firstLower == "whatsapp") && hasSpaceAfterFirst) {
            val parsedPhone = parseDirectPhoneInput(remainder)
            if (parsedPhone != null || remainder.isEmpty() || remainder.any { it.isDigit() }) {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.DIRECT_NUMBER_ONLY,
                    filterQuery = remainder,
                    directNumberMode = DirectNumberPrimaryMode.WHATSAPP_PERSONAL,
                )
            } else {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.CONTACTS_ONLY,
                    filterQuery = remainder,
                    contactAction = ContactQuickAction.WHATSAPP,
                )
            }
        }
        if ((firstLower == "wab" || firstLower == "wb" || firstLower == "w4b") && hasSpaceAfterFirst) {
            val parsedPhone = parseDirectPhoneInput(remainder)
            if (parsedPhone != null || remainder.isEmpty() || remainder.any { it.isDigit() }) {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.DIRECT_NUMBER_ONLY,
                    filterQuery = remainder,
                    directNumberMode = DirectNumberPrimaryMode.WHATSAPP_BUSINESS,
                )
            } else {
                return ParsedPromptCommand(
                    mode = PromptIntentMode.CONTACTS_ONLY,
                    filterQuery = remainder,
                    contactAction = ContactQuickAction.WHATSAPP_BUSINESS,
                )
            }
        }

        // 1. Dedicated Bookmark Prefix: `<bPrefix> <query>` or `bm <query>`
        if ((firstLower == bPrefix || firstLower == "b" || firstLower == "bm") && hasSpaceAfterFirst) {
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

        // 2. Dedicated Search Pipe Prefix: `<sPrefix> <keyword> [query]`
        if ((firstLower == sPrefix || firstLower == "s" || firstLower == "pipe" || firstLower == "pipes") && hasSpaceAfterFirst) {
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

        // 3. Contact alias: `<cPrefix> <name>`
        if ((firstLower == cPrefix || firstLower == "c" || firstLower == "call" || firstLower == "contact") && hasSpaceAfterFirst) {
            val subTokens = remainder.trim().split(WHITESPACE_REGEX)
            if (subTokens.size >= 2) {
                val lastToken = subTokens.last().lowercase()
                val chainedAction = when (lastToken) {
                    "wab", "wb", "w4b" -> ContactQuickAction.WHATSAPP_BUSINESS
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

        // 4. Direct App Launcher alias: `<appPrefix> <query>`
        if ((firstLower == appPrefix || firstLower == "app" || firstLower == "apps" || firstLower == "ap") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.APPS_ONLY, remainder)
        }

        // 5. File + Folder search alias: `<fPrefix> <query>`
        if ((firstLower == fPrefix || firstLower == "f" || firstLower == "file" || firstLower == "files") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.FILES_ONLY, remainder)
        }

        // 6. History search alias: `<histPrefix> <query>`
        if ((firstLower == histPrefix || firstLower == "hist" || firstLower == "history") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.HISTORY_ONLY, remainder)
        }

        // 7. Directory-only alias: `d <query>`
        if ((firstLower == "d" || firstLower == "dir" || firstLower == "folder" || firstLower == "folders") && hasSpaceAfterFirst) {
            return ParsedPromptCommand(PromptIntentMode.DIRS_ONLY, remainder)
        }

        // 8. Direct Folder Bookmark or Engine Keyword when followed by a space
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
        appShortcuts: List<AppShortcutItem> = emptyList(),
        universalAliases: List<UniversalAlias> = emptyList(),
        clipboardText: String = "",
        prefixes: PrefixConfig = PrefixConfig(),
        dictionaryMatches: List<DictionaryEntry> = emptyList(),
        isDictionaryInstalled: Boolean = false,
        whatsAppFlavor: WhatsAppFlavor = WhatsAppFlavor.BOTH,
        youtubeHistoryMatches: List<YouTubeWatchEntry> = emptyList(),
        youtubeHistoryTotalCount: Int = 0,
        isYouTubeListenerEnabled: Boolean = false,
        floccusBookmarks: List<FloccusBookmarkItem> = emptyList(),
        includeFloccusInUnified: Boolean = true,
    ): List<PaletteItem> {
        val cmd = parsePromptCommand(rawInput, bookmarks, engines, prefixes)
        val aliasByTargetKey = universalAliases.associate { it.targetKey to it.aliasLower }
        fun usageOf(key: String): Int = usageMap[key] ?: 0
        fun usageBonus(key: String): Int = (usageMap[key] ?: 0) * 35
        fun customAliasScore(targetKey: String, rawQuery: String): Int {
            val clean = rawQuery.trim().lowercase()
            if (clean.isEmpty()) return 0
            val alias = aliasByTargetKey[targetKey] ?: return 0
            return when {
                alias == clean -> 12_000
                alias.startsWith(clean) -> 1_600
                else -> FuzzyMatcher.scorePreLowered(clean, alias)
            }
        }

        return when (cmd.mode) {
            PromptIntentMode.ADD_COMMAND -> buildQuickAddSuggestions(cmd.filterQuery, clipboardText, prefixes)

            PromptIntentMode.RM_COMMAND -> buildQuickRemoveSuggestions(cmd.filterQuery, bookmarks, engines, history, prefixes)

            PromptIntentMode.YOUTUBE_HISTORY_ONLY -> {
                val q = cmd.filterQuery.trim()
                val out = mutableListOf<PaletteItem>()
                if (youtubeHistoryMatches.isEmpty() && youtubeHistoryTotalCount == 0) {
                    out.add(
                        PaletteItem.YouTubeWatchAction(
                            entry = null,
                            query = q,
                            isSetupPrompt = true,
                            setupTitle = if (isYouTubeListenerEnabled) {
                                "Offline YouTube History Ready (0 videos yet)"
                            } else {
                                "Enable Offline YouTube Watch History"
                            },
                            setupSubtitle = if (isYouTubeListenerEnabled) {
                                "play a YouTube video or tap to import Takeout watch-history.json"
                            } else {
                                "tap to grant Notification Access for auto-capture or import Takeout JSON"
                            },
                            usageKey = "ytw:setup",
                            rankScore = 100_000,
                        )
                    )
                } else {
                    youtubeHistoryMatches.forEachIndexed { idx, entry ->
                        val uKey = "ytw:${entry.id}"
                        out.add(
                            PaletteItem.YouTubeWatchAction(
                                entry = entry,
                                query = q,
                                isSetupPrompt = false,
                                usageKey = uKey,
                                rankScore = 50_000 - idx * 100 + usageBonus(uKey),
                                usageCount = usageOf(uKey).coerceAtLeast(entry.watchCount),
                            )
                        )
                    }
                }
                if (q.isNotEmpty()) {
                    val ytEng = engines.firstOrNull { it.keyword.equals("yt", ignoreCase = true) }
                        ?: engines.firstOrNull { it.isDefault }
                        ?: DefaultEngines.LIST.first()
                    out.add(
                        PaletteItem.EngineAction(
                            engine = ytEng,
                            query = q,
                            targetUrl = buildSearchUrl(ytEng.urlTemplate, q),
                            isHome = false,
                            usageKey = "eng:${ytEng.id}",
                            rankScore = 500,
                            usageCount = usageOf("eng:${ytEng.id}"),
                        )
                    )
                }
                out.take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.DICTIONARY_ONLY -> {
                val q = cmd.filterQuery.trim()
                val out = mutableListOf<PaletteItem>()
                if (!isDictionaryInstalled) {
                    out.add(
                        PaletteItem.DictionaryAction(
                            entry = null,
                            query = q,
                            isSetupPrompt = true,
                            usageKey = "dict:setup",
                            rankScore = 100_000,
                        )
                    )
                } else {
                    dictionaryMatches.forEachIndexed { idx, entry ->
                        out.add(
                            PaletteItem.DictionaryAction(
                                entry = entry,
                                query = q,
                                isSetupPrompt = false,
                                usageKey = "dict:${entry.wordLower}",
                                rankScore = 50_000 - idx * 100,
                            )
                        )
                    }
                }
                if (q.isNotEmpty()) {
                    val wikiEng = engines.firstOrNull { it.keyword.equals("w", ignoreCase = true) }
                        ?: engines.firstOrNull { it.isDefault }
                        ?: DefaultEngines.LIST.first()
                    out.add(
                        PaletteItem.EngineAction(
                            engine = wikiEng,
                            query = q,
                            targetUrl = buildSearchUrl(wikiEng.urlTemplate, q),
                            isHome = false,
                            usageKey = "eng:${wikiEng.id}",
                            rankScore = 500,
                            usageCount = usageOf("eng:${wikiEng.id}"),
                        )
                    )
                }
                out.take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.CLIPBOARD_ONLY -> buildClipboardAndTextSuggestions(cmd.filterQuery, clipboardText)

            PromptIntentMode.SYSTEM_SETTINGS_ONLY -> {
                val q = cmd.filterQuery.trim()
                DefaultSystemShortcuts.LIST.mapNotNull { sc ->
                    val uKey = "sys:${sc.id}"
                    val uCount = usageOf(uKey)
                    val s = maxOf(scoreSystemShortcut(q, sc), customAliasScore(uKey, q))
                    if (s > 0) {
                        PaletteItem.SystemSettingAction(
                            shortcut = sc,
                            usageKey = uKey,
                            rankScore = s * 10 + usageBonus(uKey),
                            usageCount = uCount,
                        )
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.SystemSettingAction> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem.SystemSettingAction> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.DIRECT_NUMBER_ONLY -> {
                val q = cmd.filterQuery.trim()
                val effectiveInput = q.ifEmpty { clipboardText.trim() }
                val parsed = parseDirectPhoneInput(effectiveInput)
                if (parsed != null) {
                    val (rawPhone, msg) = parsed
                    val normDigits = normalizeWhatsAppPhone(rawPhone)
                    val preferBiz = cmd.directNumberMode == DirectNumberPrimaryMode.WHATSAPP_BUSINESS ||
                        (cmd.directNumberMode == DirectNumberPrimaryMode.WHATSAPP_PERSONAL && q.isEmpty() && whatsAppFlavor == WhatsAppFlavor.BUSINESS)
                    val firstWa = if (preferBiz) DirectNumberPrimaryMode.WHATSAPP_BUSINESS else DirectNumberPrimaryMode.WHATSAPP_PERSONAL
                    val secondWa = if (preferBiz) DirectNumberPrimaryMode.WHATSAPP_PERSONAL else DirectNumberPrimaryMode.WHATSAPP_BUSINESS
                    listOf(
                        PaletteItem.DirectNumberAction(
                            rawPhone = rawPhone,
                            normalizedWhatsAppDigits = normDigits,
                            messageText = msg,
                            primaryMode = firstWa,
                            rankScore = 99_000,
                        ),
                        PaletteItem.DirectNumberAction(
                            rawPhone = rawPhone,
                            normalizedWhatsAppDigits = normDigits,
                            messageText = msg,
                            primaryMode = secondWa,
                            rankScore = 98_000,
                        ),
                        PaletteItem.DirectNumberAction(
                            rawPhone = rawPhone,
                            normalizedWhatsAppDigits = normDigits,
                            messageText = msg,
                            primaryMode = DirectNumberPrimaryMode.CALL,
                            rankScore = 97_000,
                        ),
                        PaletteItem.DirectNumberAction(
                            rawPhone = rawPhone,
                            normalizedWhatsAppDigits = normDigits,
                            messageText = msg,
                            primaryMode = DirectNumberPrimaryMode.SMS,
                            rankScore = 96_000,
                        ),
                    )
                } else {
                    listOf(
                        PaletteItem.DirectNumberAction(
                            rawPhone = "9841234567",
                            normalizedWhatsAppDigits = "9779841234567",
                            messageText = "",
                            primaryMode = cmd.directNumberMode,
                            rankScore = 90_000,
                        )
                    )
                }
            }

            PromptIntentMode.TIMER_ALARM_ONLY -> {
                val q = cmd.filterQuery.trim()
                val out = mutableListOf<PaletteItem>()
                if (cmd.isAlarmCommand) {
                    val parsedAlarm = parseAlarmTime(q)
                    if (parsedAlarm != null) {
                        val (h, m, lbl) = parsedAlarm
                        val timeText = formatAlarmTime(h, m)
                        val title = if (lbl.isNotBlank()) "Set Alarm: $timeText · \"$lbl\"" else "Set Alarm: $timeText"
                        out.add(
                            PaletteItem.TimerAlarmAction(
                                isAlarm = true,
                                hour24 = h,
                                minute = m,
                                label = lbl,
                                title = title,
                                subtitle = "sets system alarm immediately (skip UI)",
                                rankScore = 99_000,
                            )
                        )
                    }
                    val presets = listOf(6 to 0, 6 to 30, 7 to 0, 7 to 30, 8 to 0, 22 to 0)
                    presets.forEachIndexed { idx, (ph, pm) ->
                        if (parsedAlarm == null || parsedAlarm.first != ph || parsedAlarm.second != pm) {
                            val timeText = formatAlarmTime(ph, pm)
                            out.add(
                                PaletteItem.TimerAlarmAction(
                                    isAlarm = true,
                                    hour24 = ph,
                                    minute = pm,
                                    label = "",
                                    title = "Preset Alarm: $timeText",
                                    subtitle = "tap or press RET to set alarm",
                                    rankScore = 90_000 - idx * 100,
                                )
                            )
                        }
                    }
                } else {
                    val parsedTimer = parseDurationTimer(q)
                    if (parsedTimer != null) {
                        val (secs, lbl) = parsedTimer
                        val durText = formatDurationSummary(secs)
                        val title = if (lbl.isNotBlank()) "Start Timer: $durText · \"$lbl\"" else "Start Timer: $durText"
                        out.add(
                            PaletteItem.TimerAlarmAction(
                                isAlarm = false,
                                totalSeconds = secs,
                                label = lbl,
                                title = title,
                                subtitle = "starts countdown timer immediately in background",
                                rankScore = 99_000,
                            )
                        )
                    }
                    val presetMins = listOf(5 to "5m Quick", 10 to "10m", 15 to "15m", 25 to "25m Pomodoro", 45 to "45m", 60 to "1h")
                    presetMins.forEachIndexed { idx, (mins, tag) ->
                        val pSecs = mins * 60
                        if (parsedTimer == null || parsedTimer.first != pSecs) {
                            out.add(
                                PaletteItem.TimerAlarmAction(
                                    isAlarm = false,
                                    totalSeconds = pSecs,
                                    label = tag,
                                    title = "Preset Timer: $tag",
                                    subtitle = "tap or press RET to start countdown",
                                    rankScore = 90_000 - idx * 100,
                                )
                            )
                        }
                    }
                }
                out.take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.CONTACTS_ONLY -> {
                val q = cmd.filterQuery.trim()
                contacts.mapNotNull { c ->
                    val uKey = "contact:${c.cleanPhone}"
                    val uCount = usageOf(uKey)
                    val fScore = maxOf(FuzzyMatcher.scoreContact(q, c), customAliasScore(uKey, q))
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
                val out = mutableListOf<PaletteItem>()
                installedApps.forEach { app ->
                    val uKey = "app:${app.packageName}"
                    val uCount = usageOf(uKey)
                    val baseScore = if (q.isEmpty()) 200 else maxOf(
                        FuzzyMatcher.scorePreLowered(q, app.labelLower),
                        FuzzyMatcher.scorePreLowered(q, app.pkgLower.substringAfterLast('.')),
                    )
                    val fScore = maxOf(baseScore, customAliasScore(uKey, q))
                    if (fScore > 0) {
                        out.add(PaletteItem.AppAction(app, uKey, fScore * 10 + 15 + usageBonus(uKey), uCount))
                    }
                }
                appShortcuts.forEach { sc ->
                    val uKey = sc.usageKey
                    val uCount = usageOf(uKey)
                    val baseScore = if (q.isEmpty()) {
                        if (uCount > 0 || sc.kind != com.sujan.pipe.model.AppShortcutKind.APP_SYSTEM_INFO) 120 else 0
                    } else {
                        scoreAppShortcut(q, sc)
                    }
                    val fScore = maxOf(baseScore, customAliasScore(uKey, q))
                    if (fScore > 0) {
                        out.add(PaletteItem.AppShortcutAction(sc, uKey, fScore * 10 + usageBonus(uKey), uCount))
                    }
                }
                out.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.FILES_ONLY -> {
                val q = cmd.filterQuery.trim().lowercase()
                storageFiles.mapNotNull { f ->
                    val uKey = "file:${f.absolutePath}"
                    val uCount = usageOf(uKey)
                    val fScore = maxOf(FuzzyMatcher.scoreFileItemPreLowered(q, f), customAliasScore(uKey, q))
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

            PromptIntentMode.FLOCCUS_ONLY -> {
                val q = cmd.filterQuery.trim()
                val qLower = q.lowercase()
                if (floccusBookmarks.isEmpty()) {
                    listOf(
                        PaletteItem.FloccusBookmarkAction(
                            bookmark = null,
                            query = q,
                            isSetupPrompt = true,
                            usageKey = "floccus:setup",
                            rankScore = 100_000,
                        )
                    )
                } else {
                    floccusBookmarks.mapNotNull { fb ->
                        val uKey = fb.usageKey
                        val uCount = usageOf(uKey)
                        val fScore = maxOf(
                            FloccusBookmarkManager.scoreFloccusBookmark(q, fb),
                            customAliasScore(uKey, qLower),
                        )
                        if (fScore > 0) {
                            PaletteItem.FloccusBookmarkAction(
                                bookmark = fb,
                                query = q,
                                isSetupPrompt = false,
                                usageKey = uKey,
                                rankScore = fScore * 10 + usageBonus(uKey),
                                usageCount = uCount,
                            )
                        } else null
                    }.sortedWith(
                        if (q.isEmpty()) compareByDescending<PaletteItem.FloccusBookmarkAction> { it.usageCount }.thenByDescending { it.rankScore }
                        else compareByDescending<PaletteItem.FloccusBookmarkAction> { it.rankScore }.thenByDescending { it.usageCount }
                    ).take(MAX_PALETTE_RESULTS)
                }
            }

            PromptIntentMode.BOOKMARKS_ONLY -> {
                val q = cmd.filterQuery.trim()
                val qLower = q.lowercase()
                val out = mutableListOf<PaletteItem>()
                bookmarks.forEach { bm ->
                    val uKey = "bm:${bm.id}"
                    val uCount = usageOf(uKey)
                    val fScore = maxOf(FuzzyMatcher.scoreBookmark(q, bm), customAliasScore(uKey, qLower))
                    if (fScore > 0) {
                        out.add(PaletteItem.BookmarkAction(bm, uKey, fScore * 10 + 25 + usageBonus(uKey), uCount))
                    }
                }
                if (includeFloccusInUnified && floccusBookmarks.isNotEmpty()) {
                    floccusBookmarks.forEach { fb ->
                        val uKey = fb.usageKey
                        val uCount = usageOf(uKey)
                        val fScore = maxOf(
                            FloccusBookmarkManager.scoreFloccusBookmark(q, fb),
                            customAliasScore(uKey, qLower),
                        )
                        if (fScore > 0) {
                            out.add(
                                PaletteItem.FloccusBookmarkAction(
                                    bookmark = fb,
                                    query = q,
                                    isSetupPrompt = false,
                                    usageKey = uKey,
                                    rankScore = fScore * 10 + usageBonus(uKey),
                                    usageCount = uCount,
                                )
                            )
                        }
                    }
                }
                out.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem> { it.usageCount }.thenByDescending { it.rankScore }
                    else compareByDescending<PaletteItem> { it.rankScore }.thenByDescending { it.usageCount }
                ).take(MAX_PALETTE_RESULTS)
            }

            PromptIntentMode.HISTORY_ONLY -> {
                val q = cmd.filterQuery.trim().lowercase()
                history.mapNotNull { h ->
                    val hKey = "hist:${h.rawInput.lowercase()}"
                    val uCount = usageOf(hKey).coerceAtLeast(1)
                    val fScore = if (q.isEmpty()) 200 else maxOf(
                        FuzzyMatcher.scorePreLowered(q, h.rawInput.lowercase()),
                        FuzzyMatcher.scorePreLowered(q, h.engineName.lowercase()),
                    )
                    if (fScore > 0) {
                        PaletteItem.HistoryAction(h, hKey, fScore * 10 + usageBonus(hKey), uCount)
                    } else null
                }.sortedWith(
                    if (q.isEmpty()) compareByDescending<PaletteItem.HistoryAction> { it.entry.timestampEpochMs }
                    else compareByDescending<PaletteItem.HistoryAction> { it.rankScore }.thenByDescending { it.usageCount }
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
                val isYtPipe = eng.keyword.lowercase() in setOf("yt", "ytt", "ytw", "ytm")
                val relatedWatched = if (isYtPipe && youtubeHistoryMatches.isNotEmpty()) {
                    youtubeHistoryMatches.take(3).mapIndexed { idx, entry ->
                        val uKey = "ytw:${entry.id}"
                        PaletteItem.YouTubeWatchAction(
                            entry = entry,
                            query = q,
                            isSetupPrompt = false,
                            usageKey = uKey,
                            rankScore = 40_000 - idx * 100 + usageBonus(uKey),
                            usageCount = usageOf(uKey).coerceAtLeast(entry.watchCount),
                        )
                    }
                } else emptyList()
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
                    .take((MAX_PALETTE_RESULTS - 1 - relatedWatched.size).coerceAtLeast(0))
                listOf(primary) + relatedWatched + relatedHistory
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
                    if (includeFloccusInUnified) {
                        floccusBookmarks.forEach { fb ->
                            val uKey = fb.usageKey
                            val uCount = usageOf(uKey)
                            if (uCount > 0) {
                                pool.add(
                                    PaletteItem.FloccusBookmarkAction(
                                        bookmark = fb,
                                        usageKey = uKey,
                                        rankScore = 78 + uCount * 180,
                                        usageCount = uCount,
                                    )
                                )
                            }
                        }
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

                    // Feature #3a: Direct URL & IP Auto-Open at #1 when input is a domain, URL, or IP
                    val directUrl = detectDirectUrl(q)
                    if (directUrl != null) {
                        pool.add(
                            PaletteItem.DirectUrlAction(
                                targetUrl = directUrl,
                                displayHost = q,
                                usageKey = "url:$directUrl",
                                rankScore = 95_000,
                                usageCount = usageOf("url:$directUrl"),
                            )
                        )
                    }

                    // Feature #3b: Direct Unsaved Phone Number -> WhatsApp & WhatsApp Business & Dial
                    val directPhone = if (directUrl == null) parseDirectPhoneInput(q) else null
                    if (directPhone != null) {
                        val (rawPhone, msg) = directPhone
                        val normDigits = normalizeWhatsAppPhone(rawPhone)
                        val preferBiz = whatsAppFlavor == WhatsAppFlavor.BUSINESS
                        val primaryWa = if (preferBiz) DirectNumberPrimaryMode.WHATSAPP_BUSINESS else DirectNumberPrimaryMode.WHATSAPP_PERSONAL
                        val secondaryWa = if (preferBiz) DirectNumberPrimaryMode.WHATSAPP_PERSONAL else DirectNumberPrimaryMode.WHATSAPP_BUSINESS
                        pool.add(
                            PaletteItem.DirectNumberAction(
                                rawPhone = rawPhone,
                                normalizedWhatsAppDigits = normDigits,
                                messageText = msg,
                                primaryMode = primaryWa,
                                rankScore = 98_500,
                            )
                        )
                        pool.add(
                            PaletteItem.DirectNumberAction(
                                rawPhone = rawPhone,
                                normalizedWhatsAppDigits = normDigits,
                                messageText = msg,
                                primaryMode = secondaryWa,
                                rankScore = 98_000,
                            )
                        )
                        pool.add(
                            PaletteItem.DirectNumberAction(
                                rawPhone = rawPhone,
                                normalizedWhatsAppDigits = normDigits,
                                messageText = msg,
                                primaryMode = DirectNumberPrimaryMode.CALL,
                                rankScore = 97_500,
                            )
                        )
                    }

                    // Feature #1: Surface matching Android System Settings & Pipe Settings shortcuts (or custom alias match)
                    DefaultSystemShortcuts.LIST.forEach { sc ->
                        val uKey = "sys:${sc.id}"
                        val aliasScore = customAliasScore(uKey, qLower)
                        val s = maxOf(scoreSystemShortcut(qLower, sc), aliasScore)
                        if (s >= 700 || aliasScore > 0) {
                            pool.add(
                                PaletteItem.SystemSettingAction(
                                    shortcut = sc,
                                    usageKey = uKey,
                                    rankScore = s * 10 + 20 + usageBonus(uKey),
                                    usageCount = usageOf(uKey),
                                )
                            )
                        }
                    }

                    bookmarks.forEach { bm ->
                        val uKey = "bm:${bm.id}"
                        val s = maxOf(FuzzyMatcher.scoreBookmark(q, bm), customAliasScore(uKey, qLower))
                        if (s > 0) {
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.BookmarkAction(bm, uKey, s * 10 + 40 + tieBonus, usageOf(uKey)))
                        }
                    }
                    floccusBookmarks.forEach { fb ->
                        val uKey = fb.usageKey
                        val aliasScore = customAliasScore(uKey, qLower)
                        val s = if (includeFloccusInUnified) {
                            maxOf(FloccusBookmarkManager.scoreFloccusBookmark(q, fb), aliasScore)
                        } else {
                            aliasScore
                        }
                        if (s > 0) {
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(
                                PaletteItem.FloccusBookmarkAction(
                                    bookmark = fb,
                                    query = q,
                                    isSetupPrompt = false,
                                    usageKey = uKey,
                                    rankScore = s * 10 + 32 + tieBonus,
                                    usageCount = usageOf(uKey),
                                )
                            )
                        }
                    }
                    engines.forEach { eng ->
                        val uKey = "eng:${eng.id}"
                        val isExactKw = eng.keyword.equals(qLower, ignoreCase = true) || aliasByTargetKey[uKey] == qLower
                        val s = if (isExactKw) 2150 else maxOf(
                            FuzzyMatcher.scorePreLowered(qLower, eng.keyword.lowercase()),
                            FuzzyMatcher.scorePreLowered(qLower, eng.name.lowercase()),
                            customAliasScore(uKey, qLower),
                        )
                        if (s > 0) {
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
                        val uKey = "contact:${c.cleanPhone}"
                        val s = maxOf(FuzzyMatcher.scoreContact(qLower, c), customAliasScore(uKey, qLower))
                        if (s > 0) {
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.ContactAction(c, ContactQuickAction.CALL, uKey, s * 10 + 30 + tieBonus, usageOf(uKey)))
                        }
                    }
                    installedApps.forEach { app ->
                        val uKey = "app:${app.packageName}"
                        val s = maxOf(
                            FuzzyMatcher.scorePreLowered(qLower, app.labelLower),
                            FuzzyMatcher.scorePreLowered(qLower, app.pkgLower.substringAfterLast('.')),
                            customAliasScore(uKey, qLower),
                        )
                        if (s > 0) {
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.AppAction(app, uKey, s * 10 + 25 + tieBonus, usageOf(uKey)))
                        }
                    }
                    appShortcuts.forEach { sc ->
                        val uKey = sc.usageKey
                        val aliasScore = customAliasScore(uKey, qLower)
                        val s = maxOf(scoreAppShortcut(qLower, sc), aliasScore)
                        if (s >= 650 || aliasScore > 0) {
                            val tieBonus = if (s >= 500) usageBonus(uKey) else 0
                            pool.add(PaletteItem.AppShortcutAction(sc, uKey, s * 10 + 18 + tieBonus, usageOf(uKey)))
                        }
                    }
                    storageFiles.forEach { f ->
                        if (f.pathLower.trimEnd('/') !in bookmarkedPaths) {
                            val uKey = "file:${f.absolutePath}"
                            val s = maxOf(FuzzyMatcher.scoreFileItemPreLowered(qLower, f), customAliasScore(uKey, qLower))
                            if (s > 0) {
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

    /**
     * Feature #6: Clipboard Router & Text Tools (`clip` / `txt`).
     */
    private fun buildClipboardAndTextSuggestions(rawSub: String, clipboardText: String): List<PaletteItem> {
        val out = mutableListOf<PaletteItem>()
        val clip = clipboardText.trim()
        val sub = rawSub.trim()
        val effectiveText = sub.ifEmpty { clip }

        if (effectiveText.isEmpty()) {
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.COPY_TEXT,
                    title = "Clipboard is empty",
                    subtitle = "copy a URL, phone number, or text, or type `clip <text>`",
                    payloadText = "",
                    rankScore = 9000,
                )
            )
            return out
        }

        val detectedUrl = detectDirectUrl(effectiveText)
        if (detectedUrl != null) {
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.OPEN_URL,
                    title = "Open URL: $detectedUrl",
                    subtitle = "launch directly in browser",
                    payloadText = detectedUrl,
                    rankScore = 9800,
                )
            )
            val cleanedUrl = stripTrackingParams(detectedUrl)
            if (cleanedUrl != detectedUrl) {
                out.add(
                    PaletteItem.ClipboardAction(
                        kind = ClipActionKind.COPY_CLEAN_URL,
                        title = "Copy Clean URL (strip tracking params)",
                        subtitle = cleanedUrl,
                        payloadText = cleanedUrl,
                        rankScore = 9700,
                    )
                )
            }
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.SAVE_BOOKMARK,
                    title = "Save URL as Bookmark (+b)",
                    subtitle = detectedUrl,
                    payloadText = detectedUrl,
                    rankScore = 9600,
                )
            )
        }

        val digits = effectiveText.filter { it.isDigit() }
        if (digits.length in 7..15 && detectedUrl == null) {
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.CALL_NUMBER,
                    title = "Dial $effectiveText",
                    subtitle = "open phone dialer",
                    payloadText = effectiveText,
                    rankScore = 9750,
                )
            )
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.WA_NUMBER,
                    title = "Message $effectiveText on WhatsApp",
                    subtitle = "WhatsApp (Personal) -> +${normalizeWhatsAppPhone(effectiveText)}",
                    payloadText = effectiveText,
                    rankScore = 9720,
                )
            )
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.WAB_NUMBER,
                    title = "Message $effectiveText on WhatsApp Business",
                    subtitle = "WhatsApp Business -> +${normalizeWhatsAppPhone(effectiveText)}",
                    payloadText = effectiveText,
                    rankScore = 9700,
                )
            )
            out.add(
                PaletteItem.ClipboardAction(
                    kind = ClipActionKind.SAVE_CONTACT,
                    title = "Save $effectiveText as Contact (+c)",
                    subtitle = "open Quick Add Contact form",
                    payloadText = effectiveText,
                    rankScore = 9650,
                )
            )
        }

        val words = effectiveText.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val lines = effectiveText.lines()
        val statsSummary = "${words.size} words · ${effectiveText.length} chars · ${lines.size} lines"
        out.add(
            PaletteItem.ClipboardAction(
                kind = ClipActionKind.COPY_TEXT,
                title = "wc: $statsSummary",
                subtitle = "tap or RET to copy count summary",
                payloadText = statsSummary,
                rankScore = 9400,
            )
        )

        val upper = effectiveText.uppercase()
        out.add(
            PaletteItem.ClipboardAction(
                kind = ClipActionKind.COPY_TEXT,
                title = "UPPERCASE -> ${upper.take(36)}",
                subtitle = "tap to copy uppercase text",
                payloadText = upper,
                rankScore = 9300,
            )
        )

        val lower = effectiveText.lowercase()
        out.add(
            PaletteItem.ClipboardAction(
                kind = ClipActionKind.COPY_TEXT,
                title = "lowercase -> ${lower.take(36)}",
                subtitle = "tap to copy lowercase text",
                payloadText = lower,
                rankScore = 9200,
            )
        )

        val titleCased = words.joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        out.add(
            PaletteItem.ClipboardAction(
                kind = ClipActionKind.COPY_TEXT,
                title = "Title Case -> ${titleCased.take(36)}",
                subtitle = "tap to copy title-cased text",
                payloadText = titleCased,
                rankScore = 9100,
            )
        )

        val urlEncoded = URLEncoder.encode(effectiveText, StandardCharsets.UTF_8.name())
        out.add(
            PaletteItem.ClipboardAction(
                kind = ClipActionKind.COPY_TEXT,
                title = "urlencode -> ${urlEncoded.take(36)}",
                subtitle = "tap to copy URL-encoded string",
                payloadText = urlEncoded,
                rankScore = 9000,
            )
        )

        return out.take(MAX_PALETTE_RESULTS)
    }

    private fun buildQuickAddSuggestions(rawSub: String, clipboardText: String, prefixes: PrefixConfig = PrefixConfig()): List<PaletteItem> {
        val sub = rawSub.trim()
        val bPre = prefixes.bookmarksClean
        val sPre = prefixes.pipesClean
        val cPre = prefixes.contactsClean
        if (sub.isEmpty()) {
            return listOf(
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.CONTACT,
                    title = "Add Contact ($cPre)",
                    subtitle = "tap for form  |  + Name Phone",
                    isReadyToCommit = false,
                ),
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.BOOKMARK,
                    title = "Add Bookmark ($bPre)",
                    subtitle = "tap for form  |  + $bPre <alias> <url/path>",
                    isReadyToCommit = false,
                    arg3 = clipboardText,
                ),
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.PIPE,
                    title = "Add Search Pipe ($sPre)",
                    subtitle = "tap for form  |  + $sPre <keyword> <search-url>",
                    isReadyToCommit = false,
                    arg3 = clipboardText,
                ),
            )
        }

        val subTokens = sub.split(WHITESPACE_REGEX, limit = 2)
        val subType = subTokens[0].lowercase()
        val subArgs = if (subTokens.size > 1) subTokens[1].trim() else ""

        return when (subType) {
            cPre, "c", "contact", "call" -> {
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
                            subtitle = "+ $cPre <Name> <Phone>",
                            isReadyToCommit = false,
                            arg1 = subArgs,
                        )
                    )
                }
            }
            bPre, "b", "bm", "bookmark" -> {
                val parsed = parseQuickAddBookmark(subArgs, clipboardText)
                if (parsed != null) {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.BOOKMARK,
                            title = "Save Bookmark [$bPre ${parsed.first}]: \"${parsed.second}\"",
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
                            subtitle = "+ $bPre <alias> [Title] <url/path>",
                            isReadyToCommit = false,
                            arg1 = subArgs,
                            arg3 = clipboardText,
                        )
                    )
                }
            }
            sPre, "s", "p", "pipe", "eng", "engine" -> {
                val parsed = parseQuickAddPipe(subArgs, clipboardText)
                if (parsed != null) {
                    listOf(
                        PaletteItem.QuickAddAction(
                            kind = QuickAddKind.PIPE,
                            title = "Save Search Pipe [$sPre ${parsed.first}]: \"${parsed.second}\"",
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
                            subtitle = "+ $sPre <keyword> [Name] <search-url>",
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
                            title = "Save Search Pipe [$sPre ${autoPipe.first}]: \"${autoPipe.second}\"",
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
                            title = "Save Bookmark [$bPre ${autoBm.first}]: \"${autoBm.second}\"",
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
                            title = "Save Search Pipe [$sPre ${autoPipe.first}]: \"${autoPipe.second}\"",
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
                            subtitle = "tap to choose Contact ($cPre), Bookmark ($bPre), or Pipe ($sPre)",
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
        prefixes: PrefixConfig = PrefixConfig(),
    ): List<PaletteItem> {
        val sub = rawSub.trim().lowercase()
        val tokens = sub.split(WHITESPACE_REGEX, limit = 2)
        val first = tokens.firstOrNull().orEmpty()
        val bPre = prefixes.bookmarksClean
        val sPre = prefixes.pipesClean
        val onlyBookmarks = first == bPre || first == "b" || first == "bm"
        val onlyPipes = first == sPre || first == "s" || first == "p" || first == "pipe"
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
                        displayTitle = "Delete Bookmark [$bPre ${bm.alias}] ${bm.title}",
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
                        displayTitle = "Delete Search Pipe [$sPre ${eng.keyword}] ${eng.name}",
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
        prefixes: PrefixConfig = PrefixConfig(),
    ): RouteResolution {
        val activeEngines = engines.ifEmpty { DefaultEngines.LIST }
        val defaultEngine = activeEngines.firstOrNull { it.isDefault } ?: activeEngines.first()

        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return RouteResolution(defaultEngine, false, null, "", defaultEngine.homeUrl, true)
        }

        // Direct URL detection in resolve()
        val directUrl = detectDirectUrl(trimmed)
        if (directUrl != null) {
            return RouteResolution(
                engine = defaultEngine,
                matchedByKeyword = true,
                extractedKeyword = "url",
                query = trimmed,
                targetUrl = directUrl,
                isHomeLaunch = false,
            )
        }

        val tokens = trimmed.split(WHITESPACE_REGEX, limit = 2)
        val firstWord = tokens[0]
        val remainder = if (tokens.size > 1) tokens[1].trim().replace(WHITESPACE_REGEX, " ") else ""
        val sPre = prefixes.pipesClean

        if ((firstWord.equals(sPre, ignoreCase = true) || firstWord.equals("s", ignoreCase = true) || firstWord.equals("pipe", ignoreCase = true)) && remainder.isNotEmpty()) {
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

    /**
     * Feature #4: Full v2 JSON Backup Exporter (bundles `engines`, `bookmarks`, `prefixes`, `excludedFolders`, and `usageCounts`).
     */
    fun exportFullBackupToJson(bundle: FullBackupBundle): String {
        val sb = StringBuilder("{\n")
        sb.append("  \"version\": 2,\n")
        sb.append("  \"engines\": [\n")
        bundle.engines.forEachIndexed { idx, e ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${escapeJson(e.id)}\",\n")
            sb.append("      \"name\": \"${escapeJson(e.name)}\",\n")
            sb.append("      \"keyword\": \"${escapeJson(e.keyword.lowercase())}\",\n")
            sb.append("      \"urlTemplate\": \"${escapeJson(e.urlTemplate)}\",\n")
            sb.append("      \"glyph\": \"${escapeJson(e.glyph)}\",\n")
            sb.append("      \"colorRole\": \"${escapeJson(e.colorRole.name)}\",\n")
            sb.append("      \"isDefault\": ${e.isDefault}\n")
            sb.append("    }")
            if (idx < bundle.engines.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"bookmarks\": [\n")
        bundle.bookmarks.forEachIndexed { idx, bm ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${escapeJson(bm.id)}\",\n")
            sb.append("      \"alias\": \"${escapeJson(bm.alias)}\",\n")
            sb.append("      \"title\": \"${escapeJson(bm.title)}\",\n")
            sb.append("      \"target\": \"${escapeJson(bm.target)}\",\n")
            sb.append("      \"kind\": \"${escapeJson(bm.kind.name)}\",\n")
            sb.append("      \"tags\": \"${escapeJson(bm.tags)}\"\n")
            sb.append("    }")
            if (idx < bundle.bookmarks.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")
        val p = bundle.prefixes ?: PrefixConfig()
        sb.append("  \"prefixes\": {\n")
        sb.append("    \"files\": \"${escapeJson(p.filesClean)}\",\n")
        sb.append("    \"pipes\": \"${escapeJson(p.pipesClean)}\",\n")
        sb.append("    \"bookmarks\": \"${escapeJson(p.bookmarksClean)}\",\n")
        sb.append("    \"apps\": \"${escapeJson(p.appsClean)}\",\n")
        sb.append("    \"history\": \"${escapeJson(p.historyClean)}\",\n")
        sb.append("    \"contacts\": \"${escapeJson(p.contactsClean)}\",\n")
        sb.append("    \"dictionary\": \"${escapeJson(p.dictionaryClean)}\",\n")
        sb.append("    \"clipboard\": \"${escapeJson(p.clipboardClean)}\",\n")
        sb.append("    \"systemSettings\": \"${escapeJson(p.systemSettingsClean)}\",\n")
        sb.append("    \"youtubeHistory\": \"${escapeJson(p.youtubeHistoryClean)}\",\n")
        sb.append("    \"floccus\": \"${escapeJson(p.floccusClean)}\"\n")
        sb.append("  },\n")
        sb.append("  \"excludedFolders\": [")
        bundle.excludedFolders.forEachIndexed { idx, f ->
            sb.append("\"${escapeJson(f)}\"")
            if (idx < bundle.excludedFolders.lastIndex) sb.append(", ")
        }
        sb.append("],\n")
        sb.append("  \"usageCounts\": {")
        val usageEntries = bundle.usageCounts.entries.toList()
        if (usageEntries.isNotEmpty()) {
            sb.append("\n")
            usageEntries.forEachIndexed { idx, (k, v) ->
                sb.append("    \"${escapeJson(k)}\": $v")
                if (idx < usageEntries.lastIndex) sb.append(",")
                sb.append("\n")
            }
            sb.append("  ")
        }
        sb.append("},\n")
        sb.append("  \"universalAliases\": [\n")
        bundle.universalAliases.forEachIndexed { idx, ua ->
            sb.append("    {\n")
            sb.append("      \"targetKey\": \"${escapeJson(ua.targetKey)}\",\n")
            sb.append("      \"alias\": \"${escapeJson(ua.aliasLower)}\",\n")
            sb.append("      \"label\": \"${escapeJson(ua.label)}\",\n")
            sb.append("      \"category\": \"${escapeJson(ua.category)}\",\n")
            sb.append("      \"subtitle\": \"${escapeJson(ua.subtitle)}\"\n")
            sb.append("    }")
            if (idx < bundle.universalAliases.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Feature #4: Full v2 JSON Backup Importer (backward-compatible with v1 `[...]` pipe arrays).
     */
    fun importFullBackupFromJson(json: String): FullBackupBundle {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            return FullBackupBundle(engines = importEnginesFromJson(trimmed))
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("! json must be a v2 backup object { ... } or v1 array [ ... ]")
        }

        val enginesSection = extractJsonArraySection(trimmed, "engines")
            ?: throw IllegalArgumentException("! backup json missing \"engines\" array")
        val parsedEngines = importEnginesFromJson(enginesSection)

        val bookmarksSection = extractJsonArraySection(trimmed, "bookmarks")
        val parsedBookmarks = if (bookmarksSection != null) {
            val blocks = extractTopLevelJsonObjects(bookmarksSection)
            blocks.mapIndexedNotNull { idx, block ->
                val title = extractJsonString(block, "title")?.trim().orEmpty()
                val target = extractJsonString(block, "target")?.trim().orEmpty()
                if (title.isEmpty() || target.isEmpty()) null
                else PipeBookmark(
                    id = extractJsonString(block, "id")?.trim()?.ifEmpty { "bm_$idx" } ?: "bm_$idx",
                    alias = extractJsonString(block, "alias")?.trim()?.lowercase().orEmpty(),
                    title = title,
                    target = target,
                    kind = BookmarkKind.fromString(extractJsonString(block, "kind")),
                    tags = extractJsonString(block, "tags")?.trim().orEmpty(),
                )
            }
        } else emptyList()

        val prefixesSection = extractJsonObjectSection(trimmed, "prefixes")
        val parsedPrefixes = if (prefixesSection != null) {
            PrefixConfig(
                files = extractJsonString(prefixesSection, "files") ?: "f",
                pipes = extractJsonString(prefixesSection, "pipes") ?: "s",
                bookmarks = extractJsonString(prefixesSection, "bookmarks") ?: "b",
                apps = extractJsonString(prefixesSection, "apps") ?: "app",
                history = extractJsonString(prefixesSection, "history") ?: "hist",
                contacts = extractJsonString(prefixesSection, "contacts") ?: "c",
                dictionary = extractJsonString(prefixesSection, "dictionary") ?: "def",
                clipboard = extractJsonString(prefixesSection, "clipboard") ?: "clip",
                systemSettings = extractJsonString(prefixesSection, "systemSettings") ?: "set",
                youtubeHistory = extractJsonString(prefixesSection, "youtubeHistory") ?: "yth",
                floccus = extractJsonString(prefixesSection, "floccus") ?: "fl",
            )
        } else null

        val excludedSection = extractJsonArraySection(trimmed, "excludedFolders")
        val parsedExcluded = if (excludedSection != null) {
            Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(excludedSection)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .toList()
        } else emptyList()

        val usageSection = extractJsonObjectSection(trimmed, "usageCounts")
        val parsedUsage = if (usageSection != null) {
            Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*(\\d+)").findAll(usageSection)
                .mapNotNull { m ->
                    val k = m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
                    val v = m.groupValues[2].toIntOrNull()
                    if (k.isNotEmpty() && v != null) k to v else null
                }
                .toMap()
        } else emptyMap()

        val parsedVersion = Regex("\"version\"\\s*:\\s*(\\d+)").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 2

        val aliasesSection = extractJsonArraySection(trimmed, "universalAliases")
        val parsedAliases = if (aliasesSection != null) {
            val blocks = extractTopLevelJsonObjects(aliasesSection)
            blocks.mapNotNull { block ->
                val tKey = extractJsonString(block, "targetKey")?.trim().orEmpty()
                val al = extractJsonString(block, "alias")?.trim()?.lowercase().orEmpty()
                if (tKey.isEmpty() || al.isEmpty()) null
                else UniversalAlias(
                    targetKey = tKey,
                    alias = al,
                    label = extractJsonString(block, "label")?.trim().orEmpty(),
                    category = extractJsonString(block, "category")?.trim() ?: "item",
                    subtitle = extractJsonString(block, "subtitle")?.trim().orEmpty(),
                )
            }
        } else emptyList()

        return FullBackupBundle(
            version = parsedVersion,
            engines = parsedEngines,
            bookmarks = parsedBookmarks,
            prefixes = parsedPrefixes,
            excludedFolders = parsedExcluded,
            usageCounts = parsedUsage,
            universalAliases = parsedAliases,
        )
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
        if (trimmed.startsWith("{")) {
            return importFullBackupFromJson(trimmed).engines
        }
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw IllegalArgumentException("! json must be a top-level array [ ... ] or v2 backup object { ... }")
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

    private fun extractJsonArraySection(rootJson: String, key: String): String? {
        val keyMatch = Regex("\"$key\"\\s*:\\s*\\[").find(rootJson) ?: return null
        val startBracket = keyMatch.range.last
        var inString = false
        var escaped = false
        var depth = 0
        for (i in startBracket until rootJson.length) {
            val ch = rootJson[i]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else {
                when (ch) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return rootJson.substring(startBracket, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractJsonObjectSection(rootJson: String, key: String): String? {
        val keyMatch = Regex("\"$key\"\\s*:\\s*\\{").find(rootJson) ?: return null
        val startBrace = keyMatch.range.last
        var inString = false
        var escaped = false
        var depth = 0
        for (i in startBrace until rootJson.length) {
            val ch = rootJson[i]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return rootJson.substring(startBrace, i + 1)
                    }
                }
            }
        }
        return null
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
