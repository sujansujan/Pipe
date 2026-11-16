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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.sujan.pipe.ui.AsciiImagePreviewDialog
import com.sujan.pipe.ui.AsciiRaycastRow
import com.sujan.pipe.ui.AsciiResultActionSheet
import com.sujan.pipe.ui.AsciiScopeChip
import com.sujan.pipe.ui.QuickAddInlineCard
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.AsciiUndoBanner
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme
import com.sujan.pipe.ui.ascii.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface OverlayUndoPayload {
    data class DeletedBookmark(val bookmark: PipeBookmark, val index: Int) : OverlayUndoPayload
    data class DeletedEngine(val engine: SearchEngine, val index: Int) : OverlayUndoPayload
    data class DeletedHistory(val entry: SearchHistoryEntry, val index: Int) : OverlayUndoPayload
}

private data class OverlayUndoBannerState(
    val message: String,
    val payload: OverlayUndoPayload,
)

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
                    onSaveHistoryList = { updated ->
                        repository.saveHistory(updated)
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

/**
 * Floating Overlay (`~/pipe/overlay`) with full v2.0 Parity:
 * - Bottom-anchored `reverseLayout = true` results stack when `bottomAnchored = true` so `#1` sits right above the scope chips and keyboard
 * - Horizontally scrollable `44.dp` `AsciiScopeChip` bar (`[c:]`, `[f:]`, `[b:]`, `[s:]`, `[app:]`, `[dl:]`, `[yt:]`)
 * - Shared `AsciiRaycastRow` (`56.dp` `AsciiKindBadge`, `3.dp` `c.accent` left border on `#1`, fzf character highlighting, `[+]`/`[-]` folder tree peek, and `[⋯]` action sheet)
 * - 6-second `AsciiUndoBanner` on bookmark, pipe, and history deletions.
 */
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
    onSaveHistoryList: (List<SearchHistoryEntry>) -> Unit,
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
    var activeHistory by remember { mutableStateOf(history) }
    var usageMap by remember { mutableStateOf(initialUsage) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var inlineQuickAdd by remember { mutableStateOf<PaletteItem.QuickAddAction?>(null) }
    var actionSheetItem by remember { mutableStateOf<PaletteItem?>(null) }
    var undoBanner by remember { mutableStateOf<OverlayUndoBannerState?>(null) }
    var undoTick by remember { mutableIntStateOf(0) }

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
                history = activeHistory,
                usageMap = initialUsage,
                installedApps = initialCachedApps,
                clipboardText = clipText,
            )
        )
    }

    LaunchedEffect(undoTick, undoBanner) {
        if (undoBanner != null) {
            delay(6_000)
            undoBanner = null
        }
    }

    val postOverlayUndo: (String, OverlayUndoPayload) -> Unit = { msg, payload ->
        undoBanner = OverlayUndoBannerState(msg, payload)
        undoTick += 1
    }

    val performOverlayUndo: () -> Unit = {
        val current = undoBanner
        if (current != null) {
            when (val p = current.payload) {
                is OverlayUndoPayload.DeletedBookmark -> {
                    val restored = activeBookmarks.toMutableList().apply {
                        add(p.index.coerceIn(0, size), p.bookmark)
                    }
                    activeBookmarks = restored
                    onSaveBookmarksList(restored)
                }
                is OverlayUndoPayload.DeletedEngine -> {
                    val base = if (p.engine.isDefault) {
                        activeEngines.map { it.copy(isDefault = false) }.toMutableList()
                    } else {
                        activeEngines.toMutableList()
                    }
                    base.add(p.index.coerceIn(0, base.size), p.engine)
                    activeEngines = base
                    onSaveEnginesList(base)
                }
                is OverlayUndoPayload.DeletedHistory -> {
                    val restored = activeHistory.toMutableList().apply {
                        add(p.index.coerceIn(0, size), p.entry)
                    }
                    activeHistory = restored
                    onSaveHistoryList(restored)
                }
            }
            undoBanner = null
        }
    }

    val deleteBookmarkWithUndo: (PipeBookmark) -> Unit = { bm ->
        val idx = activeBookmarks.indexOfFirst { it.id == bm.id }
        if (idx >= 0) {
            val updated = activeBookmarks.toMutableList().apply { removeAt(idx) }
            activeBookmarks = updated
            onSaveBookmarksList(updated)
            postOverlayUndo("deleted bookmark ${bm.title}", OverlayUndoPayload.DeletedBookmark(bm, idx))
        }
    }

    val deleteEngineWithUndo: (SearchEngine) -> Unit = { eng ->
        if (activeEngines.size > 1) {
            val idx = activeEngines.indexOfFirst { it.id == eng.id }
            if (idx >= 0) {
                val updated = activeEngines.toMutableList().apply { removeAt(idx) }
                if (eng.isDefault && updated.isNotEmpty()) {
                    updated[0] = updated[0].copy(isDefault = true)
                }
                activeEngines = updated
                onSaveEnginesList(updated)
                postOverlayUndo("deleted |${eng.keyword}", OverlayUndoPayload.DeletedEngine(eng, idx))
            }
        }
    }

    val deleteHistoryWithUndo: (SearchHistoryEntry) -> Unit = { entry ->
        val idx = activeHistory.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            val updated = activeHistory.toMutableList().apply { removeAt(idx) }
            activeHistory = updated
            onSaveHistoryList(updated)
            postOverlayUndo("deleted \"${entry.rawInput.take(18)}\"", OverlayUndoPayload.DeletedHistory(entry, idx))
        }
    }

    LaunchedEffect(input.text, activeEngines, activeBookmarks, contacts, installedApps, storageFiles, scopedFiles, activeHistory, usageMap, clipText) {
        val computed = withContext(Dispatchers.Default) {
            QueryRouter.buildPaletteResults(
                rawInput = input.text,
                engines = activeEngines,
                bookmarks = activeBookmarks,
                contacts = contacts,
                storageFiles = storageFiles,
                scopedFolderFiles = scopedFiles,
                history = activeHistory,
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

    val commitNewBookmark: (String, String, String, BookmarkKind, String, String?) -> Unit = { alias, title, target, kind, tags, editingId ->
        val cleanAlias = alias.trim().lowercase().filter { !it.isWhitespace() }
        val cleanTitle = title.trim().ifEmpty { cleanAlias.ifEmpty { "Bookmark" } }
        val cleanTarget = target.trim()
        val existingIdx = when {
            editingId != null -> activeBookmarks.indexOfFirst { it.id == editingId }
            cleanAlias.isNotEmpty() -> activeBookmarks.indexOfFirst { it.alias.equals(cleanAlias, ignoreCase = true) }
            else -> -1
        }
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
        statusMsg = "ok: saved bookmark [b ${cleanAlias.ifEmpty { cleanTitle }}]"
    }

    val commitNewPipe: (String, String, String, String?) -> Unit = { keyword, name, rawUrl, editingId ->
        val cleanKey = keyword.trim().lowercase().filter { !it.isWhitespace() }
        val cleanName = name.trim().ifEmpty { cleanKey.uppercase() }
        val cleanUrl = QueryRouter.normalizePipeUrlTemplate(rawUrl)
        val existingIdx = when {
            editingId != null -> activeEngines.indexOfFirst { it.id == editingId }
            else -> activeEngines.indexOfFirst { it.keyword.equals(cleanKey, ignoreCase = true) }
        }
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
        statusMsg = "ok: saved pipe [s $cleanKey] $cleanName"
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
                        QuickAddKind.BOOKMARK -> commitNewBookmark(item.arg1, item.arg2, item.arg3, item.bookmarkKind, "", item.editingId)
                        QuickAddKind.PIPE -> commitNewPipe(item.arg1, item.arg2, item.arg3, item.editingId)
                    }
                } else {
                    inlineQuickAdd = item
                }
            }
            is PaletteItem.QuickRemoveAction -> {
                when {
                    item.isHistory -> {
                        activeHistory.firstOrNull { it.id.toString() == item.targetId }?.let(deleteHistoryWithUndo)
                        input = TextFieldValue("")
                    }
                    item.isBookmark -> {
                        activeBookmarks.firstOrNull { it.id == item.targetId }?.let(deleteBookmarkWithUndo)
                        input = TextFieldValue("")
                    }
                    activeEngines.size > 1 -> {
                        activeEngines.firstOrNull { it.id == item.targetId }?.let(deleteEngineWithUndo)
                        input = TextFieldValue("")
                    }
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
    val glassBorder = c.border.copy(alpha = 0.78f)
    val scopeBadge = when (parsedCommand.mode) {
        PromptIntentMode.SCOPED_FOLDER -> "b:${parsedCommand.scopedFolderBookmark?.alias ?: "folder"}"
        PromptIntentMode.ENGINE_ONLY -> "s:${parsedCommand.matchedEngine?.keyword ?: "pipe"}"
        else -> parsedCommand.mode.scopeBadge
    }
    val shouldShowList = input.text.isNotBlank() || showIdleSuggestions

    val scopeChips = listOf(
        "c" to (parsedCommand.mode == PromptIntentMode.CONTACTS_ONLY),
        "f" to (parsedCommand.mode == PromptIntentMode.FILES_ONLY || parsedCommand.mode == PromptIntentMode.DIRS_ONLY),
        "b" to (parsedCommand.mode == PromptIntentMode.BOOKMARKS_ONLY),
        "s" to (parsedCommand.mode == PromptIntentMode.PIPES_LIST || (parsedCommand.mode == PromptIntentMode.ENGINE_ONLY && !parsedCommand.matchedEngine?.keyword.equals("yt", ignoreCase = true))),
        "app" to (parsedCommand.mode == PromptIntentMode.APPS_ONLY),
        "dl" to (parsedCommand.mode == PromptIntentMode.SCOPED_FOLDER && parsedCommand.scopedFolderBookmark?.alias.equals("dl", ignoreCase = true)),
        "yt" to (parsedCommand.mode == PromptIntentMode.ENGINE_ONLY && parsedCommand.matchedEngine?.keyword.equals("yt", ignoreCase = true)),
    )

    val renderScopeChipRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(glassSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scopeChips.forEach { (alias, isActive) ->
                AsciiScopeChip(
                    label = "$alias:",
                    active = isActive,
                    onClick = {
                        val next = if (isActive) "" else "$alias "
                        input = TextFieldValue(next, TextRange(next.length))
                        errorMsg = null
                        runCatching { focusRequester.requestFocus() }
                    },
                )
            }
        }
    }

    val renderInputBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(glassBg)
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val effectivePrefix = promptPrefix.ifEmpty { "> " }
            AsciiText(
                text = effectivePrefix,
                style = AsciiType.title,
                color = c.accent,
            )
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
                                val res = QueryRouter.resolve(input.text, activeEngines)
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
            if (input.text.isNotEmpty()) {
                AsciiButton(
                    label = "x",
                    onClick = { input = TextFieldValue("") },
                    spokenLabel = "clear input",
                )
            }
            AsciiButton(
                label = "RET ->",
                onClick = {
                    val top = results.firstOrNull()
                    if (top != null) {
                        executeItem(top)
                    } else if (input.text.isNotBlank()) {
                        val res = QueryRouter.resolve(input.text, activeEngines)
                        usageMap = onRecordUsage("eng:${res.engine.id}")
                        if (!onDispatchUrl(input.text, res.targetUrl)) errorMsg = "! no browser found"
                    }
                },
                primary = true,
                spokenLabel = "run top match",
            )
        }
    }

    val renderStatusAndUndo: @Composable () -> Unit = {
        if (errorMsg != null || statusMsg != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
            val msg = errorMsg ?: statusMsg.orEmpty()
            AsciiText(
                text = "  $msg",
                style = AsciiType.small,
                color = if (errorMsg != null || msg.startsWith("!")) c.error else c.good,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(glassSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (undoBanner != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
            AsciiUndoBanner(
                message = undoBanner!!.message,
                onUndo = performOverlayUndo,
                onDismiss = { undoBanner = null },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

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
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = if (bottomAnchored) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .clip(shape)
                .background(glassBg, shape)
                .border(1.dp, glassBorder, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // 1. Top ASCII Header Bar: `+- ~/pipe/overlay ─────────────── [+] [::] [x]`
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(glassSurface)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "+- ~/pipe/overlay",
                    style = AsciiType.title,
                    color = c.accent2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    if (showMenuBtn) {
                        AsciiButton(
                            label = "::",
                            onClick = { onOpenFullApp(input.text) },
                            spokenLabel = "open full pipe app",
                        )
                    }
                    AsciiButton(
                        label = "x",
                        onClick = onDismiss,
                        spokenLabel = "close overlay",
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))

            if (!bottomAnchored) {
                renderInputBar()
                Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
                renderScopeChipRow()
                Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
            }

            if (inlineQuickAdd != null) {
                QuickAddInlineCard(
                    initialAction = inlineQuickAdd!!,
                    existingBookmarks = activeBookmarks,
                    existingEngines = activeEngines,
                    onSaveContact = commitNewContact,
                    onSaveBookmark = commitNewBookmark,
                    onSavePipe = commitNewPipe,
                    onDeleteBookmark = deleteBookmarkWithUndo,
                    onDeleteEngine = deleteEngineWithUndo,
                    onCancel = { inlineQuickAdd = null },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
            }

            if (inlineQuickAdd == null && shouldShowList) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    reverseLayout = bottomAnchored,
                    verticalArrangement = if (bottomAnchored) Arrangement.Bottom else Arrangement.Top,
                ) {
                    if (results.isEmpty()) {
                        item {
                            AsciiText(
                                text = "  | no matching items in [$scopeBadge].",
                                style = AsciiType.small,
                                color = c.dim,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        itemsIndexed(results, key = { idx, item -> "${idx}_${item.usageKey}" }) { index, item ->
                            val isTop = index == 0
                            val accentCol = c.accent
                            val folderPath = when (item) {
                                is PaletteItem.FileAction -> if (item.file.isDirectory) item.file.absolutePath else null
                                is PaletteItem.BookmarkAction -> if (item.bookmark.kind == BookmarkKind.FOLDER) item.bookmark.target else null
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
                                AsciiRaycastRow(
                                    item = item,
                                    isTop = isTop,
                                    highlightQuery = parsedCommand.filterQuery,
                                    isFolderExpanded = folderPath != null && expandedFolderPath == folderPath,
                                    expandedChildren = if (folderPath != null && expandedFolderPath == folderPath) expandedFolderChildren else emptyList(),
                                    onExecute = { executeItem(item) },
                                    onOpenActionSheet = { actionSheetItem = item },
                                    onToggleFolderPeek = if (folderPath != null) {
                                        { path -> expandedFolderPath = if (expandedFolderPath == path) null else path }
                                    } else null,
                                    onOpenChildFile = { child ->
                                        usageMap = onRecordUsage("file:${child.absolutePath}")
                                        onOpenPath(child.absolutePath)
                                    },
                                    onShareChildFile = { child -> onShareFile(child.absolutePath) },
                                    onPreviewChildImage = { child -> previewImageFile = child },
                                )
                            }
                        }
                    }
                }
            }

            renderStatusAndUndo()

            if (bottomAnchored) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
                renderScopeChipRow()
                Box(Modifier.fillMaxWidth().height(1.dp).background(glassBorder))
                renderInputBar()
            }
        }
    }

    if (actionSheetItem != null) {
        val currentSheetItem = actionSheetItem!!
        AsciiResultActionSheet(
            item = currentSheetItem,
            canDeleteEngine = activeEngines.size > 1,
            onDismiss = { actionSheetItem = null },
            onPrimaryExecute = {
                actionSheetItem = null
                executeItem(currentSheetItem)
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
            onScopeFolder = { scopeCmd ->
                actionSheetItem = null
                val next = "$scopeCmd "
                input = TextFieldValue(next, TextRange(next.length))
                runCatching { focusRequester.requestFocus() }
            },
            onBookmarkFile = { f ->
                actionSheetItem = null
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
            onShareFile = { f ->
                actionSheetItem = null
                onShareFile(f.absolutePath)
            },
            onPreviewImage = { f ->
                actionSheetItem = null
                previewImageFile = f
            },
            onEditBookmark = { bm ->
                actionSheetItem = null
                inlineQuickAdd = PaletteItem.QuickAddAction(
                    kind = QuickAddKind.BOOKMARK,
                    title = "Edit Bookmark [b ${bm.alias}]",
                    subtitle = bm.target,
                    isReadyToCommit = false,
                    arg1 = bm.alias,
                    arg2 = bm.title,
                    arg3 = bm.target,
                    bookmarkKind = bm.kind,
                    editingId = bm.id,
                )
            },
            onDeleteBookmark = { bm ->
                actionSheetItem = null
                deleteBookmarkWithUndo(bm)
            },
            onEditEngine = { eng ->
                actionSheetItem = null
                inlineQuickAdd = PaletteItem.QuickAddAction(
                    kind = QuickAddKind.PIPE,
                    title = "Edit Pipe [s ${eng.keyword}]",
                    subtitle = eng.urlTemplate,
                    isReadyToCommit = false,
                    arg1 = eng.keyword,
                    arg2 = eng.name,
                    arg3 = eng.urlTemplate,
                    editingId = eng.id,
                )
            },
            onDeleteEngine = { eng ->
                actionSheetItem = null
                deleteEngineWithUndo(eng)
            },
            onDeleteHistory = { h ->
                actionSheetItem = null
                deleteHistoryWithUndo(h)
            },
            onCopyText = { textToCopy ->
                clipboard.setText(AnnotatedString(textToCopy))
                statusMsg = "ok: copied to clipboard"
                actionSheetItem = null
            },
        )
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
