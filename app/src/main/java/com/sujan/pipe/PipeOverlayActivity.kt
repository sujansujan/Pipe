package com.sujan.pipe

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.QuickAddKind
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.ContactQuickAction
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.InstalledAppItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.AsciiContactAvatar
import com.sujan.pipe.ui.AsciiImagePreviewDialog
import com.sujan.pipe.ui.AsciiThumbnailBadge
import com.sujan.pipe.ui.QuickAddInlineCard
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme
import com.sujan.pipe.ui.ascii.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipeOverlayActivity : ComponentActivity() {

    private val repository by lazy { PipeRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dimAmount = repository.loadOverlayDimAmount()
        window?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.attributes = w.attributes.apply {
                this.dimAmount = dimAmount
            }
        }

        val initialText = extractPrefill(intent)
        val engines = repository.loadEngines()
        val bookmarks = repository.loadBookmarks()
        val history = repository.loadHistory()
        val initialUsage = repository.loadUsageCounts()

        val forceDark = repository.loadOverlayForceDark()
        val resolvedMode = if (forceDark) ThemeMode.Dark else repository.loadThemeMode()
        val palette = repository.loadPaletteChoice().palette
        val cardOpacity = repository.loadOverlayOpacity()
        val cornerRadiusDp = repository.loadOverlayCornerRadiusDp()
        val bottomAnchored = repository.loadOverlayBottomAnchored()
        val showIdleSuggestions = repository.loadOverlayShowIdleSuggestions()
        val promptPrefix = repository.loadSearchBoxPrefix()
        val placeholderText = repository.loadSearchBoxPlaceholder()
        val showScopeBadge = repository.loadShowSearchBoxScopeBadge()
        val showMenuBtn = repository.loadShowSearchBoxMenuBtn()

        setContent {
            PipeTheme(
                mode = resolvedMode,
                palette = palette,
                configureSystemBars = false,
            ) {
                SleekRaycastOverlay(
                    initialQuery = initialText,
                    engines = engines,
                    bookmarks = bookmarks,
                    history = history,
                    initialUsage = initialUsage,
                    cardOpacity = cardOpacity,
                    cornerRadiusDp = cornerRadiusDp,
                    bottomAnchored = bottomAnchored,
                    showIdleSuggestions = showIdleSuggestions,
                    promptPrefix = promptPrefix,
                    placeholderText = placeholderText,
                    showScopeBadge = showScopeBadge,
                    showMenuBtn = showMenuBtn,
                    initialCachedFiles = DeviceIntegrationManager.peekMemoryStorageFiles(),
                    initialCachedApps = DeviceIntegrationManager.peekMemoryInstalledApps(),
                    loadContacts = {
                        withContext(Dispatchers.IO) {
                            DeviceIntegrationManager.getCachedOrLoadContacts(applicationContext)
                        }
                    },
                    loadApps = {
                        withContext(Dispatchers.IO) {
                            DeviceIntegrationManager.loadInstalledApps(applicationContext)
                        }
                    },
                    loadFiles = { scopedPath ->
                        withContext(Dispatchers.IO) {
                            if (scopedPath != null) {
                                DeviceIntegrationManager.scanStorageFiles(applicationContext, bookmarks, scopedPath)
                            } else {
                                val cached = DeviceIntegrationManager.getCachedStorageFiles(applicationContext)
                                if (cached.isEmpty() || DeviceIntegrationManager.isIndexStale(applicationContext)) {
                                    DeviceIntegrationManager.rebuildAndSaveStorageIndex(applicationContext, bookmarks)
                                } else {
                                    cached
                                }
                            }
                        }
                    },
                    onRecordUsage = { key -> repository.incrementUsage(key) },
                    onDispatchUrl = { rawInput, url ->
                        val ok = DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                        if (ok) {
                            repository.recordResolutionInHistory(rawInput, QueryRouter.resolve(rawInput, engines))
                            finish()
                        }
                        ok
                    },
                    onOpenPath = { path ->
                        val ok = DeviceIntegrationManager.openLocalFileOrFolder(this, path)
                        if (ok) finish()
                        ok
                    },
                    onShareFile = { path ->
                        if (DeviceIntegrationManager.shareLocalFile(this, path)) finish()
                    },
                    onLaunchApp = { pkg ->
                        if (DeviceIntegrationManager.launchInstalledApp(this, pkg)) finish()
                    },
                    onCallContact = { contact ->
                        repository.incrementUsage("contact:${contact.cleanPhone}")
                        if (DeviceIntegrationManager.dialContact(this, contact.phoneNumber)) finish()
                    },
                    onSmsContact = { contact ->
                        repository.incrementUsage("contact:${contact.cleanPhone}")
                        if (DeviceIntegrationManager.smsContact(this, contact.phoneNumber)) finish()
                    },
                    onWhatsAppContact = { contact ->
                        repository.incrementUsage("contact:${contact.cleanPhone}")
                        val digits = QueryRouter.normalizeWhatsAppPhone(contact.phoneNumber)
                        if (DeviceIntegrationManager.whatsAppContact(this, digits)) finish()
                    },
                    onSaveNewContact = { name, phone ->
                        DeviceIntegrationManager.insertContactDirect(this, name, phone)
                    },
                    onSaveBookmarksList = { updated ->
                        repository.saveBookmarks(updated)
                    },
                    onSaveEnginesList = { updated ->
                        repository.saveEngines(updated)
                    },
                    onOpenFullApp = { currentText ->
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_PREFILL_QUERY, currentText)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun extractPrefill(intent: Intent?): String {
        if (intent == null) return ""
        return intent.getStringExtra(MainActivity.EXTRA_PREFILL_QUERY)
            ?: intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""
    }
}

@Composable
private fun SleekRaycastOverlay(
    initialQuery: String,
    engines: List<SearchEngine>,
    bookmarks: List<PipeBookmark>,
    history: List<SearchHistoryEntry>,
    initialUsage: Map<String, Int>,
    cardOpacity: Float,
    cornerRadiusDp: Int,
    bottomAnchored: Boolean,
    showIdleSuggestions: Boolean,
    promptPrefix: String,
    placeholderText: String,
    showScopeBadge: Boolean,
    showMenuBtn: Boolean,
    initialCachedFiles: List<LocalFileItem>,
    initialCachedApps: List<InstalledAppItem>,
    loadContacts: suspend () -> List<ContactItem>,
    loadApps: suspend () -> List<InstalledAppItem>,
    loadFiles: suspend (String?) -> List<LocalFileItem>,
    onRecordUsage: (String) -> Map<String, Int>,
    onDispatchUrl: (String, String) -> Boolean,
    onOpenPath: (String) -> Boolean,
    onShareFile: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onSaveNewContact: (String, String) -> Boolean,
    onSaveBookmarksList: (List<PipeBookmark>) -> Unit,
    onSaveEnginesList: (List<SearchEngine>) -> Unit,
    onOpenFullApp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var input by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    var activeEngines by remember { mutableStateOf(engines) }
    var activeBookmarks by remember { mutableStateOf(bookmarks) }
    var usageMap by remember { mutableStateOf(initialUsage) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var inlineQuickAdd by remember { mutableStateOf<PaletteItem.QuickAddAction?>(null) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var installedApps by remember { mutableStateOf(initialCachedApps) }
    var storageFiles by remember { mutableStateOf(initialCachedFiles) }
    var scopedFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }
    var expandedFolderChildren by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var previewImageFile by remember { mutableStateOf<LocalFileItem?>(null) }

    val clipText = remember(input.text) { clipboard.getText()?.text?.trim().orEmpty() }

    val parsedCommand = remember(input.text, activeBookmarks, activeEngines) {
        QueryRouter.parsePromptCommand(input.text, activeBookmarks, activeEngines)
    }

    var results by remember {
        mutableStateOf(
            QueryRouter.buildPaletteResults(
                rawInput = initialQuery,
                engines = activeEngines,
                bookmarks = activeBookmarks,
                contacts = emptyList(),
                storageFiles = initialCachedFiles,
                scopedFolderFiles = emptyList(),
                history = history,
                usageMap = initialUsage,
                installedApps = initialCachedApps,
                clipboardText = clipText,
            )
        )
    }

    LaunchedEffect(input.text, activeEngines, activeBookmarks, contacts, installedApps, storageFiles, scopedFiles, history, usageMap, clipText) {
        val computed = withContext(Dispatchers.Default) {
            QueryRouter.buildPaletteResults(
                rawInput = input.text,
                engines = activeEngines,
                bookmarks = activeBookmarks,
                contacts = contacts,
                storageFiles = storageFiles,
                scopedFolderFiles = scopedFiles,
                history = history,
                usageMap = usageMap,
                installedApps = installedApps,
                clipboardText = clipText,
            )
        }
        results = computed
    }

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        contacts = loadContacts()
        installedApps = loadApps()
        storageFiles = loadFiles(null)
    }

    LaunchedEffect(parsedCommand.scopedFolderBookmark?.target) {
        val target = parsedCommand.scopedFolderBookmark?.target
        if (target != null) {
            scopedFiles = loadFiles(target)
        }
    }

    LaunchedEffect(expandedFolderPath) {
        val path = expandedFolderPath
        expandedFolderChildren = if (path != null) {
            withContext(Dispatchers.IO) {
                DeviceIntegrationManager.listDirectChildren(path)
            }
        } else {
            emptyList()
        }
    }

    val commitNewContact: (String, String) -> Unit = { name, phone ->
        val ok = onSaveNewContact(name, phone)
        if (ok) {
            val newContact = ContactItem(
                id = "local_${System.currentTimeMillis()}",
                displayName = name.trim(),
                phoneNumber = phone.trim(),
            )
            contacts = (contacts + newContact).sortedBy { it.nameLower }
            inlineQuickAdd = null
            input = TextFieldValue("")
            statusMsg = "ok: saved contact ${name.trim()} (${phone.trim()})"
        } else {
            errorMsg = "! could not save contact"
        }
    }

    val commitNewBookmark: (String, String, String, BookmarkKind, String) -> Unit = { alias, title, target, kind, tags ->
        val cleanAlias = alias.trim().lowercase().filter { !it.isWhitespace() }
        val cleanTitle = title.trim().ifEmpty { cleanAlias.ifEmpty { "Bookmark" } }
        val cleanTarget = target.trim()
        val existingIdx = if (cleanAlias.isNotEmpty()) {
            activeBookmarks.indexOfFirst { it.alias.equals(cleanAlias, ignoreCase = true) }
        } else -1
        val updated = if (existingIdx >= 0) {
            activeBookmarks.mapIndexed { idx, bm ->
                if (idx == existingIdx) bm.copy(alias = cleanAlias, title = cleanTitle, target = cleanTarget, kind = kind, tags = tags.ifEmpty { bm.tags }) else bm
            }
        } else {
            activeBookmarks + PipeBookmark(
                id = "bm_${System.currentTimeMillis()}",
                alias = cleanAlias,
                title = cleanTitle,
                target = cleanTarget,
                kind = kind,
                tags = tags,
            )
        }
        activeBookmarks = updated
        onSaveBookmarksList(updated)
        inlineQuickAdd = null
        input = TextFieldValue("")
        statusMsg = "ok: saved bookmark [${cleanAlias.ifEmpty { cleanTitle }}]"
    }

    val commitNewPipe: (String, String, String) -> Unit = { keyword, name, rawUrl ->
        val cleanKey = keyword.trim().lowercase().filter { !it.isWhitespace() }
        val cleanName = name.trim().ifEmpty { cleanKey.uppercase() }
        val cleanUrl = QueryRouter.normalizePipeUrlTemplate(rawUrl)
        val existingIdx = activeEngines.indexOfFirst { it.keyword.equals(cleanKey, ignoreCase = true) }
        val updated = if (existingIdx >= 0) {
            activeEngines.mapIndexed { idx, eng ->
                if (idx == existingIdx) eng.copy(name = cleanName, keyword = cleanKey, urlTemplate = cleanUrl) else eng
            }
        } else {
            activeEngines + SearchEngine(
                id = "eng_${cleanKey}_${System.currentTimeMillis()}",
                name = cleanName,
                keyword = cleanKey,
                urlTemplate = cleanUrl,
                colorRole = EngineColorRole.ACCENT2,
                isDefault = false,
            )
        }
        activeEngines = updated
        onSaveEnginesList(updated)
        inlineQuickAdd = null
        input = TextFieldValue("")
        statusMsg = "ok: saved pipe [|$cleanKey] $cleanName"
    }

    val executeItem: (PaletteItem) -> Unit = { item ->
        usageMap = onRecordUsage(item.usageKey)
        when (item) {
            is PaletteItem.EngineAction -> {
                val raw = if (item.query.isNotEmpty()) "${item.engine.keyword} ${item.query}" else item.engine.keyword
                if (!onDispatchUrl(raw, item.targetUrl)) errorMsg = "! no browser found"
            }
            is PaletteItem.BookmarkAction -> {
                val bm = item.bookmark
                val ok = if (bm.kind == BookmarkKind.LINK) onDispatchUrl(bm.alias, bm.target) else onOpenPath(bm.target)
                if (!ok) errorMsg = "! could not open ${bm.title}"
            }
            is PaletteItem.FileAction -> {
                if (!onOpenPath(item.file.absolutePath)) errorMsg = "! could not open ${item.file.name}"
            }
            is PaletteItem.ContactAction -> {
                when (item.preferredAction) {
                    ContactQuickAction.WHATSAPP -> onWhatsAppContact(item.contact)
                    ContactQuickAction.SMS -> onSmsContact(item.contact)
                    ContactQuickAction.CALL -> onCallContact(item.contact)
                }
            }
            is PaletteItem.AppAction -> onLaunchApp(item.app.packageName)
            is PaletteItem.QuickAddAction -> {
                if (item.isReadyToCommit) {
                    when (item.kind) {
                        QuickAddKind.CONTACT -> commitNewContact(item.arg1, item.arg2)
                        QuickAddKind.BOOKMARK -> commitNewBookmark(item.arg1, item.arg2, item.arg3, item.bookmarkKind, "")
                        QuickAddKind.PIPE -> commitNewPipe(item.arg1, item.arg2, item.arg3)
                    }
                } else {
                    inlineQuickAdd = item
                }
            }
            is PaletteItem.QuickRemoveAction -> {
                if (item.isBookmark) {
                    val updated = activeBookmarks.filterNot { it.id == item.targetId }
                    activeBookmarks = updated
                    onSaveBookmarksList(updated)
                    input = TextFieldValue("")
                    statusMsg = "ok: removed bookmark [${item.aliasOrKeyword}]"
                } else if (activeEngines.size > 1) {
                    val updated = activeEngines.filterNot { it.id == item.targetId }
                    activeEngines = updated
                    onSaveEnginesList(updated)
                    input = TextFieldValue("")
                    statusMsg = "ok: removed pipe [|${item.aliasOrKeyword}]"
                }
            }
            is PaletteItem.HistoryAction -> {
                if (!onDispatchUrl(item.entry.rawInput, item.entry.resolvedUrl)) errorMsg = "! no browser found"
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val glassBg = c.bg.copy(alpha = cardOpacity)
    val glassSurface = c.surface.copy(alpha = (cardOpacity + 0.08f).coerceAtMost(0.95f))
    val glassBorder = c.border.copy(alpha = 0.75f)
    val scopeBadge = when (parsedCommand.mode) {
        PromptIntentMode.SCOPED_FOLDER -> parsedCommand.scopedFolderBookmark?.alias ?: "dir"
        PromptIntentMode.ENGINE_ONLY -> parsedCommand.matchedEngine?.keyword ?: "web"
        else -> parsedCommand.mode.scopeBadge
    }
    val shouldShowList = input.text.isNotBlank() || showIdleSuggestions

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = if (bottomAnchored) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clip(shape)
                .background(glassBg, shape)
                .border(1.dp, glassBorder, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
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
                    if (input.text.isEmpty() && placeholderText.isNotEmpty()) {
                        AsciiText(
                            text = placeholderText,
                            style = AsciiType.body,
                            color = c.dim,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            errorMsg = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = AsciiType.body.copy(color = c.fg),
                        cursorBrush = SolidColor(c.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val top = results.firstOrNull()
                                if (top != null) {
                                    executeItem(top)
                                } else if (input.text.isNotBlank()) {
                                    val res = QueryRouter.resolve(input.text, engines)
                                    usageMap = onRecordUsage("eng:${res.engine.id}")
                                    if (!onDispatchUrl(input.text, res.targetUrl)) errorMsg = "! no browser found"
                                }
                            }
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

                AsciiButton(
                    label = "+",
                    primary = inlineQuickAdd != null,
                    onClick = {
                        if (inlineQuickAdd != null) {
                            inlineQuickAdd = null
                        } else {
                            val raw = input.text.trim().removePrefix("+").removePrefix("add").trim()
                            inlineQuickAdd = PaletteItem.QuickAddAction(
                                kind = QuickAddKind.BOOKMARK,
                                title = "Quick Add",
                                subtitle = "",
                                isReadyToCommit = false,
                                arg1 = raw,
                                arg3 = clipText,
                            )
                        }
                    },
                    spokenLabel = "quick add or manage",
                )

                if (input.text.isNotEmpty()) {
                    AsciiButton(
                        label = "x",
                        onClick = { input = TextFieldValue("") },
                        spokenLabel = "clear",
                    )
                } else if (showMenuBtn) {
                    AsciiButton(
                        label = "::",
                        onClick = { onOpenFullApp(input.text) },
                        spokenLabel = "open full pipe settings",
                    )
                }
            }

            if (errorMsg != null) {
                AsciiText(
                    text = errorMsg!!,
                    style = AsciiType.small,
                    color = c.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (statusMsg != null) {
                AsciiText(
                    text = statusMsg!!,
                    style = AsciiType.small,
                    color = c.good,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (inlineQuickAdd != null) {
                Spacer(Modifier.height(4.dp))
                QuickAddInlineCard(
                    initialAction = inlineQuickAdd!!,
                    existingBookmarks = activeBookmarks,
                    existingEngines = activeEngines,
                    onSaveContact = commitNewContact,
                    onSaveBookmark = commitNewBookmark,
                    onSavePipe = commitNewPipe,
                    onDeleteBookmark = { bm ->
                        val updated = activeBookmarks.filterNot { it.id == bm.id }
                        activeBookmarks = updated
                        onSaveBookmarksList(updated)
                        statusMsg = "ok: deleted bookmark [b ${bm.alias}]"
                    },
                    onDeleteEngine = { eng ->
                        if (activeEngines.size > 1) {
                            val updated = activeEngines.filterNot { it.id == eng.id }
                            activeEngines = updated
                            onSaveEnginesList(updated)
                            statusMsg = "ok: deleted pipe [s ${eng.keyword}]"
                        }
                    },
                    onCancel = { inlineQuickAdd = null },
                )
            }

            if (inlineQuickAdd == null && shouldShowList && results.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(glassBorder),
                )
                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(results, key = { idx, item -> "${idx}_${item.usageKey}" }) { index, item ->
                        val isTop = index == 0
                        SleekOverlayRow(
                            item = item,
                            isTop = isTop,
                            highlightBg = glassSurface,
                            expandedFolderPath = expandedFolderPath,
                            expandedFolderChildren = expandedFolderChildren,
                            onExecute = { executeItem(item) },
                            onToggleFolderPeek = { folderPath ->
                                expandedFolderPath = if (expandedFolderPath == folderPath) null else folderPath
                            },
                            onOpenChildFile = { child ->
                                usageMap = onRecordUsage("file:${child.absolutePath}")
                                onOpenPath(child.absolutePath)
                            },
                            onShareFile = { f -> onShareFile(f.absolutePath) },
                            onPreviewImage = { f -> previewImageFile = f },
                            onBookmarkFile = { f ->
                                val suggestedAlias = f.name.lowercase().filter { it.isLetterOrDigit() }.take(6)
                                inlineQuickAdd = PaletteItem.QuickAddAction(
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
                            onDeleteBookmark = { bm ->
                                val updated = activeBookmarks.filterNot { it.id == bm.id }
                                activeBookmarks = updated
                                onSaveBookmarksList(updated)
                                statusMsg = "ok: deleted [b ${bm.alias}]"
                            },
                            onDeleteEngine = if (activeEngines.size > 1) {
                                { eng ->
                                    val updated = activeEngines.filterNot { it.id == eng.id }
                                    activeEngines = updated
                                    onSaveEnginesList(updated)
                                    statusMsg = "ok: deleted [s ${eng.keyword}]"
                                }
                            } else null,
                            onCallContact = onCallContact,
                            onSmsContact = onSmsContact,
                            onWhatsAppContact = onWhatsAppContact,
                        )
                    }
                }
            }
        }
    }

    if (previewImageFile != null) {
        AsciiImagePreviewDialog(
            file = previewImageFile!!,
            onOpen = {
                val target = previewImageFile!!
                previewImageFile = null
                onOpenPath(target.absolutePath)
            },
            onShare = {
                val target = previewImageFile!!
                previewImageFile = null
                onShareFile(target.absolutePath)
            },
            onDismiss = { previewImageFile = null },
        )
    }
}

@Composable
private fun SleekOverlayRow(
    item: PaletteItem,
    isTop: Boolean,
    highlightBg: Color,
    expandedFolderPath: String?,
    expandedFolderChildren: List<LocalFileItem>,
    onExecute: () -> Unit,
    onToggleFolderPeek: (String) -> Unit,
    onOpenChildFile: (LocalFileItem) -> Unit,
    onShareFile: (LocalFileItem) -> Unit,
    onPreviewImage: (LocalFileItem) -> Unit,
    onBookmarkFile: (LocalFileItem) -> Unit,
    onDeleteBookmark: ((PipeBookmark) -> Unit)? = null,
    onDeleteEngine: ((SearchEngine) -> Unit)? = null,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val rowShape = RoundedCornerShape(8.dp)
    val rowBg = when {
        pressed -> c.accent
        isTop -> highlightBg
        else -> Color.Transparent
    }
    val ptr = if (isTop) ">" else " "

    when (item) {
        is PaletteItem.ContactAction -> {
            val contact = item.contact
            val pref = item.preferredAction
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr ", style = AsciiType.small, color = if (pressed) c.bg else c.good)
                AsciiContactAvatar(contact = contact)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(contact.displayName, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1)
                    AsciiText(contact.phoneNumber, style = AsciiType.small, color = if (pressed) c.bg else c.dim, maxLines = 1)
                }
                AsciiButton("call", { onCallContact(contact) }, primary = isTop && pref == ContactQuickAction.CALL)
                AsciiButton("sms", { onSmsContact(contact) }, primary = isTop && pref == ContactQuickAction.SMS)
                AsciiButton("wa", { onWhatsAppContact(contact) }, primary = isTop && pref == ContactQuickAction.WHATSAPP)
            }
        }

        is PaletteItem.FileAction -> {
            val f = item.file
            val isExpanded = f.isDirectory && expandedFolderPath == f.absolutePath
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .clip(rowShape)
                        .background(rowBg, rowShape)
                        .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsciiText(
                        text = if (f.isDirectory) "$ptr [d/] " else "$ptr [f.] ",
                        style = AsciiType.small,
                        color = if (pressed) c.bg else if (f.isDirectory) c.warn else c.accent3,
                    )
                    if (f.isImageFile) {
                        AsciiThumbnailBadge(
                            filePath = f.absolutePath,
                            onClick = { onPreviewImage(f) },
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
                    if (f.isImageFile) {
                        AsciiButton("view", { onPreviewImage(f) })
                    }
                    if (!f.isDirectory) {
                        AsciiButton("shr", { onShareFile(f) })
                    }
                    AsciiButton("cp", { clipboard.setText(AnnotatedString(f.absolutePath)) })
                    AsciiButton("+bm", { onBookmarkFile(f) })
                    if (f.isDirectory) {
                        AsciiButton(
                            label = if (isExpanded) "-" else "+",
                            onClick = { onToggleFolderPeek(f.absolutePath) },
                            spokenLabel = "peek inside ${f.name}",
                        )
                    }
                }
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 2.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (expandedFolderChildren.isEmpty()) {
                            AsciiText("└── (empty folder)", style = AsciiType.small, color = c.dim)
                        } else {
                            expandedFolderChildren.take(8).forEach { child ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 36.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onOpenChildFile(child) },
                                        )
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (child.isImageFile) {
                                        AsciiThumbnailBadge(
                                            filePath = child.absolutePath,
                                            onClick = { onPreviewImage(child) },
                                        )
                                    }
                                    AsciiText(
                                        text = "├─ ${child.name}",
                                        style = AsciiType.small,
                                        color = if (child.isDirectory) c.warn else c.fg,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!child.isDirectory) {
                                        AsciiButton("shr", { onShareFile(child) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is PaletteItem.BookmarkAction -> {
            val bm = item.bookmark
            val isExpanded = bm.kind == BookmarkKind.FOLDER && expandedFolderPath == bm.target
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .clip(rowShape)
                        .background(rowBg, rowShape)
                        .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val badge = when (bm.kind) {
                        BookmarkKind.FOLDER -> "[b:d/]"
                        BookmarkKind.FILE -> "[b:f.]"
                        BookmarkKind.LINK -> "[b:bm]"
                    }
                    AsciiText("$ptr $badge ", style = AsciiType.small, color = if (pressed) c.bg else c.warn)
                    Column(modifier = Modifier.weight(1f)) {
                        AsciiText(
                            text = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title,
                            style = if (isTop) AsciiType.title else AsciiType.body,
                            color = if (pressed) c.bg else c.fg,
                            maxLines = 1,
                        )
                        if (bm.tags.isNotBlank()) {
                            AsciiText(text = bm.tags, style = AsciiType.small, color = if (pressed) c.bg else c.accent, maxLines = 1)
                        }
                    }
                    if (bm.kind == BookmarkKind.FOLDER) {
                        AsciiButton(
                            label = if (isExpanded) "-" else "+",
                            onClick = { onToggleFolderPeek(bm.target) },
                            spokenLabel = "peek inside ${bm.title}",
                        )
                    }
                    if (onDeleteBookmark != null) {
                        AsciiButton("x", { onDeleteBookmark(bm) })
                    }
                }
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 2.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        expandedFolderChildren.take(8).forEach { child ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onOpenChildFile(child) },
                                    )
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsciiText(
                                    text = "├─ ${child.name}",
                                    style = AsciiType.small,
                                    color = if (child.isDirectory) c.warn else c.fg,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!child.isDirectory) {
                                    AsciiButton("shr", { onShareFile(child) })
                                }
                            }
                        }
                    }
                }
            }
        }

        is PaletteItem.AppAction -> {
            val app = item.app
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr [app] ", style = AsciiType.small, color = if (pressed) c.bg else c.accent)
                AsciiText(app.label, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                AsciiText("app", style = AsciiType.small, color = if (pressed) c.bg else c.dim)
            }
        }

        is PaletteItem.EngineAction -> {
            val eng = item.engine
            val desc = if (item.isHome) "${eng.name} (open home)" else "${eng.name}: \"${item.query}\""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr [s:${eng.keyword}] ", style = AsciiType.small, color = if (pressed) c.bg else c.forRole(eng.colorRole))
                AsciiText(desc, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                if (item.isHome && onDeleteEngine != null) {
                    AsciiButton("x", { onDeleteEngine(eng) })
                } else {
                    AsciiText("pipe", style = AsciiType.small, color = if (pressed) c.bg else c.dim)
                }
            }
        }

        is PaletteItem.QuickAddAction -> {
            val badgeCol = when (item.kind) {
                QuickAddKind.CONTACT -> c.good
                QuickAddKind.BOOKMARK -> c.accent2
                QuickAddKind.PIPE -> c.accent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr [${item.kind.tag}] ", style = AsciiType.small, color = if (pressed) c.bg else badgeCol)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(item.title, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1)
                    AsciiText(item.subtitle, style = AsciiType.small, color = if (pressed) c.bg else c.dim, maxLines = 1)
                }
                AsciiButton(
                    label = if (item.isReadyToCommit) "save" else "form",
                    onClick = onExecute,
                    primary = item.isReadyToCommit,
                )
            }
        }

        is PaletteItem.QuickRemoveAction -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr [-rm] ", style = AsciiType.small, color = if (pressed) c.bg else c.error)
                AsciiText(item.displayTitle, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                AsciiButton("del", onExecute, primary = isTop)
            }
        }

        is PaletteItem.HistoryAction -> {
            val h = item.entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onExecute)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr [$ ] ", style = AsciiType.small, color = if (pressed) c.bg else c.accent2)
                AsciiText("${h.rawInput} -> ${h.engineName.lowercase()}", style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}
