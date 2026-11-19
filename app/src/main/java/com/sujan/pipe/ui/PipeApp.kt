package com.sujan.pipe.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.domain.DirectNumberPrimaryMode
import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.QuickAddKind
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.DictionaryEntry
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.InstalledAppItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.PrefixConfig
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.model.AppShortcutItem
import com.sujan.pipe.model.AppShortcutKind
import com.sujan.pipe.model.DefaultSystemShortcuts
import com.sujan.pipe.model.SystemSettingShortcut
import com.sujan.pipe.model.UniversalAlias
import com.sujan.pipe.model.WhatsAppFlavor
import com.sujan.pipe.ui.ascii.AsciiBox
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiCommandBar
import com.sujan.pipe.ui.ascii.AsciiDropdownRow
import com.sujan.pipe.ui.ascii.AsciiInlineSegmentedRow
import com.sujan.pipe.ui.ascii.AsciiModalBox
import com.sujan.pipe.ui.ascii.AsciiRule
import com.sujan.pipe.ui.ascii.AsciiSection
import com.sujan.pipe.ui.ascii.AsciiStatusBar
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiTextField
import com.sujan.pipe.ui.ascii.AsciiToggleRow
import com.sujan.pipe.ui.ascii.AsciiTopHeader
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.AsciiUndoBanner
import com.sujan.pipe.ui.ascii.CellBitmapImage
import com.sujan.pipe.ui.ascii.CellGrid
import com.sujan.pipe.ui.ascii.LocalGridMetrics
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PaletteChoice
import com.sujan.pipe.ui.ascii.PipeTheme
import com.sujan.pipe.ui.ascii.ThemeMode
import com.sujan.pipe.ui.ascii.bar
import com.sujan.pipe.ui.ascii.leader
import com.sujan.pipe.ui.ascii.rememberBlinkingCaret
import com.sujan.pipe.ui.ascii.rememberCols
import com.sujan.pipe.ui.ascii.row
import com.sujan.pipe.ui.ascii.rule
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Builds an [AnnotatedString] with fzf-style matched character spans highlighted in bold + underline
 * using [highlightColor], while unmatched characters use [baseColor].
 */
fun buildFzfHighlightedText(
    text: String,
    highlightQuery: String,
    baseColor: Color,
    highlightColor: Color,
): AnnotatedString {
    if (text.isEmpty() || highlightQuery.isBlank()) {
        return AnnotatedString(text)
    }
    val mask = FuzzyMatcher.findMatchedIndices(text, highlightQuery)
    var anyHit = false
    for (b in mask) {
        if (b) {
            anyHit = true
            break
        }
    }
    if (!anyHit) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val isHit = mask[i]
            var j = i + 1
            while (j < text.length && mask[j] == isHit) {
                j++
            }
            withStyle(
                if (isHit) {
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    )
                } else {
                    SpanStyle(color = baseColor)
                }
            ) {
                append(text.substring(i, j))
            }
            i = j
        }
    }
}

/**
 * Extracts a [UniversalAlias] descriptor for any searchable [ com.sujan.pipe.domain.PaletteItem ] so the user can assign
 * a custom quick alias to system settings, pipe settings, apps, app shortcuts, app settings, files, folders, contacts, bookmarks, or pipes.
 */
fun extractAliasTargetInfo(
    item: com.sujan.pipe.domain.PaletteItem,
    existingAliases: List<UniversalAlias>,
): UniversalAlias? {
    val current = existingAliases.firstOrNull { it.targetKey == item.usageKey }?.alias.orEmpty()
    return when (item) {
        is com.sujan.pipe.domain.PaletteItem.SystemSettingAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.shortcut.title,
            category = if (item.shortcut.internalPipeSection != null) "pipe:set" else "sys:${item.shortcut.alias}",
            subtitle = item.shortcut.subtitle,
        )
        is com.sujan.pipe.domain.PaletteItem.AppShortcutAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = "${item.shortcut.appLabel} - ${item.shortcut.shortLabel}",
            category = "app:${item.shortcut.kind.badge}",
            subtitle = item.shortcut.longLabel,
        )
        is com.sujan.pipe.domain.PaletteItem.AppAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.app.label,
            category = "app",
            subtitle = item.app.packageName,
        )
        is com.sujan.pipe.domain.PaletteItem.FileAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.file.name,
            category = if (item.file.isDirectory) "dir" else "file",
            subtitle = item.file.absolutePath,
        )
        is com.sujan.pipe.domain.PaletteItem.ContactAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.contact.displayName,
            category = "contact",
            subtitle = item.contact.phoneNumber,
        )
        is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.bookmark.title,
            category = "bookmark",
            subtitle = item.bookmark.target,
        )
        is com.sujan.pipe.domain.PaletteItem.EngineAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.engine.name,
            category = "pipe:${item.engine.keyword}",
            subtitle = item.engine.urlTemplate,
        )
        is com.sujan.pipe.domain.PaletteItem.DirectUrlAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.displayHost,
            category = "url",
            subtitle = item.targetUrl,
        )
        is com.sujan.pipe.domain.PaletteItem.HistoryAction -> UniversalAlias(
            targetKey = item.usageKey,
            alias = current,
            label = item.entry.rawInput,
            category = "hist",
            subtitle = item.entry.resolvedUrl,
        )
        else -> null
    }
}

@Composable
fun AsciiSetAliasDialog(
    target: UniversalAlias,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    var aliasField by remember(target) {
        mutableStateOf(TextFieldValue(target.alias, TextRange(target.alias.length)))
    }
    val clean = aliasField.text.trim().lowercase()
    val hasSpace = clean.any { it.isWhitespace() }
    val err = if (hasSpace) "! alias cannot contain spaces" else null

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(12.dp)
                .imePadding(),
        ) {
            AsciiBox(title = "set universal alias?", isModal = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsciiText(
                        text = "[${target.category}] ${target.label}",
                        style = AsciiType.title,
                        color = c.accent,
                        maxLines = 1,
                    )
                    if (target.subtitle.isNotBlank()) {
                        AsciiText(
                            text = target.subtitle,
                            style = AsciiType.small,
                            color = c.dim,
                            maxLines = 1,
                        )
                    }
                    AsciiTextField(
                        label = "quick alias (type in prompt or overlay to rank #1)",
                        promptPrefix = "@",
                        value = aliasField,
                        onValueChange = { aliasField = it },
                        placeholder = "e.g. wcfg, mix, citi, dev",
                        errorText = err,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!hasSpace) onSave(clean)
                            }
                        ),
                    )
                    AsciiLivePreviewLine(
                        previewText = if (clean.isNotEmpty()) "$clean -> ${target.label} (#1 top match)" else "empty alias removes custom shortcut",
                        isValid = clean.isNotEmpty() && !hasSpace,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiButton("cancel", onDismiss)
                        if (target.alias.isNotBlank()) {
                            AsciiButton("remove @${target.alias}", onRemove)
                        }
                        AsciiButton(
                            label = "save @alias",
                            enabled = !hasSpace,
                            primary = true,
                            onClick = { onSave(clean) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PipeApp(
    state: PipeUiState,
    onNavigate: (PipeView) -> Unit,
    onSearchInputChange: (TextFieldValue) -> Unit,
    onChipClick: (SearchEngine) -> Unit,
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimaryAction: () -> Unit,
    onOpenDirectUrl: (String) -> Unit = {},
    onOpenSystemSetting: (SystemSettingShortcut) -> Unit = {},
    onLaunchAppShortcut: (AppShortcutItem) -> Unit = {},
    onSaveUniversalAlias: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRemoveUniversalAlias: (String) -> Unit = {},
    onExecuteDirectNumber: (com.sujan.pipe.domain.PaletteItem.DirectNumberAction, DirectNumberPrimaryMode) -> Unit = { _, _ -> },
    onExecuteTimerAlarm: (com.sujan.pipe.domain.PaletteItem.TimerAlarmAction, Boolean) -> Unit = { _, _ -> },
    onSelectDictionaryItem: (com.sujan.pipe.domain.PaletteItem.DictionaryAction) -> Unit = {},
    onDismissDictionaryDialog: () -> Unit = {},
    onImportDictionaryFileClick: () -> Unit = {},
    onAutoDetectDownloadsDictionary: () -> Unit = {},
    onRemoveDictionaryClick: () -> Unit = {},
    onExecuteClipboardAction: (com.sujan.pipe.domain.PaletteItem.ClipboardAction) -> Unit = {},
    onOpenBookmark: (PipeBookmark) -> Unit,
    onOpenLocalFile: (LocalFileItem) -> Unit,
    onShareLocalFile: (LocalFileItem) -> Unit = {},
    onPreviewImageFile: (LocalFileItem?) -> Unit = {},
    onLaunchInstalledApp: (InstalledAppItem) -> Unit = {},
    onAppInfoInstalledApp: (InstalledAppItem) -> Unit = {},
    onUninstallInstalledApp: (InstalledAppItem) -> Unit = {},
    onBookmarkLocalFile: (LocalFileItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onWhatsAppBusinessContact: (ContactItem) -> Unit = {},
    onQuickSaveContact: (String, String) -> Unit = { _, _ -> },
    onQuickSaveBookmark: (String, String, String, BookmarkKind, String, String?) -> Unit = { _, _, _, _, _, _ -> },
    onQuickSavePipe: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onQuickRemoveItem: (Boolean, String) -> Unit = { _, _ -> },
    onRequestContactsPermission: () -> Unit,
    onRequestFilesPermission: () -> Unit,
    onRerunHistory: (SearchHistoryEntry) -> Unit,
    onFillHistory: (SearchHistoryEntry) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onAddBookmarkClick: () -> Unit,
    onEditBookmarkClick: (PipeBookmark) -> Unit,
    onDeleteBookmarkClick: (PipeBookmark) -> Unit,
    onBookmarkFormAliasChange: (TextFieldValue) -> Unit,
    onBookmarkFormTitleChange: (TextFieldValue) -> Unit,
    onBookmarkFormTargetChange: (TextFieldValue) -> Unit,
    onBookmarkFormTagsChange: (TextFieldValue) -> Unit = {},
    onBookmarkFormKindChange: (BookmarkKind) -> Unit,
    onBookmarkFormSave: () -> Unit,
    onBookmarkFormCancel: () -> Unit,
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
    onOverlayOpacityChange: (Float) -> Unit = {},
    onOverlayDimChange: (Float) -> Unit = {},
    onOverlayCornerChange: (Int) -> Unit = {},
    onOverlayBottomChange: (Boolean) -> Unit = {},
    onOverlayForceDarkChange: (Boolean) -> Unit = {},
    onOverlayIdleChange: (Boolean) -> Unit = {},
    onSelectSettingsSection: (SettingsSection) -> Unit = {},
    onHourlyAutoIndexChange: (Boolean) -> Unit = {},
    onRebuildIndexNow: () -> Unit = {},
    onPreferredBrowserChange: (String) -> Unit = {},
    onWhatsAppFlavorChange: (WhatsAppFlavor) -> Unit = {},
    onAddExcludedFolder: (String) -> Unit = {},
    onRemoveExcludedFolder: (String) -> Unit = {},
    onSearchBoxPrefixChange: (String) -> Unit = {},
    onSearchBoxPlaceholderChange: (String) -> Unit = {},
    onShowScopeBadgeChange: (Boolean) -> Unit = {},
    onShowMenuBtnChange: (Boolean) -> Unit = {},
    onAutoLaunchExactAliasChange: (Boolean) -> Unit = {},
    onPrefixConfigChange: (PrefixConfig) -> Unit = {},
    onResetPrefixConfig: () -> Unit = {},
    onLaunchOverlayPreview: () -> Unit,
    onPinOverlayShortcut: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
) {
    PipeTheme(mode = state.themeMode, palette = state.paletteChoice.palette) {
        val c = LocalAscii.current
        val clipboard = LocalClipboardManager.current
        CellGrid(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg)
                .safeDrawingPadding()
                .imePadding(),
        ) { _ ->
            Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (state.activeView) {
                    PipeView.PROMPT -> PipePromptScreen(
                        state = state,
                        onNavigate = onNavigate,
                        onSearchInputChange = onSearchInputChange,
                        onChipClick = onChipClick,
                        onAliasPillClick = onAliasPillClick,
                        onSubmitPrimaryAction = onSubmitPrimaryAction,
                        onOpenDirectUrl = onOpenDirectUrl,
                        onOpenSystemSetting = onOpenSystemSetting,
                        onLaunchAppShortcut = onLaunchAppShortcut,
                        onSaveUniversalAlias = onSaveUniversalAlias,
                        onRemoveUniversalAlias = onRemoveUniversalAlias,
                        onExecuteDirectNumber = onExecuteDirectNumber,
                        onExecuteTimerAlarm = onExecuteTimerAlarm,
                        onSelectDictionaryItem = onSelectDictionaryItem,
                        onExecuteClipboardAction = onExecuteClipboardAction,
                        onOpenBookmark = onOpenBookmark,
                        onOpenLocalFile = onOpenLocalFile,
                        onShareLocalFile = onShareLocalFile,
                        onPreviewImageFile = onPreviewImageFile,
                        onLaunchInstalledApp = onLaunchInstalledApp,
                        onAppInfoInstalledApp = onAppInfoInstalledApp,
                        onUninstallInstalledApp = onUninstallInstalledApp,
                        onBookmarkLocalFile = onBookmarkLocalFile,
                        onCallContact = onCallContact,
                        onSmsContact = onSmsContact,
                        onWhatsAppContact = onWhatsAppContact,
                        onWhatsAppBusinessContact = onWhatsAppBusinessContact,
                        onQuickSaveContact = onQuickSaveContact,
                        onQuickSaveBookmark = onQuickSaveBookmark,
                        onQuickSavePipe = onQuickSavePipe,
                        onQuickRemoveItem = onQuickRemoveItem,
                        onRequestContactsPermission = onRequestContactsPermission,
                        onRequestFilesPermission = onRequestFilesPermission,
                        onRerunHistory = onRerunHistory,
                        onFillHistory = onFillHistory,
                        onDeleteHistory = onDeleteHistory,
                        onClearHistory = onClearHistory,
                        onLaunchOverlay = onLaunchOverlayPreview,
                    )
                    PipeView.BOOKMARKS, PipeView.ENGINES -> PipeLibraryScreen(
                        initialTab = if (state.bookmarkForm.isOpen) LibraryTab.BOOKMARKS else LibraryTab.PIPES,
                        engines = state.engines,
                        bookmarks = state.bookmarks,
                        history = state.history,
                        universalAliases = state.universalAliases,
                        prefixes = state.prefixConfig,
                        onSaveUniversalAlias = onSaveUniversalAlias,
                        onRemoveUniversalAlias = onRemoveUniversalAlias,
                        onOpenSystemSetting = onOpenSystemSetting,
                        engineForm = state.engineForm,
                        bookmarkForm = state.bookmarkForm,
                        onBack = { onNavigate(PipeView.PROMPT) },
                        onOpenBookmark = onOpenBookmark,
                        onAddBookmarkClick = onAddBookmarkClick,
                        onEditBookmarkClick = onEditBookmarkClick,
                        onDeleteBookmarkClick = onDeleteBookmarkClick,
                        onBookmarkAliasChange = onBookmarkFormAliasChange,
                        onBookmarkTitleChange = onBookmarkFormTitleChange,
                        onBookmarkTargetChange = onBookmarkFormTargetChange,
                        onBookmarkTagsChange = onBookmarkFormTagsChange,
                        onBookmarkKindChange = onBookmarkFormKindChange,
                        onBookmarkSave = onBookmarkFormSave,
                        onBookmarkCancel = onBookmarkFormCancel,
                        onAddEngineClick = onAddEngineClick,
                        onEditEngineClick = onEditEngineClick,
                        onDeleteEngineClick = onDeleteEngineClick,
                        onMoveEngineUp = onMoveEngineUp,
                        onMoveEngineDown = onMoveEngineDown,
                        onSetDefaultEngine = onSetDefaultEngine,
                        onEngineNameChange = onFormNameChange,
                        onEngineKeywordChange = onFormKeywordChange,
                        onEngineUrlChange = onFormUrlChange,
                        onEngineColorChange = onFormColorChange,
                        onEngineDefaultChange = onFormDefaultChange,
                        onEngineSave = onFormSave,
                        onEngineCancel = onFormCancel,
                        onRerunHistory = onRerunHistory,
                        onFillHistory = { entry ->
                            onFillHistory(entry)
                            onNavigate(PipeView.PROMPT)
                        },
                        onDeleteHistory = onDeleteHistory,
                        onClearHistory = onClearHistory,
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
                        onOverlayOpacityChange = onOverlayOpacityChange,
                        onOverlayDimChange = onOverlayDimChange,
                        onOverlayCornerChange = onOverlayCornerChange,
                        onOverlayBottomChange = onOverlayBottomChange,
                        onOverlayForceDarkChange = onOverlayForceDarkChange,
                        onOverlayIdleChange = onOverlayIdleChange,
                        onSelectSettingsSection = onSelectSettingsSection,
                        onHourlyAutoIndexChange = onHourlyAutoIndexChange,
                        onRebuildIndexNow = onRebuildIndexNow,
                        onPreferredBrowserChange = onPreferredBrowserChange,
                        onWhatsAppFlavorChange = onWhatsAppFlavorChange,
                        onAddExcludedFolder = onAddExcludedFolder,
                        onRemoveExcludedFolder = onRemoveExcludedFolder,
                        onSearchBoxPrefixChange = onSearchBoxPrefixChange,
                        onSearchBoxPlaceholderChange = onSearchBoxPlaceholderChange,
                        onShowScopeBadgeChange = onShowScopeBadgeChange,
                        onShowMenuBtnChange = onShowMenuBtnChange,
                        onAutoLaunchExactAliasChange = onAutoLaunchExactAliasChange,
                        onPrefixConfigChange = onPrefixConfigChange,
                        onResetPrefixConfig = onResetPrefixConfig,
                        onImportDictionaryFileClick = onImportDictionaryFileClick,
                        onAutoDetectDownloadsDictionary = onAutoDetectDownloadsDictionary,
                        onRemoveDictionaryClick = onRemoveDictionaryClick,
                        onLaunchOverlayPreview = onLaunchOverlayPreview,
                        onPinOverlayShortcut = onPinOverlayShortcut,
                        onOpenDefaultAppsSettings = onOpenDefaultAppsSettings,
                        onRequestContactsPermission = onRequestContactsPermission,
                        onRequestFilesPermission = onRequestFilesPermission,
                        onOpenSystemSetting = onOpenSystemSetting,
                        onSaveUniversalAlias = onSaveUniversalAlias,
                        onRemoveUniversalAlias = onRemoveUniversalAlias,
                    )
                }
            }

            if (state.activeDictionaryEntry != null) {
                val entry = state.activeDictionaryEntry
                AsciiDictionaryDetailDialog(
                    entry = entry,
                    onCopy = {
                        clipboard.setText(AnnotatedString("${entry.word} (${entry.partOfSpeech}): ${entry.definition}"))
                        onSetJsonStatus("ok: copied definition for ${entry.word}")
                        onDismissDictionaryDialog()
                    },
                    onPipeToWiki = {
                        onDismissDictionaryDialog()
                        val url = "https://en.wikipedia.org/w/index.php?search=${java.net.URLEncoder.encode(entry.word, "UTF-8")}"
                        onOpenDirectUrl(url)
                    },
                    onDismiss = onDismissDictionaryDialog,
                )
            }

            if (state.previewImageFile != null) {
                AsciiImagePreviewDialog(
                    file = state.previewImageFile,
                    onOpen = {
                        onOpenLocalFile(state.previewImageFile)
                        onPreviewImageFile(null)
                    },
                    onShare = {
                        onShareLocalFile(state.previewImageFile)
                        onPreviewImageFile(null)
                    },
                    onDismiss = { onPreviewImageFile(null) },
                )
            }

            if (state.undoBanner != null) {
                AsciiUndoBanner(
                    message = state.undoBanner.message,
                    onUndo = onUndo,
                    onDismiss = onDismissUndo,
                )
            }
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
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimaryAction: () -> Unit,
    onOpenDirectUrl: (String) -> Unit,
    onOpenSystemSetting: (SystemSettingShortcut) -> Unit,
    onLaunchAppShortcut: (AppShortcutItem) -> Unit = {},
    onSaveUniversalAlias: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRemoveUniversalAlias: (String) -> Unit = {},
    onExecuteDirectNumber: (com.sujan.pipe.domain.PaletteItem.DirectNumberAction, DirectNumberPrimaryMode) -> Unit,
    onExecuteTimerAlarm: (com.sujan.pipe.domain.PaletteItem.TimerAlarmAction, Boolean) -> Unit,
    onSelectDictionaryItem: (com.sujan.pipe.domain.PaletteItem.DictionaryAction) -> Unit,
    onExecuteClipboardAction: (com.sujan.pipe.domain.PaletteItem.ClipboardAction) -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onOpenLocalFile: (LocalFileItem) -> Unit,
    onShareLocalFile: (LocalFileItem) -> Unit,
    onPreviewImageFile: (LocalFileItem?) -> Unit,
    onLaunchInstalledApp: (InstalledAppItem) -> Unit,
    onAppInfoInstalledApp: (InstalledAppItem) -> Unit,
    onUninstallInstalledApp: (InstalledAppItem) -> Unit,
    onBookmarkLocalFile: (LocalFileItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onWhatsAppBusinessContact: (ContactItem) -> Unit,
    onQuickSaveContact: (String, String) -> Unit,
    onQuickSaveBookmark: (String, String, String, BookmarkKind, String, String?) -> Unit,
    onQuickSavePipe: (String, String, String, String?) -> Unit,
    onQuickRemoveItem: (Boolean, String) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestFilesPermission: () -> Unit,
    onRerunHistory: (SearchHistoryEntry) -> Unit,
    onFillHistory: (SearchHistoryEntry) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onLaunchOverlay: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inlineQuickAdd by remember { mutableStateOf<com.sujan.pipe.domain.PaletteItem.QuickAddAction?>(null) }
    val p = state.prefixConfig

    var lastAutoLaunchedQuery by remember { mutableStateOf("") }
    LaunchedEffect(state.searchInput.text, state.paletteResults, state.autoLaunchExactAlias, state.universalAliases) {
        val raw = state.searchInput.text.trim().lowercase()
        if (raw.isEmpty()) {
            lastAutoLaunchedQuery = ""
            return@LaunchedEffect
        }
        if (state.autoLaunchExactAlias && raw != lastAutoLaunchedQuery) {
            val matchedAlias = state.universalAliases.firstOrNull { it.aliasLower == raw }
            val top = state.paletteResults.firstOrNull()
            if (matchedAlias != null && top != null && top.usageKey == matchedAlias.targetKey) {
                lastAutoLaunchedQuery = raw
                onSearchInputChange(TextFieldValue(""))
                onSubmitPrimaryAction()
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        AsciiRaycastPaletteWindow(
            titlePath = "~/pipe",
            searchInput = state.searchInput,
            onSearchInputChange = onSearchInputChange,
            parsedCommand = state.parsedCommand,
            results = state.paletteResults,
            universalAliases = state.universalAliases,
            prefixes = p,
            onSaveUniversalAlias = onSaveUniversalAlias,
            onRemoveUniversalAlias = onRemoveUniversalAlias,
            hasContactsPermission = state.hasContactsPermission,
            hasFilesPermission = state.hasFilesPermission,
            launchError = state.launchError ?: state.jsonStatusMessage,
            promptPrefix = state.searchBoxPrefix,
            placeholderText = state.searchBoxPlaceholder,
            showScopeBadge = state.showSearchBoxScopeBadge,
            focusRequester = focusRequester,
            inlineQuickAdd = inlineQuickAdd,
            onDismissInlineQuickAdd = { inlineQuickAdd = null },
            onQuickSaveContact = { name, phone ->
                inlineQuickAdd = null
                onQuickSaveContact(name, phone)
            },
            onQuickSaveBookmark = { alias, title, target, kind, tags, editingId ->
                inlineQuickAdd = null
                onQuickSaveBookmark(alias, title, target, kind, tags, editingId)
            },
            onQuickSavePipe = { kw, name, url, editingId ->
                inlineQuickAdd = null
                onQuickSavePipe(kw, name, url, editingId)
            },
            onQuickEditBookmark = { bm ->
                inlineQuickAdd = com.sujan.pipe.domain.PaletteItem.QuickAddAction(
                    kind = QuickAddKind.BOOKMARK,
                    title = "Edit Bookmark [${p.bookmarksClean} ${bm.alias}]",
                    subtitle = bm.target,
                    isReadyToCommit = false,
                    arg1 = bm.alias,
                    arg2 = bm.title,
                    arg3 = bm.target,
                    bookmarkKind = bm.kind,
                    editingId = bm.id,
                )
            },
            onQuickEditEngine = { eng ->
                inlineQuickAdd = com.sujan.pipe.domain.PaletteItem.QuickAddAction(
                    kind = QuickAddKind.PIPE,
                    title = "Edit Pipe [${p.pipesClean} ${eng.keyword}]",
                    subtitle = eng.urlTemplate,
                    isReadyToCommit = false,
                    arg1 = eng.keyword,
                    arg2 = eng.name,
                    arg3 = eng.urlTemplate,
                    editingId = eng.id,
                )
            },
            existingBookmarks = state.bookmarks,
            existingEngines = state.engines,
            onQuickDeleteBookmark = { bm -> onQuickRemoveItem(true, bm.id) },
            onQuickDeleteEngine = { eng -> onQuickRemoveItem(false, eng.id) },
            onQuickDeleteHistory = onDeleteHistory,
            onAppInfoInstalledApp = onAppInfoInstalledApp,
            onUninstallInstalledApp = onUninstallInstalledApp,
            headerRightActions = {
                AsciiButton("+", {
                    if (inlineQuickAdd != null) {
                        inlineQuickAdd = null
                    } else {
                        val raw = state.searchInput.text.trim().removePrefix("+").removePrefix("add").trim()
                        inlineQuickAdd = com.sujan.pipe.domain.PaletteItem.QuickAddAction(
                            kind = QuickAddKind.BOOKMARK,
                            title = "Quick Add",
                            subtitle = "",
                            isReadyToCommit = false,
                            arg1 = raw,
                        )
                    }
                }, primary = inlineQuickAdd != null, spokenLabel = "quick add")
                AsciiButton("lib", { onNavigate(PipeView.BOOKMARKS) }, spokenLabel = "open library")
                AsciiButton("set", { onNavigate(PipeView.SETTINGS) }, spokenLabel = "open settings")
            },
            onAliasPillClick = { alias ->
                onAliasPillClick(alias)
                runCatching { focusRequester.requestFocus() }
            },
            onSubmitPrimary = onSubmitPrimaryAction,
            onExecuteItem = { item ->
                when (item) {
                    is com.sujan.pipe.domain.PaletteItem.DirectUrlAction -> onOpenDirectUrl(item.targetUrl)
                    is com.sujan.pipe.domain.PaletteItem.SystemSettingAction -> onOpenSystemSetting(item.shortcut)
                    is com.sujan.pipe.domain.PaletteItem.AppShortcutAction -> onLaunchAppShortcut(item.shortcut)
                    is com.sujan.pipe.domain.PaletteItem.DirectNumberAction -> onExecuteDirectNumber(item, item.primaryMode)
                    is com.sujan.pipe.domain.PaletteItem.TimerAlarmAction -> onExecuteTimerAlarm(item, true)
                    is com.sujan.pipe.domain.PaletteItem.DictionaryAction -> onSelectDictionaryItem(item)
                    is com.sujan.pipe.domain.PaletteItem.ClipboardAction -> onExecuteClipboardAction(item)
                    is com.sujan.pipe.domain.PaletteItem.EngineAction -> {
                        val nextInput = if (item.query.isNotEmpty()) "${item.engine.keyword} ${item.query}" else item.engine.keyword
                        onSearchInputChange(TextFieldValue(nextInput, TextRange(nextInput.length)))
                        onSubmitPrimaryAction()
                    }
                    is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> onOpenBookmark(item.bookmark)
                    is com.sujan.pipe.domain.PaletteItem.FileAction -> onOpenLocalFile(item.file)
                    is com.sujan.pipe.domain.PaletteItem.ContactAction -> {
                        when (item.preferredAction) {
                            com.sujan.pipe.model.ContactQuickAction.WHATSAPP -> onWhatsAppContact(item.contact)
                            com.sujan.pipe.model.ContactQuickAction.WHATSAPP_BUSINESS -> onWhatsAppBusinessContact(item.contact)
                            com.sujan.pipe.model.ContactQuickAction.SMS -> onSmsContact(item.contact)
                            com.sujan.pipe.model.ContactQuickAction.CALL -> onCallContact(item.contact)
                        }
                    }
                    is com.sujan.pipe.domain.PaletteItem.AppAction -> onLaunchInstalledApp(item.app)
                    is com.sujan.pipe.domain.PaletteItem.QuickAddAction -> {
                        if (item.isReadyToCommit) {
                            when (item.kind) {
                                QuickAddKind.CONTACT -> onQuickSaveContact(item.arg1, item.arg2)
                                QuickAddKind.BOOKMARK -> onQuickSaveBookmark(item.arg1, item.arg2, item.arg3, item.bookmarkKind, "", item.editingId)
                                QuickAddKind.PIPE -> onQuickSavePipe(item.arg1, item.arg2, item.arg3, item.editingId)
                            }
                        } else {
                            inlineQuickAdd = item
                        }
                    }
                    is com.sujan.pipe.domain.PaletteItem.QuickRemoveAction -> {
                        onQuickRemoveItem(item.isBookmark, item.targetId)
                    }
                    is com.sujan.pipe.domain.PaletteItem.HistoryAction -> onRerunHistory(item.entry)
                }
            },
            onCallContact = onCallContact,
            onSmsContact = onSmsContact,
            onWhatsAppContact = onWhatsAppContact,
            onWhatsAppBusinessContact = onWhatsAppBusinessContact,
            onExecuteDirectNumber = onExecuteDirectNumber,
            onExecuteTimerAlarm = onExecuteTimerAlarm,
            onScopeIntoFolder = { alias ->
                onAliasPillClick(alias)
                runCatching { focusRequester.requestFocus() }
            },
            onOpenChildFile = onOpenLocalFile,
            onBookmarkFile = { f ->
                val suggestedAlias = f.name.lowercase().filter { it.isLetterOrDigit() }.take(6)
                inlineQuickAdd = com.sujan.pipe.domain.PaletteItem.QuickAddAction(
                    kind = QuickAddKind.BOOKMARK,
                    title = "Add Bookmark for ${f.name}",
                    subtitle = f.absolutePath,
                    isReadyToCommit = false,
                    arg1 = suggestedAlias,
                    arg2 = f.name,
                    arg3 = f.absolutePath,
                    bookmarkKind = if (f.isDirectory) BookmarkKind.FOLDER else BookmarkKind.FILE,
                )
            },
            onShareFile = onShareLocalFile,
            onPreviewImage = { onPreviewImageFile(it) },
            onRequestContactsPermission = onRequestContactsPermission,
            onRequestFilesPermission = onRequestFilesPermission,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 44dp square-cornered, 1dp-bordered ASCII scope/tab chip.
 */
@Composable
fun AsciiScopeChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val highlighted = active || pressed
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(
                interactionSource = src,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val formattedLabel = if (label.contains('[')) label else if (active) "[*$label*]" else "[$label]"
        AsciiText(
            text = formattedLabel,
            style = if (active || pressed) AsciiType.title else AsciiType.body,
            color = if (active) c.pink else if (pressed) c.cyan else c.dim,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .background(if (highlighted) c.surface else Color.Transparent)
                .padding(horizontal = 3.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun AsciiRaycastPaletteWindow(
    titlePath: String,
    searchInput: TextFieldValue,
    onSearchInputChange: (TextFieldValue) -> Unit,
    parsedCommand: com.sujan.pipe.domain.ParsedPromptCommand,
    results: List<com.sujan.pipe.domain.PaletteItem>,
    universalAliases: List<UniversalAlias> = emptyList(),
    prefixes: PrefixConfig = PrefixConfig(),
    onSaveUniversalAlias: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRemoveUniversalAlias: (String) -> Unit = {},
    hasContactsPermission: Boolean,
    hasFilesPermission: Boolean,
    launchError: String?,
    focusRequester: FocusRequester,
    promptPrefix: String = "> ",
    placeholderText: String = "pipe for???",
    showScopeBadge: Boolean = true,
    inlineQuickAdd: com.sujan.pipe.domain.PaletteItem.QuickAddAction? = null,
    onDismissInlineQuickAdd: () -> Unit = {},
    onQuickSaveContact: (String, String) -> Unit = { _, _ -> },
    onQuickSaveBookmark: (String, String, String, BookmarkKind, String, String?) -> Unit = { _, _, _, _, _, _ -> },
    onQuickSavePipe: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    existingBookmarks: List<PipeBookmark> = emptyList(),
    existingEngines: List<SearchEngine> = emptyList(),
    onQuickEditBookmark: ((PipeBookmark) -> Unit)? = null,
    onQuickEditEngine: ((SearchEngine) -> Unit)? = null,
    onQuickDeleteBookmark: ((PipeBookmark) -> Unit)? = null,
    onQuickDeleteEngine: ((SearchEngine) -> Unit)? = null,
    onQuickDeleteHistory: ((SearchHistoryEntry) -> Unit)? = null,
    onAppInfoInstalledApp: ((InstalledAppItem) -> Unit)? = null,
    onUninstallInstalledApp: ((InstalledAppItem) -> Unit)? = null,
    headerRightActions: @Composable () -> Unit,
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimary: () -> Unit,
    onExecuteItem: (com.sujan.pipe.domain.PaletteItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onWhatsAppBusinessContact: (ContactItem) -> Unit = {},
    onExecuteDirectNumber: (com.sujan.pipe.domain.PaletteItem.DirectNumberAction, DirectNumberPrimaryMode) -> Unit = { _, _ -> },
    onExecuteTimerAlarm: (com.sujan.pipe.domain.PaletteItem.TimerAlarmAction, Boolean) -> Unit = { _, _ -> },
    onScopeIntoFolder: (String) -> Unit,
    onOpenChildFile: ((LocalFileItem) -> Unit)? = null,
    onBookmarkFile: ((LocalFileItem) -> Unit)? = null,
    onShareFile: ((LocalFileItem) -> Unit)? = null,
    onPreviewImage: ((LocalFileItem) -> Unit)? = null,
    onRequestContactsPermission: (() -> Unit)? = null,
    onRequestFilesPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    var actionSheetItem by remember { mutableStateOf<com.sujan.pipe.domain.PaletteItem?>(null) }
    var aliasDialogTarget by remember { mutableStateOf<UniversalAlias?>(null) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }
    var expandedFolderChildren by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }

    LaunchedEffect(expandedFolderPath) {
        val path = expandedFolderPath
        expandedFolderChildren = if (path != null) {
            withContext(Dispatchers.IO) {
                com.sujan.pipe.data.DeviceIntegrationManager.listDirectChildren(path)
            }
        } else {
            emptyList()
        }
    }

    val scopeBadge = when (parsedCommand.mode) {
        PromptIntentMode.SCOPED_FOLDER -> "${prefixes.bookmarksClean}:${parsedCommand.scopedFolderBookmark?.alias ?: "folder"}"
        PromptIntentMode.ENGINE_ONLY -> "${prefixes.pipesClean}:${parsedCommand.matchedEngine?.keyword ?: "pipe"}"
        PromptIntentMode.CONTACTS_ONLY -> "${prefixes.contactsClean}:contacts"
        PromptIntentMode.FILES_ONLY -> "${prefixes.filesClean}:files"
        PromptIntentMode.BOOKMARKS_ONLY -> "${prefixes.bookmarksClean}:bookmarks"
        PromptIntentMode.PIPES_LIST -> "${prefixes.pipesClean}:pipes"
        PromptIntentMode.APPS_ONLY -> "${prefixes.appsClean}:launcher"
        PromptIntentMode.HISTORY_ONLY -> "${prefixes.historyClean}:history"
        PromptIntentMode.DICTIONARY_ONLY -> "${prefixes.dictionaryClean}:dictionary"
        PromptIntentMode.CLIPBOARD_ONLY -> "${prefixes.clipboardClean}:clipboard"
        PromptIntentMode.SYSTEM_SETTINGS_ONLY -> "${prefixes.systemSettingsClean}:settings"
        PromptIntentMode.DIRECT_NUMBER_ONLY -> "wa:number"
        PromptIntentMode.TIMER_ALARM_ONLY -> "clk:timer"
        else -> parsedCommand.mode.scopeBadge
    }

    val scopeChips = listOf(
        Triple(prefixes.appsClean, "[a]pp", parsedCommand.mode == PromptIntentMode.APPS_ONLY),
        Triple(prefixes.filesClean, "[f]ile", parsedCommand.mode == PromptIntentMode.FILES_ONLY || parsedCommand.mode == PromptIntentMode.DIRS_ONLY),
        Triple(prefixes.contactsClean, "[c]ontact", parsedCommand.mode == PromptIntentMode.CONTACTS_ONLY),
        Triple(prefixes.systemSettingsClean, "[s]et", parsedCommand.mode == PromptIntentMode.SYSTEM_SETTINGS_ONLY),
        Triple("wa", "[w]a", parsedCommand.mode == PromptIntentMode.DIRECT_NUMBER_ONLY),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.purple, fontWeight = FontWeight.Bold)) { append("PIPE  ") }
                    withStyle(SpanStyle(color = c.orange, fontWeight = FontWeight.Bold)) { append(scopeBadge.uppercase()) }
                },
                style = AsciiType.title,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerRightActions()
            }
        }

        AsciiRule(glyph = "-", color = c.border)

        if (!hasContactsPermission && parsedCommand.mode == PromptIntentMode.CONTACTS_ONLY && onRequestContactsPermission != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("! contacts permission needed for `${prefixes.contactsClean}:`", style = AsciiType.small, color = c.warn)
                AsciiButton("grant", onRequestContactsPermission, primary = true)
            }
            AsciiRule(glyph = "-", color = c.border)
        }
        if (!hasFilesPermission && (parsedCommand.mode == PromptIntentMode.FILES_ONLY || parsedCommand.mode == PromptIntentMode.DIRS_ONLY || parsedCommand.mode == PromptIntentMode.SCOPED_FOLDER) && onRequestFilesPermission != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("! storage access needed for `${prefixes.filesClean}:` / `dl:`", style = AsciiType.small, color = c.warn)
                AsciiButton("grant", onRequestFilesPermission, primary = true)
            }
            AsciiRule(glyph = "-", color = c.border)
        }

        if (inlineQuickAdd != null) {
            QuickAddInlineCard(
                initialAction = inlineQuickAdd,
                existingBookmarks = existingBookmarks,
                existingEngines = existingEngines,
                onSaveContact = onQuickSaveContact,
                onSaveBookmark = onQuickSaveBookmark,
                onSavePipe = onQuickSavePipe,
                onDeleteBookmark = onQuickDeleteBookmark,
                onDeleteEngine = onQuickDeleteEngine,
                onCancel = onDismissInlineQuickAdd,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            AsciiRule(glyph = "-", color = c.border)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (results.isEmpty()) {
                item {
                    AsciiText(
                        text = "  | no matching items in [$scopeBadge].",
                        style = AsciiType.small,
                        color = c.dim,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            } else {
                itemsIndexed(results, key = { idx, item -> "${idx}_${item.usageKey}" }) { index, item ->
                    val isTop = index == 0
                    val accentCol = c.accent
                    val folderPath = when (item) {
                        is com.sujan.pipe.domain.PaletteItem.FileAction -> if (item.file.isDirectory) item.file.absolutePath else null
                        is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> if (item.bookmark.kind == BookmarkKind.FOLDER) item.bookmark.target else null
                        else -> null
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                drawContent()
                                if (isTop) {
                                    drawRect(
                                        color = accentCol,
                                        topLeft = Offset.Zero,
                                        size = Size(3.dp.toPx(), size.height),
                                    )
                                }
                            },
                    ) {
                        val itemCustomAlias = universalAliases.firstOrNull { it.targetKey == item.usageKey }?.alias
                        val rowAliasTarget = remember(item, universalAliases) {
                            extractAliasTargetInfo(item, universalAliases)
                        }
                        AsciiRaycastRow(
                            item = item,
                            isTop = isTop,
                            prefixes = prefixes,
                            customAlias = itemCustomAlias,
                            highlightQuery = parsedCommand.filterQuery,
                            isFolderExpanded = folderPath != null && expandedFolderPath == folderPath,
                            expandedChildren = if (folderPath != null && expandedFolderPath == folderPath) expandedFolderChildren else emptyList(),
                            onExecute = { onExecuteItem(item) },
                            onOpenActionSheet = { actionSheetItem = item },
                            onOpenAliasDialog = if (rowAliasTarget != null) {
                                { aliasDialogTarget = rowAliasTarget }
                            } else null,
                            onSwipeRightSecondary = {
                                when (item) {
                                    is com.sujan.pipe.domain.PaletteItem.ContactAction -> onWhatsAppContact(item.contact)
                                    is com.sujan.pipe.domain.PaletteItem.AppAction -> onAppInfoInstalledApp?.invoke(item.app)
                                    is com.sujan.pipe.domain.PaletteItem.AppShortcutAction -> onAppInfoInstalledApp?.invoke(InstalledAppItem(item.shortcut.appLabel, item.shortcut.packageName))
                                    is com.sujan.pipe.domain.PaletteItem.FileAction -> {
                                        if (item.file.isDirectory) {
                                            expandedFolderPath = if (expandedFolderPath == item.file.absolutePath) null else item.file.absolutePath
                                        } else {
                                            onShareFile?.invoke(item.file)
                                        }
                                    }
                                    is com.sujan.pipe.domain.PaletteItem.DirectNumberAction -> {
                                        val alt = if (item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_PERSONAL) DirectNumberPrimaryMode.WHATSAPP_BUSINESS else DirectNumberPrimaryMode.WHATSAPP_PERSONAL
                                        onExecuteDirectNumber(item, alt)
                                    }
                                    is com.sujan.pipe.domain.PaletteItem.TimerAlarmAction -> onExecuteTimerAlarm(item, false)
                                    else -> actionSheetItem = item
                                }
                            },
                            onToggleFolderPeek = if (folderPath != null) {
                                { path -> expandedFolderPath = if (expandedFolderPath == path) null else path }
                            } else null,
                            onOpenChildFile = onOpenChildFile,
                            onShareChildFile = onShareFile,
                            onPreviewChildImage = onPreviewImage,
                            onExecuteDirectNumber = onExecuteDirectNumber,
                            onExecuteTimerAlarm = onExecuteTimerAlarm,
                        )
                    }
                }
            }
        }

        if (launchError != null) {
            AsciiRule(glyph = "-", color = c.border)
            AsciiText(
                text = "  $launchError",
                style = AsciiType.small,
                color = if (launchError.startsWith("!")) c.error else c.good,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        if (searchInput.text.isBlank()) {
            AsciiRule(glyph = "-", color = c.dim)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                scopeChips.forEach { (alias, chipLabel, isActive) ->
                    AsciiScopeChip(
                        label = chipLabel,
                        active = isActive,
                        onClick = {
                            if (isActive) {
                                onSearchInputChange(TextFieldValue(""))
                            } else {
                                onAliasPillClick(alias)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AsciiRule(glyph = "-", color = c.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .background(c.bg)
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val effectivePrefix = promptPrefix.ifEmpty { "> " }
            AsciiText(
                text = effectivePrefix,
                style = AsciiType.title,
                color = c.accent,
            )
            var promptFocused by remember { mutableStateOf(true) }
            val promptCaret = rememberBlinkingCaret(active = promptFocused)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                val shownAnnotated = buildAnnotatedString {
                    if (searchInput.text.isEmpty() && !promptFocused && placeholderText.isNotEmpty()) {
                        withStyle(SpanStyle(color = c.dim)) { append(placeholderText.lowercase()) }
                    } else {
                        withStyle(SpanStyle(color = c.fg)) { append(searchInput.text) }
                        if (promptFocused) {
                            withStyle(SpanStyle(color = c.pink, fontWeight = FontWeight.Bold)) { append(promptCaret) }
                        }
                    }
                }
                AsciiText(
                    text = shownAnnotated,
                    style = AsciiType.body,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = searchInput,
                    onValueChange = onSearchInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { promptFocused = it.isFocused }
                        .semantics { contentDescription = "command prompt" },
                    textStyle = AsciiType.body.copy(color = Color.Transparent),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val firstItem = results.firstOrNull()
                            if (firstItem != null) onExecuteItem(firstItem) else onSubmitPrimary()
                        },
                    ),
                )
            }
            if (showScopeBadge) {
                AsciiText(
                    text = "[$scopeBadge]",
                    style = AsciiType.small,
                    color = if (parsedCommand.mode == PromptIntentMode.ALL) c.dim else c.accent,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (searchInput.text.isNotEmpty()) {
                AsciiButton(
                    label = "x",
                    onClick = { onSearchInputChange(TextFieldValue("")) },
                    spokenLabel = "clear input",
                )
            }
            AsciiButton(
                label = "RET ->",
                onClick = {
                    val firstItem = results.firstOrNull()
                    if (firstItem != null) onExecuteItem(firstItem) else onSubmitPrimary()
                },
                primary = true,
                spokenLabel = "run top match",
            )
        }
    }

    if (aliasDialogTarget != null) {
        val target = aliasDialogTarget!!
        AsciiSetAliasDialog(
            target = target,
            onSave = { newAlias ->
                onSaveUniversalAlias(target.targetKey, newAlias, target.label, target.category, target.subtitle)
                aliasDialogTarget = null
            },
            onRemove = {
                onRemoveUniversalAlias(target.targetKey)
                aliasDialogTarget = null
            },
            onDismiss = { aliasDialogTarget = null },
        )
    }

    if (actionSheetItem != null) {
        val currentSheetItem = actionSheetItem!!
        val sheetAliasTarget = remember(currentSheetItem, universalAliases) {
            extractAliasTargetInfo(currentSheetItem, universalAliases)
        }
        AsciiResultActionSheet(
            item = currentSheetItem,
            prefixes = prefixes,
            currentAlias = sheetAliasTarget?.alias,
            onSetAliasClick = {
                actionSheetItem = null
                if (sheetAliasTarget != null) {
                    aliasDialogTarget = sheetAliasTarget
                }
            },
            canDeleteEngine = existingEngines.size > 1,
            onDismiss = { actionSheetItem = null },
            onPrimaryExecute = {
                actionSheetItem = null
                onExecuteItem(currentSheetItem)
            },
            onCallContact = { contact ->
                actionSheetItem = null
                onCallContact(contact)
            },
            onSmsContact = { contact ->
                actionSheetItem = null
                onSmsContact(contact)
            },
            onWhatsAppContact = { contact ->
                actionSheetItem = null
                onWhatsAppContact(contact)
            },
            onWhatsAppBusinessContact = { contact ->
                actionSheetItem = null
                onWhatsAppBusinessContact(contact)
            },
            onExecuteDirectNumber = { act, mode ->
                actionSheetItem = null
                onExecuteDirectNumber(act, mode)
            },
            onScopeFolder = { scopeCmd ->
                actionSheetItem = null
                onScopeIntoFolder(scopeCmd)
            },
            onBookmarkFile = { f ->
                actionSheetItem = null
                onBookmarkFile?.invoke(f)
            },
            onShareFile = { f ->
                actionSheetItem = null
                onShareFile?.invoke(f)
            },
            onPreviewImage = { f ->
                actionSheetItem = null
                onPreviewImage?.invoke(f)
            },
            onEditBookmark = { bm ->
                actionSheetItem = null
                onQuickEditBookmark?.invoke(bm)
            },
            onDeleteBookmark = { bm ->
                actionSheetItem = null
                onQuickDeleteBookmark?.invoke(bm)
            },
            onEditEngine = { eng ->
                actionSheetItem = null
                onQuickEditEngine?.invoke(eng)
            },
            onDeleteEngine = { eng ->
                actionSheetItem = null
                onQuickDeleteEngine?.invoke(eng)
            },
            onDeleteHistory = { h ->
                actionSheetItem = null
                onQuickDeleteHistory?.invoke(h)
            },
            onAppInfoInstalledApp = { app ->
                actionSheetItem = null
                onAppInfoInstalledApp?.invoke(app)
            },
            onUninstallInstalledApp = { app ->
                actionSheetItem = null
                onUninstallInstalledApp?.invoke(app)
            },
            onCopyText = { textToCopy ->
                clipboard.setText(AnnotatedString(textToCopy))
                actionSheetItem = null
            },
        )
    }
}

@Composable
fun AsciiDictionaryDetailDialog(
    entry: DictionaryEntry,
    onCopy: () -> Unit,
    onPipeToWiki: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsciiText(
                        text = entry.word,
                        style = AsciiType.title,
                        color = c.accent,
                    )
                    if (entry.partOfSpeech.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        AsciiText(
                            text = "[${entry.partOfSpeech}]",
                            style = AsciiType.small,
                            color = c.accent2,
                        )
                    }
                }
                AsciiButton("x", onDismiss)
            }

            AsciiRule(glyph = "-", color = c.border)

            AsciiText(
                text = entry.definition,
                style = AsciiType.body,
                color = c.fg,
            )

            if (entry.synonyms.isNotBlank()) {
                AsciiText(
                    text = "syn: ${entry.synonyms}",
                    style = AsciiType.small,
                    color = c.good,
                )
            }
            if (entry.example.isNotBlank()) {
                AsciiText(
                    text = "ex: \"${entry.example}\"",
                    style = AsciiType.small,
                    color = c.dim,
                )
            }

            AsciiRule(glyph = "-", color = c.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiButton("close", onDismiss)
                AsciiButton("pipe to w", onPipeToWiki)
                AsciiButton("copy", onCopy, primary = true)
            }
        }
    }
}

/**
 * Grid-snapped Contact Avatar (`4x2` cells) using `CellBitmapImage` per `ascii-app` Section 5.
 * Displays the contact's photo bitmap when available, or their 2-letter monogram in `green` inside the cell box.
 */
@Composable
fun AsciiContactAvatar(
    contact: ContactItem,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val context = LocalContext.current
    var bitmap by remember(contact.photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(contact.photoUri) {
        bitmap = if (!contact.photoUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                com.sujan.pipe.data.DeviceIntegrationManager.loadContactPhotoBitmap(context, contact.photoUri)
            }
        } else {
            null
        }
    }
    CellBitmapImage(
        bitmap = bitmap?.asImageBitmap(),
        contentDescription = "photo of ${contact.displayName}",
        cellCols = 4,
        cellRows = 2,
        placeholderText = contact.monogram.take(2),
        placeholderColor = c.green,
        modifier = modifier.padding(end = 6.dp),
    )
}

/**
 * Grid-snapped Domain Favicon (`3x2` cells) using `CellBitmapImage` + `FaviconManager` RAM/disk cache.
 * Shows a 2-char domain/keyword monogram fallback while loading or when offline.
 */
@Composable
fun AsciiFaviconBadge(
    url: String,
    fallbackText: String,
    fallbackColor: Color = LocalAscii.current.cyan,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(com.sujan.pipe.data.FaviconManager.peekMemory(url)) }
    LaunchedEffect(url) {
        if (bitmap == null) {
            bitmap = com.sujan.pipe.data.FaviconManager.loadFavicon(context, url)
        }
    }
    CellBitmapImage(
        bitmap = bitmap?.asImageBitmap(),
        contentDescription = "icon for $fallbackText",
        cellCols = 3,
        cellRows = 2,
        placeholderText = fallbackText.take(2),
        placeholderColor = fallbackColor,
        modifier = modifier.padding(end = 6.dp),
    )
}

@Composable
fun AsciiLivePreviewLine(
    previewText: String,
    isValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = "--> $previewText",
            style = AsciiType.small,
            color = if (isValid) c.accent else c.dim,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddInlineCard(
    initialAction: com.sujan.pipe.domain.PaletteItem.QuickAddAction,
    onSaveContact: (String, String) -> Unit,
    onSaveBookmark: (String, String, String, BookmarkKind, String, String?) -> Unit,
    onSavePipe: (String, String, String, String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    existingBookmarks: List<PipeBookmark> = emptyList(),
    existingEngines: List<SearchEngine> = emptyList(),
    onDeleteBookmark: ((PipeBookmark) -> Unit)? = null,
    onDeleteEngine: ((SearchEngine) -> Unit)? = null,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val clipText = remember { clipboard.getText()?.text?.trim().orEmpty() }

    val autoContact = remember(initialAction.arg1) {
        if (initialAction.editingId == null) QueryRouter.parseQuickAddContact(initialAction.arg1) else null
    }
    var activeKind by remember(initialAction) {
        mutableStateOf(if (autoContact != null) QuickAddKind.CONTACT else initialAction.kind)
    }
    var editingId by remember(initialAction) { mutableStateOf(initialAction.editingId) }

    val initName = autoContact?.first ?: initialAction.arg1
    val initPhone = autoContact?.second ?: initialAction.arg2.ifEmpty {
        if (clipText.any { it.isDigit() } && clipText.length in 7..16 && !clipText.startsWith("http")) clipText else ""
    }
    var contactName by remember(initialAction) {
        mutableStateOf(TextFieldValue(initName, TextRange(initName.length)))
    }
    var contactPhone by remember(initialAction) {
        mutableStateOf(TextFieldValue(initPhone, TextRange(initPhone.length)))
    }

    val initialBmTarget = initialAction.arg3.ifEmpty {
        if (clipText.startsWith("http://") || clipText.startsWith("https://") || clipText.startsWith("/")) clipText else ""
    }
    val initialBmAlias = initialAction.arg1.takeIf { !it.contains(' ') }.orEmpty()
    val initialBmTitle = initialAction.arg2.ifEmpty {
        if (initialBmTarget.isNotEmpty()) QueryRouter.inferTitleFromTarget(initialBmAlias, initialBmTarget) else ""
    }
    var bmAlias by remember(initialAction) {
        mutableStateOf(TextFieldValue(initialBmAlias, TextRange(initialBmAlias.length)))
    }
    var bmTitle by remember(initialAction) {
        mutableStateOf(TextFieldValue(initialBmTitle, TextRange(initialBmTitle.length)))
    }
    var bmTarget by remember(initialAction) {
        mutableStateOf(TextFieldValue(initialBmTarget, TextRange(initialBmTarget.length)))
    }

    val initialPipeUrl = initialAction.arg3.ifEmpty {
        if (clipText.startsWith("http://") || clipText.startsWith("https://")) {
            QueryRouter.normalizePipeUrlTemplate(clipText)
        } else {
            ""
        }
    }
    val initialPipeTitle = initialAction.arg2.ifEmpty {
        if (initialPipeUrl.isNotEmpty()) QueryRouter.inferTitleFromTarget(initialBmAlias, initialPipeUrl) else ""
    }
    var pipeKeyword by remember(initialAction) {
        mutableStateOf(TextFieldValue(initialBmAlias, TextRange(initialBmAlias.length)))
    }
    var pipeName by remember(initialAction) {
        mutableStateOf(TextFieldValue(initialPipeTitle, TextRange(initialPipeTitle.length)))
    }
    var pipeUrl by remember(initialAction) {
        mutableStateOf(TextFieldValue(initialPipeUrl, TextRange(initialPipeUrl.length)))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuickAddKind.entries.forEach { k ->
                    AsciiButton(
                        label = "${k.tag} ${k.label.lowercase()}",
                        onClick = {
                            activeKind = k
                            editingId = null
                        },
                        primary = activeKind == k,
                    )
                }
            }
            AsciiButton("x", onCancel)
        }

        if (editingId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = ":: editing existing ${activeKind.label.lowercase()}",
                    style = AsciiType.small,
                    color = c.accent2,
                )
                AsciiButton("new instead", {
                    editingId = null
                    bmAlias = TextFieldValue("")
                    bmTitle = TextFieldValue("")
                    bmTarget = TextFieldValue("")
                    pipeKeyword = TextFieldValue("")
                    pipeName = TextFieldValue("")
                    pipeUrl = TextFieldValue("")
                })
            }
        }

        AsciiRule(glyph = "-", color = c.border)

        when (activeKind) {
            QuickAddKind.CONTACT -> {
                val cleanName = contactName.text.trim()
                val cleanPhone = contactPhone.text.trim()
                val phoneDigits = cleanPhone.filter { it.isDigit() }
                val nameError = if (cleanPhone.isNotEmpty() && cleanName.isEmpty()) "! name is required" else null
                val phoneError = when {
                    cleanPhone.isNotEmpty() && phoneDigits.length < 3 -> "! phone needs at least 3 digits"
                    cleanName.isNotEmpty() && cleanPhone.isEmpty() -> "! phone number is required"
                    else -> null
                }
                val isContactValid = cleanName.isNotEmpty() && phoneDigits.length >= 3
                val previewContactLine = if (isContactValid) {
                    "c $cleanName -> tel:$cleanPhone (wa: ${QueryRouter.normalizeWhatsAppPhone(cleanPhone)})"
                } else {
                    "c Kalika Prasad -> tel:9841234567"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(0.52f)) {
                        AsciiTextField(
                            label = "name",
                            promptPrefix = "@ ",
                            value = contactName,
                            onValueChange = { contactName = it },
                            placeholder = "Kalika Prasad",
                            errorText = nameError,
                        )
                    }
                    Box(Modifier.weight(0.48f)) {
                        AsciiTextField(
                            label = "phone",
                            promptPrefix = "# ",
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            placeholder = "9841234567",
                            errorText = phoneError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        )
                    }
                }

                AsciiLivePreviewLine(
                    previewText = previewContactLine,
                    isValid = isContactValid,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiButton("cancel", onCancel)
                    AsciiButton(
                        label = "+ save contact",
                        enabled = isContactValid,
                        primary = isContactValid,
                        onClick = { onSaveContact(cleanName, cleanPhone) },
                    )
                }
            }

            QuickAddKind.BOOKMARK -> {
                val rawAlias = bmAlias.text.trim()
                val cleanAlias = rawAlias.lowercase()
                val rawTarget = bmTarget.text.trim()
                val normalizedTarget = if (!rawTarget.startsWith("/") && !rawTarget.startsWith("http://") && !rawTarget.startsWith("https://") && rawTarget.contains('.')) {
                    "https://$rawTarget"
                } else rawTarget
                val effectiveTitle = bmTitle.text.trim().ifEmpty {
                    if (normalizedTarget.isNotEmpty()) QueryRouter.inferTitleFromTarget(cleanAlias.ifEmpty { "bm" }, normalizedTarget) else ""
                }

                val dupBm = existingBookmarks.firstOrNull {
                    it.id != editingId && cleanAlias.isNotEmpty() && it.alias.equals(cleanAlias, ignoreCase = true)
                }
                val aliasError = when {
                    rawAlias.any { it.isWhitespace() } -> "! alias cannot contain spaces"
                    dupBm != null -> "! duplicate alias (used by ${dupBm.title.lowercase()})"
                    normalizedTarget.isNotEmpty() && cleanAlias.isEmpty() -> "! alias is required"
                    else -> null
                }
                val titleError = if (cleanAlias.isNotEmpty() && normalizedTarget.isNotEmpty() && effectiveTitle.isEmpty()) {
                    "! title is required"
                } else null
                val targetLooksValid = normalizedTarget.startsWith("/storage/") ||
                    normalizedTarget.startsWith("/sdcard/") ||
                    ((normalizedTarget.startsWith("http://") || normalizedTarget.startsWith("https://")) && normalizedTarget.substringAfter("://").contains('.'))
                val targetError = when {
                    rawTarget.isNotEmpty() && !targetLooksValid -> "! enter https://domain.com or /storage/... path"
                    cleanAlias.isNotEmpty() && rawTarget.isEmpty() -> "! url or folder path is required"
                    else -> null
                }

                val isBookmarkValid = cleanAlias.isNotEmpty() && aliasError == null && titleError == null && targetLooksValid
                val detectedKind = when {
                    normalizedTarget.startsWith("http://") || normalizedTarget.startsWith("https://") -> BookmarkKind.LINK
                    normalizedTarget.substringAfterLast('/').contains('.') -> BookmarkKind.FILE
                    else -> BookmarkKind.FOLDER
                }
                val shortTargetPreview = normalizedTarget
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .ifEmpty { "/storage/emulated/0/Download" }
                val previewBmLine = "b ${cleanAlias.ifEmpty { "dl" }} -> $shortTargetPreview"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(0.38f)) {
                        AsciiTextField(
                            label = "b alias",
                            promptPrefix = "b ",
                            value = bmAlias,
                            onValueChange = { bmAlias = it },
                            placeholder = "dl",
                            errorText = aliasError,
                        )
                    }
                    Box(Modifier.weight(0.62f)) {
                        AsciiTextField(
                            label = "title",
                            promptPrefix = "> ",
                            value = bmTitle,
                            onValueChange = { bmTitle = it },
                            placeholder = "Downloads",
                            errorText = titleError,
                        )
                    }
                }
                AsciiTextField(
                    label = "url or folder/file path",
                    promptPrefix = "> ",
                    value = bmTarget,
                    onValueChange = {
                        bmTarget = it
                        if (bmTitle.text.isBlank() && it.text.length > 4) {
                            val inferred = QueryRouter.inferTitleFromTarget(bmAlias.text, it.text)
                            bmTitle = TextFieldValue(inferred, TextRange(inferred.length))
                        }
                    },
                    placeholder = "https://... or /storage/emulated/0/...",
                    errorText = targetError,
                    trailingControl = if (clipText.isNotEmpty()) {
                        {
                            AsciiButton("paste", {
                                bmTarget = TextFieldValue(clipText, TextRange(clipText.length))
                                if (bmTitle.text.isBlank()) {
                                    val inf = QueryRouter.inferTitleFromTarget(bmAlias.text, clipText)
                                    bmTitle = TextFieldValue(inf, TextRange(inf.length))
                                }
                            })
                        }
                    } else null,
                )

                AsciiLivePreviewLine(
                    previewText = previewBmLine,
                    isValid = isBookmarkValid,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiButton("cancel", onCancel)
                    AsciiButton(
                        label = if (editingId != null) "update b" else "+ save b",
                        enabled = isBookmarkValid,
                        primary = isBookmarkValid,
                        onClick = {
                            onSaveBookmark(cleanAlias, effectiveTitle, normalizedTarget, detectedKind, "", editingId)
                        },
                    )
                }

                if (existingBookmarks.isNotEmpty()) {
                    AsciiRule(glyph = "-", color = c.border)
                    AsciiText("saved bookmarks ([edit] or [x] delete):", style = AsciiType.small, color = c.dim)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        existingBookmarks.forEach { bm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsciiText(
                                    text = "[b ${bm.alias}] ${bm.title}",
                                    style = AsciiType.small,
                                    color = if (editingId == bm.id) c.accent else c.fg,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                AsciiButton("edit", {
                                    editingId = bm.id
                                    bmAlias = TextFieldValue(bm.alias, TextRange(bm.alias.length))
                                    bmTitle = TextFieldValue(bm.title, TextRange(bm.title.length))
                                    bmTarget = TextFieldValue(bm.target, TextRange(bm.target.length))
                                })
                                if (onDeleteBookmark != null) {
                                    AsciiButton("x", { onDeleteBookmark(bm) })
                                }
                            }
                        }
                    }
                }
            }

            QuickAddKind.PIPE -> {
                val rawKw = pipeKeyword.text.trim()
                val cleanKw = rawKw.lowercase()
                val rawUrl = pipeUrl.text.trim()
                val normalizedTemplate = if (rawUrl.isNotEmpty()) QueryRouter.normalizePipeUrlTemplate(rawUrl) else ""
                val effectiveName = pipeName.text.trim().ifEmpty {
                    if (normalizedTemplate.isNotEmpty()) QueryRouter.inferTitleFromTarget(cleanKw.ifEmpty { "pipe" }, normalizedTemplate) else ""
                }

                val validation = QueryRouter.validateEngine(
                    name = effectiveName,
                    keyword = rawKw,
                    urlTemplate = normalizedTemplate,
                    existingEngines = existingEngines,
                    editingEngineId = editingId,
                )
                val kwError = if (rawKw.isNotEmpty() || rawUrl.isNotEmpty()) validation.keywordError else null
                val nameError = if (rawKw.isNotEmpty() && rawUrl.isNotEmpty()) validation.nameError else null
                val urlError = if (rawUrl.isNotEmpty() || rawKw.isNotEmpty()) validation.urlTemplateError else null
                val isPipeValid = validation.isValid

                val sampleQuery = "compose"
                val builtSampleUrl = if (normalizedTemplate.contains("{query}")) {
                    QueryRouter.buildSearchUrl(normalizedTemplate, sampleQuery)
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                } else {
                    "github.com/search?q=compose"
                }
                val previewPipeLine = "${cleanKw.ifEmpty { "gh" }} $sampleQuery -> $builtSampleUrl"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(0.38f)) {
                        AsciiTextField(
                            label = "s keyword",
                            promptPrefix = "s ",
                            value = pipeKeyword,
                            onValueChange = { pipeKeyword = it },
                            placeholder = "gh",
                            errorText = kwError,
                        )
                    }
                    Box(Modifier.weight(0.62f)) {
                        AsciiTextField(
                            label = "pipe name",
                            promptPrefix = "> ",
                            value = pipeName,
                            onValueChange = { pipeName = it },
                            placeholder = "GitHub",
                            errorText = nameError,
                        )
                    }
                }
                AsciiTextField(
                    label = "search url (must contain {query} or ?q=...)",
                    promptPrefix = "> ",
                    value = pipeUrl,
                    onValueChange = {
                        pipeUrl = it
                        if (pipeName.text.isBlank() && it.text.length > 4) {
                            val inf = QueryRouter.inferTitleFromTarget(pipeKeyword.text, it.text)
                            pipeName = TextFieldValue(inf, TextRange(inf.length))
                        }
                    },
                    placeholder = "https://github.com/search?q={query}",
                    errorText = urlError,
                    trailingControl = if (clipText.isNotEmpty()) {
                        {
                            AsciiButton("paste", {
                                val norm = QueryRouter.normalizePipeUrlTemplate(clipText)
                                pipeUrl = TextFieldValue(norm, TextRange(norm.length))
                                if (pipeName.text.isBlank()) {
                                    val inf = QueryRouter.inferTitleFromTarget(pipeKeyword.text, norm)
                                    pipeName = TextFieldValue(inf, TextRange(inf.length))
                                }
                            })
                        }
                    } else null,
                )

                AsciiLivePreviewLine(
                    previewText = previewPipeLine,
                    isValid = isPipeValid,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiButton("cancel", onCancel)
                    AsciiButton(
                        label = if (editingId != null) "update s" else "+ save s",
                        enabled = isPipeValid,
                        primary = isPipeValid,
                        onClick = {
                            onSavePipe(cleanKw, effectiveName, normalizedTemplate, editingId)
                        },
                    )
                }

                if (existingEngines.isNotEmpty()) {
                    AsciiRule(glyph = "-", color = c.border)
                    AsciiText("saved search pipes ([edit] or [x] delete):", style = AsciiType.small, color = c.dim)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        existingEngines.forEach { eng ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsciiText(
                                    text = "[s ${eng.keyword}] ${eng.name}",
                                    style = AsciiType.small,
                                    color = if (editingId == eng.id || eng.isDefault) c.accent else c.fg,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                AsciiButton("edit", {
                                    editingId = eng.id
                                    pipeKeyword = TextFieldValue(eng.keyword, TextRange(eng.keyword.length))
                                    pipeName = TextFieldValue(eng.name, TextRange(eng.name.length))
                                    pipeUrl = TextFieldValue(eng.urlTemplate, TextRange(eng.urlTemplate.length))
                                })
                                if (existingEngines.size > 1 && onDeleteEngine != null) {
                                    AsciiButton("x", { onDeleteEngine(eng) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grid-snapped Image Thumbnail Badge (`4x2` cells) using `CellBitmapImage` per `ascii-app` Section 5.
 * Tapping opens `AsciiImagePreviewDialog`.
 */
@Composable
fun AsciiThumbnailBadge(
    filePath: String,
    onClick: () -> Unit,
) {
    val c = LocalAscii.current
    var bitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(filePath) {
        bitmap = withContext(Dispatchers.IO) {
            com.sujan.pipe.data.DeviceIntegrationManager.loadImageThumbnail(filePath, 128)
        }
    }
    CellBitmapImage(
        bitmap = bitmap?.asImageBitmap(),
        contentDescription = "image preview thumbnail",
        cellCols = 4,
        cellRows = 2,
        placeholderText = "img",
        placeholderColor = c.cyan,
        modifier = Modifier
            .padding(end = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

/**
 * Grid-snapped Image Preview Modal (`AsciiModalBox` + `CellBitmapImage`) per `ascii-app` Sections 5 & 6.
 */
@Composable
fun AsciiImagePreviewDialog(
    file: LocalFileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    val grid = LocalGridMetrics.current
    var bitmap by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadFailed by remember(file.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(file.absolutePath) {
        val loaded = withContext(Dispatchers.IO) {
            com.sujan.pipe.data.DeviceIntegrationManager.loadImageThumbnail(file.absolutePath, 512)
        }
        bitmap = loaded
        loadFailed = (loaded == null)
    }
    Dialog(onDismissRequest = onDismiss) {
        AsciiModalBox(
            title = "preview?",
            onDismiss = onDismiss,
        ) {
            AsciiText(
                text = "${file.parentName} - ${file.formattedSize}",
                style = AsciiType.body,
                color = c.orange,
                maxLines = 1,
            )
            val previewCols = (grid.cols - 6).coerceIn(16, 32)
            CellBitmapImage(
                bitmap = bitmap?.asImageBitmap(),
                contentDescription = file.name,
                grid = grid,
                cellCols = previewCols,
                cellRows = 10,
                caption = file.name,
                placeholderText = if (loadFailed) "[ !img err ]" else "[ loading... ]",
                placeholderColor = if (loadFailed) c.red else c.cyan,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiButton("close", onDismiss, secondary = true)
                AsciiButton("shr", onShare)
                AsciiButton("open", onOpen, primary = true)
            }
        }
    }
}

/**
 * Fixed-width 1dp-bordered square-cornered kind tag (`app`, `c`, `f.`, `d/`, `b:bm`, `b:d/`, `b:f.`, `s:yt`, `def`, `url`, `clip`).
 */
@Composable
fun AsciiKindBadge(
    tag: String,
    color: Color,
    inverted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val bracketed = "[${tag.take(6)}]".padEnd(8, ' ')
    Box(
        modifier = modifier.width(64.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AsciiText(
            text = bracketed,
            style = AsciiType.small,
            color = if (inverted) c.bg else color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AsciiRaycastRow(
    item: com.sujan.pipe.domain.PaletteItem,
    isTop: Boolean,
    onExecute: () -> Unit,
    onOpenActionSheet: () -> Unit = {},
    onOpenAliasDialog: (() -> Unit)? = null,
    onSwipeRightSecondary: (() -> Unit)? = null,
    prefixes: PrefixConfig = PrefixConfig(),
    customAlias: String? = null,
    highlightQuery: String = "",
    isFolderExpanded: Boolean = false,
    expandedChildren: List<LocalFileItem> = emptyList(),
    onToggleFolderPeek: ((String) -> Unit)? = null,
    onOpenChildFile: ((LocalFileItem) -> Unit)? = null,
    onShareChildFile: ((LocalFileItem) -> Unit)? = null,
    onPreviewChildImage: ((LocalFileItem) -> Unit)? = null,
    onExecuteDirectNumber: (com.sujan.pipe.domain.PaletteItem.DirectNumberAction, DirectNumberPrimaryMode) -> Unit = { _, _ -> },
    onExecuteTimerAlarm: (com.sujan.pipe.domain.PaletteItem.TimerAlarmAction, Boolean) -> Unit = { _, _ -> },
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val rowBg = when {
        pressed || isTop -> c.surface
        else -> Color.Transparent
    }
    val pointer = if (isTop) ">" else " "

    val tagText: String
    val tagColor: Color
    val titleText: String
    val subtitleText: String
    val hasActionSheet: Boolean
    val folderPeekPath: String?

    when (item) {
        is com.sujan.pipe.domain.PaletteItem.DirectUrlAction -> {
            tagText = "url"
            tagColor = c.good
            titleText = "Open ${item.displayHost}"
            subtitleText = item.targetUrl
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.SystemSettingAction -> {
            tagText = "${prefixes.systemSettingsClean}:${item.shortcut.alias}"
            tagColor = if (item.shortcut.internalPipeSection != null) c.accent else c.accent2
            titleText = item.shortcut.title
            subtitleText = item.shortcut.subtitle
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.AppShortcutAction -> {
            val sc = item.shortcut
            tagText = "${prefixes.appsClean}:${sc.kind.badge}"
            tagColor = when (sc.kind) {
                AppShortcutKind.APP_PREFERENCES, AppShortcutKind.APP_SYSTEM_INFO -> c.accent2
                else -> c.accent
            }
            titleText = "${sc.appLabel} - ${sc.shortLabel}"
            subtitleText = sc.longLabel
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.DirectNumberAction -> {
            tagText = item.primaryMode.badge
            tagColor = c.good
            titleText = "${item.primaryMode.label}: +${item.normalizedWhatsAppDigits}"
            subtitleText = if (item.messageText.isNotBlank()) "msg: \"${item.messageText}\" - raw: ${item.rawPhone}" else "tap [wa], [wa.b], [call], or [...] for SMS / Save Contact"
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.TimerAlarmAction -> {
            tagText = if (item.isAlarm) "alm" else "tmr"
            tagColor = c.warn
            titleText = item.title
            subtitleText = item.subtitle
            hasActionSheet = false
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.DictionaryAction -> {
            tagText = prefixes.dictionaryClean
            tagColor = c.accent2
            if (item.isSetupPrompt || item.entry == null) {
                titleText = item.setupTitle.ifEmpty { "Load Offline Dictionary File" }
                subtitleText = item.setupSubtitle
                hasActionSheet = false
            } else {
                val pos = if (item.entry.partOfSpeech.isNotBlank()) " (${item.entry.partOfSpeech})" else ""
                titleText = "${item.entry.word}$pos"
                subtitleText = item.entry.definition
                hasActionSheet = true
            }
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.ClipboardAction -> {
            tagText = prefixes.clipboardClean
            tagColor = c.accent3
            titleText = item.title
            subtitleText = item.subtitle
            hasActionSheet = false
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.AppAction -> {
            tagText = prefixes.appsClean
            tagColor = c.accent
            titleText = item.app.label
            subtitleText = item.app.packageName
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.ContactAction -> {
            tagText = prefixes.contactsClean
            tagColor = c.good
            titleText = item.contact.displayName
            subtitleText = item.contact.phoneNumber
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.FileAction -> {
            val f = item.file
            tagText = if (f.isDirectory) "d/" else "${prefixes.filesClean}."
            tagColor = if (f.isDirectory) c.warn else c.accent3
            titleText = f.name
            subtitleText = "${f.parentName} - ${f.formattedSize}"
            hasActionSheet = true
            folderPeekPath = if (f.isDirectory) f.absolutePath else null
        }
        is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> {
            val bm = item.bookmark
            val bPre = prefixes.bookmarksClean
            tagText = when (bm.kind) {
                BookmarkKind.FOLDER -> "$bPre:d/"
                BookmarkKind.FILE -> "$bPre:f."
                BookmarkKind.LINK -> "$bPre:bm"
            }
            tagColor = when (bm.kind) {
                BookmarkKind.FOLDER -> c.warn
                BookmarkKind.FILE -> c.accent3
                BookmarkKind.LINK -> c.accent2
            }
            titleText = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title
            subtitleText = if (bm.tags.isNotBlank()) "${bm.target} - ${bm.tags}" else bm.target
            hasActionSheet = true
            folderPeekPath = if (bm.kind == BookmarkKind.FOLDER) bm.target else null
        }
        is com.sujan.pipe.domain.PaletteItem.EngineAction -> {
            val eng = item.engine
            tagText = "${prefixes.pipesClean}:${eng.keyword}"
            tagColor = c.forRole(eng.colorRole)
            titleText = if (item.isHome) eng.name else "${eng.name}: \"${item.query}\""
            subtitleText = if (item.isHome) eng.homeUrl else item.targetUrl
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.HistoryAction -> {
            val h = item.entry
            tagText = prefixes.historyClean
            tagColor = c.accent2
            titleText = h.rawInput
            subtitleText = "-> ${h.engineName.lowercase()} (${h.resolvedUrl})"
            hasActionSheet = true
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.QuickAddAction -> {
            tagText = item.kind.tag
            tagColor = when (item.kind) {
                QuickAddKind.CONTACT -> c.good
                QuickAddKind.BOOKMARK -> c.accent2
                QuickAddKind.PIPE -> c.accent
            }
            titleText = item.title
            subtitleText = item.subtitle
            hasActionSheet = false
            folderPeekPath = null
        }
        is com.sujan.pipe.domain.PaletteItem.QuickRemoveAction -> {
            tagText = "-del"
            tagColor = c.error
            titleText = item.displayTitle
            subtitleText = "tap or press RET to delete"
            hasActionSheet = false
            folderPeekPath = null
        }
    }

    val effectiveTitleText = if (!customAlias.isNullOrBlank()) "$titleText (@$customAlias)" else titleText
    val titleAnnotated = remember(effectiveTitleText, highlightQuery, c.fg, c.yellow) {
        buildFzfHighlightedText(
            text = effectiveTitleText,
            highlightQuery = highlightQuery,
            baseColor = c.fg,
            highlightColor = c.yellow,
        )
    }
    val subAnnotated = remember(subtitleText, highlightQuery, c.dim, c.cyan) {
        buildFzfHighlightedText(
            text = subtitleText,
            highlightQuery = highlightQuery,
            baseColor = c.dim,
            highlightColor = c.cyan,
        )
    }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(rowBg)
                .pointerInput(item.usageKey, onOpenAliasDialog, onSwipeRightSecondary) {
                    var accumX = 0f
                    var triggered = false
                    detectHorizontalDragGestures(
                        onDragStart = { accumX = 0f; triggered = false },
                        onDragEnd = { accumX = 0f; triggered = false },
                        onDragCancel = { accumX = 0f; triggered = false },
                        onHorizontalDrag = { change, dragAmount ->
                            accumX += dragAmount
                            if (!triggered && accumX <= -swipeThresholdPx && onOpenAliasDialog != null) {
                                triggered = true
                                change.consume()
                                runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                onOpenAliasDialog()
                            } else if (!triggered && accumX >= swipeThresholdPx && onSwipeRightSecondary != null) {
                                triggered = true
                                change.consume()
                                runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                onSwipeRightSecondary()
                            }
                        },
                    )
                }
                .combinedClickable(
                    interactionSource = src,
                    indication = null,
                    role = Role.Button,
                    onClick = onExecute,
                    onLongClick = {
                        if (hasActionSheet) onOpenActionSheet() else onExecute()
                    },
                )
                .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = "$pointer ",
                style = if (isTop) AsciiType.title else AsciiType.body,
                color = if (isTop) c.purple else c.dim,
            )

            AsciiKindBadge(
                tag = tagText,
                color = tagColor,
                inverted = false,
            )

            Spacer(Modifier.width(6.dp))

            when {
                item is com.sujan.pipe.domain.PaletteItem.ContactAction -> {
                    AsciiContactAvatar(contact = item.contact)
                }
                item is com.sujan.pipe.domain.PaletteItem.FileAction && item.file.isImageFile -> {
                    AsciiThumbnailBadge(
                        filePath = item.file.absolutePath,
                        onClick = { onPreviewChildImage?.invoke(item.file) ?: onOpenActionSheet() },
                    )
                }
                item is com.sujan.pipe.domain.PaletteItem.BookmarkAction && item.bookmark.kind == BookmarkKind.LINK -> {
                    AsciiFaviconBadge(
                        url = item.bookmark.target,
                        fallbackText = item.bookmark.alias.ifBlank { item.bookmark.title },
                        fallbackColor = c.cyan,
                    )
                }
                item is com.sujan.pipe.domain.PaletteItem.EngineAction -> {
                    AsciiFaviconBadge(
                        url = item.engine.homeUrl,
                        fallbackText = item.engine.keyword,
                        fallbackColor = c.forRole(item.engine.colorRole),
                    )
                }
                item is com.sujan.pipe.domain.PaletteItem.DirectUrlAction -> {
                    AsciiFaviconBadge(
                        url = item.targetUrl,
                        fallbackText = item.displayHost.removePrefix("www."),
                        fallbackColor = c.green,
                    )
                }
                item is com.sujan.pipe.domain.PaletteItem.HistoryAction -> {
                    AsciiFaviconBadge(
                        url = item.entry.resolvedUrl,
                        fallbackText = item.entry.engineKeyword.ifBlank { item.entry.engineName },
                        fallbackColor = c.purple,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            ) {
                AsciiText(
                    text = titleAnnotated,
                    style = if (isTop) AsciiType.title else AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                )
                AsciiText(
                    text = subAnnotated,
                    style = AsciiType.small,
                    color = if (pressed) c.bg else c.dim,
                    maxLines = if (item is com.sujan.pipe.domain.PaletteItem.DictionaryAction) 2 else 1,
                )
            }

            if (folderPeekPath != null && onToggleFolderPeek != null) {
                AsciiButton(
                    label = if (isFolderExpanded) "-" else "+",
                    onClick = { onToggleFolderPeek(folderPeekPath) },
                    spokenLabel = "peek inside $titleText",
                )
            }



            if (isTop) {
                AsciiText(
                    text = "*1",
                    style = AsciiType.title,
                    color = c.yellow,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (item is com.sujan.pipe.domain.PaletteItem.DirectNumberAction) {
                AsciiButton(
                    label = "wa",
                    onClick = { onExecuteDirectNumber(item, DirectNumberPrimaryMode.WHATSAPP_PERSONAL) },
                    primary = isTop && item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_PERSONAL,
                )
                AsciiButton(
                    label = "wa.b",
                    onClick = { onExecuteDirectNumber(item, DirectNumberPrimaryMode.WHATSAPP_BUSINESS) },
                    primary = isTop && item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_BUSINESS,
                )
                AsciiButton(
                    label = "call",
                    onClick = { onExecuteDirectNumber(item, DirectNumberPrimaryMode.CALL) },
                    primary = isTop && item.primaryMode == DirectNumberPrimaryMode.CALL,
                )
            } else if (item is com.sujan.pipe.domain.PaletteItem.TimerAlarmAction) {
                AsciiButton(
                    label = "ui",
                    onClick = { onExecuteTimerAlarm(item, false) },
                )
                AsciiButton(
                    label = "start",
                    onClick = { onExecuteTimerAlarm(item, true) },
                    primary = isTop,
                )
            }

            if (hasActionSheet) {
                AsciiButton(
                    label = "...",
                    onClick = onOpenActionSheet,
                    spokenLabel = "more actions for $titleText",
                )
            } else if (item is com.sujan.pipe.domain.PaletteItem.DictionaryAction && item.isSetupPrompt) {
                AsciiButton(
                    label = "load",
                    onClick = onExecute,
                    primary = true,
                )
            } else if (item is com.sujan.pipe.domain.PaletteItem.ClipboardAction) {
                AsciiButton(
                    label = "run",
                    onClick = onExecute,
                    primary = isTop,
                )
            } else if (item is com.sujan.pipe.domain.PaletteItem.QuickAddAction) {
                AsciiButton(
                    label = if (item.isReadyToCommit) "save" else "form",
                    onClick = onExecute,
                    primary = item.isReadyToCommit,
                )
            } else if (item is com.sujan.pipe.domain.PaletteItem.QuickRemoveAction) {
                AsciiButton(
                    label = "del",
                    onClick = onExecute,
                    primary = true,
                )
            }
        }

        if (isFolderExpanded && folderPeekPath != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .padding(start = 28.dp, end = 6.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (expandedChildren.isEmpty()) {
                    AsciiText(
                        text = "`-- (empty folder)",
                        style = AsciiType.small,
                        color = c.dim,
                    )
                } else {
                    val visibleChildren = expandedChildren.take(8)
                    visibleChildren.forEachIndexed { childIdx, child ->
                        val branchPrefix = if (childIdx == visibleChildren.lastIndex) "`-- " else "|-- "
                        val childBaseCol = if (child.isDirectory) c.warn else c.fg
                        val childHighlighted = remember(child.name, highlightQuery, childBaseCol, c.accent) {
                            buildFzfHighlightedText(
                                text = "$branchPrefix${child.name}",
                                highlightQuery = highlightQuery,
                                baseColor = childBaseCol,
                                highlightColor = c.accent,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 38.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpenChildFile?.invoke(child) },
                                )
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (child.isImageFile && onPreviewChildImage != null) {
                                AsciiThumbnailBadge(
                                    filePath = child.absolutePath,
                                    onClick = { onPreviewChildImage(child) },
                                )
                            }
                            AsciiText(
                                text = childHighlighted,
                                style = AsciiType.small,
                                color = childBaseCol,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            if (!child.isDirectory && onShareChildFile != null) {
                                AsciiButton(
                                    label = "shr",
                                    onClick = { onShareChildFile(child) },
                                    spokenLabel = "share ${child.name}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AsciiSheetActionRow(
    tag: String,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    isPrimary: Boolean = false,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val ink = when {
        isDestructive -> c.error
        isPrimary -> c.accent
        else -> c.fg
    }
    val ptr = if (isPrimary) ">" else " "
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .background(if (pressed) ink else Color.Transparent)
            .clickable(
                interactionSource = src,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = "$ptr [${tag.take(6)}]".padEnd(11, ' '),
            style = AsciiType.title,
            color = if (pressed) c.bg else ink,
            maxLines = 1,
            softWrap = false,
        )
        AsciiText(
            text = label,
            style = if (isPrimary) AsciiType.title else AsciiType.body,
            color = if (pressed) c.bg else ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun AsciiResultActionSheet(
    item: com.sujan.pipe.domain.PaletteItem,
    currentAlias: String? = null,
    onSetAliasClick: () -> Unit = {},
    canDeleteEngine: Boolean,
    onDismiss: () -> Unit,
    onPrimaryExecute: () -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onWhatsAppBusinessContact: (ContactItem) -> Unit = {},
    onExecuteDirectNumber: (com.sujan.pipe.domain.PaletteItem.DirectNumberAction, DirectNumberPrimaryMode) -> Unit = { _, _ -> },
    onScopeFolder: (String) -> Unit,
    onBookmarkFile: (LocalFileItem) -> Unit,
    onShareFile: (LocalFileItem) -> Unit,
    onPreviewImage: (LocalFileItem) -> Unit,
    onEditBookmark: (PipeBookmark) -> Unit,
    onDeleteBookmark: (PipeBookmark) -> Unit,
    onEditEngine: (SearchEngine) -> Unit,
    onDeleteEngine: (SearchEngine) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit = {},
    onAppInfoInstalledApp: (InstalledAppItem) -> Unit = {},
    onUninstallInstalledApp: (InstalledAppItem) -> Unit = {},
    onCopyText: (String) -> Unit,
    prefixes: PrefixConfig = PrefixConfig(),
) {
    val c = LocalAscii.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (item) {
                    is com.sujan.pipe.domain.PaletteItem.DirectNumberAction -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge(item.primaryMode.badge, c.good)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText("+${item.normalizedWhatsAppDigits}", style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText("raw: ${item.rawPhone}", style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow("wa", "Open in WhatsApp (Personal)", { onExecuteDirectNumber(item, DirectNumberPrimaryMode.WHATSAPP_PERSONAL) }, isPrimary = item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_PERSONAL)
                        AsciiSheetActionRow("wa.b", "Open in WhatsApp Business", { onExecuteDirectNumber(item, DirectNumberPrimaryMode.WHATSAPP_BUSINESS) }, isPrimary = item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_BUSINESS)
                        AsciiSheetActionRow("call", "Dial ${item.rawPhone}", { onExecuteDirectNumber(item, DirectNumberPrimaryMode.CALL) })
                        AsciiSheetActionRow("sms", "Send SMS to ${item.rawPhone}", { onExecuteDirectNumber(item, DirectNumberPrimaryMode.SMS) })
                        AsciiSheetActionRow("copy", "Copy phone number", { onCopyText(item.rawPhone) })
                    }

                    is com.sujan.pipe.domain.PaletteItem.DirectUrlAction -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge("url", c.good)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(item.displayHost, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(item.targetUrl, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        AsciiSheetActionRow("open", "Open ${item.targetUrl}", onPrimaryExecute, isPrimary = true)
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("copy", "Copy clean URL", { onCopyText(QueryRouter.stripTrackingParams(item.targetUrl)) })
                    }

                    is com.sujan.pipe.domain.PaletteItem.DictionaryAction -> {
                        val entry = item.entry
                        if (entry != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    AsciiKindBadge(prefixes.dictionaryClean, c.accent2)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        AsciiText("${entry.word} (${entry.partOfSpeech})", style = AsciiType.title, color = c.fg, maxLines = 1)
                                        AsciiText(entry.definition, style = AsciiType.small, color = c.dim, maxLines = 2)
                                    }
                                }
                                AsciiButton("x", onDismiss)
                            }
                            AsciiRule(glyph = "-", color = c.border)
                            AsciiSheetActionRow("view", "View full definition card", onPrimaryExecute, isPrimary = true)
                            AsciiSheetActionRow("copy", "Copy definition", { onCopyText("${entry.word} (${entry.partOfSpeech}): ${entry.definition}") })
                        } else {
                            AsciiButton("close", onDismiss)
                        }
                    }

                    is com.sujan.pipe.domain.PaletteItem.ContactAction -> {
                        val contact = item.contact
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge(prefixes.contactsClean, c.good)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(contact.displayName, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(contact.phoneNumber, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        AsciiSheetActionRow("call", "Call ${contact.phoneNumber}", { onCallContact(contact) }, isPrimary = true)
                        AsciiSheetActionRow("sms", "Send SMS to ${contact.displayName}", { onSmsContact(contact) })
                        AsciiSheetActionRow("wa", "Message on WhatsApp (Personal)", { onWhatsAppContact(contact) })
                        AsciiSheetActionRow("wa.b", "Message on WhatsApp Business", { onWhatsAppBusinessContact(contact) })
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("copy", "Copy phone number", { onCopyText(contact.phoneNumber) })
                    }

                    is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> {
                        val bm = item.bookmark
                        val bPre = prefixes.bookmarksClean
                        val tag = when (bm.kind) {
                            BookmarkKind.FOLDER -> "$bPre:d/"
                            BookmarkKind.FILE -> "$bPre:f."
                            BookmarkKind.LINK -> "$bPre:bm"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge(tag, c.accent2)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText("${bm.title} (${bm.alias})", style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(bm.target, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow("open", "Open ${bm.title}", onPrimaryExecute, isPrimary = true)
                        if (bm.kind == BookmarkKind.FOLDER && bm.alias.isNotBlank()) {
                            AsciiSheetActionRow("ls", "Scope inside folder ($bPre ${bm.alias})", { onScopeFolder("$bPre ${bm.alias}") })
                        }
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("edit", "Edit bookmark", { onEditBookmark(bm) })
                        AsciiSheetActionRow("copy", "Copy target URL / path", { onCopyText(bm.target) })
                        AsciiSheetActionRow("delete", "Delete bookmark", { onDeleteBookmark(bm) }, isDestructive = true)
                    }

                    is com.sujan.pipe.domain.PaletteItem.FileAction -> {
                        val f = item.file
                        val fPre = prefixes.filesClean
                        val tag = if (f.isDirectory) "d/" else "$fPre."
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge(tag, if (f.isDirectory) c.warn else c.accent3)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(f.name, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText("${f.parentName} - ${f.formattedSize}", style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow("open", "Open ${f.name}", onPrimaryExecute, isPrimary = true)
                        if (f.isDirectory) {
                            AsciiSheetActionRow("ls", "Filter inside folder ($fPre ${f.name})", { onScopeFolder("$fPre ${f.name}") })
                        }
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("bookmark", "Save as bookmark", { onBookmarkFile(f) })
                        if (!f.isDirectory) {
                            AsciiSheetActionRow("share", "Share file", { onShareFile(f) })
                        }
                        if (f.isImageFile) {
                            AsciiSheetActionRow("preview", "Preview image", { onPreviewImage(f) })
                        }
                        AsciiSheetActionRow("copy", "Copy full path", { onCopyText(f.absolutePath) })
                    }

                    is com.sujan.pipe.domain.PaletteItem.EngineAction -> {
                        val eng = item.engine
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge("${prefixes.pipesClean}:${eng.keyword}", c.forRole(eng.colorRole))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(eng.name, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(eng.urlTemplate, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow(
                            tag = "open",
                            label = if (item.isHome) "Open ${eng.name} home" else "Pipe \"${item.query}\" to ${eng.name}",
                            onClick = onPrimaryExecute,
                            isPrimary = true,
                        )
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("edit", "Edit search pipe", { onEditEngine(eng) })
                        if (canDeleteEngine) {
                            AsciiSheetActionRow("delete", "Delete search pipe", { onDeleteEngine(eng) }, isDestructive = true)
                        }
                    }

                    is com.sujan.pipe.domain.PaletteItem.AppAction -> {
                        val app = item.app
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge(prefixes.appsClean, c.accent)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(app.label, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(app.packageName, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        AsciiSheetActionRow("open", "Launch ${app.label}", onPrimaryExecute, isPrimary = true)
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("info", "Open Android App Info", { onAppInfoInstalledApp(app) })
                        AsciiSheetActionRow("copy", "Copy package name", { onCopyText(app.packageName) })
                        AsciiSheetActionRow("uninstall", "Uninstall ${app.label}", { onUninstallInstalledApp(app) }, isDestructive = true)
                    }

                    is com.sujan.pipe.domain.PaletteItem.AppShortcutAction -> {
                        val sc = item.shortcut
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge("${prefixes.appsClean}:${sc.kind.badge}", c.accent2)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText("${sc.appLabel} - ${sc.shortLabel}", style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(sc.longLabel, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow("open", "Launch ${sc.shortLabel}", onPrimaryExecute, isPrimary = true)
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("info", "Open ${sc.appLabel} App Info", {
                            onAppInfoInstalledApp(InstalledAppItem(sc.appLabel, sc.packageName))
                        })
                        AsciiSheetActionRow("copy", "Copy package (${sc.packageName})", { onCopyText(sc.packageName) })
                    }

                    is com.sujan.pipe.domain.PaletteItem.SystemSettingAction -> {
                        val sc = item.shortcut
                        val aliasLabel = if (!currentAlias.isNullOrBlank()) "Edit alias (@$currentAlias)" else "Set quick alias (@...)"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge("${prefixes.systemSettingsClean}:${sc.alias}", if (sc.internalPipeSection != null) c.accent else c.accent2)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(sc.title, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(sc.subtitle, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow("open", "Open ${sc.title}", onPrimaryExecute, isPrimary = true)
                        AsciiSheetActionRow("alias", aliasLabel, onSetAliasClick)
                        AsciiSheetActionRow("copy", "Copy command (${prefixes.systemSettingsClean} ${sc.alias})", {
                            onCopyText("${prefixes.systemSettingsClean} ${sc.alias}")
                        })
                    }

                    is com.sujan.pipe.domain.PaletteItem.HistoryAction -> {
                        val h = item.entry
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsciiKindBadge(prefixes.historyClean, c.accent2)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    AsciiText(h.rawInput, style = AsciiType.title, color = c.fg, maxLines = 1)
                                    AsciiText(h.resolvedUrl, style = AsciiType.small, color = c.dim, maxLines = 1)
                                }
                            }
                            AsciiButton("x", onDismiss)
                        }
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiSheetActionRow("open", "Re-run \"${h.rawInput}\"", onPrimaryExecute, isPrimary = true)
                        AsciiSheetActionRow("copy", "Copy query text", { onCopyText(h.rawInput) })
                        AsciiSheetActionRow("delete", "Delete from history", { onDeleteHistory(h) }, isDestructive = true)
                    }

                    else -> {
                        AsciiButton("close", onDismiss)
                    }
                }
            }
        }
    }
}

enum class LibraryTab(val label: String) {
    PIPES("pipes"),
    BOOKMARKS("bookmarks"),
    ALIASES("aliases"),
    HISTORY("history"),
}

private sealed interface LibrarySheetTarget {
    data class PipeTarget(val engine: SearchEngine) : LibrarySheetTarget
    data class BookmarkTarget(val bookmark: PipeBookmark) : LibrarySheetTarget
    data class HistoryTarget(val entry: SearchHistoryEntry) : LibrarySheetTarget
}

/**
 * Merged Library Screen (`~/pipe/lib`) with flush-aligned equal-width tabs (`Modifier.weight(1f)`).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PipeLibraryScreen(
    initialTab: LibraryTab = LibraryTab.PIPES,
    engines: List<SearchEngine>,
    bookmarks: List<PipeBookmark>,
    history: List<SearchHistoryEntry>,
    universalAliases: List<UniversalAlias> = emptyList(),
    prefixes: PrefixConfig = PrefixConfig(),
    onSaveUniversalAlias: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRemoveUniversalAlias: (String) -> Unit = {},
    onOpenSystemSetting: (SystemSettingShortcut) -> Unit = {},
    engineForm: EngineFormState,
    bookmarkForm: BookmarkFormState,
    onBack: () -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onAddBookmarkClick: () -> Unit,
    onEditBookmarkClick: (PipeBookmark) -> Unit,
    onDeleteBookmarkClick: (PipeBookmark) -> Unit,
    onBookmarkAliasChange: (TextFieldValue) -> Unit,
    onBookmarkTitleChange: (TextFieldValue) -> Unit,
    onBookmarkTargetChange: (TextFieldValue) -> Unit,
    onBookmarkTagsChange: (TextFieldValue) -> Unit,
    onBookmarkKindChange: (BookmarkKind) -> Unit,
    onBookmarkSave: () -> Unit,
    onBookmarkCancel: () -> Unit,
    onAddEngineClick: () -> Unit,
    onEditEngineClick: (SearchEngine) -> Unit,
    onDeleteEngineClick: (SearchEngine) -> Unit,
    onMoveEngineUp: (Int) -> Unit,
    onMoveEngineDown: (Int) -> Unit,
    onSetDefaultEngine: (String) -> Unit,
    onEngineNameChange: (TextFieldValue) -> Unit,
    onEngineKeywordChange: (TextFieldValue) -> Unit,
    onEngineUrlChange: (TextFieldValue) -> Unit,
    onEngineColorChange: (EngineColorRole) -> Unit,
    onEngineDefaultChange: (Boolean) -> Unit,
    onEngineSave: () -> Unit,
    onEngineCancel: () -> Unit,
    onRerunHistory: (SearchHistoryEntry) -> Unit,
    onFillHistory: (SearchHistoryEntry) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val dragStepPx = remember(density) { with(density) { 48.dp.toPx() } }

    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var sheetTarget by remember { mutableStateOf<LibrarySheetTarget?>(null) }
    var editingAliasTarget by remember { mutableStateOf<UniversalAlias?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        AsciiTopHeader(
            path = "~/pipe/lib",
            actions = {
                AsciiButton(label = "< back", onClick = onBack, spokenLabel = "back to search prompt")
            },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
        when (activeTab) {
            LibraryTab.PIPES -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        AsciiText(
                            text = "drag [::] to reorder pipes. `*` marks the default fallback pipe.",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                    }

                    itemsIndexed(engines, key = { _, item -> item.id }) { idx, eng ->
                        var dragAccumY by remember(eng.id, idx) { mutableStateOf(0f) }
                        var isDragging by remember(eng.id) { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .background(if (isDragging) c.surface else c.bg)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onSetDefaultEngine(eng.id) },
                                    onLongClick = { sheetTarget = LibrarySheetTarget.PipeTarget(eng) },
                                )
                                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 44.dp)
                                    .pointerInput(idx, engines.size) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                dragAccumY = 0f
                                                isDragging = true
                                            },
                                            onDragEnd = {
                                                dragAccumY = 0f
                                                isDragging = false
                                            },
                                            onDragCancel = {
                                                dragAccumY = 0f
                                                isDragging = false
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumY += dragAmount
                                                if (dragAccumY >= dragStepPx && idx < engines.lastIndex) {
                                                    dragAccumY -= dragStepPx
                                                    onMoveEngineDown(idx)
                                                } else if (dragAccumY <= -dragStepPx && idx > 0) {
                                                    dragAccumY += dragStepPx
                                                    onMoveEngineUp(idx)
                                                }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsciiText(
                                    text = "::",
                                    style = AsciiType.title,
                                    color = if (isDragging) c.accent else c.dim,
                                )
                            }

                            AsciiFaviconBadge(
                                url = eng.homeUrl,
                                fallbackText = eng.keyword,
                                fallbackColor = c.forRole(eng.colorRole),
                            )
                            AsciiKindBadge(
                                tag = "${prefixes.pipesClean}:${eng.keyword}",
                                color = c.forRole(eng.colorRole),
                            )

                            Spacer(Modifier.width(6.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsciiText(
                                        text = eng.name,
                                        style = AsciiType.title,
                                        color = if (eng.isDefault) c.accent else c.fg,
                                        maxLines = 1,
                                    )
                                    if (eng.isDefault) {
                                        AsciiText(
                                            text = " *",
                                            style = AsciiType.title,
                                            color = c.accent,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                AsciiText(
                                    text = eng.urlTemplate,
                                    style = AsciiType.small,
                                    color = c.dim,
                                    maxLines = 1,
                                )
                            }

                            AsciiButton(
                                label = "...",
                                onClick = { sheetTarget = LibrarySheetTarget.PipeTarget(eng) },
                                spokenLabel = "options for ${eng.name}",
                            )
                        }
                    }
                }

                AsciiRule(glyph = "-", color = c.border)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    AsciiButton(
                        label = "+ add pipe",
                        onClick = onAddEngineClick,
                        primary = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp),
                        spokenLabel = "add pipe",
                    )
                }
            }

            LibraryTab.BOOKMARKS -> {
                val bPre = prefixes.bookmarksClean
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(bookmarks, key = { it.id }) { bm ->
                        val badge = when (bm.kind) {
                            BookmarkKind.FOLDER -> "$bPre:d/"
                            BookmarkKind.FILE -> "$bPre:f."
                            BookmarkKind.LINK -> "$bPre:bm"
                        }
                        val badgeCol = when (bm.kind) {
                            BookmarkKind.FOLDER -> c.warn
                            BookmarkKind.FILE -> c.accent3
                            BookmarkKind.LINK -> c.accent2
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .background(c.bg)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onOpenBookmark(bm) },
                                    onLongClick = { sheetTarget = LibrarySheetTarget.BookmarkTarget(bm) },
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (bm.kind == BookmarkKind.LINK) {
                                AsciiFaviconBadge(
                                    url = bm.target,
                                    fallbackText = bm.alias.ifBlank { bm.title },
                                    fallbackColor = c.cyan,
                                )
                            }
                            AsciiKindBadge(tag = badge, color = badgeCol)
                            Spacer(Modifier.width(6.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp),
                            ) {
                                AsciiText(
                                    text = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title,
                                    style = AsciiType.title,
                                    color = c.fg,
                                    maxLines = 1,
                                )
                                AsciiText(
                                    text = if (bm.tags.isNotBlank()) "${bm.target} - ${bm.tags}" else bm.target,
                                    style = AsciiType.small,
                                    color = c.dim,
                                    maxLines = 1,
                                )
                            }
                            AsciiButton(
                                label = "...",
                                onClick = { sheetTarget = LibrarySheetTarget.BookmarkTarget(bm) },
                                spokenLabel = "options for ${bm.title}",
                            )
                        }
                    }
                }

                AsciiRule(glyph = "-", color = c.border)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    AsciiButton(
                        label = "+ add bookmark",
                        onClick = onAddBookmarkClick,
                        primary = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp),
                        spokenLabel = "add bookmark",
                    )
                }
            }

            LibraryTab.ALIASES -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        AsciiText(
                            text = "universal aliases (@alias) boost any setting, app, shortcut, file, contact, bookmark, or pipe to #1:",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                    }
                    if (universalAliases.isEmpty()) {
                        item {
                            AsciiText(
                                text = "no custom aliases yet. tap [@+] or [...] -> [alias] on any search result in ~/pipe or the overlay to set one.",
                                style = AsciiType.small,
                                color = c.accent2,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(universalAliases, key = { it.targetKey }) { ua: UniversalAlias ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .background(c.bg)
                                    .clickable { editingAliasTarget = ua }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsciiKindBadge(tag = "@${ua.alias}", color = c.accent)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    AsciiText(
                                        text = "${ua.label} [${ua.category}]",
                                        style = AsciiType.title,
                                        color = c.fg,
                                        maxLines = 1,
                                    )
                                    if (ua.subtitle.isNotBlank()) {
                                        AsciiText(
                                            text = ua.subtitle,
                                            style = AsciiType.small,
                                            color = c.dim,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                AsciiButton("edit", { editingAliasTarget = ua })
                                AsciiButton("x", { onRemoveUniversalAlias(ua.targetKey) })
                            }
                        }
                    }

                    item {
                        AsciiRule(glyph = "-", color = c.border)
                        AsciiText(
                            text = "+- pipe & android settings jumpers (tap [@+] to set custom alias):",
                            style = AsciiType.title,
                            color = c.accent2,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    items(DefaultSystemShortcuts.LIST, key = { it.id }) { sc: SystemSettingShortcut ->
                        val tKey = "sys:${sc.id}"
                        val curAlias = universalAliases.firstOrNull { it.targetKey == tKey }?.alias
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .background(c.bg)
                                .clickable { onOpenSystemSetting(sc) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsciiKindBadge(
                                tag = "${prefixes.systemSettingsClean}:${sc.alias}",
                                color = if (sc.internalPipeSection != null) c.accent else c.accent2,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                AsciiText(
                                    text = if (!curAlias.isNullOrBlank()) "${sc.title} (@$curAlias)" else sc.title,
                                    style = AsciiType.body,
                                    color = c.fg,
                                    maxLines = 1,
                                )
                                AsciiText(
                                    text = sc.subtitle,
                                    style = AsciiType.small,
                                    color = c.dim,
                                    maxLines = 1,
                                )
                            }
                            AsciiButton(
                                label = if (!curAlias.isNullOrBlank()) "@$curAlias" else "@+",
                                onClick = {
                                    editingAliasTarget = UniversalAlias(
                                        targetKey = tKey,
                                        alias = curAlias.orEmpty(),
                                        label = sc.title,
                                        category = if (sc.internalPipeSection != null) "pipe:set" else "sys:${sc.alias}",
                                        subtitle = sc.subtitle,
                                    )
                                },
                            )
                            AsciiButton("open", { onOpenSystemSetting(sc) })
                        }
                    }
                }
            }

            LibraryTab.HISTORY -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (history.isEmpty()) {
                        item {
                            AsciiText(
                                text = "no command history recorded yet.",
                                style = AsciiType.small,
                                color = c.dim,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    } else {
                        items(history, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .background(c.bg)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = { onRerunHistory(entry) },
                                        onLongClick = { sheetTarget = LibrarySheetTarget.HistoryTarget(entry) },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsciiFaviconBadge(
                                    url = entry.resolvedUrl,
                                    fallbackText = entry.engineKeyword.ifBlank { entry.engineName },
                                    fallbackColor = c.purple,
                                )
                                AsciiKindBadge(tag = prefixes.historyClean, color = c.accent2)
                                Spacer(Modifier.width(6.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp),
                                ) {
                                    AsciiText(
                                        text = entry.rawInput,
                                        style = AsciiType.title,
                                        color = c.fg,
                                        maxLines = 1,
                                    )
                                    AsciiText(
                                        text = "-> ${entry.engineName.lowercase()} (${entry.resolvedUrl})",
                                        style = AsciiType.small,
                                        color = c.dim,
                                        maxLines = 1,
                                    )
                                }
                                AsciiButton(
                                    label = "...",
                                    onClick = { sheetTarget = LibrarySheetTarget.HistoryTarget(entry) },
                                    spokenLabel = "options for ${entry.rawInput}",
                                )
                            }
                        }
                    }
                }

                AsciiRule(glyph = "-", color = c.border)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    AsciiButton(
                        label = "clear history",
                        destructive = true,
                        onClick = onClearHistory,
                        enabled = history.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        spokenLabel = "clear history",
                    )
                }
            }
        }
        }
        }

        // Thumb-zone bottom tabs with FULL NAMES (2 rows x 2 columns)
        Column(modifier = Modifier.fillMaxWidth().background(c.bg)) {
            AsciiCommandBar(
                items = listOf(
                    Triple("pipes (${engines.size})", activeTab == LibraryTab.PIPES) { activeTab = LibraryTab.PIPES },
                    Triple("bookmarks (${bookmarks.size})", activeTab == LibraryTab.BOOKMARKS) { activeTab = LibraryTab.BOOKMARKS },
                )
            )
            AsciiCommandBar(
                items = listOf(
                    Triple("aliases (${universalAliases.size})", activeTab == LibraryTab.ALIASES) { activeTab = LibraryTab.ALIASES },
                    Triple("history (${history.size})", activeTab == LibraryTab.HISTORY) { activeTab = LibraryTab.HISTORY },
                )
            )
        }
    }

    val currentTarget = sheetTarget
    if (currentTarget != null) {
        Dialog(onDismissRequest = { sheetTarget = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { sheetTarget = null },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (currentTarget) {
                        is LibrarySheetTarget.PipeTarget -> {
                            val eng = currentTarget.engine
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    AsciiKindBadge("${prefixes.pipesClean}:${eng.keyword}", c.forRole(eng.colorRole))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        AsciiText(if (eng.isDefault) "${eng.name} *" else eng.name, style = AsciiType.title, color = c.fg, maxLines = 1)
                                        AsciiText(eng.urlTemplate, style = AsciiType.small, color = c.dim, maxLines = 1)
                                    }
                                }
                                AsciiButton("x", { sheetTarget = null })
                            }
                            AsciiRule(glyph = "-", color = c.border)
                            if (!eng.isDefault) {
                                AsciiSheetActionRow("default", "Set as default pipe (*)", {
                                    sheetTarget = null
                                    onSetDefaultEngine(eng.id)
                                }, isPrimary = true)
                            }
                            AsciiSheetActionRow("edit", "Edit search pipe", {
                                sheetTarget = null
                                onEditEngineClick(eng)
                            }, isPrimary = eng.isDefault)
                            if (engines.size > 1) {
                                AsciiSheetActionRow("delete", "Delete search pipe", {
                                    sheetTarget = null
                                    onDeleteEngineClick(eng)
                                }, isDestructive = true)
                            }
                        }

                        is LibrarySheetTarget.BookmarkTarget -> {
                            val bm = currentTarget.bookmark
                            val bPre = prefixes.bookmarksClean
                            val badge = when (bm.kind) {
                                BookmarkKind.FOLDER -> "$bPre:d/"
                                BookmarkKind.FILE -> "$bPre:f."
                                BookmarkKind.LINK -> "$bPre:bm"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    AsciiKindBadge(badge, c.accent2)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        AsciiText("${bm.title} (${bm.alias})", style = AsciiType.title, color = c.fg, maxLines = 1)
                                        AsciiText(bm.target, style = AsciiType.small, color = c.dim, maxLines = 1)
                                    }
                                }
                                AsciiButton("x", { sheetTarget = null })
                            }
                            AsciiRule(glyph = "-", color = c.border)
                            AsciiSheetActionRow("open", "Open ${bm.title}", {
                                sheetTarget = null
                                onOpenBookmark(bm)
                            }, isPrimary = true)
                            AsciiSheetActionRow("edit", "Edit bookmark", {
                                sheetTarget = null
                                onEditBookmarkClick(bm)
                            })
                            AsciiSheetActionRow("copy", "Copy target URL / path", {
                                clipboard.setText(AnnotatedString(bm.target))
                                sheetTarget = null
                            })
                            AsciiSheetActionRow("delete", "Delete bookmark", {
                                sheetTarget = null
                                onDeleteBookmarkClick(bm)
                            }, isDestructive = true)
                        }

                        is LibrarySheetTarget.HistoryTarget -> {
                            val entry = currentTarget.entry
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    AsciiKindBadge(prefixes.historyClean, c.accent2)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        AsciiText(entry.rawInput, style = AsciiType.title, color = c.fg, maxLines = 1)
                                        AsciiText(entry.resolvedUrl, style = AsciiType.small, color = c.dim, maxLines = 1)
                                    }
                                }
                                AsciiButton("x", { sheetTarget = null })
                            }
                            AsciiRule(glyph = "-", color = c.border)
                            AsciiSheetActionRow("open", "Re-run query", {
                                sheetTarget = null
                                onRerunHistory(entry)
                            }, isPrimary = true)
                            AsciiSheetActionRow("fill", "Fill into search box", {
                                sheetTarget = null
                                onFillHistory(entry)
                            })
                            AsciiSheetActionRow("copy", "Copy query text", {
                                clipboard.setText(AnnotatedString(entry.rawInput))
                                sheetTarget = null
                            })
                            AsciiSheetActionRow("delete", "Delete history item", {
                                sheetTarget = null
                                onDeleteHistory(entry)
                            }, isDestructive = true)
                        }
                    }
                }
            }
        }
    }

    if (editingAliasTarget != null) {
        val t = editingAliasTarget!!
        AsciiSetAliasDialog(
            target = t,
            onSave = { newAlias ->
                onSaveUniversalAlias(t.targetKey, newAlias, t.label, t.category, t.subtitle)
                editingAliasTarget = null
            },
            onRemove = {
                onRemoveUniversalAlias(t.targetKey)
                editingAliasTarget = null
            },
            onDismiss = { editingAliasTarget = null },
        )
    }

    if (bookmarkForm.isOpen) {
        Dialog(onDismissRequest = onBookmarkCancel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                AsciiBox(title = if (bookmarkForm.editingId != null) "edit bookmark?" else "new bookmark?", isModal = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsciiDropdownRow(
                            label = "bookmark type",
                            selectedValue = bookmarkForm.kind,
                            options = BookmarkKind.entries.map { it to it.name.lowercase() },
                            onSelect = onBookmarkKindChange,
                        )
                        AsciiTextField(
                            label = "${prefixes.bookmarksClean} alias (e.g. dl, docs, hn)",
                            promptPrefix = "${prefixes.bookmarksClean} ",
                            value = bookmarkForm.alias,
                            onValueChange = onBookmarkAliasChange,
                            placeholder = "dl",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "title",
                            promptPrefix = "> ",
                            value = bookmarkForm.title,
                            onValueChange = onBookmarkTitleChange,
                            placeholder = "Downloads",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = if (bookmarkForm.kind == BookmarkKind.LINK) "url (https://...)" else "phone path (/storage/emulated/0/...)",
                            promptPrefix = "> ",
                            value = bookmarkForm.target,
                            onValueChange = onBookmarkTargetChange,
                            placeholder = if (bookmarkForm.kind == BookmarkKind.LINK) "https://news.ycombinator.com" else "/storage/emulated/0/Download",
                            errorText = bookmarkForm.errorText,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "optional #tags",
                            promptPrefix = "# ",
                            value = bookmarkForm.tags,
                            onValueChange = onBookmarkTagsChange,
                            placeholder = "#legal #id",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AsciiButton("cancel", onBookmarkCancel)
                            AsciiButton("save", onBookmarkSave, primary = true)
                        }
                    }
                }
            }
        }
    }

    if (engineForm.isOpen) {
        Dialog(onDismissRequest = onEngineCancel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                AsciiBox(title = if (engineForm.editingId != null) "edit pipe?" else "new pipe?", isModal = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsciiTextField(
                            label = "name",
                            promptPrefix = "> ",
                            value = engineForm.name,
                            onValueChange = onEngineNameChange,
                            placeholder = "DuckDuckGo",
                            errorText = engineForm.errors.nameError,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "${prefixes.pipesClean} keyword (unique, no spaces)",
                            promptPrefix = "${prefixes.pipesClean} ",
                            value = engineForm.keyword,
                            onValueChange = onEngineKeywordChange,
                            placeholder = "ddg",
                            errorText = engineForm.errors.keywordError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "url template (must contain {query})",
                            promptPrefix = "> ",
                            value = engineForm.urlTemplate,
                            onValueChange = onEngineUrlChange,
                            placeholder = "https://duckduckgo.com/?q={query}",
                            errorText = engineForm.errors.urlTemplateError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        )
                        AsciiDropdownRow(
                            label = "tag color role",
                            selectedValue = engineForm.colorRole,
                            options = EngineColorRole.entries.map { it to it.label },
                            onSelect = onEngineColorChange,
                        )
                        AsciiToggleRow(
                            label = "set as default fallback pipe (*)",
                            checked = engineForm.isDefault,
                            onCheckedChange = onEngineDefaultChange,
                        )
                        val dlgKw = engineForm.keyword.text.trim().lowercase().ifEmpty { "gh" }
                        val dlgPreviewUrl = if (engineForm.urlTemplate.text.contains("{query}")) {
                            QueryRouter.buildSearchUrl(engineForm.urlTemplate.text, "compose")
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .removePrefix("www.")
                        } else {
                            "github.com/search?q=compose"
                        }
                        AsciiLivePreviewLine(
                            previewText = "$dlgKw compose -> $dlgPreviewUrl",
                            isValid = engineForm.errors.isValid,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AsciiButton("cancel", onEngineCancel)
                            AsciiButton("save", onEngineSave, enabled = engineForm.errors.isValid, primary = engineForm.errors.isValid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AsciiSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val pct = (value * 100f).toInt()
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.01f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.small)
            AsciiText(
                text = leader(label, "$pct%", cols),
                style = AsciiType.body,
                color = c.fg,
                softWrap = false,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .pointerInput(valueRange.start, valueRange.endInclusive) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val newFrac = (offset.x / w).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + newFrac * rangeSpan)
                    }
                }
                .pointerInput(valueRange.start, valueRange.endInclusive) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val newFrac = (change.position.x / w).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + newFrac * rangeSpan)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val totalCells = rememberCols(AsciiType.body).coerceIn(12, 40)
            val barStr = bar(fraction, totalCells)
            AsciiText(
                text = barStr,
                style = AsciiType.title,
                color = if (fraction > 0.85f) c.yellow else c.green,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
fun AsciiDangerBox(
    title: String = "danger zone",
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalAscii.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.small)
            val prefix = "+- ! $title "
            val dashCount = (cols - prefix.length - 1).coerceAtLeast(2)
            AsciiText(
                text = prefix + "-".repeat(dashCount) + "+",
                style = AsciiType.title,
                color = c.error,
                softWrap = false,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            content = content,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.small)
            AsciiText(
                text = "+" + "-".repeat((cols - 2).coerceAtLeast(6)) + "+",
                style = AsciiType.small,
                color = c.error,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

/**
 * Settings Screen (`~/pipe/set/<section>`):
 * - Full-name tabs (`overlay`, `index`, `theme`, `routing`, `backup`) in a flush-aligned 2-row grid (`Modifier.weight(1f)`).
 * - Tapping any tab switches directly to that section's view and highlights the active tab.
 * - All permissions (`storage`, `contacts`, `default apps`) are grouped in one unified box inside `index`.
 * - External on-demand offline dictionary file management (`load`, `scan Download/`, `unlink`) is inside `index`.
 * - Custom command prefixes (`f`, `s`, `b`, `app`, `hist`, `c`, `def`, `clip`) are editable inside `routing`.
 */
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
    onOverlayOpacityChange: (Float) -> Unit,
    onOverlayDimChange: (Float) -> Unit,
    onOverlayCornerChange: (Int) -> Unit,
    onOverlayBottomChange: (Boolean) -> Unit,
    onOverlayForceDarkChange: (Boolean) -> Unit,
    onOverlayIdleChange: (Boolean) -> Unit,
    onSelectSettingsSection: (SettingsSection) -> Unit,
    onHourlyAutoIndexChange: (Boolean) -> Unit,
    onRebuildIndexNow: () -> Unit,
    onPreferredBrowserChange: (String) -> Unit,
    onWhatsAppFlavorChange: (WhatsAppFlavor) -> Unit = {},
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onSearchBoxPrefixChange: (String) -> Unit,
    onSearchBoxPlaceholderChange: (String) -> Unit,
    onShowScopeBadgeChange: (Boolean) -> Unit,
    onShowMenuBtnChange: (Boolean) -> Unit,
    onAutoLaunchExactAliasChange: (Boolean) -> Unit = {},
    onPrefixConfigChange: (PrefixConfig) -> Unit,
    onResetPrefixConfig: () -> Unit,
    onImportDictionaryFileClick: () -> Unit = {},
    onAutoDetectDownloadsDictionary: () -> Unit = {},
    onRemoveDictionaryClick: () -> Unit = {},
    onLaunchOverlayPreview: () -> Unit,
    onPinOverlayShortcut: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestFilesPermission: () -> Unit,
    onOpenSystemSetting: (SystemSettingShortcut) -> Unit = {},
    onSaveUniversalAlias: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onRemoveUniversalAlias: (String) -> Unit = {},
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val activeSection = state.settingsSection
    var settingsAliasTarget by remember { mutableStateOf<UniversalAlias?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        AsciiTopHeader(
            path = "~/pipe/set/${activeSection.label}",
            actions = {
                AsciiButton(label = "< back", onClick = onBack, spokenLabel = "back to search prompt")
            },
        )



        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (activeSection) {
                SettingsSection.OVERLAY -> {
                    AsciiBox(title = "overlay - floating window") {
                        AsciiSliderRow(
                            label = "card opacity",
                            value = state.overlayOpacity,
                            valueRange = 0.15f..1.0f,
                            onValueChange = onOverlayOpacityChange,
                        )
                        AsciiSliderRow(
                            label = "background dim",
                            value = state.overlayDimAmount,
                            valueRange = 0.0f..0.85f,
                            onValueChange = onOverlayDimChange,
                        )

                        AsciiInlineSegmentedRow(
                            label = "overlay position",
                            selectedValue = state.overlayBottomAnchored,
                            options = listOf(
                                true to "bottom",
                                false to "top",
                            ),
                            onSelect = onOverlayBottomChange,
                        )

                        AsciiInlineSegmentedRow(
                            label = "corner radius",
                            selectedValue = state.overlayCornerRadiusDp,
                            options = listOf(
                                0 to "0dp",
                                10 to "10dp",
                                16 to "16dp",
                                24 to "24dp",
                            ),
                            onSelect = onOverlayCornerChange,
                        )

                        AsciiToggleRow(
                            label = "force dark glass (override app theme)",
                            checked = state.overlayForceDark,
                            onCheckedChange = onOverlayForceDarkChange,
                        )
                        AsciiToggleRow(
                            label = "show most-used items when idle",
                            checked = state.overlayShowIdleSuggestions,
                            onCheckedChange = onOverlayIdleChange,
                        )

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsciiButton(
                                label = "launch overlay",
                                onClick = onLaunchOverlayPreview,
                                primary = true,
                                modifier = Modifier.weight(1f),
                            )
                            AsciiButton(
                                label = "+ pin shortcut",
                                onClick = onPinOverlayShortcut,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    AsciiBox(title = "overlay - search box") {
                        var placeholderField by remember(state.searchBoxPlaceholder) {
                            mutableStateOf(TextFieldValue(state.searchBoxPlaceholder, TextRange(state.searchBoxPlaceholder.length)))
                        }
                        AsciiTextField(
                            label = "placeholder text (empty for blank prompt)",
                            promptPrefix = "> ",
                            value = placeholderField,
                            onValueChange = {
                                placeholderField = it
                                onSearchBoxPlaceholderChange(it.text)
                            },
                            placeholder = "pipe for???",
                        )
                        Spacer(Modifier.height(4.dp))
                        AsciiInlineSegmentedRow(
                            label = "prompt prefix",
                            selectedValue = state.searchBoxPrefix,
                            options = listOf(
                                "> " to ">",
                                "$ " to "$",
                                "| " to "|",
                                "" to "none",
                            ),
                            onSelect = onSearchBoxPrefixChange,
                        )
                        AsciiToggleRow(
                            label = "show [scope] badge in search box",
                            checked = state.showSearchBoxScopeBadge,
                            onCheckedChange = onShowScopeBadgeChange,
                        )
                        AsciiToggleRow(
                            label = "show [::] settings button in overlay",
                            checked = state.showSearchBoxMenuBtn,
                            onCheckedChange = onShowMenuBtnChange,
                        )
                    }
                }

                SettingsSection.INDEX -> {
                    // Unified Permissions & System Access Box
                    AsciiBox(title = "permissions & system access") {
                        AsciiText(
                            text = "manage all app permissions and default system handler settings in one place:",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsciiButton(
                                label = if (state.hasFilesPermission) "storage: ok" else "! grant storage",
                                onClick = onRequestFilesPermission,
                                primary = !state.hasFilesPermission,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                            )
                            AsciiButton(
                                label = if (state.hasContactsPermission) "contacts: ok" else "! grant contacts",
                                onClick = onRequestContactsPermission,
                                primary = !state.hasContactsPermission,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AsciiButton(
                            label = "default apps settings (browser / assist)",
                            onClick = onOpenDefaultAppsSettings,
                            primary = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                        )
                    }

                    // On-Demand External Offline Dictionary Box
                    AsciiBox(title = "index - offline dictionary (on-demand)") {
                        AsciiText(
                            text = if (state.isDictionaryInstalled) {
                                "status: linked (${state.dictionaryWordCount} words)  |  idle RAM: 0 MB"
                            } else {
                                "status: not linked  |  tapped into only when you type `${state.prefixConfig.dictionaryClean} <word>`"
                            },
                            style = AsciiType.body,
                            color = if (state.isDictionaryInstalled) c.good else c.accent2,
                        )
                        AsciiText(
                            text = if (state.isDictionaryIndexing) {
                                "building 2-letter byte-offset index..."
                            } else {
                                "source: ${state.dictionarySourceName} (supports .tsv, .csv, .json, .json.gz)"
                            },
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsciiButton(
                                label = if (state.isDictionaryIndexing) "indexing..." else "load dictionary file",
                                onClick = onImportDictionaryFileClick,
                                enabled = !state.isDictionaryIndexing,
                                primary = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                            )
                            AsciiButton(
                                label = "scan Download/",
                                onClick = onAutoDetectDownloadsDictionary,
                                enabled = !state.isDictionaryIndexing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                            )
                        }
                        if (state.isDictionaryInstalled) {
                            Spacer(Modifier.height(6.dp))
                            AsciiButton(
                                label = "unlink dictionary file",
                                onClick = onRemoveDictionaryClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp),
                            )
                        }
                        if (state.jsonStatusMessage != null) {
                            Spacer(Modifier.height(4.dp))
                            AsciiText(
                                text = state.jsonStatusMessage,
                                style = AsciiType.small,
                                color = if (state.jsonStatusMessage.startsWith("!")) c.error else c.good,
                            )
                        }
                    }

                    AsciiBox(title = "index - local storage index") {
                        val ageText = if (state.lastIndexedTimestampMs <= 0L) {
                            "never indexed"
                        } else {
                            val mins = ((System.currentTimeMillis() - state.lastIndexedTimestampMs) / 60_000L).coerceAtLeast(0L)
                            if (mins == 0L) "just now" else "${mins}m ago"
                        }
                        AsciiText(
                            text = "folders: ${state.indexedFolderCount}  |  files: ${state.indexedFileCount}  |  contacts: ${state.contacts.size}",
                            style = AsciiType.body,
                            color = c.accent,
                        )
                        AsciiText(
                            text = "last sync: ${if (state.isIndexingInProgress) "indexing now..." else ageText}",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        AsciiToggleRow(
                            label = "auto-index storage every 1 hour",
                            checked = state.hourlyAutoIndexEnabled,
                            onCheckedChange = onHourlyAutoIndexChange,
                        )
                        Spacer(Modifier.height(6.dp))
                        AsciiButton(
                            label = if (state.isIndexingInProgress) "indexing..." else "re-index storage now",
                            onClick = onRebuildIndexNow,
                            enabled = !state.isIndexingInProgress,
                            primary = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                        )
                    }

                    AsciiBox(title = "index - excluded folders (${state.excludedFolders.size})") {
                        var newFolderInput by remember { mutableStateOf(TextFieldValue("")) }
                        AsciiTextField(
                            label = "skip folder name during indexing",
                            promptPrefix = "- ",
                            value = newFolderInput,
                            onValueChange = { newFolderInput = it },
                            placeholder = "Telegram, .Statuses, Backups",
                            trailingControl = {
                                AsciiButton(
                                    label = "+ add",
                                    primary = true,
                                    onClick = {
                                        if (newFolderInput.text.isNotBlank()) {
                                            onAddExcludedFolder(newFolderInput.text)
                                            newFolderInput = TextFieldValue("")
                                        }
                                    },
                                )
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.excludedFolders.forEach { folderName ->
                                AsciiButton(
                                    label = "$folderName x",
                                    onClick = { onRemoveExcludedFolder(folderName) },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }

                SettingsSection.THEME -> {
                    AsciiBox(title = "theme - mode & color palette") {
                        AsciiText(
                            text = "overlay and main app share this theme automatically unless 'force dark glass' is enabled in overlay settings.",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        Spacer(Modifier.height(6.dp))
                        AsciiInlineSegmentedRow(
                            label = "theme mode",
                            selectedValue = state.themeMode,
                            options = listOf(
                                ThemeMode.System to "sys",
                                ThemeMode.Dark to "dark",
                                ThemeMode.Light to "light",
                            ),
                            onSelect = onThemeModeChange,
                        )
                        AsciiInlineSegmentedRow(
                            label = "color palette",
                            selectedValue = state.paletteChoice,
                            options = listOf(
                                PaletteChoice.DRACULA to "dracula",
                                PaletteChoice.MOSS to "moss",
                                PaletteChoice.EMBER to "ember",
                                PaletteChoice.LAGOON to "lagoon",
                            ),
                            onSelect = onPaletteChoiceChange,
                        )
                    }
                }

                SettingsSection.ROUTING -> {
                    val p = state.prefixConfig
                    var filesField by remember(p.files) { mutableStateOf(TextFieldValue(p.files, TextRange(p.files.length))) }
                    var pipesField by remember(p.pipes) { mutableStateOf(TextFieldValue(p.pipes, TextRange(p.pipes.length))) }
                    var bookmarksField by remember(p.bookmarks) { mutableStateOf(TextFieldValue(p.bookmarks, TextRange(p.bookmarks.length))) }
                    var appsField by remember(p.apps) { mutableStateOf(TextFieldValue(p.apps, TextRange(p.apps.length))) }
                    var histField by remember(p.history) { mutableStateOf(TextFieldValue(p.history, TextRange(p.history.length))) }
                    var contactsField by remember(p.contacts) { mutableStateOf(TextFieldValue(p.contacts, TextRange(p.contacts.length))) }
                    var dictField by remember(p.dictionary) { mutableStateOf(TextFieldValue(p.dictionary, TextRange(p.dictionary.length))) }
                    var clipField by remember(p.clipboard) { mutableStateOf(TextFieldValue(p.clipboard, TextRange(p.clipboard.length))) }
                    var setField by remember(p.systemSettings) { mutableStateOf(TextFieldValue(p.systemSettings, TextRange(p.systemSettings.length))) }

                    val pushPrefixUpdate: (String, String, String, String, String, String, String, String) -> Unit = { fVal, sVal, bVal, appVal, hVal, cVal, dVal, clVal ->
                        onPrefixConfigChange(
                            PrefixConfig(
                                files = fVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "f" },
                                pipes = sVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "s" },
                                bookmarks = bVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "b" },
                                apps = appVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "app" },
                                history = hVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "hist" },
                                contacts = cVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "c" },
                                dictionary = dVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "def" },
                                clipboard = clVal.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "clip" },
                                systemSettings = setField.text.trim().lowercase().filter { !it.isWhitespace() }.ifEmpty { "set" },
                            )
                        )
                    }

                    AsciiBox(title = "routing - custom prefixes (f, s, b, app, hist, c, def, clip)") {
                        AsciiText(
                            text = "change the command prefixes and row tags used to filter each category:",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "files prefix",
                                    promptPrefix = "> ",
                                    value = filesField,
                                    onValueChange = {
                                        filesField = it
                                        pushPrefixUpdate(it.text, pipesField.text, bookmarksField.text, appsField.text, histField.text, contactsField.text, dictField.text, clipField.text)
                                    },
                                    placeholder = "f",
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "search pipes prefix",
                                    promptPrefix = "> ",
                                    value = pipesField,
                                    onValueChange = {
                                        pipesField = it
                                        pushPrefixUpdate(filesField.text, it.text, bookmarksField.text, appsField.text, histField.text, contactsField.text, dictField.text, clipField.text)
                                    },
                                    placeholder = "s",
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "bookmarks prefix",
                                    promptPrefix = "> ",
                                    value = bookmarksField,
                                    onValueChange = {
                                        bookmarksField = it
                                        pushPrefixUpdate(filesField.text, pipesField.text, it.text, appsField.text, histField.text, contactsField.text, dictField.text, clipField.text)
                                    },
                                    placeholder = "b",
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "apps prefix",
                                    promptPrefix = "> ",
                                    value = appsField,
                                    onValueChange = {
                                        appsField = it
                                        pushPrefixUpdate(filesField.text, pipesField.text, bookmarksField.text, it.text, histField.text, contactsField.text, dictField.text, clipField.text)
                                    },
                                    placeholder = "app",
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "history prefix",
                                    promptPrefix = "> ",
                                    value = histField,
                                    onValueChange = {
                                        histField = it
                                        pushPrefixUpdate(filesField.text, pipesField.text, bookmarksField.text, appsField.text, it.text, contactsField.text, dictField.text, clipField.text)
                                    },
                                    placeholder = "hist",
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "contacts prefix",
                                    promptPrefix = "> ",
                                    value = contactsField,
                                    onValueChange = {
                                        contactsField = it
                                        pushPrefixUpdate(filesField.text, pipesField.text, bookmarksField.text, appsField.text, histField.text, it.text, dictField.text, clipField.text)
                                    },
                                    placeholder = "c",
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "dictionary prefix",
                                    promptPrefix = "> ",
                                    value = dictField,
                                    onValueChange = {
                                        dictField = it
                                        pushPrefixUpdate(filesField.text, pipesField.text, bookmarksField.text, appsField.text, histField.text, contactsField.text, it.text, clipField.text)
                                    },
                                    placeholder = "def",
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                AsciiTextField(
                                    label = "clipboard prefix",
                                    promptPrefix = "> ",
                                    value = clipField,
                                    onValueChange = {
                                        clipField = it
                                        pushPrefixUpdate(filesField.text, pipesField.text, bookmarksField.text, appsField.text, histField.text, contactsField.text, dictField.text, it.text)
                                    },
                                    placeholder = "clip",
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        AsciiTextField(
                            label = "system settings jumper prefix (e.g. set dev, set spot, set dns)",
                            promptPrefix = "> ",
                            value = setField,
                            onValueChange = {
                                setField = it
                                onPrefixConfigChange(
                                    PrefixConfig(
                                        files = filesField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "f" },
                                        pipes = pipesField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "s" },
                                        bookmarks = bookmarksField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "b" },
                                        apps = appsField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "app" },
                                        history = histField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "hist" },
                                        contacts = contactsField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "c" },
                                        dictionary = dictField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "def" },
                                        clipboard = clipField.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "clip" },
                                        systemSettings = it.text.trim().lowercase().filter { ch -> !ch.isWhitespace() }.ifEmpty { "set" },
                                    )
                                )
                            },
                            placeholder = "set",
                        )
                        Spacer(Modifier.height(8.dp))
                        AsciiButton(
                            label = "reset default prefixes (f, s, b, app, hist, c, def, clip, set)",
                            onClick = onResetPrefixConfig,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                        )
                    }

                    AsciiBox(title = "routing - browser & external text") {
                        val browserOptions = remember(state.installedBrowsers) {
                            listOf("auto" to "System Default (Auto)") +
                                state.installedBrowsers.map { it.packageName to it.label }
                        }
                        AsciiDropdownRow(
                            label = "default browser",
                            subtitle = "opens web links & search pipes",
                            selectedValue = state.preferredBrowserPackage,
                            options = browserOptions,
                            onSelect = onPreferredBrowserChange,
                        )
                        AsciiInlineSegmentedRow(
                            label = "whatsapp target (wa / wab)",
                            subtitle = "default for unsaved numbers & contacts",
                            selectedValue = state.whatsAppFlavor,
                            options = listOf(
                                WhatsAppFlavor.BOTH to "both",
                                WhatsAppFlavor.PERSONAL to "wa",
                                WhatsAppFlavor.BUSINESS to "wa.b",
                            ),
                            onSelect = onWhatsAppFlavorChange,
                        )
                        AsciiInlineSegmentedRow(
                            label = "share text action",
                            subtitle = "when sharing or selecting text",
                            selectedValue = state.externalTextMode,
                            options = listOf(
                                ExternalTextMode.OPEN_OVERLAY to "overlay",
                                ExternalTextMode.OPEN_PROMPT to "prompt",
                                ExternalTextMode.DIRECT_PIPE to "pipe",
                            ),
                            onSelect = onExternalTextModeChange,
                        )
                        AsciiToggleRow(
                            label = "auto-launch on exact @alias",
                            subtitle = "runs #1 match immediately when you type an exact custom alias",
                            checked = state.autoLaunchExactAlias,
                            onCheckedChange = onAutoLaunchExactAliasChange,
                        )
                        AsciiToggleRow(
                            label = "record local command history",
                            checked = state.historyEnabled,
                            onCheckedChange = onHistoryEnabledChange,
                        )
                        Spacer(Modifier.height(6.dp))
                        AsciiButton(
                            label = "copy pipe://search?q=%s url",
                            onClick = {
                                clipboard.setText(AnnotatedString("pipe://search?q=%s"))
                                onSetJsonStatus("ok: copied pipe://search?q=%s")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                        )
                        if (state.jsonStatusMessage != null) {
                            Spacer(Modifier.height(4.dp))
                            AsciiText(
                                text = state.jsonStatusMessage,
                                style = AsciiType.small,
                                color = c.good,
                            )
                        }
                    }

                }

                SettingsSection.BACKUP -> {
                    AsciiBox(title = "backup - full v2 json backup (2x2)") {
                        AsciiText(
                            text = "exports & imports pipes, bookmarks, custom prefixes, excluded folders, and usage rankings:",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AsciiButton(
                                    label = "export file",
                                    onClick = onExportJsonToFile,
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                                )
                                AsciiButton(
                                    label = "import file",
                                    onClick = onImportJsonFromFile,
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AsciiButton(
                                    label = "copy json",
                                    onClick = onOpenJsonExportDialog,
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                                )
                                AsciiButton(
                                    label = "paste json",
                                    onClick = onOpenJsonImportDialog,
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                                )
                            }
                        }
                        if (state.jsonStatusMessage != null) {
                            Spacer(Modifier.height(6.dp))
                            AsciiText(
                                text = state.jsonStatusMessage,
                                style = AsciiType.small,
                                color = if (state.jsonStatusMessage.startsWith("!")) c.error else c.good,
                            )
                        }
                    }

                    AsciiDangerBox(title = "danger zone") {
                        AsciiText(
                            text = "restores the ${com.sujan.pipe.model.DefaultEngines.LIST.size} default search pipes including ytt, ytw, ytm (undo within 6s):",
                            style = AsciiType.small,
                            color = c.dim,
                        )
                        Spacer(Modifier.height(8.dp))
                        val src = remember { MutableInteractionSource() }
                        val pressed by src.collectIsPressedAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .background(if (pressed) c.error else c.surface)
                                .clickable(
                                    interactionSource = src,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onResetDefaults,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsciiText(
                                text = "[reset pipes]",
                                style = AsciiType.title,
                                color = if (pressed) c.bg else c.error,
                            )
                        }
                    }

                    AsciiBox(title = "about") {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cols = rememberCols(AsciiType.small)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AsciiText("pipe v2.9 (release)", style = AsciiType.title, color = c.purple)
                                AsciiText(leader("package", "com.sujan.pipe", cols), style = AsciiType.small, color = c.dim, softWrap = false)
                                AsciiText(leader("system settings", "${state.prefixConfig.systemSettingsClean} <dev|spot|dns|notif>", cols), style = AsciiType.small, color = c.accent2, softWrap = false)
                                AsciiText(leader("whatsapp / biz", "wa <num> / wab <num>", cols), style = AsciiType.small, color = c.good, softWrap = false)
                                AsciiText(leader("timer & alarm", "timer 12m / alarm 06:30", cols), style = AsciiType.small, color = c.warn, softWrap = false)
                                AsciiText(leader("dictionary", "${state.prefixConfig.dictionaryClean} <word> (0 MB idle)", cols), style = AsciiType.small, color = c.accent2, softWrap = false)
                                AsciiText(leader("clipboard", "${state.prefixConfig.clipboardClean} (url / phone / text)", cols), style = AsciiType.small, color = c.accent3, softWrap = false)
                                AsciiText(leader("bookmarks", "${state.prefixConfig.bookmarksClean} <alias>", cols), style = AsciiType.small, color = c.accent2, softWrap = false)
                                AsciiText(leader("search pipes", "${state.prefixConfig.pipesClean} <keyword> <query>", cols), style = AsciiType.small, color = c.accent, softWrap = false)
                                AsciiText(leader("youtube time", "ytt / ytw / ytm <query>", cols), style = AsciiType.small, color = c.good, softWrap = false)
                            }
                        }
                    }
                }
            }
        }

        // Thumb-zone bottom Settings tabs with FULL NAMES (2 rows)
        Column(modifier = Modifier.fillMaxWidth().background(c.bg)) {
            AsciiCommandBar(
                items = listOf(
                    Triple("overlay", activeSection == SettingsSection.OVERLAY) { onSelectSettingsSection(SettingsSection.OVERLAY) },
                    Triple("index", activeSection == SettingsSection.INDEX) { onSelectSettingsSection(SettingsSection.INDEX) },
                    Triple("theme", activeSection == SettingsSection.THEME) { onSelectSettingsSection(SettingsSection.THEME) },
                )
            )
            AsciiCommandBar(
                items = listOf(
                    Triple("routing", activeSection == SettingsSection.ROUTING) { onSelectSettingsSection(SettingsSection.ROUTING) },
                    Triple("backup", activeSection == SettingsSection.BACKUP) { onSelectSettingsSection(SettingsSection.BACKUP) },
                )
            )
        }
    }

    if (settingsAliasTarget != null) {
        val t = settingsAliasTarget!!
        AsciiSetAliasDialog(
            target = t,
            onSave = { newAlias ->
                onSaveUniversalAlias(t.targetKey, newAlias, t.label, t.category, t.subtitle)
                settingsAliasTarget = null
            },
            onRemove = {
                onRemoveUniversalAlias(t.targetKey)
                settingsAliasTarget = null
            },
            onDismiss = { settingsAliasTarget = null },
        )
    }

    if (state.jsonDialogMode != null) {
        val initial = if (state.jsonDialogMode == JsonDialogMode.EXPORT) onExportJsonString() else ""
        var textVal by remember(state.jsonDialogMode, initial) {
            mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        }
        var err by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = onCloseJsonDialog) {
            Column(
                modifier = Modifier.fillMaxWidth().background(c.bg).padding(12.dp).imePadding(),
            ) {
                AsciiBox(title = if (state.jsonDialogMode == JsonDialogMode.EXPORT) "export json?" else "import json?", isModal = true) {
                    AsciiTextField(
                        label = "full v2 backup json (pipes, bookmarks, prefixes, folders)",
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
                                onSetJsonStatus("ok: copied full v2 json to clipboard")
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

@Composable
fun PipePromptStep1Preview() {
    val sampleEngine = com.sujan.pipe.model.DefaultEngines.LIST.first { it.keyword == "gh" }
    val sampleBookmark = com.sujan.pipe.model.PipeBookmark(
        id = "bm_dl",
        alias = "dl",
        title = "Downloads",
        target = "/storage/emulated/0/Download",
        kind = BookmarkKind.FOLDER,
    )
    val sampleContact = ContactItem(
        id = "c_1",
        displayName = "Kalika Prasad",
        phoneNumber = "9841234567",
    )
    val sampleFile = LocalFileItem(
        name = "citizenship_scan.pdf",
        absolutePath = "/storage/emulated/0/Documents/Legal/citizenship_scan.pdf",
        isDirectory = false,
        parentName = "Documents/Legal",
        sizeBytes = 840_000L,
    )
    val sampleApp = InstalledAppItem(
        label = "MiXplorer Silver",
        packageName = "com.mixplorer.silver",
    )
    val sampleResults = listOf(
        com.sujan.pipe.domain.PaletteItem.AppAction(sampleApp, "app:com.mixplorer.silver", 9500, 12),
        com.sujan.pipe.domain.PaletteItem.ContactAction(sampleContact, com.sujan.pipe.model.ContactQuickAction.CALL, "contact:9841234567", 8800, 8),
        com.sujan.pipe.domain.PaletteItem.BookmarkAction(sampleBookmark, "bm:bm_dl", 8200, 6),
        com.sujan.pipe.domain.PaletteItem.FileAction(sampleFile, "file:/storage/emulated/0/Documents/Legal/citizenship_scan.pdf", 7600, 3),
        com.sujan.pipe.domain.PaletteItem.EngineAction(sampleEngine, "compose", "https://github.com/search?q=compose", false, "eng:eng_github", 7000, 2),
    )

    PipeTheme(mode = ThemeMode.Dark, palette = PaletteChoice.MOSS.palette) {
        AsciiRaycastPaletteWindow(
            titlePath = "~/pipe",
            searchInput = TextFieldValue("kal"),
            onSearchInputChange = {},
            parsedCommand = com.sujan.pipe.domain.ParsedPromptCommand(PromptIntentMode.ALL, "kal"),
            results = sampleResults,
            hasContactsPermission = true,
            hasFilesPermission = true,
            launchError = null,
            focusRequester = remember { FocusRequester() },
            headerRightActions = {
                AsciiButton("+", {})
                AsciiButton("lib", {})
                AsciiButton("set", {})
            },
            onAliasPillClick = {},
            onSubmitPrimary = {},
            onExecuteItem = {},
            onCallContact = {},
            onSmsContact = {},
            onWhatsAppContact = {},
            onScopeIntoFolder = {},
        )
    }
}
