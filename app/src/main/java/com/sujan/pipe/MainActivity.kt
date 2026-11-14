package com.sujan.pipe

import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.ui.JsonDialogMode
import com.sujan.pipe.ui.PipeApp
import com.sujan.pipe.ui.PipeView
import com.sujan.pipe.ui.PipeViewModel

class MainActivity : ComponentActivity() {

    private val repository by lazy { PipeRepository(applicationContext) }
    private val viewModel: PipeViewModel by viewModels { PipeViewModel.Factory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsState()

            BackHandler(enabled = state.activeView != PipeView.PROMPT) {
                viewModel.navigateTo(PipeView.PROMPT)
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
                onSubmitSearch = { viewModel.submitSearch(onOpenUrl = ::launchExternalUrl) },
                onRerunHistory = { entry ->
                    viewModel.submitSearch(overrideInput = entry.rawInput, onOpenUrl = ::launchExternalUrl)
                },
                onFillHistory = { entry -> viewModel.setSearchTextFromExternal(entry.rawInput) },
                onDeleteHistory = viewModel::deleteHistoryItem,
                onClearHistory = viewModel::clearAllHistory,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
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

    private fun launchExternalUrl(url: String): Boolean = try {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    companion object {
        const val EXTRA_PREFILL_QUERY = "com.sujan.pipe.EXTRA_PREFILL_QUERY"
    }
}
