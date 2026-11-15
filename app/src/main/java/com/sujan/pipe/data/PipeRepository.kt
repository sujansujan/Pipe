package com.sujan.pipe.data

import android.content.Context
import android.content.SharedPreferences
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.RouteResolution
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.ascii.PaletteChoice
import com.sujan.pipe.ui.ascii.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

enum class ExternalTextMode { OPEN_OVERLAY, OPEN_PROMPT, DIRECT_PIPE }

class PipeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pipe_ascii_prefs", Context.MODE_PRIVATE)

    fun loadEngines(): List<SearchEngine> {
        val raw = prefs.getString("engines_json", null) ?: return DefaultEngines.LIST
        return try { QueryRouter.importEnginesFromJson(raw) } catch (_: Exception) { DefaultEngines.LIST }
    }

    fun saveEngines(engines: List<SearchEngine>) {
        val idx = engines.indexOfFirst { it.isDefault }.let { if (it >= 0) it else 0 }
        val normalized = engines.ifEmpty { DefaultEngines.LIST }.mapIndexed { i, e -> e.copy(isDefault = i == idx) }
        prefs.edit().putString("engines_json", QueryRouter.exportEnginesToJson(normalized)).apply()
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

    fun loadOverlayOpacity(): Float = prefs.getFloat("overlay_opacity_v1", 0.82f).coerceIn(0.15f, 1.0f)
    fun saveOverlayOpacity(alpha: Float) = prefs.edit().putFloat("overlay_opacity_v1", alpha.coerceIn(0.15f, 1.0f)).apply()

    fun loadOverlayDimAmount(): Float = prefs.getFloat("overlay_dim_v1", 0.35f).coerceIn(0.0f, 0.85f)
    fun saveOverlayDimAmount(dim: Float) = prefs.edit().putFloat("overlay_dim_v1", dim.coerceIn(0.0f, 0.85f)).apply()

    fun loadOverlayCornerRadiusDp(): Int = prefs.getInt("overlay_corner_dp_v1", 16).coerceIn(0, 28)
    fun saveOverlayCornerRadiusDp(dp: Int) = prefs.edit().putInt("overlay_corner_dp_v1", dp.coerceIn(0, 28)).apply()

    fun loadOverlayBottomAnchored(): Boolean = prefs.getBoolean("overlay_bottom_v1", false)
    fun saveOverlayBottomAnchored(bottom: Boolean) = prefs.edit().putBoolean("overlay_bottom_v1", bottom).apply()

    fun loadOverlayForceDark(): Boolean = prefs.getBoolean("overlay_force_dark_v1", true)
    fun saveOverlayForceDark(forceDark: Boolean) = prefs.edit().putBoolean("overlay_force_dark_v1", forceDark).apply()

    fun loadOverlayShowIdleSuggestions(): Boolean = prefs.getBoolean("overlay_idle_suggestions_v1", false)
    fun saveOverlayShowIdleSuggestions(show: Boolean) = prefs.edit().putBoolean("overlay_idle_suggestions_v1", show).apply()

    fun loadExternalTextMode(): ExternalTextMode =
        ExternalTextMode.entries.firstOrNull { it.name == prefs.getString("ext_mode", ExternalTextMode.OPEN_OVERLAY.name) }
            ?: ExternalTextMode.OPEN_OVERLAY

    fun saveExternalTextMode(mode: ExternalTextMode) = prefs.edit().putString("ext_mode", mode.name).apply()
}
