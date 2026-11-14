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

    fun loadExternalTextMode(): ExternalTextMode =
        ExternalTextMode.entries.firstOrNull { it.name == prefs.getString("ext_mode", ExternalTextMode.OPEN_OVERLAY.name) }
            ?: ExternalTextMode.OPEN_OVERLAY

    fun saveExternalTextMode(mode: ExternalTextMode) = prefs.edit().putString("ext_mode", mode.name).apply()
}
