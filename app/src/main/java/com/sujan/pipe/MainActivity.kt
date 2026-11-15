package com.sujan.pipe

import android.Manifest
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.ui.JsonDialogMode
import com.sujan.pipe.ui.PipeApp
import com.sujan.pipe.ui.PipeView
import com.sujan.pipe.ui.PipeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val repository by lazy { PipeRepository(applicationContext) }
    private val viewModel: PipeViewModel by viewModels { PipeViewModel.Factory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        refreshDeviceDataAsync()

        setContent {
            val state by viewModel.uiState.collectAsState()

            BackHandler(enabled = state.activeView != PipeView.PROMPT) {
                viewModel.navigateTo(PipeView.PROMPT)
            }

            // Whenever the user types a folder alias like `dl `, load files inside that folder
            val scopedTarget = state.parsedCommand.scopedFolderBookmark?.target
            LaunchedEffect(scopedTarget, state.hasFilesPermission) {
                if (scopedTarget != null && state.hasFilesPermission) {
                    val files = withContext(Dispatchers.IO) {
                        DeviceIntegrationManager.scanStorageFiles(
                            applicationContext,
                            state.bookmarks,
                            scopedTarget,
                        )
                    }
                    viewModel.updateScopedFolderFiles(files)
                }
            }

            val contactsPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                refreshDeviceDataAsync()
            }

            val legacyFilesPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                refreshDeviceDataAsync()
            }

            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri: Uri? ->
                if (uri != null) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use {
                            it.write(viewModel.exportJsonString().toByteArray(Charsets.UTF_8))
                        }
                        viewModel.setJsonStatusMessage("ok: exported pipes to json file")
                    }.onFailure { viewModel.setJsonStatusMessage("! failed to write json file") }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    runCatching {
                        val content = contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }.orEmpty()
                        viewModel.importJsonString(content)?.let(viewModel::setJsonStatusMessage)
                    }.onFailure { viewModel.setJsonStatusMessage("! failed to read json file") }
                }
            }

            PipeApp(
                state = state,
                onNavigate = viewModel::navigateTo,
                onSearchInputChange = viewModel::updateSearchInput,
                onChipClick = viewModel::applyKeywordChip,
                onAliasPillClick = viewModel::applyPrefixAlias,
                onSubmitPrimaryAction = {
                    val top = state.paletteResults.firstOrNull()
                    when (top) {
                        is com.sujan.pipe.domain.PaletteItem.BookmarkAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            handleOpenBookmark(top.bookmark)
                        }
                        is com.sujan.pipe.domain.PaletteItem.FileAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            DeviceIntegrationManager.openLocalFileOrFolder(this, top.file.absolutePath)
                        }
                        is com.sujan.pipe.domain.PaletteItem.ContactAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            DeviceIntegrationManager.dialContact(this, top.contact.phoneNumber)
                        }
                        is com.sujan.pipe.domain.PaletteItem.HistoryAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            viewModel.submitSearch(overrideInput = top.entry.rawInput, onOpenUrl = { url ->
                                DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                            })
                        }
                        is com.sujan.pipe.domain.PaletteItem.EngineAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            val raw = if (top.query.isNotEmpty()) "${top.engine.keyword} ${top.query}" else top.engine.keyword
                            viewModel.submitSearch(overrideInput = raw, onOpenUrl = { url ->
                                DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                            })
                        }
                        null -> {
                            viewModel.submitSearch(onOpenUrl = { url ->
                                DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                            })
                        }
                    }
                },
                onOpenBookmark = { bm -> viewModel.recordItemUsage("bm:${bm.id}"); handleOpenBookmark(bm) },
                onOpenLocalFile = { fileItem ->
                    viewModel.recordItemUsage("file:${fileItem.absolutePath}")
                    if (!DeviceIntegrationManager.openLocalFileOrFolder(this, fileItem.absolutePath)) {
                        viewModel.setJsonStatusMessage("! no app found to open ${fileItem.name}")
                    }
                },
                onBookmarkLocalFile = { fileItem ->
                    viewModel.openAddBookmarkForm(
                        prefillTitle = fileItem.name,
                        prefillTarget = fileItem.absolutePath,
                        prefillKind = if (fileItem.isDirectory) BookmarkKind.FOLDER else BookmarkKind.FILE,
                    )
                    viewModel.navigateTo(PipeView.BOOKMARKS)
                },
                onCallContact = { c -> viewModel.recordItemUsage("contact:${c.phoneNumber.filter { it.isDigit() }}"); DeviceIntegrationManager.dialContact(this, c.phoneNumber) },
                onSmsContact = { c -> viewModel.recordItemUsage("contact:${c.phoneNumber.filter { it.isDigit() }}"); DeviceIntegrationManager.smsContact(this, c.phoneNumber) },
                onWhatsAppContact = { c ->
                    viewModel.recordItemUsage("contact:${c.phoneNumber.filter { it.isDigit() }}")
                    val digits = QueryRouter.normalizeWhatsAppPhone(c.phoneNumber)
                    DeviceIntegrationManager.whatsAppContact(this, digits)
                },
                onRequestContactsPermission = {
                    contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
                onRequestFilesPermission = {
                    requestStoragePermission(legacyFilesPermLauncher::launch)
                },
                onRerunHistory = { entry ->
                    viewModel.submitSearch(overrideInput = entry.rawInput, onOpenUrl = { url ->
                        DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                    })
                },
                onFillHistory = { entry -> viewModel.setSearchTextFromExternal(entry.rawInput) },
                onDeleteHistory = viewModel::deleteHistoryItem,
                onClearHistory = viewModel::clearAllHistory,
                onAddBookmarkClick = { viewModel.openAddBookmarkForm() },
                onEditBookmarkClick = viewModel::openEditBookmarkForm,
                onDeleteBookmarkClick = viewModel::deleteBookmark,
                onBookmarkFormAliasChange = viewModel::updateBookmarkFormAlias,
                onBookmarkFormTitleChange = viewModel::updateBookmarkFormTitle,
                onBookmarkFormTargetChange = viewModel::updateBookmarkFormTarget,
                onBookmarkFormKindChange = viewModel::updateBookmarkFormKind,
                onBookmarkFormSave = {
                    viewModel.saveBookmarkForm()
                    refreshDeviceDataAsync()
                },
                onBookmarkFormCancel = viewModel::closeBookmarkForm,
                onAddEngineClick = viewModel::openAddEngineForm,
                onEditEngineClick = viewModel::openEditEngineForm,
                onDeleteEngineClick = viewModel::deleteEngine,
                onMoveEngineUp = { idx -> viewModel.moveEngine(idx, -1) },
                onMoveEngineDown = { idx -> viewModel.moveEngine(idx, +1) },
                onSetDefaultEngine = viewModel::setDefaultEngine,
                onFormNameChange = viewModel::updateFormName,
                onFormKeywordChange = viewModel::updateFormKeyword,
                onFormUrlChange = viewModel::updateFormUrlTemplate,
                onFormColorChange = viewModel::updateFormColorRole,
                onFormDefaultChange = viewModel::updateFormDefault,
                onFormSave = viewModel::saveEngineForm,
                onFormCancel = viewModel::closeEngineForm,
                onThemeModeChange = viewModel::setThemeMode,
                onPaletteChoiceChange = viewModel::setPaletteChoice,
                onHistoryEnabledChange = viewModel::setHistoryEnabled,
                onExternalTextModeChange = viewModel::setExternalTextMode,
                onResetDefaults = viewModel::resetEnginesToDefault,
                onOpenJsonExportDialog = { viewModel.openJsonDialog(JsonDialogMode.EXPORT) },
                onOpenJsonImportDialog = { viewModel.openJsonDialog(JsonDialogMode.IMPORT) },
                onExportJsonToFile = { exportLauncher.launch("pipe-engines.json") },
                onImportJsonFromFile = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onCloseJsonDialog = viewModel::closeJsonDialog,
                onExportJsonString = viewModel::exportJsonString,
                onImportJsonString = viewModel::importJsonString,
                onSetJsonStatus = viewModel::setJsonStatusMessage,
                onLaunchOverlayPreview = ::launchOverlayActivity,
                onPinOverlayShortcut = ::requestPinOverlayShortcut,
                onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                onUndo = viewModel::performUndo,
                onDismissUndo = viewModel::dismissUndo,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceDataAsync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun refreshDeviceDataAsync() {
        val hasContacts = DeviceIntegrationManager.hasContactsPermission(this)
        val hasFiles = DeviceIntegrationManager.hasAllFilesPermission(this)
        val currentBookmarks = viewModel.uiState.value.bookmarks
        lifecycleScope.launch(Dispatchers.IO) {
            val contacts = if (hasContacts) DeviceIntegrationManager.loadDeviceContacts(applicationContext) else emptyList()
            val files = if (hasFiles) DeviceIntegrationManager.scanStorageFiles(applicationContext, currentBookmarks) else emptyList()
            viewModel.syncDeviceState(
                hasContactsPerm = hasContacts,
                hasFilesPerm = hasFiles,
                contacts = contacts,
                files = files,
            )
        }
    }

    private fun handleOpenBookmark(bm: PipeBookmark) {
        if (bm.kind == BookmarkKind.LINK) {
            DeviceIntegrationManager.openUrlExcludingSelf(this, bm.target)
        } else {
            if (!DeviceIntegrationManager.hasAllFilesPermission(this)) {
                requestStoragePermission {}
                return
            }
            DeviceIntegrationManager.openLocalFileOrFolder(this, bm.target)
        }
    }

    private fun requestStoragePermission(onLegacyRequest: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                )
            }.onFailure {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            onLegacyRequest(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val prefill = intent.getStringExtra(EXTRA_PREFILL_QUERY) ?: intent.getStringExtra(SearchManager.QUERY)
        if (!prefill.isNullOrEmpty()) {
            viewModel.setSearchTextFromExternal(prefill)
        }
    }

    private fun launchOverlayActivity() {
        startActivity(
            Intent(this, PipeOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    private fun requestPinOverlayShortcut() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        if (!sm.isRequestPinShortcutSupported) {
            viewModel.setJsonStatusMessage("! launcher does not support pinning; use UbikiTouch -> Shortcuts -> pipe overlay")
            return
        }
        val overlayIntent = Intent(this, PipeOverlayActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val info = ShortcutInfo.Builder(this, "pipe_overlay_pinned")
            .setShortLabel(getString(R.string.overlay_shortcut_label))
            .setLongLabel(getString(R.string.overlay_shortcut_long))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(overlayIntent)
            .build()
        val callback = PendingIntent.getBroadcast(
            this, 0, sm.createShortcutResultIntent(info),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        sm.requestPinShortcut(info, callback.intentSender)
        viewModel.setJsonStatusMessage("ok: requested launcher shortcut pin for pipe overlay")
    }

    private fun openDefaultAppsSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    companion object {
        const val EXTRA_PREFILL_QUERY = "com.sujan.pipe.EXTRA_PREFILL_QUERY"
    }
}
