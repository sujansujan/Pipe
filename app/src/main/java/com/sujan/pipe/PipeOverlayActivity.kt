package com.sujan.pipe

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiTextField
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimalist, clutter-free floating ASCII command bar (`PipeOverlayActivity`).
 * - When opened with empty input, shows ONLY the single `$ ` input prompt bar.
 * - As you type, shows a compact 1-line route hint and up to 5 slim single-line results
 *   (contacts, folders/files, or bookmarks) without any extra headers, pills, or chrome.
 */
class PipeOverlayActivity : ComponentActivity() {

    private val repository by lazy { PipeRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = extractPrefill(intent)
        val engines = repository.loadEngines()
        val bookmarks = repository.loadBookmarks()
        val themeMode = repository.loadThemeMode()
        val palette = repository.loadPaletteChoice().palette

        setContent {
            PipeTheme(
                mode = themeMode,
                palette = palette,
                configureSystemBars = false,
            ) {
                MinimalOverlayPrompt(
                    initialQuery = initialText,
                    engines = engines,
                    bookmarks = bookmarks,
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
                        if (DeviceIntegrationManager.dialContact(this, contact.phoneNumber)) finish()
                    },
                    onSmsContact = { contact ->
                        if (DeviceIntegrationManager.smsContact(this, contact.phoneNumber)) finish()
                    },
                    onWhatsAppContact = { contact ->
                        val digits = QueryRouter.normalizeWhatsAppPhone(contact.phoneNumber)
                        if (DeviceIntegrationManager.whatsAppContact(this, digits)) finish()
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
private fun MinimalOverlayPrompt(
    initialQuery: String,
    engines: List<SearchEngine>,
    bookmarks: List<PipeBookmark>,
    loadContacts: suspend () -> List<ContactItem>,
    loadFiles: suspend (String?) -> List<LocalFileItem>,
    onDispatchUrl: (String, String) -> Boolean,
    onOpenPath: (String) -> Boolean,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var input by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var storageFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var scopedFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }

    val trimmed = input.text.trim()
    val cmd = remember(input.text, bookmarks) {
        QueryRouter.parsePromptCommand(input.text, bookmarks)
    }
    val resolution = remember(input.text, engines) {
        QueryRouter.resolve(input.text, engines)
    }

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        contacts = loadContacts()
        storageFiles = loadFiles(null)
    }

    LaunchedEffect(cmd.scopedFolderBookmark?.target) {
        val target = cmd.scopedFolderBookmark?.target
        if (target != null) {
            scopedFiles = loadFiles(target)
        }
    }

    // Only show results once the user actually types something!
    val matchedContacts = remember(trimmed, cmd, contacts) {
        if (trimmed.isEmpty()) emptyList()
        else when (cmd.mode) {
            PromptIntentMode.CONTACTS_ONLY -> FuzzyMatcher.filterContacts(cmd.filterQuery, contacts, limit = 5)
            PromptIntentMode.GENERAL -> if (trimmed.length >= 2) FuzzyMatcher.filterContacts(trimmed, contacts, limit = 2) else emptyList()
            else -> emptyList()
        }
    }

    val matchedBookmarks = remember(trimmed, cmd, bookmarks) {
        if (trimmed.isEmpty()) emptyList()
        else when (cmd.mode) {
            PromptIntentMode.CONTACTS_ONLY, PromptIntentMode.FILES_ONLY -> emptyList()
            PromptIntentMode.SCOPED_FOLDER -> listOfNotNull(cmd.scopedFolderBookmark)
            else -> FuzzyMatcher.filterBookmarks(trimmed, bookmarks, limit = 3)
        }
    }

    val matchedFiles = remember(trimmed, cmd, storageFiles, scopedFiles) {
        if (trimmed.isEmpty()) emptyList()
        else when (cmd.mode) {
            PromptIntentMode.SCOPED_FOLDER -> FuzzyMatcher.filterFiles(cmd.filterQuery, scopedFiles, limit = 5)
            PromptIntentMode.FILES_ONLY -> FuzzyMatcher.filterFiles(cmd.filterQuery, storageFiles, limit = 6)
            PromptIntentMode.GENERAL -> if (trimmed.length >= 2) FuzzyMatcher.filterFiles(trimmed, storageFiles, limit = 3) else emptyList()
            else -> emptyList()
        }
    }

    val executePrimary = {
        val exactBm = cmd.exactBookmark
        if (exactBm != null) {
            val ok = if (exactBm.kind == BookmarkKind.LINK) {
                onDispatchUrl(input.text, exactBm.target)
            } else {
                onOpenPath(exactBm.target)
            }
            if (!ok) errorMsg = "! could not open ${exactBm.title}"
        } else if (cmd.mode == PromptIntentMode.CONTACTS_ONLY && matchedContacts.isNotEmpty()) {
            onCallContact(matchedContacts.first())
        } else if ((cmd.mode == PromptIntentMode.FILES_ONLY || cmd.mode == PromptIntentMode.SCOPED_FOLDER) && matchedFiles.isNotEmpty()) {
            if (!onOpenPath(matchedFiles.first().absolutePath)) {
                errorMsg = "! could not open folder/file"
            }
        } else if (trimmed.isNotEmpty()) {
            if (!onDispatchUrl(input.text, resolution.targetUrl)) {
                errorMsg = "! no browser found"
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
            .padding(horizontal = 14.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .background(c.bg)
                .border(1.dp, c.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Single clean input bar
            AsciiTextField(
                label = null,
                promptPrefix = "$ ",
                value = input,
                onValueChange = { input = it; errorMsg = null },
                placeholder = "yt lofi | c kali | f folder | dl",
                errorText = errorMsg,
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executePrimary() }),
                trailingControl = {
                    AsciiButton("| ->", { executePrimary() }, primary = true)
                },
            )

            // Only render the dropdown section when the user has typed something
            if (trimmed.isNotEmpty()) {
                val hintLine = when {
                    cmd.exactBookmark != null -> "  | [${cmd.exactBookmark.alias}] -> ${cmd.exactBookmark.title} (${cmd.exactBookmark.kind.badge})"
                    cmd.mode == PromptIntentMode.CONTACTS_ONLY -> "  | contacts: ${matchedContacts.size} matched"
                    cmd.mode == PromptIntentMode.FILES_ONLY -> "  | files & folders: ${matchedFiles.size} matched"
                    cmd.mode == PromptIntentMode.SCOPED_FOLDER -> "  | ${cmd.scopedFolderBookmark?.title}: ${matchedFiles.size} matched"
                    else -> "  | ${resolution.engine.keyword} -> ${resolution.engine.name.lowercase()} (${resolution.query})"
                }
                AsciiText(
                    text = hintLine,
                    style = AsciiType.small,
                    color = c.dim,
                    maxLines = 1,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(matchedContacts, key = { "c_${it.id}_${it.phoneNumber}" }) { contact ->
                        SlimOverlayContactRow(
                            contact = contact,
                            onCall = { onCallContact(contact) },
                            onSms = { onSmsContact(contact) },
                            onWhatsApp = { onWhatsAppContact(contact) },
                        )
                    }
                    items(matchedBookmarks, key = { "bm_${it.id}" }) { bm ->
                        SlimOverlayItemRow(
                            tag = "[${bm.alias.ifEmpty { bm.kind.badge }}]",
                            title = bm.title,
                            meta = bm.kind.badge,
                            tagColor = c.warn,
                            onClick = {
                                if (bm.kind == BookmarkKind.LINK) onDispatchUrl(input.text, bm.target)
                                else onOpenPath(bm.target)
                            },
                        )
                    }
                    items(matchedFiles, key = { "f_${it.absolutePath}" }) { fileItem ->
                        SlimOverlayItemRow(
                            tag = if (fileItem.isDirectory) "[dir]" else "[file]",
                            title = fileItem.name,
                            meta = fileItem.parentName,
                            tagColor = if (fileItem.isDirectory) c.warn else c.accent3,
                            onClick = { onOpenPath(fileItem.absolutePath) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlimOverlayContactRow(
    contact: ContactItem,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    val c = LocalAscii.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = "@ ${contact.displayName}",
            style = AsciiType.body,
            color = c.good,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsciiButton("call", onCall, primary = true)
            AsciiButton("sms", onSms)
            AsciiButton("wa", onWhatsApp)
        }
    }
}

@Composable
private fun SlimOverlayItemRow(
    tag: String,
    title: String,
    meta: String,
    tagColor: Color,
    onClick: () -> Unit,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(if (pressed) c.fg else Color.Transparent)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = "$tag ",
                style = AsciiType.small,
                color = if (pressed) c.bg else tagColor,
            )
            AsciiText(
                text = title,
                style = AsciiType.body,
                color = if (pressed) c.bg else c.fg,
                maxLines = 1,
            )
        }
        AsciiText(
            text = meta,
            style = AsciiType.small,
            color = if (pressed) c.bg else c.dim,
            maxLines = 1,
        )
    }
}
