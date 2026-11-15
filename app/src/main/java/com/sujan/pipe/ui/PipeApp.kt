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
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
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
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimaryAction: () -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onOpenLocalFile: (LocalFileItem) -> Unit,
    onBookmarkLocalFile: (LocalFileItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
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
                        onBookmarkLocalFile = onBookmarkLocalFile,
                        onCallContact = onCallContact,
                        onSmsContact = onSmsContact,
                        onWhatsAppContact = onWhatsAppContact,
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
                        onLaunchOverlayPreview = onLaunchOverlayPreview,
                        onPinOverlayShortcut = onPinOverlayShortcut,
                        onOpenDefaultAppsSettings = onOpenDefaultAppsSettings,
                        onRequestContactsPermission = onRequestContactsPermission,
                        onRequestFilesPermission = onRequestFilesPermission,
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
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimaryAction: () -> Unit,
    onOpenBookmark: (PipeBookmark) -> Unit,
    onOpenLocalFile: (LocalFileItem) -> Unit,
    onBookmarkLocalFile: (LocalFileItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
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
            launchError = state.launchError,
            focusRequester = focusRequester,
            headerRightActions = {
                AsciiButton("ovl", onLaunchOverlay, spokenLabel = "overlay")
                AsciiButton("bm", { onNavigate(PipeView.BOOKMARKS) }, spokenLabel = "bookmarks")
                AsciiButton("pipes", { onNavigate(PipeView.ENGINES) }, spokenLabel = "engines")
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
                    is com.sujan.pipe.domain.PaletteItem.ContactAction -> onCallContact(item.contact)
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
            onBookmarkFile = onBookmarkLocalFile,
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
    headerRightActions: @Composable () -> Unit,
    onAliasPillClick: (String) -> Unit,
    onSubmitPrimary: () -> Unit,
    onExecuteItem: (com.sujan.pipe.domain.PaletteItem) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onScopeIntoFolder: (String) -> Unit,
    onBookmarkFile: ((LocalFileItem) -> Unit)? = null,
    onRequestContactsPermission: (() -> Unit)? = null,
    onRequestFilesPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val scopeBadge = when (parsedCommand.mode) {
        PromptIntentMode.SCOPED_FOLDER -> "${parsedCommand.scopedFolderBookmark?.alias ?: "dir"}:folder"
        PromptIntentMode.ENGINE_ONLY -> "${parsedCommand.matchedEngine?.keyword ?: "web"}:pipe"
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
            AsciiText(
                text = "> ",
                style = AsciiType.title,
                color = c.accent,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (searchInput.text.isEmpty()) {
                    AsciiText(
                        text = "type alias (c, f, dl, yt) or search all...",
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
            // Raycast right-hand Scope Tag `[all]` / `[c:contacts]` / `[f:files+dirs]`
            AsciiText(
                text = "[$scopeBadge]",
                style = AsciiType.small,
                color = if (parsedCommand.mode == PromptIntentMode.ALL) c.dim else c.accent,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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

        // 3. ASCII Mid-Frame Divider (`├─ results (10 max · most used) ─────────────────┤`)
        com.sujan.pipe.ui.ascii.AsciiFrameDivider(
            leftLabel = if (parsedCommand.mode == PromptIntentMode.ALL && searchInput.text.isBlank()) {
                "most used (${results.size}/10)"
            } else {
                "$scopeBadge (${results.size}/10)"
            },
            rightLabel = "rank: usage",
        )

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
                AsciiButton("c:", { onAliasPillClick("c") }, spokenLabel = "contacts alias c")
                AsciiButton("f:", { onAliasPillClick("f") }, spokenLabel = "files and folders alias f")
                AsciiButton("d:", { onAliasPillClick("d") }, spokenLabel = "folders alias d")
                AsciiButton("dl:", { onAliasPillClick("dl") }, spokenLabel = "downloads alias dl")
                AsciiButton("yt:", { onAliasPillClick("yt") }, spokenLabel = "youtube alias yt")
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
private fun AsciiRaycastRow(
    item: com.sujan.pipe.domain.PaletteItem,
    isTop: Boolean,
    onExecute: () -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onScopeIntoFolder: (String) -> Unit,
    onBookmarkFile: ((LocalFileItem) -> Unit)?,
) {
    val c = LocalAscii.current
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(rowBg)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = { onCallContact(contact) })
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "$pointer [@c] ",
                    style = if (isTop) AsciiType.title else AsciiType.small,
                    color = if (pressed) c.bg else c.good,
                )
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
                if (item.usageCount > 0) {
                    AsciiText(
                        text = "#${item.usageCount} ",
                        style = AsciiType.small,
                        color = if (pressed) c.bg else c.dim,
                    )
                }
                AsciiButton("call", { onCallContact(contact) }, primary = isTop)
                AsciiButton("sms", { onSmsContact(contact) })
                AsciiButton("wa", { onWhatsAppContact(contact) })
            }
        }

        is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> {
            val bm = item.bookmark
            val badge = when (bm.kind) {
                BookmarkKind.FOLDER -> "[d/]"
                BookmarkKind.FILE -> "[f.]"
                BookmarkKind.LINK -> "[bm]"
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
                AsciiText(
                    text = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title,
                    style = if (isTop) AsciiType.title else AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (bm.kind == BookmarkKind.FOLDER && bm.alias.isNotBlank()) {
                    AsciiButton("ls", { onScopeIntoFolder(bm.alias) })
                }
                val meta = if (item.usageCount > 0) "${bm.kind.badge} #${item.usageCount}" else bm.kind.badge
                AsciiText(
                    text = " $meta",
                    style = AsciiType.small,
                    color = if (pressed) c.bg else c.dim,
                    maxLines = 1,
                )
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
                if (onBookmarkFile != null) {
                    AsciiButton("+bm", { onBookmarkFile(f) })
                }
                val kindTag = f.formattedSize
                val meta = if (item.usageCount > 0) "$kindTag #${item.usageCount}" else kindTag
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
                    text = "$pointer [|${eng.keyword}] ",
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
                val meta = if (item.usageCount > 0) "web #${item.usageCount}" else "web"
                AsciiText(
                    text = " $meta",
                    style = AsciiType.small,
                    color = if (pressed) c.bg else c.dim,
                    maxLines = 1,
                )
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
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
    onLaunchOverlayPreview: () -> Unit,
    onPinOverlayShortcut: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestFilesPermission: () -> Unit,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current

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
            // 1. Permissions & Default Browser Setup
            AsciiBox(title = "default browser, contacts & storage access") {
                AsciiText(
                    text = "pipe is registered as a Browser so Android lets you select it under Default Apps -> Browser app (and Default Assist app).",
                    style = AsciiType.small,
                    color = c.fg,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsciiButton("set default browser / assist", onOpenDefaultAppsSettings, primary = true)
                    AsciiButton(
                        label = if (state.hasContactsPermission) "contacts: ok" else "grant contacts",
                        onClick = onRequestContactsPermission,
                    )
                    AsciiButton(
                        label = if (state.hasFilesPermission) "files: ok" else "grant all-files",
                        onClick = onRequestFilesPermission,
                    )
                }
            }

            // 2. Raycast Overlay Design, Transparency, Soft Edges & Position
            AsciiBox(title = "raycast overlay appearance & ubitouch") {
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
                Spacer(Modifier.height(4.dp))
                AsciiText("soft corner radius:", style = AsciiType.small, color = c.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "0dp", 10 to "10dp", 16 to "16dp", 24 to "24dp").forEach { (dpVal, label) ->
                        AsciiRadio(
                            selected = state.overlayCornerRadiusDp == dpVal,
                            onClick = { onOverlayCornerChange(dpVal) },
                            label = label,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                AsciiText("overlay position:", style = AsciiType.small, color = c.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsciiRadio(!state.overlayBottomAnchored, { onOverlayBottomChange(false) }, "top")
                    AsciiRadio(state.overlayBottomAnchored, { onOverlayBottomChange(true) }, "bottom (above keyboard)")
                }
                AsciiCheckbox(
                    checked = state.overlayForceDark,
                    onChange = onOverlayForceDarkChange,
                    label = "force dark glass theme in overlay",
                )
                AsciiCheckbox(
                    checked = state.overlayShowIdleSuggestions,
                    onChange = onOverlayIdleChange,
                    label = "show top most-used items before typing",
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsciiButton("test overlay now", onLaunchOverlayPreview, primary = true)
                    AsciiButton("+ pin ubitouch shortcut", onPinOverlayShortcut)
                }
            }

            // 3. Theme & Palette
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

            // 4. Share & Process Text Behavior
            AsciiBox(title = "pipeline behavior") {
                AsciiCheckbox(
                    checked = state.historyEnabled,
                    onChange = onHistoryEnabledChange,
                    label = "record local command history",
                )
                Spacer(Modifier.height(6.dp))
                AsciiText("share sheet & text selection (process text):", style = AsciiType.small, color = c.dim)
                AsciiRadio(
                    selected = state.externalTextMode == ExternalTextMode.OPEN_OVERLAY,
                    onClick = { onExternalTextModeChange(ExternalTextMode.OPEN_OVERLAY) },
                    label = "open floating ~/pipe/overlay with text pre-filled",
                )
                AsciiRadio(
                    selected = state.externalTextMode == ExternalTextMode.OPEN_PROMPT,
                    onClick = { onExternalTextModeChange(ExternalTextMode.OPEN_PROMPT) },
                    label = "open full ~/pipe prompt with text pre-filled",
                )
                AsciiRadio(
                    selected = state.externalTextMode == ExternalTextMode.DIRECT_PIPE,
                    onClick = { onExternalTextModeChange(ExternalTextMode.DIRECT_PIPE) },
                    label = "pipe immediately to matched / default engine",
                )
            }

            // 5. JSON Backup & Reset
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

            // 6. About
            AsciiBox(title = "about") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cols = rememberCols(AsciiType.small)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (cols >= 28) {
                            AsciiText(
                                text = frame(
                                    title = "pipe v1.2",
                                    lines = listOf(
                                        "pipe queries, contacts, files & bookmarks.",
                                        "aliases : c <contact>, dl <file>, f <file>",
                                        "browser : loop-safe default search router",
                                    ),
                                    cols = cols,
                                ),
                                style = AsciiType.small,
                                color = c.accent,
                                softWrap = false,
                            )
                        } else {
                            AsciiText("pipe v1.2", style = AsciiType.title, color = c.accent)
                        }
                        AsciiText(leader("package", "com.sujan.pipe", cols), style = AsciiType.small, color = c.dim, softWrap = false)
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
