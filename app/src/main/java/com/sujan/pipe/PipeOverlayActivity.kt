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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.sujan.pipe.domain.FuzzyMatcher
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.ui.BookmarkResultRow
import com.sujan.pipe.ui.ContactResultRow
import com.sujan.pipe.ui.FileResultRow
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiPipePill
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiTextField
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                OverlayPromptWindow(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayPromptWindow(
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
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var storageFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var scopedFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }

    val cmd = remember(input.text, bookmarks) {
        QueryRouter.parsePromptCommand(input.text, bookmarks)
    }
    val resolution = remember(input.text, engines) {
        QueryRouter.resolve(input.text, engines)
    }
    val suggestions = remember(input.text, engines) {
        QueryRouter.suggestEngines(input.text, engines)
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

    val matchedContacts = remember(input.text, cmd, contacts) {
        when (cmd.mode) {
            PromptIntentMode.CONTACTS_ONLY -> FuzzyMatcher.filterContacts(cmd.filterQuery, contacts, limit = 5)
            PromptIntentMode.GENERAL -> if (input.text.trim().length >= 2) {
                FuzzyMatcher.filterContacts(input.text.trim(), contacts, limit = 3)
            } else emptyList()
            else -> emptyList()
        }
    }

    val matchedBookmarks = remember(input.text, cmd, bookmarks) {
        when (cmd.mode) {
            PromptIntentMode.CONTACTS_ONLY, PromptIntentMode.FILES_ONLY -> emptyList()
            PromptIntentMode.SCOPED_FOLDER -> listOfNotNull(cmd.scopedFolderBookmark)
            else -> if (input.text.isBlank()) bookmarks.take(4) else FuzzyMatcher.filterBookmarks(input.text.trim(), bookmarks, limit = 4)
        }
    }

    val matchedFiles = remember(input.text, cmd, storageFiles, scopedFiles) {
        when (cmd.mode) {
            PromptIntentMode.SCOPED_FOLDER -> FuzzyMatcher.filterFiles(cmd.filterQuery, scopedFiles, limit = 6)
            PromptIntentMode.FILES_ONLY -> FuzzyMatcher.filterFiles(cmd.filterQuery, storageFiles, limit = 6)
            PromptIntentMode.GENERAL -> if (input.text.trim().length >= 2) {
                FuzzyMatcher.filterFiles(input.text.trim(), storageFiles, limit = 4)
            } else emptyList()
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
            if (!ok) errorMsg = "! could not open ${exactBm.target}"
        } else if (cmd.mode == PromptIntentMode.CONTACTS_ONLY && matchedContacts.isNotEmpty()) {
            onCallContact(matchedContacts.first())
        } else if ((cmd.mode == PromptIntentMode.FILES_ONLY || cmd.mode == PromptIntentMode.SCOPED_FOLDER) && matchedFiles.isNotEmpty()) {
            if (!onOpenPath(matchedFiles.first().absolutePath)) {
                errorMsg = "! could not open file"
            }
        } else {
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
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .background(c.bg)
                .border(2.dp, c.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText("~/pipe/overlay", style = AsciiType.title, color = c.accent2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsciiButton("full", { onOpenFullApp(input.text) })
                    AsciiButton("x", onDismiss)
                }
            }

            AsciiTextField(
                label = null,
                promptPrefix = "$ ",
                value = input,
                onValueChange = { input = it; errorMsg = null },
                placeholder = "yt lofi | c kali | dl | f pdf",
                errorText = errorMsg,
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executePrimary() }),
                trailingControl = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (input.text.isNotEmpty()) {
                            AsciiButton("c", { input = TextFieldValue("") })
                        }
                        AsciiButton("| ->", { executePrimary() }, primary = true)
                    }
                },
            )

            AsciiText(
                text = "+--> ${resolution.pipelinePreview}",
                style = AsciiType.small,
                color = if (resolution.matchedByKeyword) c.accent else c.dim,
                maxLines = 1,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AsciiPipePill("c", "contacts", cmd.mode == PromptIntentMode.CONTACTS_ONLY, accentColor = c.good, onClick = {
                    input = TextFieldValue("c ", TextRange(2))
                })
                AsciiPipePill("dl", "downloads", cmd.scopedFolderBookmark?.alias == "dl", accentColor = c.warn, onClick = {
                    input = TextFieldValue("dl ", TextRange(3))
                })
                AsciiPipePill("f", "files", cmd.mode == PromptIntentMode.FILES_ONLY, accentColor = c.accent3, onClick = {
                    input = TextFieldValue("f ", TextRange(2))
                })
                suggestions.take(5).forEach { eng ->
                    val active = cmd.mode == PromptIntentMode.GENERAL && resolution.matchedByKeyword && resolution.engine.id == eng.id
                    AsciiPipePill(
                        keyword = eng.keyword,
                        name = eng.name.lowercase(),
                        selected = active,
                        isDefault = eng.isDefault,
                        accentColor = c.forRole(eng.colorRole),
                        onClick = {
                            val next = "${eng.keyword} "
                            input = TextFieldValue(next, TextRange(next.length))
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(matchedContacts, key = { "c_${it.id}_${it.phoneNumber}" }) { contact ->
                    ContactResultRow(
                        contact = contact,
                        onCall = { onCallContact(contact) },
                        onSms = { onSmsContact(contact) },
                        onWhatsApp = { onWhatsAppContact(contact) },
                        onCopy = { clipboard.setText(AnnotatedString(contact.phoneNumber)) },
                    )
                }
                items(matchedBookmarks, key = { "bm_${it.id}" }) { bm ->
                    BookmarkResultRow(
                        bookmark = bm,
                        onOpen = {
                            if (bm.kind == BookmarkKind.LINK) onDispatchUrl(input.text, bm.target)
                            else onOpenPath(bm.target)
                        },
                        onScopeInto = if (bm.kind == BookmarkKind.FOLDER) {
                            { input = TextFieldValue("${bm.alias} ", TextRange(bm.alias.length + 1)) }
                        } else null,
                    )
                }
                items(matchedFiles, key = { "f_${it.absolutePath}" }) { fileItem ->
                    FileResultRow(
                        item = fileItem,
                        onOpen = { onOpenPath(fileItem.absolutePath) },
                        onBookmark = { onOpenFullApp(input.text) },
                    )
                }
            }
        }
    }
}
