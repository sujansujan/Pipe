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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
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
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme
import com.sujan.pipe.ui.ascii.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sleek, translucent Raycast-inspired ASCII Overlay (`PipeOverlayActivity`).
 * - Respects user-configured Card Opacity (`15%–100%`), Background Dim (`0%–85%`),
 *   Soft Rounded Corners (`0dp–24dp`), Top vs. Bottom-Above-Keyboard position,
 *   Force-Dark Glass mode, and Inline Folder Peek (`[+]`/`[-]`).
 */
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
                    loadContacts = {
                        withContext(Dispatchers.IO) {
                            DeviceIntegrationManager.loadDeviceContacts(applicationContext)
                        }
                    },
                    loadFiles = { scopedPath ->
                        withContext(Dispatchers.IO) {
                            DeviceIntegrationManager.scanStorageFiles(applicationContext, bookmarks, scopedPath)
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
                    onCallContact = { contact ->
                        repository.incrementUsage("contact:${contact.phoneNumber.filter { it.isDigit() }}")
                        if (DeviceIntegrationManager.dialContact(this, contact.phoneNumber)) finish()
                    },
                    onSmsContact = { contact ->
                        repository.incrementUsage("contact:${contact.phoneNumber.filter { it.isDigit() }}")
                        if (DeviceIntegrationManager.smsContact(this, contact.phoneNumber)) finish()
                    },
                    onWhatsAppContact = { contact ->
                        repository.incrementUsage("contact:${contact.phoneNumber.filter { it.isDigit() }}")
                        val digits = QueryRouter.normalizeWhatsAppPhone(contact.phoneNumber)
                        if (DeviceIntegrationManager.whatsAppContact(this, digits)) finish()
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
    loadContacts: suspend () -> List<ContactItem>,
    loadFiles: suspend (String?) -> List<LocalFileItem>,
    onRecordUsage: (String) -> Map<String, Int>,
    onDispatchUrl: (String, String) -> Boolean,
    onOpenPath: (String) -> Boolean,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onOpenFullApp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var input by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    var usageMap by remember { mutableStateOf(initialUsage) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var storageFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var scopedFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }
    var expandedFolderChildren by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }

    val parsedCommand = remember(input.text, bookmarks, engines) {
        QueryRouter.parsePromptCommand(input.text, bookmarks, engines)
    }

    val results = remember(input.text, engines, bookmarks, contacts, storageFiles, scopedFiles, history, usageMap) {
        QueryRouter.buildPaletteResults(
            rawInput = input.text,
            engines = engines,
            bookmarks = bookmarks,
            contacts = contacts,
            storageFiles = storageFiles,
            scopedFolderFiles = scopedFiles,
            history = history,
            usageMap = usageMap,
        )
    }

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        contacts = loadContacts()
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
            is PaletteItem.ContactAction -> onCallContact(item.contact)
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
            // 1. Zero-Chrome Raycast Search Bar (`> query_                 [scope] [::]`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
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
                    if (input.text.isEmpty()) {
                        AsciiText(
                            text = "pipe (c, f, d, dl, yt)...",
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

                AsciiText(
                    text = "[$scopeBadge]",
                    style = AsciiType.small,
                    color = if (parsedCommand.mode == PromptIntentMode.ALL) c.dim else c.accent,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                if (input.text.isNotEmpty()) {
                    AsciiButton(
                        label = "x",
                        onClick = { input = TextFieldValue("") },
                        spokenLabel = "clear",
                    )
                } else {
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

            // 2. Results List (only shown when typing, or when idle suggestions are enabled)
            if (shouldShowList && results.isNotEmpty()) {
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
                            onCallContact = onCallContact,
                            onSmsContact = onSmsContact,
                            onWhatsAppContact = onWhatsAppContact,
                        )
                    }
                }
            }
        }
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
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
) {
    val c = LocalAscii.current
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = { onCallContact(contact) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("$ptr [@] ", style = AsciiType.small, color = if (pressed) c.bg else c.good)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(contact.displayName, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1)
                    AsciiText(contact.phoneNumber, style = AsciiType.small, color = if (pressed) c.bg else c.dim, maxLines = 1)
                }
                AsciiButton("call", { onCallContact(contact) }, primary = isTop)
                AsciiButton("sms", { onSmsContact(contact) })
                AsciiButton("wa", { onWhatsAppContact(contact) })
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
                                    AsciiText(
                                        text = "├─ ${child.name}",
                                        style = AsciiType.small,
                                        color = if (child.isDirectory) c.warn else c.fg,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AsciiText(
                                        text = child.formattedSize,
                                        style = AsciiType.small,
                                        color = c.dim,
                                    )
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
                        BookmarkKind.FOLDER -> "[d/]"
                        BookmarkKind.FILE -> "[f.]"
                        BookmarkKind.LINK -> "[bm]"
                    }
                    AsciiText("$ptr $badge ", style = AsciiType.small, color = if (pressed) c.bg else c.warn)
                    AsciiText(
                        text = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title,
                        style = if (isTop) AsciiType.title else AsciiType.body,
                        color = if (pressed) c.bg else c.fg,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (bm.kind == BookmarkKind.FOLDER) {
                        AsciiButton(
                            label = if (isExpanded) "-" else "+",
                            onClick = { onToggleFolderPeek(bm.target) },
                            spokenLabel = "peek inside ${bm.title}",
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
                                AsciiText(text = child.formattedSize, style = AsciiType.small, color = c.dim)
                            }
                        }
                    }
                }
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
                AsciiText("$ptr [|${eng.keyword}] ", style = AsciiType.small, color = if (pressed) c.bg else c.forRole(eng.colorRole))
                AsciiText(desc, style = if (isTop) AsciiType.title else AsciiType.body, color = if (pressed) c.bg else c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                AsciiText("web", style = AsciiType.small, color = if (pressed) c.bg else c.dim)
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
