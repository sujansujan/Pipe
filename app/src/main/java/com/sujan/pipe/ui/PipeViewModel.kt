package com.sujan.pipe.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.EngineValidationErrors
import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.domain.ParsedPromptCommand
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.RouteResolution
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
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

enum class PipeView { PROMPT, BOOKMARKS, ENGINES, SETTINGS }

sealed interface UndoPayload {
    data class DeletedEngine(val engine: SearchEngine, val index: Int) : UndoPayload
    data class ResetEngines(val previousEngines: List<SearchEngine>) : UndoPayload
    data class DeletedBookmark(val bookmark: PipeBookmark, val index: Int) : UndoPayload
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

data class BookmarkFormState(
    val isOpen: Boolean = false,
    val editingId: String? = null,
    val alias: TextFieldValue = TextFieldValue(""),
    val title: TextFieldValue = TextFieldValue(""),
    val target: TextFieldValue = TextFieldValue("/storage/emulated/0/"),
    val kind: BookmarkKind = BookmarkKind.FOLDER,
    val errorText: String? = null,
)

enum class JsonDialogMode { EXPORT, IMPORT }

data class PipeUiState(
    val activeView: PipeView = PipeView.PROMPT,
    val searchInput: TextFieldValue = TextFieldValue(""),
    val engines: List<SearchEngine> = DefaultEngines.LIST,
    val bookmarks: List<PipeBookmark> = DefaultBookmarks.LIST,
    val contacts: List<ContactItem> = emptyList(),
    val storageFiles: List<LocalFileItem> = emptyList(),
    val scopedFolderFiles: List<LocalFileItem> = emptyList(),
    val hasContactsPermission: Boolean = false,
    val hasFilesPermission: Boolean = false,
    val history: List<SearchHistoryEntry> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.System,
    val paletteChoice: PaletteChoice = PaletteChoice.MOSS,
    val historyEnabled: Boolean = true,
    val externalTextMode: ExternalTextMode = ExternalTextMode.OPEN_OVERLAY,
    val overlayOpacity: Float = 0.82f,
    val overlayDimAmount: Float = 0.35f,
    val overlayCornerRadiusDp: Int = 16,
    val overlayBottomAnchored: Boolean = false,
    val overlayForceDark: Boolean = true,
    val overlayShowIdleSuggestions: Boolean = false,
    val engineForm: EngineFormState = EngineFormState(),
    val bookmarkForm: BookmarkFormState = BookmarkFormState(),
    val undoBanner: UndoBannerState? = null,
    val launchError: String? = null,
    val usageCounts: Map<String, Int> = emptyMap(),
    val jsonDialogMode: JsonDialogMode? = null,
    val jsonStatusMessage: String? = null,
) {
    val paletteResults: List< com.sujan.pipe.domain.PaletteItem >
        get() = QueryRouter.buildPaletteResults(
            rawInput = searchInput.text,
            engines = engines,
            bookmarks = bookmarks,
            contacts = contacts,
            storageFiles = storageFiles,
            scopedFolderFiles = scopedFolderFiles,
            history = history,
            usageMap = usageCounts,
        )
    val parsedCommand: ParsedPromptCommand
        get() = QueryRouter.parsePromptCommand(searchInput.text, bookmarks)

    val liveResolution: RouteResolution
        get() = QueryRouter.resolve(searchInput.text, engines)

    val suggestedEngines: List<SearchEngine>
        get() = QueryRouter.suggestEngines(searchInput.text, engines)

    val matchedBookmarks: List<PipeBookmark>
        get() {
            val cmd = parsedCommand
            return when (cmd.mode) {
                PromptIntentMode.BOOKMARKS_ONLY -> FuzzyMatcher.filterBookmarks(cmd.filterQuery, bookmarks, limit = 10)
                PromptIntentMode.SCOPED_FOLDER -> listOfNotNull(cmd.scopedFolderBookmark)
                PromptIntentMode.ALL -> {
                    if (searchInput.text.isBlank()) bookmarks.take(6)
                    else FuzzyMatcher.filterBookmarks(searchInput.text.trim(), bookmarks, limit = 5)
                }
                else -> emptyList()
            }
        }

    val matchedContacts: List<ContactItem>
        get() {
            val cmd = parsedCommand
            return when (cmd.mode) {
                PromptIntentMode.CONTACTS_ONLY -> FuzzyMatcher.filterContacts(cmd.filterQuery, contacts, limit = 10)
                PromptIntentMode.ALL -> {
                    val q = searchInput.text.trim()
                    if (q.length < 2) emptyList() else FuzzyMatcher.filterContacts(q, contacts, limit = 4)
                }
                else -> emptyList()
            }
        }

    val matchedFiles: List<LocalFileItem>
        get() {
            val cmd = parsedCommand
            return when (cmd.mode) {
                PromptIntentMode.SCOPED_FOLDER -> {
                    FuzzyMatcher.filterFiles(cmd.filterQuery, scopedFolderFiles, limit = 12)
                }
                PromptIntentMode.FILES_ONLY -> {
                    FuzzyMatcher.filterFiles(cmd.filterQuery, storageFiles, limit = 12)
                }
                PromptIntentMode.ALL -> {
                    val q = searchInput.text.trim()
                    if (q.length < 2) emptyList() else FuzzyMatcher.filterFiles(q, storageFiles, limit = 6)
                }
                else -> emptyList()
            }
        }
}

class PipeViewModel(private val repository: PipeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PipeUiState(
            engines = repository.loadEngines(),
            bookmarks = repository.loadBookmarks(),
            history = repository.loadHistory(),
            usageCounts = repository.loadUsageCounts(),
            themeMode = repository.loadThemeMode(),
            paletteChoice = repository.loadPaletteChoice(),
            historyEnabled = repository.isHistoryEnabled(),
            externalTextMode = repository.loadExternalTextMode(),
            overlayOpacity = repository.loadOverlayOpacity(),
            overlayDimAmount = repository.loadOverlayDimAmount(),
            overlayCornerRadiusDp = repository.loadOverlayCornerRadiusDp(),
            overlayBottomAnchored = repository.loadOverlayBottomAnchored(),
            overlayForceDark = repository.loadOverlayForceDark(),
            overlayShowIdleSuggestions = repository.loadOverlayShowIdleSuggestions(),
        )
    )
    val uiState: StateFlow<PipeUiState> = _uiState.asStateFlow()
    private var undoJob: Job? = null

    fun syncDeviceState(
        hasContactsPerm: Boolean,
        hasFilesPerm: Boolean,
        contacts: List<ContactItem>,
        files: List<LocalFileItem>,
    ) {
        _uiState.update {
            it.copy(
                hasContactsPermission = hasContactsPerm,
                hasFilesPermission = hasFilesPerm,
                contacts = contacts,
                storageFiles = files,
            )
        }
    }

    fun updateScopedFolderFiles(files: List<LocalFileItem>) {
        _uiState.update { it.copy(scopedFolderFiles = files) }
    }

    fun recordItemUsage(usageKey: String) {
        val updated = repository.incrementUsage(usageKey)
        _uiState.update { it.copy(usageCounts = updated) }
    }

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

    fun applyPrefixAlias(alias: String) {
        val text = "$alias "
        _uiState.update {
            it.copy(
                searchInput = TextFieldValue(text, TextRange(text.length)),
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
        repository.recordResolutionInHistory(raw, resolution)
        _uiState.update { it.copy(history = repository.loadHistory(), launchError = null) }
    }

    // -----------------------------------------------------------------------
    // Bookmarks Management (Links, Folders, Files)
    // -----------------------------------------------------------------------
    fun openAddBookmarkForm(
        prefillTitle: String = "",
        prefillTarget: String = "/storage/emulated/0/",
        prefillKind: BookmarkKind = BookmarkKind.FOLDER,
    ) {
        val suggestedAlias = prefillTitle.lowercase().filter { it.isLetterOrDigit() }.take(6)
        _uiState.update {
            it.copy(
                bookmarkForm = BookmarkFormState(
                    isOpen = true,
                    editingId = null,
                    alias = TextFieldValue(suggestedAlias, TextRange(suggestedAlias.length)),
                    title = TextFieldValue(prefillTitle, TextRange(prefillTitle.length)),
                    target = TextFieldValue(prefillTarget, TextRange(prefillTarget.length)),
                    kind = prefillKind,
                )
            )
        }
    }

    fun openEditBookmarkForm(bm: PipeBookmark) {
        _uiState.update {
            it.copy(
                bookmarkForm = BookmarkFormState(
                    isOpen = true,
                    editingId = bm.id,
                    alias = TextFieldValue(bm.alias, TextRange(bm.alias.length)),
                    title = TextFieldValue(bm.title, TextRange(bm.title.length)),
                    target = TextFieldValue(bm.target, TextRange(bm.target.length)),
                    kind = bm.kind,
                )
            )
        }
    }

    fun closeBookmarkForm() = _uiState.update { it.copy(bookmarkForm = BookmarkFormState(isOpen = false)) }

    fun updateBookmarkFormAlias(v: TextFieldValue) = _uiState.update {
        it.copy(bookmarkForm = it.bookmarkForm.copy(alias = v, errorText = null))
    }

    fun updateBookmarkFormTitle(v: TextFieldValue) = _uiState.update {
        it.copy(bookmarkForm = it.bookmarkForm.copy(title = v, errorText = null))
    }

    fun updateBookmarkFormTarget(v: TextFieldValue) = _uiState.update {
        it.copy(bookmarkForm = it.bookmarkForm.copy(target = v, errorText = null))
    }

    fun updateBookmarkFormKind(k: BookmarkKind) = _uiState.update {
        val defaultTarget = when (k) {
            BookmarkKind.LINK -> if (it.bookmarkForm.target.text.startsWith("/")) "https://" else it.bookmarkForm.target.text
            else -> if (it.bookmarkForm.target.text.startsWith("http")) "/storage/emulated/0/" else it.bookmarkForm.target.text
        }
        it.copy(
            bookmarkForm = it.bookmarkForm.copy(
                kind = k,
                target = TextFieldValue(defaultTarget, TextRange(defaultTarget.length)),
                errorText = null,
            )
        )
    }

    fun saveBookmarkForm() {
        val state = _uiState.value
        val f = state.bookmarkForm
        val cleanAlias = f.alias.text.trim().lowercase()
        val cleanTitle = f.title.text.trim()
        val cleanTarget = f.target.text.trim()

        if (cleanTitle.isEmpty() || cleanTarget.isEmpty()) {
            _uiState.update { it.copy(bookmarkForm = f.copy(errorText = "! title and target path/url are required")) }
            return
        }
        if (cleanAlias.any { it.isWhitespace() }) {
            _uiState.update { it.copy(bookmarkForm = f.copy(errorText = "! alias cannot contain spaces")) }
            return
        }

        val updated = if (f.editingId != null) {
            state.bookmarks.map { existing ->
                if (existing.id == f.editingId) {
                    existing.copy(alias = cleanAlias, title = cleanTitle, target = cleanTarget, kind = f.kind)
                } else existing
            }
        } else {
            state.bookmarks + PipeBookmark(
                id = "bm_${System.currentTimeMillis()}",
                alias = cleanAlias,
                title = cleanTitle,
                target = cleanTarget,
                kind = f.kind,
            )
        }
        repository.saveBookmarks(updated)
        _uiState.update { it.copy(bookmarks = updated, bookmarkForm = BookmarkFormState(isOpen = false)) }
    }

    fun deleteBookmark(bm: PipeBookmark) {
        val current = _uiState.value.bookmarks
        val idx = current.indexOfFirst { it.id == bm.id }
        if (idx < 0) return
        val updated = current.toMutableList().apply { removeAt(idx) }
        repository.saveBookmarks(updated)
        _uiState.update { it.copy(bookmarks = updated) }
        postUndo("deleted bookmark ${bm.title}", UndoPayload.DeletedBookmark(bm, idx))
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

    fun setOverlayOpacity(alpha: Float) {
        val clamped = alpha.coerceIn(0.15f, 1.0f)
        repository.saveOverlayOpacity(clamped)
        _uiState.update { it.copy(overlayOpacity = clamped) }
    }

    fun setOverlayDimAmount(dim: Float) {
        val clamped = dim.coerceIn(0.0f, 0.85f)
        repository.saveOverlayDimAmount(clamped)
        _uiState.update { it.copy(overlayDimAmount = clamped) }
    }

    fun setOverlayCornerRadiusDp(dp: Int) {
        val clamped = dp.coerceIn(0, 28)
        repository.saveOverlayCornerRadiusDp(clamped)
        _uiState.update { it.copy(overlayCornerRadiusDp = clamped) }
    }

    fun setOverlayBottomAnchored(bottom: Boolean) {
        repository.saveOverlayBottomAnchored(bottom)
        _uiState.update { it.copy(overlayBottomAnchored = bottom) }
    }

    fun setOverlayForceDark(forceDark: Boolean) {
        repository.saveOverlayForceDark(forceDark)
        _uiState.update { it.copy(overlayForceDark = forceDark) }
    }

    fun setOverlayShowIdleSuggestions(show: Boolean) {
        repository.saveOverlayShowIdleSuggestions(show)
        _uiState.update { it.copy(overlayShowIdleSuggestions = show) }
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
            is UndoPayload.DeletedBookmark -> {
                val list = _uiState.value.bookmarks.toMutableList()
                list.add(p.index.coerceIn(0, list.size), p.bookmark)
                repository.saveBookmarks(list)
                _uiState.update { it.copy(bookmarks = list, undoBanner = null) }
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
