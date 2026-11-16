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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.QuickAddKind
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.ascii.AsciiBox
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiCheckbox
import com.sujan.pipe.ui.ascii.AsciiDropdownRow
import com.sujan.pipe.ui.ascii.AsciiPipePill
import com.sujan.pipe.ui.ascii.AsciiRadio
import com.sujan.pipe.ui.ascii.AsciiToggleRow
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
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimaryAction: () -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onOpenLocalFile: (LocalFileItem) -> Unit,
    onShareLocalFile: (LocalFileItem) -> Unit = {},
    onPreviewImageFile: (LocalFileItem?) -> Unit = {},
    onLaunchInstalledApp: (com.sujan.pipe.model.InstalledAppItem) -> Unit = {},
    onBookmarkLocalFile: (LocalFileItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onQuickSaveContact: (String, String) -> Unit = { _, _ -> },
    onQuickSaveBookmark: (String, String, String, BookmarkKind, String) -> Unit = { _, _, _, _, _ -> },
    onQuickSavePipe: (String, String, String) -> Unit = { _, _, _ -> },
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
    onAddExcludedFolder: (String) -> Unit = {},
    onRemoveExcludedFolder: (String) -> Unit = {},
    onSearchBoxPrefixChange: (String) -> Unit = {},
    onSearchBoxPlaceholderChange: (String) -> Unit = {},
    onShowScopeBadgeChange: (Boolean) -> Unit = {},
    onShowMenuBtnChange: (Boolean) -> Unit = {},
    onLaunchOverlayPreview: () -> Unit,
    onPinOverlayShortcut: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
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
                        onAliasPillClick = onAliasPillClick,
                        onSubmitPrimaryAction = onSubmitPrimaryAction,
                        onOpenBookmark = onOpenBookmark,
                        onOpenLocalFile = onOpenLocalFile,
                        onShareLocalFile = onShareLocalFile,
                        onPreviewImageFile = onPreviewImageFile,
                        onLaunchInstalledApp = onLaunchInstalledApp,
                        onBookmarkLocalFile = onBookmarkLocalFile,
                        onCallContact = onCallContact,
                        onSmsContact = onSmsContact,
                        onWhatsAppContact = onWhatsAppContact,
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
                    PipeView.BOOKMARKS -> PipeBookmarksScreen(
                        bookmarks = state.bookmarks,
                        formState = state.bookmarkForm,
                        hasFilesPermission = state.hasFilesPermission,
                        onBack = { onNavigate(PipeView.PROMPT) },
                        onOpenBookmark = onOpenBookmark,
                        onAddClick = onAddBookmarkClick,
                        onEditClick = onEditBookmarkClick,
                        onDeleteClick = onDeleteBookmarkClick,
                        onRequestFilesPermission = onRequestFilesPermission,
                        onAliasChange = onBookmarkFormAliasChange,
                        onTitleChange = onBookmarkFormTitleChange,
                        onTargetChange = onBookmarkFormTargetChange,
                        onTagsChange = onBookmarkFormTagsChange,
                        onKindChange = onBookmarkFormKindChange,
                        onSave = onBookmarkFormSave,
                        onCancel = onBookmarkFormCancel,
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
                        onAddExcludedFolder = onAddExcludedFolder,
                        onRemoveExcludedFolder = onRemoveExcludedFolder,
                        onSearchBoxPrefixChange = onSearchBoxPrefixChange,
                        onSearchBoxPlaceholderChange = onSearchBoxPlaceholderChange,
                        onShowScopeBadgeChange = onShowScopeBadgeChange,
                        onShowMenuBtnChange = onShowMenuBtnChange,
                        onLaunchOverlayPreview = onLaunchOverlayPreview,
                        onPinOverlayShortcut = onPinOverlayShortcut,
                        onOpenDefaultAppsSettings = onOpenDefaultAppsSettings,
                        onRequestContactsPermission = onRequestContactsPermission,
                        onRequestFilesPermission = onRequestFilesPermission,
                    )
                }
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
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimaryAction: () -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onOpenLocalFile: (LocalFileItem) -> Unit,
    onShareLocalFile: (LocalFileItem) -> Unit,
    onPreviewImageFile: (LocalFileItem?) -> Unit,
    onLaunchInstalledApp: (com.sujan.pipe.model.InstalledAppItem) -> Unit,
    onBookmarkLocalFile: (LocalFileItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onQuickSaveContact: (String, String) -> Unit,
    onQuickSaveBookmark: (String, String, String, BookmarkKind, String) -> Unit,
    onQuickSavePipe: (String, String, String) -> Unit,
    onQuickRemoveItem: (Boolean, String) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestFilesPermission: () -> Unit,
    onRerunHistory: (SearchHistoryEntry) -> Unit,
    onFillHistory: (SearchHistoryEntry) -> Unit,
    onDeleteHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onLaunchOverlay: () -> Unit,
) {
    val c = LocalAscii.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inlineQuickAdd by remember { mutableStateOf<com.sujan.pipe.domain.PaletteItem.QuickAddAction?>(null) }

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
        // Raycast-style ASCII Command Palette Frame
        AsciiRaycastPaletteWindow(
            titlePath = "~/pipe",
            searchInput = state.searchInput,
            onSearchInputChange = onSearchInputChange,
            parsedCommand = state.parsedCommand,
            results = state.paletteResults,
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
            onQuickSaveBookmark = { alias, title, target, kind, tags ->
                inlineQuickAdd = null
                onQuickSaveBookmark(alias, title, target, kind, tags)
            },
            onQuickSavePipe = { kw, name, url ->
                inlineQuickAdd = null
                onQuickSavePipe(kw, name, url)
            },
            existingBookmarks = state.bookmarks,
            existingEngines = state.engines,
            onQuickDeleteBookmark = { bm -> onQuickRemoveItem(true, bm.id) },
            onQuickDeleteEngine = { eng -> onQuickRemoveItem(false, eng.id) },
            onSetDefaultEngine = { id ->
                // handled via quick pipe manager
            },
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
                }, primary = inlineQuickAdd != null, spokenLabel = "quick add or manage")
                AsciiButton("ovl", onLaunchOverlay, spokenLabel = "floating overlay")
                AsciiButton("set", { onNavigate(PipeView.SETTINGS) }, spokenLabel = "settings")
            },
            onAliasPillClick = { alias ->
                onAliasPillClick(alias)
                runCatching { focusRequester.requestFocus() }
            },
            onSubmitPrimary = onSubmitPrimaryAction,
            onExecuteItem = { item ->
                when (item) {
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
                            com.sujan.pipe.model.ContactQuickAction.SMS -> onSmsContact(item.contact)
                            com.sujan.pipe.model.ContactQuickAction.CALL -> onCallContact(item.contact)
                        }
                    }
                    is com.sujan.pipe.domain.PaletteItem.AppAction -> onLaunchInstalledApp(item.app)
                    is com.sujan.pipe.domain.PaletteItem.QuickAddAction -> {
                        if (item.isReadyToCommit) {
                            when (item.kind) {
                                QuickAddKind.CONTACT -> onQuickSaveContact(item.arg1, item.arg2)
                                QuickAddKind.BOOKMARK -> onQuickSaveBookmark(item.arg1, item.arg2, item.arg3, item.bookmarkKind, "")
                                QuickAddKind.PIPE -> onQuickSavePipe(item.arg1, item.arg2, item.arg3)
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
            onScopeIntoFolder = { alias ->
                onAliasPillClick(alias)
                runCatching { focusRequester.requestFocus() }
            },
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
 * Shared Raycast-inspired ASCII Command Palette Window used in both `~/pipe` and `~/pipe/overlay`.
 * Features character-drawn `┌──┐`, `├──┤`, `└──┘` borders, a unified top-10 result list sorted by
 * usage frequency (`#N`), strict alias scoping (`c`, `f`, `d`, `bm`, `dl`, `yt`), and a Raycast footer bar.
 */
@Composable
fun AsciiRaycastPaletteWindow(
    titlePath: String,
    searchInput: TextFieldValue,
    onSearchInputChange: (TextFieldValue) -> Unit,
    parsedCommand: com.sujan.pipe.domain.ParsedPromptCommand,
    results: List<com.sujan.pipe.domain.PaletteItem>,
    hasContactsPermission: Boolean,
    hasFilesPermission: Boolean,
    launchError: String?,
    focusRequester: FocusRequester,
    promptPrefix: String = "> ",
    placeholderText: String = "pipe (c, f, d, app, dl, yt)...",
    showScopeBadge: Boolean = true,
    inlineQuickAdd: com.sujan.pipe.domain.PaletteItem.QuickAddAction? = null,
    onDismissInlineQuickAdd: () -> Unit = {},
    onQuickSaveContact: (String, String) -> Unit = { _, _ -> },
    onQuickSaveBookmark: (String, String, String, BookmarkKind, String) -> Unit = { _, _, _, _, _ -> },
    onQuickSavePipe: (String, String, String) -> Unit = { _, _, _ -> },
    existingBookmarks: List<PipeBookmark> = emptyList(),
    existingEngines: List<SearchEngine> = emptyList(),
    onQuickDeleteBookmark: ((PipeBookmark) -> Unit)? = null,
    onQuickDeleteEngine: ((SearchEngine) -> Unit)? = null,
    onSetDefaultEngine: ((String) -> Unit)? = null,
    headerRightActions: @Composable () -> Unit,
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimary: () -> Unit,
    onExecuteItem: (com.sujan.pipe.domain.PaletteItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onScopeIntoFolder: (String) -> Unit,
    onBookmarkFile: ((LocalFileItem) -> Unit)? = null,
    onShareFile: ((LocalFileItem) -> Unit)? = null,
    onPreviewImage: ((LocalFileItem) -> Unit)? = null,
    onRequestContactsPermission: (() -> Unit)? = null,
    onRequestFilesPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val scopeBadge = when (parsedCommand.mode) {
        PromptIntentMode.SCOPED_FOLDER -> "b:${parsedCommand.scopedFolderBookmark?.alias ?: "folder"}"
        PromptIntentMode.ENGINE_ONLY -> "s:${parsedCommand.matchedEngine?.keyword ?: "pipe"}"
        else -> parsedCommand.mode.scopeBadge
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bg)
            .border(1.dp, c.border),
    ) {
        // 1. Top ASCII Title Bar: `┌─ ~/pipe ─────────────────── [ovl] [bm] [pipes] [set] ─┐`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = "+- $titlePath",
                style = AsciiType.title,
                color = c.accent2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerRightActions()
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

        // 2. Raycast Command Input Bar (`| > type alias or command_         [scope] [x] |`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(c.bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (promptPrefix.isNotEmpty()) {
                AsciiText(
                    text = promptPrefix,
                    style = AsciiType.title,
                    color = c.accent,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (searchInput.text.isEmpty() && placeholderText.isNotEmpty()) {
                    AsciiText(
                        text = placeholderText,
                        style = AsciiType.body,
                        color = c.dim,
                        maxLines = 1,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = searchInput,
                    onValueChange = onSearchInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = AsciiType.body.copy(color = c.fg),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitPrimary() }),
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
        }

        if (launchError != null) {
            AsciiText(
                text = "  $launchError",
                style = AsciiType.small,
                color = c.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // Permission banners if needed for active alias
        if (!hasContactsPermission && parsedCommand.mode == PromptIntentMode.CONTACTS_ONLY && onRequestContactsPermission != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("! contacts permission needed for `c` alias", style = AsciiType.small, color = c.warn)
                AsciiButton("grant", onRequestContactsPermission, primary = true)
            }
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
                AsciiText("! storage access needed to search/open folders", style = AsciiType.small, color = c.warn)
                AsciiButton("grant", onRequestFilesPermission, primary = true)
            }
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
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

        // 4. Unified Raycast Results List (Max 10 rows)
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
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
                    AsciiRaycastRow(
                        item = item,
                        isTop = isTop,
                        onExecute = { onExecuteItem(item) },
                        onCallContact = onCallContact,
                        onSmsContact = onSmsContact,
                        onWhatsAppContact = onWhatsAppContact,
                        onScopeIntoFolder = onScopeIntoFolder,
                        onBookmarkFile = onBookmarkFile,
                        onShareFile = onShareFile,
                        onPreviewImage = onPreviewImage,
                        onDeleteBookmark = onQuickDeleteBookmark,
                        onDeleteEngine = if (existingEngines.size > 1) onQuickDeleteEngine else null,
                    )
                }
            }
        }

        // 5. Raycast Bottom Action Bar / Alias Strip (`└─ [c] [f] [d] [dl] [yt] ─────── [RET ->] ─┘`)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsciiButton("c:", { onAliasPillClick("c") }, spokenLabel = "contacts scope c")
                AsciiButton("f:", { onAliasPillClick("f") }, spokenLabel = "files scope f")
                AsciiButton("b:", { onAliasPillClick("b") }, spokenLabel = "bookmarks scope b")
                AsciiButton("s:", { onAliasPillClick("s") }, spokenLabel = "search pipes scope s")
                AsciiButton("app:", { onAliasPillClick("app") }, spokenLabel = "apps scope app")
            }
            AsciiButton(
                label = "RET ->",
                onClick = {
                    val firstItem = results.firstOrNull()
                    if (firstItem != null) onExecuteItem(firstItem) else onSubmitPrimary()
                },
                primary = true,
                spokenLabel = "run top command",
            )
        }
    }
}

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
    val bmp = bitmap
    Box(
        modifier = modifier
            .padding(end = 6.dp)
            .size(34.dp)
            .background(c.surface)
            .border(1.dp, c.good),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contact.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsciiText(
                text = contact.monogram,
                style = AsciiType.small,
                color = c.good,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddInlineCard(
    initialAction: com.sujan.pipe.domain.PaletteItem.QuickAddAction,
    onSaveContact: (String, String) -> Unit,
    onSaveBookmark: (String, String, String, BookmarkKind, String) -> Unit,
    onSavePipe: (String, String, String) -> Unit,
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

    // Auto-parse if user typed something like "Ramesh 9841234567" before tapping [+]
    val autoContact = remember(initialAction.arg1) {
        QueryRouter.parseQuickAddContact(initialAction.arg1)
    }
    var activeKind by remember(initialAction) {
        mutableStateOf(if (autoContact != null) QuickAddKind.CONTACT else initialAction.kind)
    }

    // Contact fields
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

    // Bookmark fields (auto-detects Link vs Folder vs File from target!)
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

    // Pipe fields (auto-converts ?q=sample into ?q={query}!)
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
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.accent)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Standardized 3-tab header: `[+c Contact]  [+b Bookmark]  [+s Pipe]  [x]`
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
                            errorMsg = null
                        },
                        primary = activeKind == k,
                    )
                }
            }
            AsciiButton("x", onCancel)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

        when (activeKind) {
            QuickAddKind.CONTACT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(0.52f)) {
                        AsciiTextField(
                            label = "name",
                            promptPrefix = "@ ",
                            value = contactName,
                            onValueChange = { contactName = it; errorMsg = null },
                            placeholder = "Ramesh Dai",
                        )
                    }
                    Box(Modifier.weight(0.48f)) {
                        AsciiTextField(
                            label = "phone",
                            promptPrefix = "# ",
                            value = contactPhone,
                            onValueChange = { contactPhone = it; errorMsg = null },
                            placeholder = "9841234567",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        )
                    }
                }
                if (errorMsg != null) {
                    AsciiText(errorMsg!!, style = AsciiType.small, color = c.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiText("tip: or type `+ Name Phone`", style = AsciiType.small, color = c.dim)
                    Row {
                        AsciiButton("cancel", onCancel)
                        AsciiButton(
                            label = "+ save contact",
                            primary = true,
                            onClick = {
                                val n = contactName.text.trim()
                                val p = contactPhone.text.trim()
                                if (n.isEmpty() || p.isEmpty()) {
                                    errorMsg = "! enter both name and phone"
                                } else {
                                    onSaveContact(n, p)
                                }
                            },
                        )
                    }
                }
            }

            QuickAddKind.BOOKMARK -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(0.35f)) {
                        AsciiTextField(
                            label = "b alias",
                            promptPrefix = "b ",
                            value = bmAlias,
                            onValueChange = { bmAlias = it; errorMsg = null },
                            placeholder = "abb",
                        )
                    }
                    Box(Modifier.weight(0.65f)) {
                        AsciiTextField(
                            label = "title (auto if blank)",
                            promptPrefix = "> ",
                            value = bmTitle,
                            onValueChange = { bmTitle = it; errorMsg = null },
                            placeholder = "AudiobookBay",
                        )
                    }
                }
                AsciiTextField(
                    label = "url or folder/file path (auto-detects type)",
                    promptPrefix = "> ",
                    value = bmTarget,
                    onValueChange = {
                        bmTarget = it
                        errorMsg = null
                        if (bmTitle.text.isBlank() && it.text.length > 4) {
                            val inferred = QueryRouter.inferTitleFromTarget(bmAlias.text, it.text)
                            bmTitle = TextFieldValue(inferred, TextRange(inferred.length))
                        }
                    },
                    placeholder = "https://... or /storage/emulated/0/...",
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
                if (errorMsg != null) {
                    AsciiText(errorMsg!!, style = AsciiType.small, color = c.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiText("use via: `b ${bmAlias.text.ifBlank { "alias" }}`", style = AsciiType.small, color = c.accent2)
                    Row {
                        AsciiButton("cancel", onCancel)
                        AsciiButton(
                            label = "+ save b",
                            primary = true,
                            onClick = {
                                val rawU = bmTarget.text.trim()
                                val u = if (!rawU.startsWith("/") && !rawU.startsWith("http://") && !rawU.startsWith("https://") && rawU.contains('.')) {
                                    "https://$rawU"
                                } else rawU
                                val a = bmAlias.text.trim().lowercase().ifEmpty {
                                    QueryRouter.inferTitleFromTarget("bm", u).lowercase().take(6)
                                }
                                val t = bmTitle.text.trim().ifEmpty { QueryRouter.inferTitleFromTarget(a, u) }
                                if (u.isEmpty()) {
                                    errorMsg = "! enter or paste a url or file path"
                                } else {
                                    val detectedKind = when {
                                        u.startsWith("http://") || u.startsWith("https://") -> BookmarkKind.LINK
                                        u.substringAfterLast('/').contains('.') -> BookmarkKind.FILE
                                        else -> BookmarkKind.FOLDER
                                    }
                                    onSaveBookmark(a, t, u, detectedKind, "")
                                }
                            },
                        )
                    }
                }
                if (existingBookmarks.isNotEmpty() && onDeleteBookmark != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    AsciiText("saved bookmarks (tap [x] to delete):", style = AsciiType.small, color = c.dim)
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
                                    color = c.fg,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                AsciiButton("x", { onDeleteBookmark(bm) })
                            }
                        }
                    }
                }
            }

            QuickAddKind.PIPE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(0.35f)) {
                        AsciiTextField(
                            label = "s keyword",
                            promptPrefix = "s ",
                            value = pipeKeyword,
                            onValueChange = { pipeKeyword = it; errorMsg = null },
                            placeholder = "abb",
                        )
                    }
                    Box(Modifier.weight(0.65f)) {
                        AsciiTextField(
                            label = "pipe name (auto if blank)",
                            promptPrefix = "> ",
                            value = pipeName,
                            onValueChange = { pipeName = it; errorMsg = null },
                            placeholder = "AudiobookBay",
                        )
                    }
                }
                AsciiTextField(
                    label = "search url (paste any ?q=test url; auto-converts to {query})",
                    promptPrefix = "> ",
                    value = pipeUrl,
                    onValueChange = {
                        pipeUrl = it
                        errorMsg = null
                        if (pipeName.text.isBlank() && it.text.length > 4) {
                            val inf = QueryRouter.inferTitleFromTarget(pipeKeyword.text, it.text)
                            pipeName = TextFieldValue(inf, TextRange(inf.length))
                        }
                    },
                    placeholder = "https://site.com/search?q={query}",
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
                if (errorMsg != null) {
                    AsciiText(errorMsg!!, style = AsciiType.small, color = c.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiText("use via: `s ${pipeKeyword.text.ifBlank { "kw" }} <query>`", style = AsciiType.small, color = c.accent)
                    Row {
                        AsciiButton("cancel", onCancel)
                        AsciiButton(
                            label = "+ save s",
                            primary = true,
                            onClick = {
                                val u = QueryRouter.normalizePipeUrlTemplate(pipeUrl.text)
                                val k = pipeKeyword.text.trim().lowercase().ifEmpty {
                                    QueryRouter.inferTitleFromTarget("s", u).lowercase().take(4)
                                }
                                val n = pipeName.text.trim().ifEmpty { QueryRouter.inferTitleFromTarget(k, u) }
                                if (k.isEmpty() || u.isEmpty()) {
                                    errorMsg = "! enter keyword and search url"
                                } else {
                                    onSavePipe(k, n, u)
                                }
                            },
                        )
                    }
                }
                if (existingEngines.isNotEmpty() && onDeleteEngine != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    AsciiText("saved search pipes (tap [x] to delete):", style = AsciiType.small, color = c.dim)
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
                                    color = if (eng.isDefault) c.accent else c.fg,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                if (existingEngines.size > 1) {
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

@Composable
fun AsciiThumbnailBadge(
    filePath: String,
    onClick: () -> Unit,
) {
    val c = LocalAscii.current
    var bitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(filePath) {
        bitmap = withContext(Dispatchers.IO) {
            com.sujan.pipe.data.DeviceIntegrationManager.loadImageThumbnail(filePath, 96)
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(34.dp)
                .border(1.dp, c.accent2)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "image thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun AsciiImagePreviewDialog(
    file: LocalFileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    var bitmap by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        bitmap = withContext(Dispatchers.IO) {
            com.sujan.pipe.data.DeviceIntegrationManager.loadImageThumbnail(file.absolutePath, 512)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg)
                .border(1.dp, c.accent)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsciiText(text = "+- preview: ${file.name}", style = AsciiType.title, color = c.accent2, maxLines = 1)
            AsciiText(text = "${file.parentName} · ${file.formattedSize}", style = AsciiType.small, color = c.dim, maxLines = 1)
            val bmp = bitmap
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .border(1.dp, c.border),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                AsciiText("loading image preview...", style = AsciiType.small, color = c.dim)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiButton("close", onDismiss)
                AsciiButton("shr", onShare)
                AsciiButton("open", onOpen, primary = true)
            }
        }
    }
}

@Composable
private fun AsciiRaycastRow(
    item: com.sujan.pipe.domain.PaletteItem,
    isTop: Boolean,
    onExecute: () -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onScopeIntoFolder: (String) -> Unit,
    onBookmarkFile: ((LocalFileItem) -> Unit)?,
    onShareFile: ((LocalFileItem) -> Unit)? = null,
    onPreviewImage: ((LocalFileItem) -> Unit)? = null,
    onDeleteBookmark: ((PipeBookmark) -> Unit)? = null,
    onDeleteEngine: ((SearchEngine) -> Unit)? = null,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val rowBg = when {
        pressed -> c.accent
        isTop -> c.surface
        else -> Color.Transparent
    }
    val pointer = if (isTop) "|>" else "  "

    when (item) {
        is com.sujan.pipe.domain.PaletteItem.ContactAction -> {
            val contact = item.contact
            val pref = item.preferredAction
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else c.good,
                )
                AsciiContactAvatar(contact = contact)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(
                        text = contact.displayName,
                        style = if (isTop) AsciiType.title else AsciiType.body,
                        color = if (pressed) c.bg else c.fg,
                        maxLines = 1,
                    )
                    AsciiText(
                        text = contact.phoneNumber,
                        style = AsciiType.small,
                        color = if (pressed) c.bg else c.dim,
                        maxLines = 1,
                    )
                }
                AsciiButton("call", { onCallContact(contact) }, primary = isTop && pref == com.sujan.pipe.model.ContactQuickAction.CALL)
                AsciiButton("sms", { onSmsContact(contact) }, primary = isTop && pref == com.sujan.pipe.model.ContactQuickAction.SMS)
                AsciiButton("wa", { onWhatsAppContact(contact) }, primary = isTop && pref == com.sujan.pipe.model.ContactQuickAction.WHATSAPP)
            }
        }

        is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> {
            val bm = item.bookmark
            val badge = when (bm.kind) {
                BookmarkKind.FOLDER -> "[b:d/]"
                BookmarkKind.FILE -> "[b:f.]"
                BookmarkKind.LINK -> "[b:bm]"
            }
            val badgeCol = when (bm.kind) {
                BookmarkKind.FOLDER -> c.warn
                BookmarkKind.FILE -> c.accent3
                BookmarkKind.LINK -> c.accent2
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer $badge ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else badgeCol,
                )
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(
                        text = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title,
                        style = if (isTop) AsciiType.title else AsciiType.body,
                        color = if (pressed) c.bg else c.fg,
                        maxLines = 1,
                    )
                    if (bm.tags.isNotBlank()) {
                        AsciiText(
                            text = bm.tags,
                            style = AsciiType.small,
                            color = if (pressed) c.bg else c.accent,
                            maxLines = 1,
                        )
                    }
                }
                if (bm.kind == BookmarkKind.FOLDER && bm.alias.isNotBlank()) {
                    AsciiButton("ls", { onScopeIntoFolder("b ${bm.alias}") })
                }
                if (onDeleteBookmark != null) {
                    AsciiButton("x", { onDeleteBookmark(bm) }, spokenLabel = "delete bookmark ${bm.title}")
                }
            }
        }

        is com.sujan.pipe.domain.PaletteItem.FileAction -> {
            val f = item.file
            val badge = if (f.isDirectory) "[d/]" else "[f.]"
            val badgeCol = if (f.isDirectory) c.warn else c.accent3
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer $badge ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else badgeCol,
                )
                if (f.isImageFile) {
                    AsciiThumbnailBadge(
                        filePath = f.absolutePath,
                        onClick = { onPreviewImage?.invoke(f) ?: onExecute() },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(
                        text = f.name,
                        style = if (isTop) AsciiType.title else AsciiType.body,
                        color = if (pressed) c.bg else c.fg,
                        maxLines = 1,
                    )
                    AsciiText(
                        text = "${f.parentName} · ${f.formattedSize}",
                        style = AsciiType.small,
                        color = if (pressed) c.bg else c.dim,
                        maxLines = 1,
                    )
                }
                if (f.isImageFile && onPreviewImage != null) {
                    AsciiButton("view", { onPreviewImage(f) })
                }
                if (!f.isDirectory && onShareFile != null) {
                    AsciiButton("shr", { onShareFile(f) })
                }
                AsciiButton("cp", { clipboard.setText(AnnotatedString(f.absolutePath)) })
                if (onBookmarkFile != null) {
                    AsciiButton("+bm", { onBookmarkFile(f) })
                }
            }
        }

        is com.sujan.pipe.domain.PaletteItem.AppAction -> {
            val app = item.app
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer [app] ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else c.accent,
                )
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(
                        text = app.label,
                        style = if (isTop) AsciiType.title else AsciiType.body,
                        color = if (pressed) c.bg else c.fg,
                        maxLines = 1,
                    )
                    AsciiText(
                        text = app.packageName,
                        style = AsciiType.small,
                        color = if (pressed) c.bg else c.dim,
                        maxLines = 1,
                    )
                }
                val meta = if (item.usageCount > 0) "app #${item.usageCount}" else "app"
                AsciiText(
                    text = " $meta",
                    style = AsciiType.small,
                    color = if (pressed) c.bg else c.dim,
                    maxLines = 1,
                )
            }
        }

        is com.sujan.pipe.domain.PaletteItem.EngineAction -> {
            val eng = item.engine
            val desc = if (item.isHome) "${eng.name} (open home)" else "${eng.name}: \"${item.query}\""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer [s:${eng.keyword}] ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else c.forRole(eng.colorRole),
                )
                AsciiText(
                    text = desc,
                    style = if (isTop) AsciiType.title else AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (item.isHome && onDeleteEngine != null) {
                    AsciiButton("x", { onDeleteEngine(eng) }, spokenLabel = "delete pipe ${eng.name}")
                } else {
                    AsciiText(
                        text = " pipe",
                        style = AsciiType.small,
                        color = if (pressed) c.bg else c.dim,
                        maxLines = 1,
                    )
                }
            }
        }

        is com.sujan.pipe.domain.PaletteItem.QuickAddAction -> {
            val badgeCol = when (item.kind) {
                QuickAddKind.CONTACT -> c.good
                QuickAddKind.BOOKMARK -> c.accent2
                QuickAddKind.PIPE -> c.accent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer [${item.kind.tag}] ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else badgeCol,
                )
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(
                        text = item.title,
                        style = if (isTop) AsciiType.title else AsciiType.body,
                        color = if (pressed) c.bg else c.fg,
                        maxLines = 1,
                    )
                    AsciiText(
                        text = item.subtitle,
                        style = AsciiType.small,
                        color = if (pressed) c.bg else c.dim,
                        maxLines = 1,
                    )
                }
                AsciiButton(
                    label = if (item.isReadyToCommit) "save" else "form",
                    onClick = onExecute,
                    primary = item.isReadyToCommit,
                )
            }
        }

        is com.sujan.pipe.domain.PaletteItem.QuickRemoveAction -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer [-rm] ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else c.error,
                )
                AsciiText(
                    text = item.displayTitle,
                    style = if (isTop) AsciiType.title else AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                AsciiButton("del", onExecute, primary = isTop)
            }
        }

        is com.sujan.pipe.domain.PaletteItem.HistoryAction -> {
            val h = item.entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer [$ ] ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else c.accent2,
                )
                AsciiText(
                    text = "${h.rawInput} -> ${h.engineName.lowercase()}",
                    style = if (isTop) AsciiType.title else AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                val meta = if (item.usageCount > 0) "hist #${item.usageCount}" else "hist"
                AsciiText(
                    text = " $meta",
                    style = AsciiType.small,
                    color = if (pressed) c.bg else c.dim,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipeBookmarksScreen(
    bookmarks: List<PipeBookmark>,
    formState: BookmarkFormState,
    hasFilesPermission: Boolean,
    onBack: () -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (PipeBookmark) -> Unit,
    onDeleteClick: (PipeBookmark) -> Unit,
    onRequestFilesPermission: () -> Unit,
    onAliasChange: (TextFieldValue) -> Unit,
    onTitleChange: (TextFieldValue) -> Unit,
    onTargetChange: (TextFieldValue) -> Unit,
    onTagsChange: (TextFieldValue) -> Unit,
    onKindChange: (BookmarkKind) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalAscii.current

    Column(Modifier.fillMaxSize()) {
        AsciiTopHeader(
            path = "~/pipe/bm",
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
                    text = "type an alias (e.g. `dl`, `docs`, `pic`) in ~/pipe to jump to folders, files, or links.",
                    style = AsciiType.small,
                    color = c.dim,
                )
                if (!hasFilesPermission) {
                    Spacer(Modifier.height(4.dp))
                    AsciiButton("grant all-files access", onRequestFilesPermission, primary = true)
                }
            }

            items(bookmarks, key = { it.id }) { bm ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiText(
                            text = "[${bm.alias}] ${bm.title}",
                            style = AsciiType.title,
                            color = c.accent,
                        )
                        AsciiText(
                            text = bm.kind.badge,
                            style = AsciiType.small,
                            color = c.accent2,
                        )
                    }
                    AsciiText(text = bm.target, style = AsciiType.small, color = c.dim, maxLines = 1)
                    if (bm.tags.isNotBlank()) {
                        AsciiText(text = "tags: ${bm.tags}", style = AsciiType.small, color = c.accent2, maxLines = 1)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiButton("open", { onOpenBookmark(bm) }, primary = true)
                        AsciiButton("edit", { onEditClick(bm) })
                        AsciiButton("del", { onDeleteClick(bm) })
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        Box(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
            AsciiButton(
                label = "+ add bookmark (link / folder / file)",
                onClick = onAddClick,
                primary = true,
                modifier = Modifier.fillMaxWidth().border(1.dp, c.accent),
            )
        }
    }

    if (formState.isOpen) {
        Dialog(onDismissRequest = onCancel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .border(1.dp, c.accent2)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                AsciiBox(title = if (formState.editingId != null) "edit bookmark" else "new bookmark") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsciiText("type:", style = AsciiType.small, color = c.dim)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BookmarkKind.entries.forEach { k ->
                                AsciiRadio(
                                    selected = formState.kind == k,
                                    onClick = { onKindChange(k) },
                                    label = k.name.lowercase(),
                                )
                            }
                        }
                        AsciiTextField(
                            label = "alias (short trigger, e.g. dl, notes, hn)",
                            promptPrefix = "| ",
                            value = formState.alias,
                            onValueChange = onAliasChange,
                            placeholder = "dl",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "title",
                            promptPrefix = "> ",
                            value = formState.title,
                            onValueChange = onTitleChange,
                            placeholder = "Downloads",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = if (formState.kind == BookmarkKind.LINK) "url (https://...)" else "phone path (/storage/emulated/0/...)",
                            promptPrefix = "> ",
                            value = formState.target,
                            onValueChange = onTargetChange,
                            placeholder = if (formState.kind == BookmarkKind.LINK) "https://news.ycombinator.com" else "/storage/emulated/0/Download",
                            errorText = formState.errorText,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        )
                        AsciiTextField(
                            label = "optional #tags (e.g. #legal #work #docs)",
                            promptPrefix = "# ",
                            value = formState.tags,
                            onValueChange = onTagsChange,
                            placeholder = "#legal #id",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AsciiButton("cancel", onCancel)
                            AsciiButton("save", onSave, primary = true)
                        }
                    }
                }
            }
        }
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
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onSearchBoxPrefixChange: (String) -> Unit,
    onSearchBoxPlaceholderChange: (String) -> Unit,
    onShowScopeBadgeChange: (Boolean) -> Unit,
    onShowMenuBtnChange: (Boolean) -> Unit,
    onLaunchOverlayPreview: () -> Unit,
    onPinOverlayShortcut: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestFilesPermission: () -> Unit,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val section = state.settingsSection

    Column(Modifier.fillMaxSize()) {
        AsciiTopHeader(
            path = "~/pipe/set/${section.label}",
            actions = {
                AsciiButton(label = "< back", onClick = onBack, spokenLabel = "back to search prompt")
            },
        )

        // Standardized Sub-Tab Bar (`[1 ovl] [2 idx] [3 thm] [4 rte] [5 bak]`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSection.entries.forEach { tab ->
                val isSelected = section == tab
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val inverted = isSelected || pressed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .background(if (inverted) c.accent else Color.Transparent)
                        .clickable(
                            interactionSource = src,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelectSettingsSection(tab) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AsciiText(
                        text = "[${tab.index} ${tab.label}]",
                        style = if (isSelected) AsciiType.title else AsciiType.small,
                        color = if (inverted) c.bg else c.fg,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (section) {
                // TAB 1: OVERLAY & SEARCH BOX (Standardized Dropdowns + Toggles)
                SettingsSection.OVERLAY -> {
                    AsciiBox(title = "floating overlay") {
                        val pct = (state.overlayOpacity * 100).toInt()
                        val dimPct = (state.overlayDimAmount * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsciiText("card opacity: $pct%", style = AsciiType.body, color = c.fg)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsciiButton("-10%", { onOverlayOpacityChange(state.overlayOpacity - 0.10f) })
                                AsciiButton("+10%", { onOverlayOpacityChange(state.overlayOpacity + 0.10f) })
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsciiText("background dim: $dimPct%", style = AsciiType.body, color = c.fg)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsciiButton("-10%", { onOverlayDimChange(state.overlayDimAmount - 0.10f) })
                                AsciiButton("+10%", { onOverlayDimChange(state.overlayDimAmount + 0.10f) })
                            }
                        }

                        AsciiDropdownRow(
                            label = "overlay position",
                            selectedValue = state.overlayBottomAnchored,
                            options = listOf(
                                false to "Top of screen",
                                true to "Bottom (above keyboard)",
                            ),
                            onSelect = onOverlayBottomChange,
                        )

                        AsciiDropdownRow(
                            label = "corner radius",
                            selectedValue = state.overlayCornerRadiusDp,
                            options = listOf(
                                0 to "0dp (Sharp ASCII)",
                                10 to "10dp (Subtle)",
                                16 to "16dp (Default)",
                                24 to "24dp (Round)",
                            ),
                            onSelect = onOverlayCornerChange,
                        )

                        AsciiToggleRow(
                            label = "force dark glass in overlay",
                            checked = state.overlayForceDark,
                            onCheckedChange = onOverlayForceDarkChange,
                        )
                        AsciiToggleRow(
                            label = "show most-used items when idle",
                            checked = state.overlayShowIdleSuggestions,
                            onCheckedChange = onOverlayIdleChange,
                        )

                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsciiButton("test overlay", onLaunchOverlayPreview, primary = true)
                            AsciiButton("+ pin shortcut", onPinOverlayShortcut)
                        }
                    }

                    AsciiBox(title = "search box appearance") {
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
                            placeholder = "pipe (c, f, b, s, app)...",
                        )
                        Spacer(Modifier.height(4.dp))
                        AsciiDropdownRow(
                            label = "prompt prefix",
                            selectedValue = state.searchBoxPrefix,
                            options = listOf(
                                "> " to "> (Arrow)",
                                "$ " to "$ (Shell)",
                                "| " to "| (Pipe)",
                                "λ " to "λ (Lambda)",
                                "" to "None (Blank)",
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

                // TAB 2: PERSISTENT STORAGE INDEX & PERMISSIONS
                SettingsSection.INDEX -> {
                    AsciiBox(title = "local index & permissions") {
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsciiButton(
                                label = if (state.isIndexingInProgress) "indexing..." else "re-index now",
                                onClick = onRebuildIndexNow,
                                enabled = !state.isIndexingInProgress,
                                primary = true,
                            )
                            AsciiButton(
                                label = if (state.hasFilesPermission) "files: ok" else "! grant files",
                                onClick = onRequestFilesPermission,
                                primary = !state.hasFilesPermission,
                            )
                            AsciiButton(
                                label = if (state.hasContactsPermission) "contacts: ok" else "! grant contacts",
                                onClick = onRequestContactsPermission,
                                primary = !state.hasContactsPermission,
                            )
                        }
                    }

                    AsciiBox(title = "excluded folders (${state.excludedFolders.size})") {
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.excludedFolders.forEach { folderName ->
                                AsciiButton(
                                    label = "$folderName x",
                                    onClick = { onRemoveExcludedFolder(folderName) },
                                )
                            }
                        }
                    }
                }

                // TAB 3: THEME & PALETTE (Standardized Dropdowns)
                SettingsSection.THEME -> {
                    AsciiBox(title = "theme & color palette") {
                        AsciiDropdownRow(
                            label = "theme mode",
                            selectedValue = state.themeMode,
                            options = listOf(
                                ThemeMode.System to "System default",
                                ThemeMode.Dark to "Dark mode",
                                ThemeMode.Light to "Light mode",
                            ),
                            onSelect = onThemeModeChange,
                        )
                        AsciiDropdownRow(
                            label = "color palette",
                            selectedValue = state.paletteChoice,
                            options = PaletteChoice.entries.map { it to it.palette.name },
                            onSelect = onPaletteChoiceChange,
                        )
                    }
                }

                // TAB 4: DEFAULT BROWSER & ROUTING (Dropdown Menu!)
                SettingsSection.ROUTING -> {
                    AsciiBox(title = "browser & routing") {
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
                        AsciiDropdownRow(
                            label = "share text action",
                            subtitle = "when sharing or selecting text",
                            selectedValue = state.externalTextMode,
                            options = listOf(
                                ExternalTextMode.OPEN_OVERLAY to "Open floating overlay",
                                ExternalTextMode.OPEN_PROMPT to "Open main prompt",
                                ExternalTextMode.DIRECT_PIPE to "Pipe immediately",
                            ),
                            onSelect = onExternalTextModeChange,
                        )
                        AsciiToggleRow(
                            label = "record local command history",
                            checked = state.historyEnabled,
                            onCheckedChange = onHistoryEnabledChange,
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsciiButton("system default apps", onOpenDefaultAppsSettings, primary = true)
                            AsciiButton("copy pipe:// url", {
                                clipboard.setText(AnnotatedString("pipe://search?q=%s"))
                                onSetJsonStatus("ok: copied pipe://search?q=%s")
                            })
                        }
                    }
                }

                // TAB 5: BACKUP, RESET & ABOUT
                SettingsSection.BACKUP -> {
                    AsciiBox(title = "json backup & reset") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsciiButton("export file", onExportJsonToFile)
                            AsciiButton("import file", onImportJsonFromFile)
                            AsciiButton("copy json", onOpenJsonExportDialog)
                            AsciiButton("paste json", onOpenJsonImportDialog)
                            AsciiButton("reset pipes", onResetDefaults)
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

                    AsciiBox(title = "about") {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cols = rememberCols(AsciiType.small)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AsciiText("pipe v2.0 (release)", style = AsciiType.title, color = c.accent)
                                AsciiText(leader("package", "com.sujan.pipe", cols), style = AsciiType.small, color = c.dim, softWrap = false)
                                AsciiText(leader("bookmarks", "b <alias>", cols), style = AsciiType.small, color = c.accent2, softWrap = false)
                                AsciiText(leader("search pipes", "s <keyword> <query>", cols), style = AsciiType.small, color = c.accent, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.jsonDialogMode != null) {
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
