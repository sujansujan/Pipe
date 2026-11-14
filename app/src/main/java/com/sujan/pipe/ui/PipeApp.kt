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
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val res = state.liveResolution
    val cmd = state.parsedCommand

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AsciiTopHeader(
            path = "~/pipe",
            actions = {
                AsciiButton("ovl", onLaunchOverlay, spokenLabel = "open overlay mode")
                AsciiButton("bm (${state.bookmarks.size})", { onNavigate(PipeView.BOOKMARKS) }, spokenLabel = "bookmarks")
                AsciiButton("pipes", { onNavigate(PipeView.ENGINES) }, spokenLabel = "search engines")
                AsciiButton("set", { onNavigate(PipeView.SETTINGS) }, spokenLabel = "settings")
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Unified Command Prompt (`$ yt lofi`, `$ c kali`, `$ dl`, `$ f report`)
            item {
                Spacer(Modifier.height(6.dp))
                AsciiTextField(
                    label = null,
                    promptPrefix = "$ ",
                    value = state.searchInput,
                    onValueChange = onSearchInputChange,
                    placeholder = "yt lofi | c kali | dl | f notes",
                    errorText = state.launchError,
                    focusRequester = focusRequester,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitPrimaryAction() }),
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
                                onClick = onSubmitPrimaryAction,
                                primary = true,
                                spokenLabel = "execute pipe command",
                            )
                        }
                    },
                )

                // Live pipeline trace line
                Spacer(Modifier.height(4.dp))
                val traceText = when {
                    cmd.exactBookmark != null ->
                        "+--> [${cmd.exactBookmark.alias}] ${cmd.exactBookmark.kind.badge} :: ${cmd.exactBookmark.target}"
                    cmd.mode == PromptIntentMode.CONTACTS_ONLY ->
                        "+--> [c] contacts fuzzy search :: \"${cmd.filterQuery}\""
                    cmd.mode == PromptIntentMode.FILES_ONLY ->
                        "+--> [f] storage files fuzzy search :: \"${cmd.filterQuery}\""
                    cmd.mode == PromptIntentMode.SCOPED_FOLDER ->
                        "+--> [${cmd.scopedFolderBookmark?.alias}] folder filter :: \"${cmd.filterQuery}\" in ${cmd.scopedFolderBookmark?.target}"
                    else -> "+--> ${res.pipelinePreview}"
                }
                AsciiText(
                    text = traceText,
                    style = AsciiType.small,
                    color = c.accent,
                    maxLines = 1,
                )
            }

            // 2. Quick Mode & Engine Pills (`[|c contacts] [|dl downloads] [|f files] [|g* google] [|yt youtube] ...`)
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AsciiPipePill(
                        keyword = "c",
                        name = "contacts",
                        selected = cmd.mode == PromptIntentMode.CONTACTS_ONLY,
                        accentColor = c.good,
                        onClick = {
                            onAliasPillClick("c")
                            runCatching { focusRequester.requestFocus() }
                        },
                    )
                    AsciiPipePill(
                        keyword = "dl",
                        name = "downloads",
                        selected = cmd.scopedFolderBookmark?.alias == "dl",
                        accentColor = c.warn,
                        onClick = {
                            onAliasPillClick("dl")
                            runCatching { focusRequester.requestFocus() }
                        },
                    )
                    AsciiPipePill(
                        keyword = "f",
                        name = "files",
                        selected = cmd.mode == PromptIntentMode.FILES_ONLY,
                        accentColor = c.accent3,
                        onClick = {
                            onAliasPillClick("f")
                            runCatching { focusRequester.requestFocus() }
                        },
                    )
                    state.suggestedEngines.forEach { eng ->
                        val active = cmd.mode == PromptIntentMode.GENERAL &&
                            cmd.exactBookmark == null &&
                            res.matchedByKeyword &&
                            res.engine.id == eng.id
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

            // 3. Permission prompt banners when Contacts or Files are queried without permission
            if (!state.hasContactsPermission && (cmd.mode == PromptIntentMode.CONTACTS_ONLY || state.searchInput.text.isNotBlank())) {
                item {
                    AsciiBox(title = "contacts permission") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsciiText("grant contacts access for `c <name>` search:", style = AsciiType.small, color = c.warn)
                            AsciiButton("grant contacts", onRequestContactsPermission, primary = true)
                        }
                    }
                }
            }

            if (!state.hasFilesPermission && (cmd.mode == PromptIntentMode.FILES_ONLY || cmd.mode == PromptIntentMode.SCOPED_FOLDER)) {
                item {
                    AsciiBox(title = "all-files access") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsciiText("grant storage access to open & search phone files:", style = AsciiType.small, color = c.warn)
                            AsciiButton("grant files", onRequestFilesPermission, primary = true)
                        }
                    }
                }
            }

            // 4. Live Contact Fuzzy Search Results (`c kali` or matching contact names)
            if (state.matchedContacts.isNotEmpty()) {
                item {
                    AsciiBox(title = "contacts (${state.matchedContacts.size})") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.matchedContacts.forEach { contact ->
                                ContactResultRow(
                                    contact = contact,
                                    onCall = { onCallContact(contact) },
                                    onSms = { onSmsContact(contact) },
                                    onWhatsApp = { onWhatsAppContact(contact) },
                                    onCopy = { clipboard.setText(AnnotatedString(contact.phoneNumber)) },
                                )
                            }
                        }
                    }
                }
            }

            // 5. Live Bookmarks (Folders, Files, Links)
            if (state.matchedBookmarks.isNotEmpty()) {
                item {
                    AsciiBox(
                        title = "bookmarks (${state.matchedBookmarks.size})",
                        rightActionLabel = "manage",
                        onRightAction = { onNavigate(PipeView.BOOKMARKS) },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.matchedBookmarks.forEach { bm ->
                                BookmarkResultRow(
                                    bookmark = bm,
                                    onOpen = { onOpenBookmark(bm) },
                                    onScopeInto = if (bm.kind == BookmarkKind.FOLDER) {
                                        {
                                            onAliasPillClick(bm.alias)
                                            runCatching { focusRequester.requestFocus() }
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }

            // 6. Live Fuzzy-Searched Files & Folders
            if (state.matchedFiles.isNotEmpty()) {
                val boxTitle = if (cmd.mode == PromptIntentMode.SCOPED_FOLDER) {
                    "files in ${cmd.scopedFolderBookmark?.title ?: "folder"} (${state.matchedFiles.size})"
                } else {
                    "files & folders (${state.matchedFiles.size})"
                }
                item {
                    AsciiBox(title = boxTitle) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.matchedFiles.forEach { fileItem ->
                                FileResultRow(
                                    item = fileItem,
                                    onOpen = { onOpenLocalFile(fileItem) },
                                    onBookmark = { onBookmarkLocalFile(fileItem) },
                                )
                            }
                        }
                    }
                }
            }

            // 7. Command History
            item {
                AsciiBox(
                    title = "history (${state.history.size})",
                    rightActionLabel = if (state.history.isNotEmpty()) "clear all" else null,
                    onRightAction = if (state.history.isNotEmpty()) onClearHistory else null,
                ) {
                    if (state.history.isEmpty()) {
                        AsciiText(
                            text = "nothing piped yet. try `yt lofi`, `c kali`, or `dl`.",
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
fun ContactResultRow(
    contact: ContactItem,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit,
    onCopy: () -> Unit,
) {
    val c = LocalAscii.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, c.border)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = "@ ${contact.displayName}",
                style = AsciiType.title,
                color = c.good,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            AsciiText(
                text = contact.phoneNumber,
                style = AsciiType.small,
                color = c.fg,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiButton("call", onCall, primary = true, spokenLabel = "call ${contact.displayName}")
            AsciiButton("sms", onSms, spokenLabel = "text ${contact.displayName}")
            AsciiButton("wa", onWhatsApp, spokenLabel = "whatsapp ${contact.displayName}")
            AsciiButton("copy", onCopy, spokenLabel = "copy number for ${contact.displayName}")
        }
    }
}

@Composable
fun BookmarkResultRow(
    bookmark: PipeBookmark,
    onOpen: () -> Unit,
    onScopeInto: (() -> Unit)? = null,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val badgeColor = when (bookmark.kind) {
        BookmarkKind.FOLDER -> c.warn
        BookmarkKind.FILE -> c.accent3
        BookmarkKind.LINK -> c.accent2
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (pressed) c.fg else Color.Transparent)
            .border(1.dp, c.border)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bookmark.alias.isNotBlank()) {
                    AsciiText(
                        text = "[${bookmark.alias}] ",
                        style = AsciiType.title,
                        color = if (pressed) c.bg else badgeColor,
                    )
                }
                AsciiText(
                    text = bookmark.title,
                    style = AsciiType.body,
                    color = if (pressed) c.bg else c.fg,
                    maxLines = 1,
                )
                AsciiText(
                    text = " (${bookmark.kind.badge})",
                    style = AsciiType.small,
                    color = if (pressed) c.bg else c.dim,
                )
            }
            AsciiText(
                text = bookmark.target,
                style = AsciiType.small,
                color = if (pressed) c.bg else c.dim,
                maxLines = 1,
            )
        }
        if (onScopeInto != null) {
            AsciiButton("ls", onScopeInto, spokenLabel = "search files inside ${bookmark.title}")
        }
        AsciiButton("open", onOpen, primary = true, spokenLabel = "open ${bookmark.title}")
    }
}

@Composable
fun FileResultRow(
    item: LocalFileItem,
    onOpen: () -> Unit,
    onBookmark: () -> Unit,
) {
    val c = LocalAscii.current
    val prefix = if (item.isDirectory) "[dir] " else "[file]"
    val tint = if (item.isDirectory) c.warn else c.accent3

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .border(1.dp, c.border)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsciiText(text = "$prefix ", style = AsciiType.small, color = tint)
                AsciiText(text = item.name, style = AsciiType.body, color = c.fg, maxLines = 1)
            }
            AsciiText(text = item.absolutePath, style = AsciiType.small, color = c.dim, maxLines = 1)
        }
        AsciiButton("+bm", onBookmark, spokenLabel = "bookmark ${item.name}")
        AsciiButton("open", onOpen, primary = true, spokenLabel = "open ${item.name}")
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
            .heightIn(min = 44.dp)
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

            // 2. Overlay & UbikiTouch Shortcut
            AsciiBox(title = "overlay mode & ubitouch shortcut") {
                AsciiText(
                    text = "bind in UbikiTouch via Shortcuts -> `pipe overlay` or Activities -> `PipeOverlayActivity`:",
                    style = AsciiType.small,
                    color = c.dim,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsciiButton("launch overlay now", onLaunchOverlayPreview, primary = true)
                    AsciiButton("+ pin overlay shortcut", onPinOverlayShortcut)
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
