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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.lifecycleScope
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.OfflineDictionaryManager
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.ClipActionKind
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.SystemSettingShortcut
import com.sujan.pipe.ui.JsonDialogMode
import com.sujan.pipe.ui.PipeApp
import com.sujan.pipe.ui.PipeView
import com.sujan.pipe.ui.PipeViewModel
import com.sujan.pipe.ui.SettingsSection
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
            val clipboard = LocalClipboardManager.current

            BackHandler(enabled = state.activeView != PipeView.PROMPT) {
                viewModel.navigateTo(PipeView.PROMPT)
            }

            // Keep clipboard text synced when user enters `clip` mode
            val cmdMode = state.parsedCommand.mode
            val filterQ = state.parsedCommand.filterQuery
            LaunchedEffect(cmdMode) {
                if (cmdMode == PromptIntentMode.CLIPBOARD_ONLY || cmdMode == PromptIntentMode.ADD_COMMAND) {
                    val clip = clipboard.getText()?.text?.trim().orEmpty()
                    viewModel.updateClipboardPreviewText(clip)
                }
            }

            // Feature #1: Strictly On-Demand Dictionary Seek (0 MB idle RAM; only runs in DICTIONARY_ONLY mode)
            LaunchedEffect(cmdMode, filterQ, state.isDictionaryInstalled) {
                if (cmdMode == PromptIntentMode.DICTIONARY_ONLY && state.isDictionaryInstalled) {
                    val matches = withContext(Dispatchers.IO) {
                        OfflineDictionaryManager.searchOnDemand(applicationContext, filterQ, limit = 10)
                    }
                    viewModel.updateDictionaryMatches(matches)
                } else if (cmdMode != PromptIntentMode.DICTIONARY_ONLY && state.dictionaryMatches.isNotEmpty()) {
                    viewModel.updateDictionaryMatches(emptyList())
                }
            }

            // True Offline YouTube Watch History search (runs in YOUTUBE_HISTORY_ONLY or `yt`/`ytt`/`ytw`/`ytm` mode)
            val matchedEngineKw = state.parsedCommand.matchedEngine?.keyword?.lowercase().orEmpty()
            val isYtEngineMode = cmdMode == PromptIntentMode.ENGINE_ONLY &&
                matchedEngineKw in setOf("yt", "ytt", "ytw", "ytm")
            LaunchedEffect(cmdMode, filterQ, isYtEngineMode, state.youtubeHistoryCount) {
                if (cmdMode == PromptIntentMode.YOUTUBE_HISTORY_ONLY) {
                    val matches = withContext(Dispatchers.IO) {
                        OfflineYouTubeHistoryManager.searchOnDemand(applicationContext, filterQ, limit = 10)
                    }
                    viewModel.updateYouTubeHistoryMatches(matches)
                } else if (isYtEngineMode) {
                    val matches = withContext(Dispatchers.IO) {
                        OfflineYouTubeHistoryManager.searchOnDemand(applicationContext, filterQ, limit = 3)
                    }
                    viewModel.updateYouTubeHistoryMatches(matches)
                } else if (state.youtubeHistoryMatches.isNotEmpty()) {
                    viewModel.updateYouTubeHistoryMatches(emptyList())
                }
            }

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
                contract = ActivityResultContracts.RequestMultiplePermissions(),
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
                        viewModel.setJsonStatusMessage("ok: exported full v2 backup to json file")
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

            val dictImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    importDictionaryUriAsync(uri)
                }
            }

            val ytHistoryImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    importYouTubeHistoryUriAsync(uri)
                }
            }

            val ytHistoryExportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri: Uri? ->
                if (uri != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            val entries = OfflineYouTubeHistoryManager.getCachedOrLoadHistory(applicationContext)
                            val json = OfflineYouTubeHistoryManager.exportHistoryToJson(entries)
                            contentResolver.openOutputStream(uri)?.use {
                                it.write(json.toByteArray(Charsets.UTF_8))
                            }
                            viewModel.setJsonStatusMessage("ok: exported ${entries.size} offline YouTube watches to json")
                        }.onFailure {
                            viewModel.setJsonStatusMessage("! failed to export YouTube watch history")
                        }
                    }
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
                        is com.sujan.pipe.domain.PaletteItem.DirectUrlAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            DeviceIntegrationManager.openUrlExcludingSelf(this, top.targetUrl)
                        }
                        is com.sujan.pipe.domain.PaletteItem.SystemSettingAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            handleSystemOrPipeSettingShortcut(top.shortcut)
                        }
                        is com.sujan.pipe.domain.PaletteItem.AppShortcutAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            DeviceIntegrationManager.launchAppShortcut(this, top.shortcut)
                        }
                        is com.sujan.pipe.domain.PaletteItem.DirectNumberAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            handleDirectNumberAction(top, top.primaryMode)
                        }
                        is com.sujan.pipe.domain.PaletteItem.TimerAlarmAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            handleTimerAlarmAction(top, skipUi = true)
                        }
                        is com.sujan.pipe.domain.PaletteItem.DictionaryAction -> {
                            if (top.isSetupPrompt) {
                                handleSetupDictionaryClick { dictImportLauncher.launch(arrayOf("*/*")) }
                            } else if (top.entry != null) {
                                clipboard.setText(AnnotatedString("${top.entry.word} (${top.entry.partOfSpeech}): ${top.entry.definition}"))
                                viewModel.setJsonStatusMessage("ok: copied definition for ${top.entry.word}")
                            }
                        }
                        is com.sujan.pipe.domain.PaletteItem.ClipboardAction -> {
                            handleClipboardAction(top, clipboard::setText)
                        }
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
                            when (top.preferredAction) {
                                com.sujan.pipe.model.ContactQuickAction.WHATSAPP -> {
                                    val digits = QueryRouter.normalizeWhatsAppPhone(top.contact.phoneNumber)
                                    DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp")
                                }
                                com.sujan.pipe.model.ContactQuickAction.WHATSAPP_BUSINESS -> {
                                    val digits = QueryRouter.normalizeWhatsAppPhone(top.contact.phoneNumber)
                                    DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp.w4b")
                                }
                                com.sujan.pipe.model.ContactQuickAction.SMS ->
                                    DeviceIntegrationManager.smsContact(this, top.contact.phoneNumber)
                                com.sujan.pipe.model.ContactQuickAction.CALL ->
                                    DeviceIntegrationManager.dialContact(this, top.contact.phoneNumber)
                            }
                        }
                        is com.sujan.pipe.domain.PaletteItem.AppAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            DeviceIntegrationManager.launchInstalledApp(this, top.app.packageName)
                        }
                        is com.sujan.pipe.domain.PaletteItem.QuickAddAction -> {
                            if (top.isReadyToCommit) {
                                when (top.kind) {
                                    com.sujan.pipe.domain.QuickAddKind.CONTACT -> {
                                        if (DeviceIntegrationManager.insertContactDirect(this, top.arg1, top.arg2)) {
                                            viewModel.setSearchTextFromExternal("")
                                            viewModel.setJsonStatusMessage("ok: saved contact ${top.arg1}")
                                            refreshDeviceDataAsync()
                                        }
                                    }
                                    com.sujan.pipe.domain.QuickAddKind.BOOKMARK -> {
                                        viewModel.quickSaveBookmark(top.arg1, top.arg2, top.arg3, top.bookmarkKind)
                                    }
                                    com.sujan.pipe.domain.QuickAddKind.PIPE -> {
                                        viewModel.quickSavePipe(top.arg1, top.arg2, top.arg3)
                                    }
                                }
                            }
                        }
                        is com.sujan.pipe.domain.PaletteItem.QuickRemoveAction -> {
                            viewModel.quickRemoveById(top.isBookmark, top.targetId, top.isHistory)
                        }
                        is com.sujan.pipe.domain.PaletteItem.HistoryAction -> {
                            viewModel.recordItemUsage(top.usageKey)
                            viewModel.submitSearch(overrideInput = top.entry.rawInput, onOpenUrl = { url ->
                                DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                            })
                        }
                        is com.sujan.pipe.domain.PaletteItem.YouTubeWatchAction -> {
                            if (top.isSetupPrompt || top.entry == null) {
                                viewModel.selectSettingsSection(SettingsSection.INDEX)
                                viewModel.navigateTo(PipeView.SETTINGS)
                            } else {
                                viewModel.recordItemUsage(top.usageKey)
                                DeviceIntegrationManager.openUrlExcludingSelf(this, top.entry.watchUrl)
                            }
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
                onOpenDirectUrl = { url ->
                    viewModel.recordItemUsage("url:$url")
                    DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                },
                onOpenSystemSetting = { shortcut ->
                    viewModel.recordItemUsage("sys:${shortcut.id}")
                    handleSystemOrPipeSettingShortcut(shortcut)
                },
                onLaunchAppShortcut = { shortcutItem ->
                    viewModel.recordItemUsage(shortcutItem.usageKey)
                    DeviceIntegrationManager.launchAppShortcut(this, shortcutItem)
                },
                onSaveUniversalAlias = viewModel::setUniversalAlias,
                onRemoveUniversalAlias = viewModel::removeUniversalAlias,
                onExecuteDirectNumber = { action, mode ->
                    viewModel.recordItemUsage(action.usageKey)
                    handleDirectNumberAction(action, mode)
                },
                onExecuteTimerAlarm = { action, skipUi ->
                    viewModel.recordItemUsage(action.usageKey)
                    handleTimerAlarmAction(action, skipUi)
                },
                onSelectDictionaryItem = { dictAction ->
                    if (dictAction.isSetupPrompt) {
                        handleSetupDictionaryClick { dictImportLauncher.launch(arrayOf("*/*")) }
                    } else if (dictAction.entry != null) {
                        viewModel.setActiveDictionaryEntry(dictAction.entry)
                    }
                },
                onDismissDictionaryDialog = { viewModel.setActiveDictionaryEntry(null) },
                onImportDictionaryFileClick = { dictImportLauncher.launch(arrayOf("*/*")) },
                onAutoDetectDownloadsDictionary = ::autoDetectDownloadsDictionary,
                onRemoveDictionaryClick = {
                    OfflineDictionaryManager.removeDictionary(applicationContext)
                    repository.saveDictionaryMetadata(0, "not linked")
                    viewModel.syncDictionaryState(false, 0, "not linked")
                    viewModel.setJsonStatusMessage("ok: unlinked offline dictionary file")
                },
                onOpenYouTubeWatch = { entry, resume ->
                    viewModel.recordItemUsage("ytw:${entry.id}")
                    DeviceIntegrationManager.openUrlExcludingSelf(this, if (resume) entry.resumeUrl else entry.watchUrl)
                },
                onSetupYouTubeHistoryClick = {
                    viewModel.selectSettingsSection(SettingsSection.INDEX)
                    viewModel.navigateTo(PipeView.SETTINGS)
                },
                onDeleteYouTubeWatch = { entry ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val updated = OfflineYouTubeHistoryManager.deleteEntry(applicationContext, entry.id)
                        val listenerOn = OfflineYouTubeHistoryManager.isNotificationListenerEnabled(applicationContext)
                        viewModel.syncYouTubeHistoryState(listenerOn, updated.size)
                        val q = viewModel.uiState.value.parsedCommand.filterQuery
                        viewModel.updateYouTubeHistoryMatches(
                            OfflineYouTubeHistoryManager.searchEntries(updated, q, limit = 10)
                        )
                        viewModel.setJsonStatusMessage("ok: deleted \"${entry.title.take(28)}\" from offline YT history")
                    }
                },
                onYouTubeHistoryEnabledChange = viewModel::setYouTubeHistoryEnabled,
                onYouTubeSaveThumbnailsChange = viewModel::setYouTubeSaveThumbnails,
                onRequestYouTubeListenerAccess = ::openNotificationListenerSettings,
                onImportYouTubeHistoryClick = { ytHistoryImportLauncher.launch(arrayOf("*/*")) },
                onScanDownloadsYouTubeHistory = ::autoDetectDownloadsYouTubeHistory,
                onExportYouTubeHistoryClick = { ytHistoryExportLauncher.launch("pipe-yt-history.json") },
                onClearYouTubeHistoryClick = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        OfflineYouTubeHistoryManager.clearAll(applicationContext)
                        val listenerOn = OfflineYouTubeHistoryManager.isNotificationListenerEnabled(applicationContext)
                        viewModel.syncYouTubeHistoryState(listenerOn, 0)
                        viewModel.updateYouTubeHistoryMatches(emptyList())
                        viewModel.setJsonStatusMessage("ok: cleared offline YouTube watch history")
                    }
                },
                onExecuteClipboardAction = { action ->
                    handleClipboardAction(action, clipboard::setText)
                },
                onOpenBookmark = { bm -> viewModel.recordItemUsage("bm:${bm.id}"); handleOpenBookmark(bm) },
                onOpenLocalFile = { fileItem ->
                    viewModel.recordItemUsage("file:${fileItem.absolutePath}")
                    if (!DeviceIntegrationManager.openLocalFileOrFolder(this, fileItem.absolutePath)) {
                        viewModel.setJsonStatusMessage("! no app found to open ${fileItem.name}")
                    }
                },
                onShareLocalFile = { fileItem ->
                    viewModel.recordItemUsage("file:${fileItem.absolutePath}")
                    DeviceIntegrationManager.shareLocalFile(this, fileItem.absolutePath)
                },
                onPreviewImageFile = viewModel::setPreviewImageFile,
                onLaunchInstalledApp = { app ->
                    viewModel.recordItemUsage("app:${app.packageName}")
                    DeviceIntegrationManager.launchInstalledApp(this, app.packageName)
                },
                onAppInfoInstalledApp = { app ->
                    DeviceIntegrationManager.openAppInfoSettings(this, app.packageName)
                },
                onUninstallInstalledApp = { app ->
                    DeviceIntegrationManager.uninstallInstalledApp(this, app.packageName)
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
                    DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp")
                },
                onWhatsAppBusinessContact = { c ->
                    viewModel.recordItemUsage("contact:${c.phoneNumber.filter { it.isDigit() }}")
                    val digits = QueryRouter.normalizeWhatsAppPhone(c.phoneNumber)
                    DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp.w4b")
                },
                onQuickSaveContact = { name, phone ->
                    if (DeviceIntegrationManager.insertContactDirect(this, name, phone)) {
                        viewModel.setSearchTextFromExternal("")
                        viewModel.setJsonStatusMessage("ok: saved contact $name ($phone)")
                        refreshDeviceDataAsync()
                    }
                },
                onQuickSaveBookmark = { alias, title, target, kind, tags, editingId ->
                    viewModel.quickSaveBookmark(alias, title, target, kind, tags, editingId)
                    refreshDeviceDataAsync()
                },
                onQuickSavePipe = { kw, name, url, editingId ->
                    viewModel.quickSavePipe(kw, name, url, editingId)
                },
                onQuickRemoveItem = { isBm, id ->
                    viewModel.quickRemoveById(isBm, id)
                },
                onRequestContactsPermission = {
                    contactsPermLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS,
                        )
                    )
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
                onBookmarkFormTagsChange = viewModel::updateBookmarkFormTags,
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
                onExportJsonToFile = { exportLauncher.launch("pipe-backup-v2.json") },
                onImportJsonFromFile = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onCloseJsonDialog = viewModel::closeJsonDialog,
                onExportJsonString = viewModel::exportJsonString,
                onImportJsonString = viewModel::importJsonString,
                onSetJsonStatus = viewModel::setJsonStatusMessage,
                onOverlayOpacityChange = viewModel::setOverlayOpacity,
                onOverlayDimChange = viewModel::setOverlayDimAmount,
                onOverlayCornerChange = viewModel::setOverlayCornerRadiusDp,
                onOverlayBottomChange = viewModel::setOverlayBottomAnchored,
                onOverlayForceDarkChange = viewModel::setOverlayForceDark,
                onOverlayIdleChange = viewModel::setOverlayShowIdleSuggestions,
                onSelectSettingsSection = viewModel::selectSettingsSection,
                onHourlyAutoIndexChange = { enabled ->
                    viewModel.setHourlyAutoIndexEnabled(enabled)
                    DeviceIntegrationManager.syncHourlyIndexSchedule(this, enabled)
                },
                onRebuildIndexNow = { refreshDeviceDataAsync(forceRebuild = true) },
                onPreferredBrowserChange = viewModel::setPreferredBrowserPackage,
                onWhatsAppFlavorChange = viewModel::setWhatsAppFlavor,
                onAddExcludedFolder = { folder ->
                    viewModel.addExcludedFolder(folder)
                    refreshDeviceDataAsync(forceRebuild = true)
                },
                onRemoveExcludedFolder = { folder ->
                    viewModel.removeExcludedFolder(folder)
                    refreshDeviceDataAsync(forceRebuild = true)
                },
                onSearchBoxPrefixChange = viewModel::setSearchBoxPrefix,
                onSearchBoxPlaceholderChange = viewModel::setSearchBoxPlaceholder,
                onShowScopeBadgeChange = viewModel::setShowSearchBoxScopeBadge,
                onShowMenuBtnChange = viewModel::setShowSearchBoxMenuBtn,
                onAutoLaunchExactAliasChange = viewModel::setAutoLaunchExactAlias,
                onPrefixConfigChange = viewModel::setPrefixConfig,
                onResetPrefixConfig = viewModel::resetPrefixConfig,
                onLaunchOverlayPreview = ::launchOverlayActivity,
                onPinOverlayShortcut = ::requestPinOverlayShortcut,
                onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                onUndo = viewModel::performUndo,
                onDismissUndo = viewModel::dismissUndo,
            )
        }
    }

    private fun handleSystemOrPipeSettingShortcut(shortcut: SystemSettingShortcut) {
        val internalSec = shortcut.internalPipeSection
        if (internalSec != null) {
            if (internalSec == "APP_INFO") {
                DeviceIntegrationManager.openAppInfoSettings(this, packageName)
            } else {
                val sec = runCatching { SettingsSection.valueOf(internalSec) }.getOrNull()
                if (sec != null) {
                    viewModel.selectSettingsSection(sec)
                    viewModel.navigateTo(PipeView.SETTINGS)
                }
            }
        } else {
            DeviceIntegrationManager.openSystemSettingShortcut(this, shortcut)
        }
    }

    private fun handleClipboardAction(
        action: com.sujan.pipe.domain.PaletteItem.ClipboardAction,
        copyToClipboard: (AnnotatedString) -> Unit,
    ) {
        when (action.kind) {
            ClipActionKind.OPEN_URL -> DeviceIntegrationManager.openUrlExcludingSelf(this, action.payloadText)
            ClipActionKind.COPY_CLEAN_URL -> {
                copyToClipboard(AnnotatedString(action.payloadText))
                viewModel.setJsonStatusMessage("ok: copied clean URL without tracking params")
            }
            ClipActionKind.SAVE_BOOKMARK -> {
                val inferred = QueryRouter.inferTitleFromTarget("bm", action.payloadText)
                viewModel.openAddBookmarkForm(
                    prefillTitle = inferred,
                    prefillTarget = action.payloadText,
                    prefillKind = BookmarkKind.LINK,
                )
                viewModel.navigateTo(PipeView.BOOKMARKS)
            }
            ClipActionKind.CALL_NUMBER -> DeviceIntegrationManager.dialContact(this, action.payloadText)
            ClipActionKind.WA_NUMBER -> {
                val digits = QueryRouter.normalizeWhatsAppPhone(action.payloadText)
                DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp")
            }
            ClipActionKind.WAB_NUMBER -> {
                val digits = QueryRouter.normalizeWhatsAppPhone(action.payloadText)
                DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp.w4b")
            }
            ClipActionKind.SAVE_CONTACT -> {
                val next = "+ c Name ${action.payloadText}"
                viewModel.setSearchTextFromExternal(next)
            }
            ClipActionKind.COPY_TEXT -> {
                if (action.payloadText.isNotEmpty()) {
                    copyToClipboard(AnnotatedString(action.payloadText))
                    viewModel.setJsonStatusMessage("ok: copied \"${action.payloadText.take(28)}\"")
                }
            }
        }
    }

    private fun handleDirectNumberAction(
        action: com.sujan.pipe.domain.PaletteItem.DirectNumberAction,
        mode: com.sujan.pipe.domain.DirectNumberPrimaryMode,
    ) {
        when (mode) {
            com.sujan.pipe.domain.DirectNumberPrimaryMode.WHATSAPP_PERSONAL ->
                DeviceIntegrationManager.whatsAppContact(this, action.normalizedWhatsAppDigits, "com.whatsapp", action.messageText)
            com.sujan.pipe.domain.DirectNumberPrimaryMode.WHATSAPP_BUSINESS ->
                DeviceIntegrationManager.whatsAppContact(this, action.normalizedWhatsAppDigits, "com.whatsapp.w4b", action.messageText)
            com.sujan.pipe.domain.DirectNumberPrimaryMode.CALL ->
                DeviceIntegrationManager.dialContact(this, action.rawPhone)
            com.sujan.pipe.domain.DirectNumberPrimaryMode.SMS ->
                DeviceIntegrationManager.smsContact(this, action.rawPhone)
        }
    }

    private fun handleTimerAlarmAction(
        action: com.sujan.pipe.domain.PaletteItem.TimerAlarmAction,
        skipUi: Boolean,
    ) {
        if (action.isAlarm) {
            val ok = DeviceIntegrationManager.setSystemAlarm(this, action.hour24, action.minute, action.label, skipUi)
            if (ok) {
                viewModel.setSearchTextFromExternal("")
                viewModel.setJsonStatusMessage("ok: set alarm for ${QueryRouter.formatAlarmTime(action.hour24, action.minute)}")
            } else {
                viewModel.setJsonStatusMessage("! no clock app found to set alarm")
            }
        } else {
            val ok = DeviceIntegrationManager.startCountdownTimer(this, action.totalSeconds, action.label, skipUi)
            if (ok) {
                viewModel.setSearchTextFromExternal("")
                viewModel.setJsonStatusMessage("ok: started ${QueryRouter.formatDurationSummary(action.totalSeconds)} timer")
            } else {
                viewModel.setJsonStatusMessage("! no clock app found to start timer")
            }
        }
    }

    private fun handleSetupDictionaryClick(launchPicker: () -> Unit) {
        val candidate = OfflineDictionaryManager.findCandidateInDownloads()
        if (candidate != null) {
            autoDetectDownloadsDictionary()
        } else {
            launchPicker()
        }
    }

    private fun importDictionaryUriAsync(uri: Uri) {
        viewModel.syncDictionaryState(
            installed = OfflineDictionaryManager.isDictionaryInstalled(applicationContext),
            isIndexing = true,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val nameHint = uri.lastPathSegment?.substringAfterLast('/') ?: "dictionary_file"
                val count = OfflineDictionaryManager.importDictionaryFromUri(applicationContext, uri, nameHint)
                repository.saveDictionaryMetadata(count, nameHint)
                viewModel.syncDictionaryState(installed = true, wordCount = count, sourceName = nameHint, isIndexing = false)
                viewModel.setJsonStatusMessage("ok: indexed $count words from $nameHint (on-demand seek ready)")
            }.onFailure { err ->
                viewModel.syncDictionaryState(
                    installed = OfflineDictionaryManager.isDictionaryInstalled(applicationContext),
                    isIndexing = false,
                )
                viewModel.setJsonStatusMessage(err.message ?: "! failed to import dictionary file")
            }
        }
    }

    private fun autoDetectDownloadsDictionary() {
        val candidate = OfflineDictionaryManager.findCandidateInDownloads()
        if (candidate == null) {
            viewModel.setJsonStatusMessage("! no dictionary file found in Download/ (use [pick dictionary file])")
            return
        }
        viewModel.syncDictionaryState(
            installed = OfflineDictionaryManager.isDictionaryInstalled(applicationContext),
            isIndexing = true,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val count = OfflineDictionaryManager.importDictionaryFromFile(applicationContext, candidate)
                repository.saveDictionaryMetadata(count, candidate.name)
                viewModel.syncDictionaryState(installed = true, wordCount = count, sourceName = candidate.name, isIndexing = false)
                viewModel.setJsonStatusMessage("ok: linked ${candidate.name} ($count words)")
            }.onFailure { err ->
                viewModel.syncDictionaryState(
                    installed = OfflineDictionaryManager.isDictionaryInstalled(applicationContext),
                    isIndexing = false,
                )
                viewModel.setJsonStatusMessage(err.message ?: "! could not index ${candidate.name}")
            }
        }
    }

    private fun openNotificationListenerSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun importYouTubeHistoryUriAsync(uri: Uri) {
        val listenerOn = OfflineYouTubeHistoryManager.isNotificationListenerEnabled(applicationContext)
        viewModel.syncYouTubeHistoryState(listenerOn, viewModel.uiState.value.youtubeHistoryCount, isImporting = true)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val nameHint = uri.lastPathSegment?.substringAfterLast('/') ?: "watch-history.json"
                val (imported, total) = OfflineYouTubeHistoryManager.importFromUri(applicationContext, uri, nameHint)
                viewModel.syncYouTubeHistoryState(listenerOn, total, isImporting = false)
                viewModel.setJsonStatusMessage("ok: imported $imported watches ($total total offline)")
            }.onFailure { err ->
                viewModel.syncYouTubeHistoryState(listenerOn, viewModel.uiState.value.youtubeHistoryCount, isImporting = false)
                viewModel.setJsonStatusMessage(err.message ?: "! failed to import YouTube watch history")
            }
        }
    }

    private fun autoDetectDownloadsYouTubeHistory() {
        val candidate = OfflineYouTubeHistoryManager.findCandidateInDownloads()
        if (candidate == null) {
            viewModel.setJsonStatusMessage("! no watch-history.json / .html found in Download/ (use [import file])")
            return
        }
        val listenerOn = OfflineYouTubeHistoryManager.isNotificationListenerEnabled(applicationContext)
        viewModel.syncYouTubeHistoryState(listenerOn, viewModel.uiState.value.youtubeHistoryCount, isImporting = true)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val (imported, total) = OfflineYouTubeHistoryManager.importFromFile(applicationContext, candidate)
                viewModel.syncYouTubeHistoryState(listenerOn, total, isImporting = false)
                viewModel.setJsonStatusMessage("ok: imported $imported watches from ${candidate.name} ($total total)")
            }.onFailure { err ->
                viewModel.syncYouTubeHistoryState(listenerOn, viewModel.uiState.value.youtubeHistoryCount, isImporting = false)
                viewModel.setJsonStatusMessage(err.message ?: "! could not parse ${candidate.name}")
            }
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

    private fun refreshDeviceDataAsync(forceRebuild: Boolean = false) {
        val hasContacts = DeviceIntegrationManager.hasContactsPermission(this)
        val hasFiles = DeviceIntegrationManager.hasAllFilesPermission(this)
        val currentBookmarks = viewModel.uiState.value.bookmarks
        val dictInstalled = OfflineDictionaryManager.isDictionaryInstalled(applicationContext)
        viewModel.syncDictionaryState(installed = dictInstalled)
        val ytListenerOn = OfflineYouTubeHistoryManager.isNotificationListenerEnabled(applicationContext)
        DeviceIntegrationManager.syncHourlyIndexSchedule(this, repository.loadHourlyAutoIndexEnabled())

        lifecycleScope.launch(Dispatchers.IO) {
            val ytCount = OfflineYouTubeHistoryManager.getCachedOrLoadHistory(applicationContext).size
            viewModel.syncYouTubeHistoryState(listenerEnabled = ytListenerOn, totalCount = ytCount)
            val cachedFiles = if (hasFiles) DeviceIntegrationManager.getCachedStorageFiles(applicationContext) else emptyList()
            val contacts = if (hasContacts) DeviceIntegrationManager.getCachedOrLoadContacts(applicationContext) else emptyList()
            val apps = DeviceIntegrationManager.loadInstalledApps(applicationContext)
            val appShortcuts = DeviceIntegrationManager.loadAppShortcuts(applicationContext)
            val browsers = DeviceIntegrationManager.listInstalledBrowsers(applicationContext)
            val needsRebuild = hasFiles && (forceRebuild || cachedFiles.isEmpty() || DeviceIntegrationManager.isIndexStale(applicationContext))

            viewModel.syncDeviceState(
                hasContactsPerm = hasContacts,
                hasFilesPerm = hasFiles,
                contacts = contacts,
                files = cachedFiles,
                apps = apps,
                appShortcuts = appShortcuts,
                browsers = browsers,
                isIndexing = needsRebuild,
            )

            // Auto-link dictionary from Download/ if present and not yet linked
            if (!dictInstalled && hasFiles) {
                val dlDict = OfflineDictionaryManager.findCandidateInDownloads()
                if (dlDict != null) {
                    runCatching {
                        val count = OfflineDictionaryManager.importDictionaryFromFile(applicationContext, dlDict)
                        repository.saveDictionaryMetadata(count, dlDict.name)
                        viewModel.syncDictionaryState(installed = true, wordCount = count, sourceName = dlDict.name, isIndexing = false)
                    }
                }
            }

            if (needsRebuild) {
                val freshContacts = if (hasContacts) DeviceIntegrationManager.loadDeviceContacts(applicationContext) else contacts
                val freshFiles = DeviceIntegrationManager.rebuildAndSaveStorageIndex(applicationContext, currentBookmarks)
                viewModel.syncDeviceState(
                    hasContactsPerm = hasContacts,
                    hasFilesPerm = hasFiles,
                    contacts = freshContacts,
                    files = freshFiles,
                    apps = apps,
                    appShortcuts = appShortcuts,
                    browsers = browsers,
                    isIndexing = false,
                )
            }
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
        val openSection = intent.getStringExtra(EXTRA_OPEN_SETTINGS_SECTION)
        if (!openSection.isNullOrEmpty()) {
            if (openSection == "APP_INFO") {
                DeviceIntegrationManager.openAppInfoSettings(this, packageName)
            } else {
                val sec = runCatching { SettingsSection.valueOf(openSection) }.getOrNull()
                if (sec != null) {
                    viewModel.selectSettingsSection(sec)
                    viewModel.navigateTo(PipeView.SETTINGS)
                }
            }
            return
        }
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
        const val EXTRA_OPEN_SETTINGS_SECTION = "com.sujan.pipe.EXTRA_OPEN_SETTINGS_SECTION"
    }
}
