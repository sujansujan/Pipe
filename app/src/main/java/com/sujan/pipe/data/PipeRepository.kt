package com.sujan.pipe.data

import android.content.Context
import android.content.SharedPreferences
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.RouteResolution
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.FullBackupBundle
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.PrefixConfig
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.model.WhatsAppFlavor
import com.sujan.pipe.ui.ascii.PaletteChoice
import com.sujan.pipe.ui.ascii.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

enum class ExternalTextMode { OPEN_OVERLAY, OPEN_PROMPT, DIRECT_PIPE }

class PipeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pipe_ascii_prefs", Context.MODE_PRIVATE)

    fun loadEngines(): List<SearchEngine> {
        val raw = prefs.getString("engines_json", null)
        if (raw == null) {
            prefs.edit().putBoolean("seeded_yt_time_pipes_v1", true).apply()
            return DefaultEngines.LIST
        }
        val loaded = try {
            QueryRouter.importEnginesFromJson(raw)
        } catch (_: Exception) {
            DefaultEngines.LIST
        }
        if (!prefs.getBoolean("seeded_yt_time_pipes_v1", false)) {
            val existingKeywords = loaded.map { it.keyword.lowercase() }.toSet()
            val missingYt = DefaultEngines.YT_TIME_PIPES.filter { it.keyword.lowercase() !in existingKeywords }
            val merged = if (missingYt.isNotEmpty()) {
                val ytIndex = loaded.indexOfFirst { it.keyword.equals("yt", ignoreCase = true) }
                if (ytIndex >= 0) {
                    loaded.subList(0, ytIndex + 1) + missingYt + loaded.subList(ytIndex + 1, loaded.size)
                } else {
                    loaded + missingYt
                }
            } else {
                loaded
            }
            prefs.edit().putBoolean("seeded_yt_time_pipes_v1", true).apply()
            saveEngines(merged)
            return merged
        }
        return loaded
    }

    fun saveEngines(engines: List<SearchEngine>) {
        val idx = engines.indexOfFirst { it.isDefault }.let { if (it >= 0) it else 0 }
        val normalized = engines.ifEmpty { DefaultEngines.LIST }.mapIndexed { i, e -> e.copy(isDefault = i == idx) }
        prefs.edit().putString("engines_json", QueryRouter.exportEnginesToJson(normalized)).apply()
    }

    fun loadPrefixConfig(): PrefixConfig {
        return PrefixConfig(
            files = prefs.getString("prefix_files_v1", "f")?.trim()?.ifEmpty { "f" } ?: "f",
            pipes = prefs.getString("prefix_pipes_v1", "s")?.trim()?.ifEmpty { "s" } ?: "s",
            bookmarks = prefs.getString("prefix_bookmarks_v1", "b")?.trim()?.ifEmpty { "b" } ?: "b",
            apps = prefs.getString("prefix_apps_v1", "app")?.trim()?.ifEmpty { "app" } ?: "app",
            history = prefs.getString("prefix_history_v1", "hist")?.trim()?.ifEmpty { "hist" } ?: "hist",
            contacts = prefs.getString("prefix_contacts_v1", "c")?.trim()?.ifEmpty { "c" } ?: "c",
            dictionary = prefs.getString("prefix_dictionary_v1", "def")?.trim()?.ifEmpty { "def" } ?: "def",
            clipboard = prefs.getString("prefix_clipboard_v1", "clip")?.trim()?.ifEmpty { "clip" } ?: "clip",
            systemSettings = prefs.getString("prefix_system_settings_v1", "set")?.trim()?.ifEmpty { "set" } ?: "set",
        )
    }

    fun savePrefixConfig(config: PrefixConfig) {
        prefs.edit()
            .putString("prefix_files_v1", config.filesClean)
            .putString("prefix_pipes_v1", config.pipesClean)
            .putString("prefix_bookmarks_v1", config.bookmarksClean)
            .putString("prefix_apps_v1", config.appsClean)
            .putString("prefix_history_v1", config.historyClean)
            .putString("prefix_contacts_v1", config.contactsClean)
            .putString("prefix_dictionary_v1", config.dictionaryClean)
            .putString("prefix_clipboard_v1", config.clipboardClean)
            .putString("prefix_system_settings_v1", config.systemSettingsClean)
            .apply()
    }

    fun loadWhatsAppFlavor(): WhatsAppFlavor =
        WhatsAppFlavor.fromString(prefs.getString("whatsapp_flavor_v1", WhatsAppFlavor.BOTH.name))

    fun saveWhatsAppFlavor(flavor: WhatsAppFlavor) {
        prefs.edit().putString("whatsapp_flavor_v1", flavor.name).apply()
    }

    fun loadDictionaryWordCount(): Int = prefs.getInt("dict_word_count_v1", 0)
    fun loadDictionarySourceName(): String = prefs.getString("dict_source_name_v1", "not linked") ?: "not linked"

    fun saveDictionaryMetadata(wordCount: Int, sourceName: String) {
        prefs.edit()
            .putInt("dict_word_count_v1", wordCount)
            .putString("dict_source_name_v1", sourceName)
            .apply()
    }

    fun buildFullBackupBundle(): FullBackupBundle = FullBackupBundle(
        version = 2,
        engines = loadEngines(),
        bookmarks = loadBookmarks(),
        prefixes = loadPrefixConfig(),
        excludedFolders = loadExcludedFolders(),
        usageCounts = loadUsageCounts(),
    )

    fun applyFullBackupBundle(bundle: FullBackupBundle) {
        saveEngines(bundle.engines)
        if (bundle.bookmarks.isNotEmpty()) {
            saveBookmarks(bundle.bookmarks)
        }
        if (bundle.prefixes != null) {
            savePrefixConfig(bundle.prefixes)
        }
        if (bundle.excludedFolders.isNotEmpty()) {
            saveExcludedFolders(bundle.excludedFolders)
        }
        if (bundle.usageCounts.isNotEmpty()) {
            val obj = JSONObject()
            bundle.usageCounts.forEach { (k, v) -> obj.put(k, v) }
            prefs.edit().putString("usage_counts_json_v1", obj.toString()).apply()
        }
    }

    fun loadBookmarks(): List<PipeBookmark> {
        val raw = prefs.getString("bookmarks_json_v1", null) ?: return DefaultBookmarks.LIST
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PipeBookmark(
                            id = o.optString("id", "bm_$i"),
                            alias = o.optString("alias", ""),
                            title = o.optString("title", ""),
                            target = o.optString("target", ""),
                            kind = BookmarkKind.fromString(o.optString("kind", "LINK")),
                            tags = o.optString("tags", ""),
                        )
                    )
                }
            }.ifEmpty { DefaultBookmarks.LIST }
        } catch (_: Exception) {
            DefaultBookmarks.LIST
        }
    }

    fun saveBookmarks(bookmarks: List<PipeBookmark>) {
        val arr = JSONArray()
        bookmarks.forEach { bm ->
            arr.put(
                JSONObject().apply {
                    put("id", bm.id)
                    put("alias", bm.alias.trim().lowercase())
                    put("title", bm.title.trim())
                    put("target", bm.target.trim())
                    put("kind", bm.kind.name)
                    put("tags", bm.tags.trim())
                }
            )
        }
        prefs.edit().putString("bookmarks_json_v1", arr.toString()).apply()
    }

    fun loadHistory(): List<SearchHistoryEntry> {
        val raw = prefs.getString("history_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SearchHistoryEntry(
                            id = o.optLong("id", i.toLong()),
                            rawInput = o.optString("rawInput", ""),
                            engineName = o.optString("engineName", ""),
                            engineKeyword = o.optString("engineKeyword", ""),
                            queryText = o.optString("queryText", ""),
                            resolvedUrl = o.optString("resolvedUrl", ""),
                            timestampEpochMs = o.optLong("timestampEpochMs", 0L),
                        )
                    )
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveHistory(entries: List<SearchHistoryEntry>) {
        val arr = JSONArray()
        entries.take(50).forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("rawInput", item.rawInput)
                    put("engineName", item.engineName)
                    put("engineKeyword", item.engineKeyword)
                    put("queryText", item.queryText)
                    put("resolvedUrl", item.resolvedUrl)
                    put("timestampEpochMs", item.timestampEpochMs)
                }
            )
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    fun recordResolutionInHistory(rawInput: String, resolution: RouteResolution) {
        val trimmed = rawInput.trim()
        if (!isHistoryEnabled() || trimmed.isEmpty()) return
        val current = loadHistory()
        val entry = SearchHistoryEntry(
            id = System.currentTimeMillis(),
            rawInput = trimmed,
            engineName = resolution.engine.name,
            engineKeyword = resolution.engine.keyword,
            queryText = if (resolution.isHomeLaunch) "(home)" else resolution.query,
            resolvedUrl = resolution.targetUrl,
            timestampEpochMs = System.currentTimeMillis(),
        )
        val deduped = current.filterNot { it.rawInput.equals(trimmed, ignoreCase = true) }
        saveHistory(listOf(entry) + deduped.take(49))
    }

    fun loadUsageCounts(): Map<String, Int> {
        val raw = prefs.getString("usage_counts_json_v1", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optInt(k, 0)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun incrementUsage(usageKey: String): Map<String, Int> {
        if (usageKey.isBlank()) return loadUsageCounts()
        val current = loadUsageCounts().toMutableMap()
        current[usageKey] = (current[usageKey] ?: 0) + 1
        val obj = JSONObject()
        current.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("usage_counts_json_v1", obj.toString()).apply()
        return current
    }

    fun loadThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme_mode", ThemeMode.System.name) }
            ?: ThemeMode.System

    fun saveThemeMode(mode: ThemeMode) = prefs.edit().putString("theme_mode", mode.name).apply()

    fun loadPaletteChoice(): PaletteChoice =
        PaletteChoice.entries.firstOrNull { it.name == prefs.getString("palette_choice", PaletteChoice.MOSS.name) }
            ?: PaletteChoice.MOSS

    fun savePaletteChoice(choice: PaletteChoice) = prefs.edit().putString("palette_choice", choice.name).apply()

    fun isHistoryEnabled(): Boolean = prefs.getBoolean("history_enabled", true)
    fun setHistoryEnabled(enabled: Boolean) = prefs.edit().putBoolean("history_enabled", enabled).apply()

    fun loadHourlyAutoIndexEnabled(): Boolean = prefs.getBoolean("hourly_auto_index_v1", true)
    fun saveHourlyAutoIndexEnabled(enabled: Boolean) = prefs.edit().putBoolean("hourly_auto_index_v1", enabled).apply()

    fun loadLastIndexedTimestampMs(): Long = prefs.getLong("last_indexed_ms_v1", 0L)
    fun loadIndexedFileCount(): Int = prefs.getInt("indexed_file_count_v1", 0)
    fun loadIndexedFolderCount(): Int = prefs.getInt("indexed_folder_count_v1", 0)

    fun saveLastIndexedMetadata(timestampMs: Long, fileCount: Int, folderCount: Int) {
        prefs.edit()
            .putLong("last_indexed_ms_v1", timestampMs)
            .putInt("indexed_file_count_v1", fileCount)
            .putInt("indexed_folder_count_v1", folderCount)
            .apply()
    }

    fun loadOverlayOpacity(): Float = prefs.getFloat("overlay_opacity_v1", 0.88f).coerceIn(0.15f, 1.0f)
    fun saveOverlayOpacity(alpha: Float) = prefs.edit().putFloat("overlay_opacity_v1", alpha.coerceIn(0.15f, 1.0f)).apply()

    fun loadOverlayDimAmount(): Float = prefs.getFloat("overlay_dim_v1", 0.35f).coerceIn(0.0f, 0.85f)
    fun saveOverlayDimAmount(dim: Float) = prefs.edit().putFloat("overlay_dim_v1", dim.coerceIn(0.0f, 0.85f)).apply()

    fun loadOverlayCornerRadiusDp(): Int = prefs.getInt("overlay_corner_dp_v2", 10).coerceIn(0, 28)
    fun saveOverlayCornerRadiusDp(dp: Int) = prefs.edit().putInt("overlay_corner_dp_v2", dp.coerceIn(0, 28)).apply()

    fun loadOverlayBottomAnchored(): Boolean = prefs.getBoolean("overlay_bottom_v2", true)
    fun saveOverlayBottomAnchored(bottom: Boolean) = prefs.edit().putBoolean("overlay_bottom_v2", bottom).apply()

    fun loadOverlayForceDark(): Boolean = prefs.getBoolean("overlay_force_dark_v2", false)
    fun saveOverlayForceDark(forceDark: Boolean) = prefs.edit().putBoolean("overlay_force_dark_v2", forceDark).apply()

    fun loadOverlayShowIdleSuggestions(): Boolean = prefs.getBoolean("overlay_idle_suggestions_v1", false)
    fun saveOverlayShowIdleSuggestions(show: Boolean) = prefs.edit().putBoolean("overlay_idle_suggestions_v1", show).apply()

    fun loadPreferredBrowserPackage(): String = prefs.getString("preferred_browser_pkg_v1", "auto") ?: "auto"
    fun savePreferredBrowserPackage(pkg: String) = prefs.edit().putString("preferred_browser_pkg_v1", pkg).apply()

    fun loadExcludedFolders(): List<String> {
        val raw = prefs.getString("excluded_folders_v1", ".Statuses,.thumbnails,Telegram Audio,WhatsApp Voice Notes") ?: ""
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveExcludedFolders(folders: List<String>) {
        prefs.edit().putString("excluded_folders_v1", folders.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")).apply()
    }

    fun loadSearchBoxPrefix(): String = prefs.getString("search_box_prefix_v1", "> ") ?: "> "
    fun saveSearchBoxPrefix(prefix: String) = prefs.edit().putString("search_box_prefix_v1", prefix).apply()

    fun loadSearchBoxPlaceholder(): String = prefs.getString("search_box_placeholder_v2", "pipe for???") ?: "pipe for???"
    fun saveSearchBoxPlaceholder(hint: String) = prefs.edit().putString("search_box_placeholder_v2", hint).apply()

    fun loadShowSearchBoxScopeBadge(): Boolean = prefs.getBoolean("search_box_scope_badge_v1", true)
    fun saveShowSearchBoxScopeBadge(show: Boolean) = prefs.edit().putBoolean("search_box_scope_badge_v1", show).apply()

    fun loadShowSearchBoxMenuBtn(): Boolean = prefs.getBoolean("search_box_menu_btn_v1", true)
    fun saveShowSearchBoxMenuBtn(show: Boolean) = prefs.edit().putBoolean("search_box_menu_btn_v1", show).apply()

    fun loadExternalTextMode(): ExternalTextMode =
        ExternalTextMode.entries.firstOrNull { it.name == prefs.getString("ext_mode", ExternalTextMode.OPEN_OVERLAY.name) }
            ?: ExternalTextMode.OPEN_OVERLAY

    fun saveExternalTextMode(mode: ExternalTextMode) = prefs.edit().putString("ext_mode", mode.name).apply()
}
