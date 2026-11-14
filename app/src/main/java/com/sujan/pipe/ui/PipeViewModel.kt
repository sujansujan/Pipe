package com.sujan.pipe.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.EngineValidationErrors
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.RouteResolution
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.ascii.PaletteChoice
import com.sujan.pipe.ui.ascii.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PipeView { PROMPT, ENGINES, SETTINGS }

sealed interface UndoPayload {
    data class DeletedEngine(val engine: SearchEngine, val index: Int) : UndoPayload
    data class ResetEngines(val previousEngines: List<SearchEngine>) : UndoPayload
    data class DeletedHistoryItem(val entry: SearchHistoryEntry, val index: Int) : UndoPayload
    data class ClearedHistory(val previousEntries: List<SearchHistoryEntry>) : UndoPayload
}

data class UndoBannerState(val message: String, val payload: UndoPayload)

data class EngineFormState(
    val isOpen: Boolean = false,
    val editingId: String? = null,
    val name: TextFieldValue = TextFieldValue(""),
    val keyword: TextFieldValue = TextFieldValue(""),
    val urlTemplate: TextFieldValue = TextFieldValue("https://"),
    val glyph: String = "|",
    val colorRole: EngineColorRole = EngineColorRole.ACCENT2,
    val isDefault: Boolean = false,
    val errors: EngineValidationErrors = EngineValidationErrors(),
)

enum class JsonDialogMode { EXPORT, IMPORT }

data class PipeUiState(
    val activeView: PipeView = PipeView.PROMPT,
    val searchInput: TextFieldValue = TextFieldValue(""),
    val engines: List<SearchEngine> = DefaultEngines.LIST,
    val history: List<SearchHistoryEntry> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.System,
    val paletteChoice: PaletteChoice = PaletteChoice.MOSS,
    val historyEnabled: Boolean = true,
    val externalTextMode: ExternalTextMode = ExternalTextMode.OPEN_PROMPT,
    val engineForm: EngineFormState = EngineFormState(),
    val undoBanner: UndoBannerState? = null,
    val launchError: String? = null,
    val jsonDialogMode: JsonDialogMode? = null,
    val jsonStatusMessage: String? = null,
) {
    val liveResolution: RouteResolution
        get() = QueryRouter.resolve(searchInput.text, engines)

    val suggestedEngines: List<SearchEngine>
        get() = QueryRouter.suggestEngines(searchInput.text, engines)
}

class PipeViewModel(private val repository: PipeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PipeUiState(
            engines = repository.loadEngines(),
            history = repository.loadHistory(),
            themeMode = repository.loadThemeMode(),
            paletteChoice = repository.loadPaletteChoice(),
            historyEnabled = repository.isHistoryEnabled(),
            externalTextMode = repository.loadExternalTextMode(),
        )
    )
    val uiState: StateFlow<PipeUiState> = _uiState.asStateFlow()
    private var undoJob: Job? = null

    fun navigateTo(view: PipeView) {
        _uiState.update { it.copy(activeView = view, launchError = null, jsonStatusMessage = null) }
    }

    fun updateSearchInput(newValue: TextFieldValue) {
        _uiState.update { it.copy(searchInput = newValue, launchError = null) }
    }

    fun setSearchTextFromExternal(text: String) {
        _uiState.update {
            it.copy(
                activeView = PipeView.PROMPT,
                searchInput = TextFieldValue(text, TextRange(text.length)),
                launchError = null,
            )
        }
    }

    fun applyKeywordChip(engine: SearchEngine) {
        val current = _uiState.value.searchInput.text
        val trimmedStart = current.trimStart()
        val tokens = trimmedStart.split(Regex("\\s+"), limit = 2)
        val existingFirst = tokens.firstOrNull().orEmpty()
        val remainder = if (tokens.size > 1) tokens[1] else ""
        val firstMatchesAnyEngine = _uiState.value.engines.any { it.keyword.equals(existingFirst, ignoreCase = true) }

        val updatedText = when {
            trimmedStart.isEmpty() -> "${engine.keyword} "
            firstMatchesAnyEngine && remainder.isNotEmpty() -> "${engine.keyword} $remainder"
            !trimmedStart.contains(' ') -> "${engine.keyword} "
            else -> "${engine.keyword} $trimmedStart"
        }
        _uiState.update {
            it.copy(
                searchInput = TextFieldValue(updatedText, TextRange(updatedText.length)),
                launchError = null,
            )
        }
    }

    fun submitSearch(overrideInput: String? = null, onOpenUrl: (String) -> Boolean) {
        val state = _uiState.value
        val raw = overrideInput ?: state.searchInput.text
        val resolution = QueryRouter.resolve(raw, state.engines)
        if (!onOpenUrl(resolution.targetUrl)) {
            _uiState.update { it.copy(launchError = "! could not pipe to ${resolution.targetUrl}") }
            return
        }
        if (state.historyEnabled && raw.trim().isNotEmpty()) {
            val entry = SearchHistoryEntry(
                id = System.currentTimeMillis(),
                rawInput = raw.trim(),
                engineName = resolution.engine.name,
                engineKeyword = resolution.engine.keyword,
                queryText = if (resolution.isHomeLaunch) "(home)" else resolution.query,
                resolvedUrl = resolution.targetUrl,
                timestampEpochMs = System.currentTimeMillis(),
            )
            val deduped = state.history.filterNot { it.rawInput.equals(entry.rawInput, ignoreCase = true) }
            val updated = listOf(entry) + deduped.take(49)
            repository.saveHistory(updated)
            _uiState.update { it.copy(history = updated, launchError = null) }
        } else {
            _uiState.update { it.copy(launchError = null) }
        }
    }

    fun deleteHistoryItem(entry: SearchHistoryEntry) {
        val current = _uiState.value.history
        val idx = current.indexOfFirst { it.id == entry.id }
        if (idx < 0) return
        val updated = current.toMutableList().apply { removeAt(idx) }
        repository.saveHistory(updated)
        _uiState.update { it.copy(history = updated) }
        postUndo("deleted \"${entry.rawInput.take(18)}\"", UndoPayload.DeletedHistoryItem(entry, idx))
    }

    fun clearAllHistory() {
        val prev = _uiState.value.history
        if (prev.isEmpty()) return
        repository.saveHistory(emptyList())
        _uiState.update { it.copy(history = emptyList()) }
        postUndo("cleared ${prev.size} history items", UndoPayload.ClearedHistory(prev))
    }

    fun openAddEngineForm() {
        _uiState.update { it.copy(engineForm = EngineFormState(isOpen = true)) }
    }

    fun openEditEngineForm(e: SearchEngine) {
        _uiState.update {
            it.copy(
                engineForm = EngineFormState(
                    isOpen = true,
                    editingId = e.id,
                    name = TextFieldValue(e.name, TextRange(e.name.length)),
                    keyword = TextFieldValue(e.keyword, TextRange(e.keyword.length)),
                    urlTemplate = TextFieldValue(e.urlTemplate, TextRange(e.urlTemplate.length)),
                    glyph = e.glyph,
                    colorRole = e.colorRole,
                    isDefault = e.isDefault,
                )
            )
        }
    }

    fun closeEngineForm() = _uiState.update { it.copy(engineForm = EngineFormState(isOpen = false)) }

    fun updateFormName(v: TextFieldValue) = _uiState.update { s ->
        s.copy(engineForm = revalidate(s.engineForm.copy(name = v), s.engines))
    }

    fun updateFormKeyword(v: TextFieldValue) = _uiState.update { s ->
        s.copy(engineForm = revalidate(s.engineForm.copy(keyword = v), s.engines))
    }

    fun updateFormUrlTemplate(v: TextFieldValue) = _uiState.update { s ->
        s.copy(engineForm = revalidate(s.engineForm.copy(urlTemplate = v), s.engines))
    }

    fun updateFormColorRole(r: EngineColorRole) = _uiState.update { s ->
        s.copy(engineForm = s.engineForm.copy(colorRole = r))
    }

    fun updateFormDefault(d: Boolean) = _uiState.update { s ->
        s.copy(engineForm = s.engineForm.copy(isDefault = d))
    }

    private fun revalidate(form: EngineFormState, engines: List<SearchEngine>): EngineFormState =
        form.copy(
            errors = QueryRouter.validateEngine(
                name = form.name.text,
                keyword = form.keyword.text,
                urlTemplate = form.urlTemplate.text,
                existingEngines = engines,
                editingEngineId = form.editingId,
            )
        )

    fun saveEngineForm() {
        val s = _uiState.value
        val validated = revalidate(s.engineForm, s.engines)
        if (!validated.errors.isValid) {
            _uiState.update { it.copy(engineForm = validated) }
            return
        }
        val cleanName = validated.name.text.trim()
        val cleanKey = validated.keyword.text.trim().lowercase()
        val cleanUrl = validated.urlTemplate.text.trim()

        val updated = if (validated.editingId != null) {
            s.engines.map { existing ->
                if (existing.id == validated.editingId) {
                    existing.copy(
                        name = cleanName, keyword = cleanKey, urlTemplate = cleanUrl,
                        colorRole = validated.colorRole, isDefault = validated.isDefault || existing.isDefault,
                    )
                } else if (validated.isDefault) existing.copy(isDefault = false) else existing
            }
        } else {
            val created = SearchEngine(
                id = "eng_${cleanKey}_${System.currentTimeMillis()}",
                name = cleanName, keyword = cleanKey, urlTemplate = cleanUrl,
                colorRole = validated.colorRole, isDefault = validated.isDefault,
            )
            val base = if (validated.isDefault) s.engines.map { it.copy(isDefault = false) } else s.engines
            base + created
        }
        repository.saveEngines(updated)
        _uiState.update { it.copy(engines = repository.loadEngines(), engineForm = EngineFormState(isOpen = false)) }
    }

    fun setDefaultEngine(id: String) {
        val updated = _uiState.value.engines.map { it.copy(isDefault = it.id == id) }
        repository.saveEngines(updated)
        _uiState.update { it.copy(engines = updated) }
    }

    fun moveEngine(index: Int, delta: Int) {
        val list = _uiState.value.engines.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        repository.saveEngines(list)
        _uiState.update { it.copy(engines = list) }
    }

    fun deleteEngine(engine: SearchEngine) {
        val current = _uiState.value.engines
        if (current.size <= 1) return
        val idx = current.indexOfFirst { it.id == engine.id }
        if (idx < 0) return
        val rem = current.toMutableList().apply { removeAt(idx) }
        if (engine.isDefault && rem.isNotEmpty()) rem[0] = rem[0].copy(isDefault = true)
        repository.saveEngines(rem)
        _uiState.update { it.copy(engines = rem) }
        postUndo("deleted |${engine.keyword}", UndoPayload.DeletedEngine(engine, idx))
    }

    fun resetEnginesToDefault() {
        val prev = _uiState.value.engines
        repository.saveEngines(DefaultEngines.LIST)
        _uiState.update { it.copy(engines = DefaultEngines.LIST, jsonStatusMessage = "ok: reset to 7 default pipes") }
        postUndo("reset engines to defaults", UndoPayload.ResetEngines(prev))
    }

    fun setThemeMode(m: ThemeMode) {
        repository.saveThemeMode(m)
        _uiState.update { it.copy(themeMode = m) }
    }

    fun setPaletteChoice(c: PaletteChoice) {
        repository.savePaletteChoice(c)
        _uiState.update { it.copy(paletteChoice = c) }
    }

    fun setHistoryEnabled(e: Boolean) {
        repository.setHistoryEnabled(e)
        _uiState.update { it.copy(historyEnabled = e) }
    }

    fun setExternalTextMode(m: ExternalTextMode) {
        repository.saveExternalTextMode(m)
        _uiState.update { it.copy(externalTextMode = m) }
    }

    fun openJsonDialog(m: JsonDialogMode) = _uiState.update { it.copy(jsonDialogMode = m, jsonStatusMessage = null) }
    fun closeJsonDialog() = _uiState.update { it.copy(jsonDialogMode = null) }
    fun exportJsonString(): String = QueryRouter.exportEnginesToJson(_uiState.value.engines)

    fun importJsonString(rawJson: String): String? = try {
        val imported = QueryRouter.importEnginesFromJson(rawJson)
        repository.saveEngines(imported)
        _uiState.update {
            it.copy(engines = imported, jsonDialogMode = null, jsonStatusMessage = "ok: imported ${imported.size} pipes")
        }
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "! invalid json"
    }

    fun setJsonStatusMessage(msg: String) = _uiState.update { it.copy(jsonStatusMessage = msg) }

    private fun postUndo(msg: String, payload: UndoPayload) {
        undoJob?.cancel()
        _uiState.update { it.copy(undoBanner = UndoBannerState(msg, payload)) }
        undoJob = viewModelScope.launch {
            delay(6_000)
            _uiState.update { it.copy(undoBanner = null) }
        }
    }

    fun performUndo() {
        val banner = _uiState.value.undoBanner ?: return
        undoJob?.cancel()
        when (val p = banner.payload) {
            is UndoPayload.DeletedEngine -> {
                val base = if (p.engine.isDefault) {
                    _uiState.value.engines.map { it.copy(isDefault = false) }.toMutableList()
                } else {
                    _uiState.value.engines.toMutableList()
                }
                base.add(p.index.coerceIn(0, base.size), p.engine)
                repository.saveEngines(base)
                _uiState.update { it.copy(engines = base, undoBanner = null) }
            }
            is UndoPayload.ResetEngines -> {
                repository.saveEngines(p.previousEngines)
                _uiState.update { it.copy(engines = p.previousEngines, undoBanner = null) }
            }
            is UndoPayload.DeletedHistoryItem -> {
                val list = _uiState.value.history.toMutableList()
                list.add(p.index.coerceIn(0, list.size), p.entry)
                repository.saveHistory(list)
                _uiState.update { it.copy(history = list, undoBanner = null) }
            }
            is UndoPayload.ClearedHistory -> {
                repository.saveHistory(p.previousEntries)
                _uiState.update { it.copy(history = p.previousEntries, undoBanner = null) }
            }
        }
    }

    fun dismissUndo() {
        undoJob?.cancel()
        _uiState.update { it.copy(undoBanner = null) }
    }

    class Factory(private val repository: PipeRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PipeViewModel(repository) as T
    }
}
