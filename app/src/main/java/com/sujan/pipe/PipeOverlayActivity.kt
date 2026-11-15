package com.sujan.pipe

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.ui.AsciiRaycastPaletteWindow
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.PipeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raycast-inspired ASCII floating command palette overlay (`~/pipe/overlay`).
 * - Scopes results strictly to the typed alias (`c`, `f`, `d`, `bm`, `dl`, `yt`),
 *   or shows all categories combined when no alias is typed.
 * - Caps results at max 10 items ranked by most-used frequency (`#N`).
 */
class PipeOverlayActivity : ComponentActivity() {

    private val repository by lazy { PipeRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = extractPrefill(intent)
        val engines = repository.loadEngines()
        val bookmarks = repository.loadBookmarks()
        val history = repository.loadHistory()
        val initialUsage = repository.loadUsageCounts()
        val themeMode = repository.loadThemeMode()
        val palette = repository.loadPaletteChoice().palette

        setContent {
            PipeTheme(
                mode = themeMode,
                palette = palette,
                configureSystemBars = false,
            ) {
                RaycastOverlayContainer(
                    initialQuery = initialText,
                    engines = engines,
                    bookmarks = bookmarks,
                    history = history,
                    initialUsage = initialUsage,
                    hasContactsPerm = DeviceIntegrationManager.hasContactsPermission(this),
                    hasFilesPerm = DeviceIntegrationManager.hasAllFilesPermission(this),
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
private fun RaycastOverlayContainer(
    initialQuery: String,
    engines: List<SearchEngine>,
    bookmarks: List<PipeBookmark>,
    history: List<SearchHistoryEntry>,
    initialUsage: Map<String, Int>,
    hasContactsPerm: Boolean,
    hasFilesPerm: Boolean,
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
            .padding(horizontal = 12.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            AsciiRaycastPaletteWindow(
                titlePath = "~/pipe/overlay",
                searchInput = input,
                onSearchInputChange = { input = it; errorMsg = null },
                parsedCommand = parsedCommand,
                results = results,
                hasContactsPermission = hasContactsPerm,
                hasFilesPermission = hasFilesPerm,
                launchError = errorMsg,
                focusRequester = focusRequester,
                headerRightActions = {
                    AsciiButton("full", { onOpenFullApp(input.text) })
                    AsciiButton("esc", onDismiss)
                },
                onAliasPillClick = { alias ->
                    val next = "$alias "
                    input = TextFieldValue(next, TextRange(next.length))
                },
                onSubmitPrimary = {
                    val top = results.firstOrNull()
                    if (top != null) {
                        executeItem(top)
                    } else if (input.text.isNotBlank()) {
                        val res = QueryRouter.resolve(input.text, engines)
                        usageMap = onRecordUsage("eng:${res.engine.id}")
                        if (!onDispatchUrl(input.text, res.targetUrl)) errorMsg = "! no browser found"
                    }
                },
                onExecuteItem = executeItem,
                onCallContact = onCallContact,
                onSmsContact = onSmsContact,
                onWhatsAppContact = onWhatsAppContact,
                onScopeIntoFolder = { alias ->
                    val next = "$alias "
                    input = TextFieldValue(next, TextRange(next.length))
                },
            )
        }
    }
}
