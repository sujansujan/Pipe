package com.sujan.pipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.model.DefaultEngines
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.ascii.AsciiBox
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiCheckbox
import com.sujan.pipe.ui.ascii.AsciiPipePill
import com.sujan.pipe.ui.ascii.AsciiRadio
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiTextField
import com.sujan.pipe.ui.ascii.AsciiTopHeader
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.AsciiUndoBanner
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PaletteChoice
import com.sujan.pipe.ui.ascii.PipeTheme
import com.sujan.pipe.ui.ascii.ThemeMode
import com.sujan.pipe.ui.ascii.frame
import com.sujan.pipe.ui.ascii.leader
import com.sujan.pipe.ui.ascii.rememberCols

@Composable
fun PipeApp(
    state: PipeUiState,
    onNavigate: (PipeView) -> Unit,
    onSearchInputChange: (TextFieldValue) -> Unit,
    onChipClick: (SearchEngine) -> Unit,
    onSubmitSearch: () -> Unit,
    onRerunHistory: (SearchHistoryEntry) -> Unit,
    onFillHistory: (SearchHistoryEntry) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onAddEngineClick: () -> Unit,
    onEditEngineClick: (SearchEngine) -> Unit,
    onDeleteEngineClick: (SearchEngine) -> Unit,
    onMoveEngineUp: (Int) -> Unit,
    onMoveEngineDown: (Int) -> Unit,
    onSetDefaultEngine: (String) -> Unit,
    onFormNameChange: (TextFieldValue) -> Unit,
    onFormKeywordChange: (TextFieldValue) -> Unit,
    onFormUrlChange: (TextFieldValue) -> Unit,
    onFormColorChange: (EngineColorRole) -> Unit,
    onFormDefaultChange: (Boolean) -> Unit,
    onFormSave: () -> Unit,
    onFormCancel: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChoiceChange: (PaletteChoice) -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onExternalTextModeChange: (ExternalTextMode) -> Unit,
    onResetDefaults: () -> Unit,
    onOpenJsonExportDialog: () -> Unit,
    onOpenJsonImportDialog: () -> Unit,
    onExportJsonToFile: () -> Unit,
    onImportJsonFromFile: () -> Unit,
    onCloseJsonDialog: () -> Unit,
    onExportJsonString: () -> String,
    onImportJsonString: (String) -> String?,
    onSetJsonStatus: (String) -> Unit,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
) {
    PipeTheme(mode = state.themeMode, palette = state.paletteChoice.palette) {
        val c = LocalAscii.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg)
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (state.activeView) {
                    PipeView.PROMPT -> PipePromptScreen(
                        state = state,
                        onNavigate = onNavigate,
                        onSearchInputChange = onSearchInputChange,
                        onChipClick = onChipClick,
                        onSubmitSearch = onSubmitSearch,
                        onRerunHistory = onRerunHistory,
                        onFillHistory = onFillHistory,
                        onDeleteHistory = onDeleteHistory,
                        onClearHistory = onClearHistory,
                    )
                    PipeView.ENGINES -> PipeEnginesScreen(
                        engines = state.engines,
                        formState = state.engineForm,
                        onBack = { onNavigate(PipeView.PROMPT) },
                        onAddClick = onAddEngineClick,
                        onEditClick = onEditEngineClick,
                        onDeleteClick = onDeleteEngineClick,
                        onMoveUp = onMoveEngineUp,
                        onMoveDown = onMoveEngineDown,
                        onSetDefault = onSetDefaultEngine,
                        onFormNameChange = onFormNameChange,
                        onFormKeywordChange = onFormKeywordChange,
                        onFormUrlChange = onFormUrlChange,
                        onFormColorChange = onFormColorChange,
                        onFormDefaultChange = onFormDefaultChange,
                        onFormSave = onFormSave,
                        onFormCancel = onFormCancel,
                    )
                    PipeView.SETTINGS -> PipeSettingsScreen(
                        state = state,
                        onBack = { onNavigate(PipeView.PROMPT) },
                        onThemeModeChange = onThemeModeChange,
                        onPaletteChoiceChange = onPaletteChoiceChange,
                        onHistoryEnabledChange = onHistoryEnabledChange,
                        onExternalTextModeChange = onExternalTextModeChange,
                        onResetDefaults = onResetDefaults,
                        onOpenJsonExportDialog = onOpenJsonExportDialog,
                        onOpenJsonImportDialog = onOpenJsonImportDialog,
                        onExportJsonToFile = onExportJsonToFile,
                        onImportJsonFromFile = onImportJsonFromFile,
                        onCloseJsonDialog = onCloseJsonDialog,
                        onExportJsonString = onExportJsonString,
                        onImportJsonString = onImportJsonString,
                        onSetJsonStatus = onSetJsonStatus,
                    )
                }
            }

            if (state.undoBanner != null) {
                AsciiUndoBanner(
                    message = state.undoBanner.message,
                    onUndo = onUndo,
                    onDismiss = onDismissUndo,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipePromptScreen(
    state: PipeUiState,
    onNavigate: (PipeView) -> Unit,
    onSearchInputChange: (TextFieldValue) -> Unit,
    onChipClick: (SearchEngine) -> Unit,
    onSubmitSearch: () -> Unit,
    onRerunHistory: (SearchHistoryEntry) -> Unit,
    onFillHistory: (SearchHistoryEntry) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    val c = LocalAscii.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val res = state.liveResolution

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Minimalist single-screen header: ~/pipe on left, [pipes] [set] on right
        AsciiTopHeader(
            path = "~/pipe",
            actions = {
                AsciiButton(
                    label = "pipes (${state.engines.size})",
                    onClick = { onNavigate(PipeView.ENGINES) },
                    spokenLabel = "manage search engine pipes",
                )
                AsciiButton(
                    label = "set",
                    onClick = { onNavigate(PipeView.SETTINGS) },
                    spokenLabel = "settings",
                )
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Auto-focused Unix command prompt `$ query |`
            item {
                Spacer(Modifier.height(6.dp))
                AsciiTextField(
                    label = null,
                    promptPrefix = "$ ",
                    value = state.searchInput,
                    onValueChange = onSearchInputChange,
                    placeholder = "yt lofi music  |  g best phones",
                    errorText = state.launchError,
                    focusRequester = focusRequester,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    trailingControl = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.searchInput.text.isNotEmpty()) {
                                AsciiButton(
                                    label = "x",
                                    onClick = { onSearchInputChange(TextFieldValue("")) },
                                    spokenLabel = "clear input",
                                )
                            }
                            AsciiButton(
                                label = "| ->",
                                onClick = onSubmitSearch,
                                primary = true,
                                spokenLabel = "pipe search to ${res.engine.name}",
                            )
                        }
                    },
                )

                // Terse Unix stdout pipeline trace line right below the prompt
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiText(
                        text = "+--> ",
                        style = AsciiType.small,
                        color = c.dim,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    AsciiText(
                        text = res.pipelinePreview,
                        style = AsciiType.small,
                        color = if (res.matchedByKeyword) c.accent else c.accent2,
                        maxLines = 1,
                    )
                }
                AsciiText(
                    text = "     ${res.targetUrl}",
                    style = AsciiType.small,
                    color = c.dim,
                    maxLines = 1,
                )
            }

            // 2. Direct keyword filter pills: `[|g* google] [|yt youtube] ...`
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.suggestedEngines.forEach { eng ->
                        val active = res.matchedByKeyword && res.engine.id == eng.id
                        AsciiPipePill(
                            keyword = eng.keyword,
                            name = eng.name.lowercase(),
                            selected = active,
                            isDefault = eng.isDefault,
                            accentColor = c.forRole(eng.colorRole),
                            onClick = {
                                onChipClick(eng)
                                runCatching { focusRequester.requestFocus() }
                            },
                        )
                    }
                }
            }

            // 3. Plain-text box-table history log
            item {
                AsciiBox(
                    title = "history (${state.history.size})",
                    rightActionLabel = if (state.history.isNotEmpty()) "clear all" else null,
                    onRightAction = if (state.history.isNotEmpty()) onClearHistory else null,
                ) {
                    if (state.history.isEmpty()) {
                        AsciiText(
                            text = "nothing piped yet. type a keyword + query above.",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.history.forEach { entry ->
                                HistoryPipeRow(
                                    entry = entry,
                                    onRerun = { onRerunHistory(entry) },
                                    onFill = {
                                        onFillHistory(entry)
                                        runCatching { focusRequester.requestFocus() }
                                    },
                                    onDelete = { onDeleteHistory(entry) },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HistoryPipeRow(
    entry: SearchHistoryEntry,
    onRerun: () -> Unit,
    onFill: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (pressed) c.fg else Color.Transparent)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onRerun)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsciiText(
                    text = "$ ${entry.rawInput} ",
                    style = AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                )
                AsciiText(
                    text = "| ${entry.engineKeyword}",
                    style = AsciiType.title,
                    color = if (pressed) c.bg else c.accent,
                    maxLines = 1,
                )
            }
            AsciiText(
                text = "  -> ${entry.engineName.lowercase()} (${entry.queryText})",
                style = AsciiType.small,
                color = if (pressed) c.bg else c.dim,
                maxLines = 1,
            )
        }
        AsciiButton(label = "^", onClick = onFill, spokenLabel = "edit ${entry.rawInput}")
        AsciiButton(label = "x", onClick = onDelete, spokenLabel = "remove ${entry.rawInput}")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipeEnginesScreen(
    engines: List<SearchEngine>,
    formState: EngineFormState,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (SearchEngine) -> Unit,
    onDeleteClick: (SearchEngine) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSetDefault: (String) -> Unit,
    onFormNameChange: (TextFieldValue) -> Unit,
    onFormKeywordChange: (TextFieldValue) -> Unit,
    onFormUrlChange: (TextFieldValue) -> Unit,
    onFormColorChange: (EngineColorRole) -> Unit,
    onFormDefaultChange: (Boolean) -> Unit,
    onFormSave: () -> Unit,
    onFormCancel: () -> Unit,
) {
    val c = LocalAscii.current

    Column(Modifier.fillMaxSize()) {
        AsciiTopHeader(
            path = "~/pipe/engines",
            actions = {
                AsciiButton(label = "< back", onClick = onBack, spokenLabel = "back to search prompt")
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                AsciiText(
                    text = "tap (*) to set default fallback pipe. use [^] [v] to reorder.",
                    style = AsciiType.small,
                    color = c.dim,
                )
            }

            itemsIndexed(engines, key = { _, item -> item.id }) { idx, eng ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (eng.isDefault) c.accent else c.border)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cols = rememberCols(AsciiType.body)
                        val left = "|${eng.keyword}  ${eng.name}"
                        val right = if (eng.isDefault) "[default*]" else "pipe"
                        AsciiText(
                            text = leader(left, right, cols),
                            style = AsciiType.title,
                            color = if (eng.isDefault) c.accent else c.forRole(eng.colorRole),
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                    AsciiText(text = eng.urlTemplate, style = AsciiType.small, color = c.dim, maxLines = 1)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiRadio(selected = eng.isDefault, onClick = { onSetDefault(eng.id) }, label = "default")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsciiButton("^", { onMoveUp(idx) }, enabled = idx > 0, spokenLabel = "move ${eng.name} up")
                            AsciiButton("v", { onMoveDown(idx) }, enabled = idx < engines.lastIndex, spokenLabel = "move ${eng.name} down")
                            AsciiButton("edit", { onEditClick(eng) }, spokenLabel = "edit ${eng.name}")
                            AsciiButton("del", { onDeleteClick(eng) }, enabled = engines.size > 1, spokenLabel = "delete ${eng.name}")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        Box(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
            AsciiButton(
                label = "+ add pipe engine",
                onClick = onAddClick,
                primary = true,
                modifier = Modifier.fillMaxWidth().border(1.dp, c.accent),
                spokenLabel = "add new search engine pipe",
            )
        }
    }

    if (formState.isOpen) {
        Dialog(onDismissRequest = onFormCancel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .border(1.dp, c.accent2)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                AsciiBox(title = if (formState.editingId != null) "edit pipe" else "new pipe") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsciiTextField(
                            label = "name",
                            promptPrefix = "> ",
                            value = formState.name,
                            onValueChange = onFormNameChange,
                            placeholder = "DuckDuckGo",
                            errorText = formState.errors.nameError,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "keyword (unique, no spaces)",
                            promptPrefix = "| ",
                            value = formState.keyword,
                            onValueChange = onFormKeywordChange,
                            placeholder = "ddg",
                            errorText = formState.errors.keywordError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "url template (must contain {query})",
                            promptPrefix = "> ",
                            value = formState.urlTemplate,
                            onValueChange = onFormUrlChange,
                            placeholder = "https://duckduckgo.com/?q={query}",
                            errorText = formState.errors.urlTemplateError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        )
                        AsciiText("tag color role:", style = AsciiType.small, color = c.dim)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            EngineColorRole.entries.forEach { role ->
                                AsciiPipePill(
                                    keyword = role.label,
                                    selected = formState.colorRole == role,
                                    accentColor = c.forRole(role),
                                    onClick = { onFormColorChange(role) },
                                )
                            }
                        }
                        AsciiCheckbox(
                            checked = formState.isDefault,
                            onChange = onFormDefaultChange,
                            label = "set as default fallback pipe",
                        )
                        if (!formState.errors.isValid) {
                            AsciiText("! fix validation errors above to save", style = AsciiType.small, color = c.error)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AsciiButton("cancel", onFormCancel)
                            AsciiButton("save", onFormSave, enabled = formState.errors.isValid, primary = true)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipeSettingsScreen(
    state: PipeUiState,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChoiceChange: (PaletteChoice) -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onExternalTextModeChange: (ExternalTextMode) -> Unit,
    onResetDefaults: () -> Unit,
    onOpenJsonExportDialog: () -> Unit,
    onOpenJsonImportDialog: () -> Unit,
    onExportJsonToFile: () -> Unit,
    onImportJsonFromFile: () -> Unit,
    onCloseJsonDialog: () -> Unit,
    onExportJsonString: () -> String,
    onImportJsonString: (String) -> String?,
    onSetJsonStatus: (String) -> Unit,
) {
    val c = LocalAscii.current

    Column(Modifier.fillMaxSize()) {
        AsciiTopHeader(
            path = "~/pipe/set",
            actions = {
                AsciiButton(label = "< back", onClick = onBack, spokenLabel = "back to search prompt")
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsciiBox(title = "theme & palette") {
                AsciiText("mode (follows system by default):", style = AsciiType.small, color = c.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsciiRadio(state.themeMode == ThemeMode.System, { onThemeModeChange(ThemeMode.System) }, "system")
                    AsciiRadio(state.themeMode == ThemeMode.Light, { onThemeModeChange(ThemeMode.Light) }, "light")
                    AsciiRadio(state.themeMode == ThemeMode.Dark, { onThemeModeChange(ThemeMode.Dark) }, "dark")
                }
                Spacer(Modifier.height(6.dp))
                AsciiText("palette:", style = AsciiType.small, color = c.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaletteChoice.entries.forEach { choice ->
                        AsciiRadio(
                            selected = state.paletteChoice == choice,
                            onClick = { onPaletteChoiceChange(choice) },
                            label = choice.palette.name,
                        )
                    }
                }
            }

            AsciiBox(title = "pipeline behavior") {
                AsciiCheckbox(
                    checked = state.historyEnabled,
                    onChange = onHistoryEnabledChange,
                    label = "record local command history",
                )
                Spacer(Modifier.height(6.dp))
                AsciiText("share sheet & text selection (process text):", style = AsciiType.small, color = c.dim)
                AsciiRadio(
                    selected = state.externalTextMode == ExternalTextMode.OPEN_PROMPT,
                    onClick = { onExternalTextModeChange(ExternalTextMode.OPEN_PROMPT) },
                    label = "open ~/pipe prompt with text pre-filled",
                )
                AsciiRadio(
                    selected = state.externalTextMode == ExternalTextMode.DIRECT_PIPE,
                    onClick = { onExternalTextModeChange(ExternalTextMode.DIRECT_PIPE) },
                    label = "pipe immediately to matched / default engine",
                )
            }

            AsciiBox(title = "json import / export & reset") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsciiButton("export json file", onExportJsonToFile)
                    AsciiButton("import json file", onImportJsonFromFile)
                    AsciiButton("copy json", onOpenJsonExportDialog)
                    AsciiButton("paste json", onOpenJsonImportDialog)
                    AsciiButton("reset defaults", onResetDefaults)
                }
                if (state.jsonStatusMessage != null) {
                    AsciiText(
                        text = state.jsonStatusMessage,
                        style = AsciiType.small,
                        color = if (state.jsonStatusMessage.startsWith("!")) c.error else c.good,
                    )
                }
            }

            AsciiBox(title = "about") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cols = rememberCols(AsciiType.small)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (cols >= 28) {
                            AsciiText(
                                text = frame(
                                    title = "pipe v1.0",
                                    lines = listOf(
                                        "pipe queries by keyword. nothing else.",
                                        "syntax  : $ <keyword> <query> | ACTION_VIEW",
                                        "network : zero permissions, zero tracking",
                                    ),
                                    cols = cols,
                                ),
                                style = AsciiType.small,
                                color = c.accent,
                                softWrap = false,
                            )
                        } else {
                            AsciiText("pipe — pipe queries by keyword. nothing else.", style = AsciiType.title, color = c.accent)
                        }
                        AsciiText(leader("package", "com.sujan.pipe", cols), style = AsciiType.small, color = c.dim, softWrap = false)
                        AsciiText(leader("permissions", "none (0)", cols), style = AsciiType.small, color = c.dim, softWrap = false)
                    }
                }
            }
        }
    }

    if (state.jsonDialogMode != null) {
        val clipboard = LocalClipboardManager.current
        val initial = if (state.jsonDialogMode == JsonDialogMode.EXPORT) onExportJsonString() else ""
        var textVal by remember(state.jsonDialogMode, initial) {
            mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        }
        var err by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = onCloseJsonDialog) {
            Column(
                modifier = Modifier.fillMaxWidth().background(c.bg).border(1.dp, c.accent).padding(12.dp).imePadding(),
            ) {
                AsciiBox(title = if (state.jsonDialogMode == JsonDialogMode.EXPORT) "export json" else "import json") {
                    AsciiTextField(
                        label = "engines json array",
                        promptPrefix = "> ",
                        value = textVal,
                        onValueChange = { textVal = it; err = null },
                        errorText = err,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AsciiButton("close", onCloseJsonDialog)
                        if (state.jsonDialogMode == JsonDialogMode.EXPORT) {
                            AsciiButton("copy", {
                                clipboard.setText(AnnotatedString(textVal.text))
                                onSetJsonStatus("ok: copied json to clipboard")
                                onCloseJsonDialog()
                            }, primary = true)
                        } else {
                            AsciiButton("import", { err = onImportJsonString(textVal.text) }, primary = true)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Pipe Prompt - Moss Dark", showBackground = true, widthDp = 380, heightDp = 740)
@Composable
private fun PreviewPipeDark() {
    PipePreviewHost(PipeUiState(searchInput = TextFieldValue("yt lofi music"), themeMode = ThemeMode.Dark))
}

@Preview(name = "Pipe Prompt - Moss Light", showBackground = true, widthDp = 380, heightDp = 740)
@Composable
private fun PreviewPipeLight() {
    PipePreviewHost(PipeUiState(searchInput = TextFieldValue("yt lofi music"), themeMode = ThemeMode.Light))
}

@Preview(name = "Pipe Prompt - 200% Font Scale", showBackground = true, fontScale = 2.0f, widthDp = 380, heightDp = 740)
@Composable
private fun PreviewPipeFont2x() {
    PipePreviewHost(PipeUiState(searchInput = TextFieldValue("gh compose"), themeMode = ThemeMode.Dark))
}

@Composable
private fun PipePreviewHost(state: PipeUiState) {
    PipeApp(
        state = state, onNavigate = {}, onSearchInputChange = {}, onChipClick = {}, onSubmitSearch = {},
        onRerunHistory = {}, onFillHistory = {}, onDeleteHistory = {}, onClearHistory = {},
        onAddEngineClick = {}, onEditEngineClick = {}, onDeleteEngineClick = {},
        onMoveEngineUp = {}, onMoveEngineDown = {}, onSetDefaultEngine = {},
        onFormNameChange = {}, onFormKeywordChange = {}, onFormUrlChange = {},
        onFormColorChange = {}, onFormDefaultChange = {}, onFormSave = {}, onFormCancel = {},
        onThemeModeChange = {}, onPaletteChoiceChange = {}, onHistoryEnabledChange = {},
        onExternalTextModeChange = {}, onResetDefaults = {}, onOpenJsonExportDialog = {},
        onOpenJsonImportDialog = {}, onExportJsonToFile = {}, onImportJsonFromFile = {},
        onCloseJsonDialog = {}, onExportJsonString = { "[]" }, onImportJsonString = { null },
        onSetJsonStatus = {}, onUndo = {}, onDismissUndo = {},
    )
}
