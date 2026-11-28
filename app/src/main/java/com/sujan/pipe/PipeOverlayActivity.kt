package com.sujan.pipe

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.FloccusBookmarkManager
import com.sujan.pipe.data.OfflineDictionaryManager
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.service.YouTubeWatchListenerService
import com.sujan.pipe.domain.ClipActionKind
import com.sujan.pipe.domain.DirectNumberPrimaryMode
import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.domain.QuickAddKind
import com.sujan.pipe.model.AppShortcutItem
import com.sujan.pipe.model.AppShortcutKind
import com.sujan.pipe.model.BookmarkKind
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.ContactQuickAction
import com.sujan.pipe.model.DictionaryEntry
import com.sujan.pipe.model.EngineColorRole
import com.sujan.pipe.model.FloccusBookmarkItem
import com.sujan.pipe.model.InstalledAppItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import com.sujan.pipe.model.PrefixConfig
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.model.SearchHistoryEntry
import com.sujan.pipe.model.SystemSettingShortcut
import com.sujan.pipe.model.UniversalAlias
import com.sujan.pipe.model.WhatsAppFlavor
import com.sujan.pipe.model.YouTubeWatchEntry
import com.sujan.pipe.ui.AsciiContactAvatar
import com.sujan.pipe.ui.AsciiFaviconBadge
import com.sujan.pipe.ui.AsciiFileTypeBadge
import com.sujan.pipe.ui.AsciiDictionaryDetailDialog
import com.sujan.pipe.ui.AsciiImagePreviewDialog
import com.sujan.pipe.ui.AsciiThumbnailBadge
import com.sujan.pipe.ui.AsciiYouTubeThumbBadge
import com.sujan.pipe.ui.QuickAddInlineCard
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiTextField
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme
import com.sujan.pipe.ui.ascii.ThemeMode
import com.sujan.pipe.ui.buildFzfHighlightedText
import com.sujan.pipe.ui.extractAliasTargetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipeOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CALC_MODE = "com.sujan.pipe.EXTRA_OPEN_CALC_MODE"
        const val EXTRA_OPEN_QUICK_ADD = "com.sujan.pipe.EXTRA_OPEN_QUICK_ADD"
    }

    private val repository by lazy { PipeRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        val initialOpenCalc = intent?.getBooleanExtra(EXTRA_OPEN_CALC_MODE, false) == true
        val initialOpenQuickAdd = intent?.getBooleanExtra(EXTRA_OPEN_QUICK_ADD, false) == true

        val dimAmount = repository.loadOverlayDimAmount()
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (initialOpenCalc) {
                w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }
            w.attributes = w.attributes.apply {
                this.dimAmount = dimAmount
            }
        }

        val initialText = extractPrefill(intent)
        val engines = repository.loadEngines()
        val bookmarks = repository.loadBookmarks()
        val prefixes = repository.loadPrefixConfig()
        val history = repository.loadHistory()
        val initialUsage = repository.loadUsageCounts()
        val initialUniversalAliases = repository.loadUniversalAliases()

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
        val whatsAppFlavor = repository.loadWhatsAppFlavor()
        val autoLaunchExactAlias = repository.loadAutoLaunchExactAlias()
        val floccusUnifiedSearch = repository.loadFloccusUnifiedSearch()

        setContent {
            PipeTheme(
                mode = resolvedMode,
                palette = palette,
                configureSystemBars = false,
            ) {
                SleekRaycastOverlay(
                    initialQuery = initialText,
                    initialOpenCalcMode = initialOpenCalc,
                    initialOpenQuickAdd = initialOpenQuickAdd,
                    engines = engines,
                    bookmarks = bookmarks,
                    prefixes = prefixes,
                    history = history,
                    initialUsage = initialUsage,
                    initialUniversalAliases = initialUniversalAliases,
                    cardOpacity = cardOpacity,
                    cornerRadiusDp = cornerRadiusDp,
                    bottomAnchored = bottomAnchored,
                    showIdleSuggestions = showIdleSuggestions,
                    promptPrefix = promptPrefix,
                    placeholderText = placeholderText,
                    showScopeBadge = showScopeBadge,
                    showMenuBtn = showMenuBtn,
                    whatsAppFlavor = whatsAppFlavor,
                    autoLaunchExactAlias = autoLaunchExactAlias,
                    initialCachedContacts = DeviceIntegrationManager.peekMemoryContacts(),
                    initialCachedFiles = DeviceIntegrationManager.peekMemoryStorageFiles(),
                    initialCachedApps = DeviceIntegrationManager.peekMemoryInstalledApps(),
                    initialCachedShortcuts = DeviceIntegrationManager.peekMemoryAppShortcuts(),
                    initialCachedFloccus = FloccusBookmarkManager.peekMemoryBookmarks(),
                    floccusUnifiedSearch = floccusUnifiedSearch,
                    initialDictInstalled = OfflineDictionaryManager.isDictionaryInstalled(applicationContext),
                    initialYtListenerEnabled = OfflineYouTubeHistoryManager.isNotificationListenerEnabled(applicationContext),
                    initialYtHistoryCount = OfflineYouTubeHistoryManager.peekMemoryHistory().size,
                    searchDictionaryOnDemand = { q ->
                        withContext(Dispatchers.IO) {
                            OfflineDictionaryManager.searchOnDemand(applicationContext, q, limit = 10)
                        }
                    },
                    searchYouTubeHistoryOnDemand = { q, limit ->
                        withContext(Dispatchers.IO) {
                            if (q.isBlank()) {
                                YouTubeWatchListenerService.ensureBoundAndCaptureActive(applicationContext)
                            }
                            val all = OfflineYouTubeHistoryManager.getCachedOrLoadHistory(applicationContext)
                            val matches = OfflineYouTubeHistoryManager.searchEntries(all, q, limit = limit)
                            matches to all.size
                        }
                    },
                    onDeleteYouTubeWatch = { entry ->
                        OfflineYouTubeHistoryManager.deleteEntry(applicationContext, entry.id).size
                    },
                    onOpenYouTubeWatch = { entry, resume ->
                        if (entry.hasDirectVideoId) {
                            val targetUrl = if (resume) entry.resumeUrl else entry.watchUrl
                            if (DeviceIntegrationManager.openUrlExcludingSelf(this, targetUrl, recordInYtHistory = false)) finish()
                        } else {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val resolved = OfflineYouTubeHistoryManager.resolveAndAttachVideoId(
                                    context = applicationContext,
                                    entry = entry,
                                    forceRetry = true,
                                )
                                val targetUrl = if (resume) resolved.resumeUrl else resolved.watchUrl
                                withContext(Dispatchers.Main) {
                                    if (DeviceIntegrationManager.openUrlExcludingSelf(this@PipeOverlayActivity, targetUrl, recordInYtHistory = false)) {
                                        finish()
                                    }
                                }
                            }
                        }
                    },
                    onOpenYouTubeSettings = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_OPEN_SETTINGS_SECTION, "INDEX")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        finish()
                    },
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
                    loadShortcuts = {
                        withContext(Dispatchers.IO) {
                            DeviceIntegrationManager.loadAppShortcuts(applicationContext)
                        }
                    },
                    loadFloccus = {
                        withContext(Dispatchers.IO) {
                            FloccusBookmarkManager.syncIfNeeded(applicationContext, forceReload = false)
                        }
                    },
                    loadFiles = { scopedPath ->
                        withContext(Dispatchers.IO) {
                            if (scopedPath != null) {
                                DeviceIntegrationManager.scanStorageFiles(applicationContext, bookmarks, scopedPath)
                            } else {
                                DeviceIntegrationManager.getCachedStorageFiles(applicationContext)
                            }
                        }
                    },
                    onRecordUsage = { key -> repository.incrementUsage(key) },
                    onSaveUniversalAliases = { updated -> repository.saveUniversalAliases(updated) },
                    onDispatchUrl = { rawInput, url ->
                        val ok = DeviceIntegrationManager.openUrlExcludingSelf(this, url)
                        if (ok) {
                            repository.recordResolutionInHistory(rawInput, QueryRouter.resolve(rawInput, engines, prefixes))
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
                    onLaunchAppShortcut = { sc ->
                        if (DeviceIntegrationManager.launchAppShortcut(this, sc)) finish()
                    },
                    onAppInfoApp = { pkg ->
                        if (DeviceIntegrationManager.openAppInfoSettings(this, pkg)) finish()
                    },
                    onUninstallApp = { pkg ->
                        if (DeviceIntegrationManager.uninstallInstalledApp(this, pkg)) finish()
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
                        if (DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp")) finish()
                    },
                    onWhatsAppBusinessContact = { contact ->
                        repository.incrementUsage("contact:${contact.cleanPhone}")
                        val digits = QueryRouter.normalizeWhatsAppPhone(contact.phoneNumber)
                        if (DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp.w4b")) finish()
                    },
                    onDialNumber = { phone ->
                        if (DeviceIntegrationManager.dialContact(this, phone)) finish()
                    },
                    onSmsNumber = { phone ->
                        if (DeviceIntegrationManager.smsContact(this, phone)) finish()
                    },
                    onWhatsAppNumber = { phone ->
                        val digits = QueryRouter.normalizeWhatsAppPhone(phone)
                        if (DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp")) finish()
                    },
                    onWhatsAppBusinessNumber = { phone ->
                        val digits = QueryRouter.normalizeWhatsAppPhone(phone)
                        if (DeviceIntegrationManager.whatsAppContact(this, digits, "com.whatsapp.w4b")) finish()
                    },
                    onWhatsAppDirectWithMsg = { digits, pkg, msg ->
                        if (DeviceIntegrationManager.whatsAppContact(this, digits, pkg, msg)) finish()
                    },
                    onOpenSystemSetting = { shortcut ->
                        val internalSec = shortcut.internalPipeSection
                        if (internalSec != null) {
                            if (internalSec == "APP_INFO") {
                                if (DeviceIntegrationManager.openAppInfoSettings(this, packageName)) finish()
                            } else {
                                startActivity(
                                    Intent(this, MainActivity::class.java).apply {
                                        putExtra(MainActivity.EXTRA_OPEN_SETTINGS_SECTION, internalSec)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                finish()
                            }
                        } else {
                            if (DeviceIntegrationManager.openSystemSettingShortcut(this, shortcut)) finish()
                        }
                    },
                    onStartTimer = { secs, lbl, skipUi ->
                        if (DeviceIntegrationManager.startCountdownTimer(this, secs, lbl, skipUi)) finish()
                    },
                    onSetAlarm = { h, m, lbl, skipUi ->
                        if (DeviceIntegrationManager.setSystemAlarm(this, h, m, lbl, skipUi)) finish()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
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
    initialOpenCalcMode: Boolean = false,
    initialOpenQuickAdd: Boolean = false,
    engines: List<SearchEngine>,
    bookmarks: List<PipeBookmark>,
    prefixes: PrefixConfig,
    history: List<SearchHistoryEntry>,
    initialUsage: Map<String, Int>,
    initialUniversalAliases: List<UniversalAlias> = emptyList(),
    cardOpacity: Float,
    cornerRadiusDp: Int,
    bottomAnchored: Boolean,
    showIdleSuggestions: Boolean,
    promptPrefix: String,
    placeholderText: String,
    showScopeBadge: Boolean,
    showMenuBtn: Boolean,
    whatsAppFlavor: WhatsAppFlavor = WhatsAppFlavor.BOTH,
    autoLaunchExactAlias: Boolean = false,
    initialCachedContacts: List<ContactItem> = emptyList(),
    initialCachedFiles: List<LocalFileItem>,
    initialCachedApps: List<InstalledAppItem>,
    initialCachedShortcuts: List<AppShortcutItem> = emptyList(),
    initialCachedFloccus: List<FloccusBookmarkItem> = emptyList(),
    floccusUnifiedSearch: Boolean = true,
    initialDictInstalled: Boolean = false,
    initialYtListenerEnabled: Boolean = false,
    initialYtHistoryCount: Int = 0,
    searchDictionaryOnDemand: suspend (String) -> List<DictionaryEntry>,
    searchYouTubeHistoryOnDemand: suspend (String, Int) -> Pair<List<YouTubeWatchEntry>, Int> = { _, _ -> emptyList<YouTubeWatchEntry>() to 0 },
    onOpenYouTubeWatch: (YouTubeWatchEntry, Boolean) -> Unit = { _, _ -> },
    onDeleteYouTubeWatch: (YouTubeWatchEntry) -> Int = { 0 },
    onOpenYouTubeSettings: () -> Unit = {},
    loadContacts: suspend () -> List<ContactItem>,
    loadApps: suspend () -> List<InstalledAppItem>,
    loadShortcuts: suspend () -> List<AppShortcutItem> = { emptyList() },
    loadFloccus: suspend () -> List<FloccusBookmarkItem> = { emptyList() },
    loadFiles: suspend (String?) -> List<LocalFileItem>,
    onRecordUsage: (String) -> Map<String, Int>,
    onSaveUniversalAliases: (List<UniversalAlias>) -> Unit = {},
    onDispatchUrl: (String, String) -> Boolean,
    onOpenPath: (String) -> Boolean,
    onShareFile: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onLaunchAppShortcut: (AppShortcutItem) -> Unit = {},
    onAppInfoApp: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onWhatsAppBusinessContact: (ContactItem) -> Unit = {},
    onDialNumber: (String) -> Unit,
    onSmsNumber: (String) -> Unit = {},
    onWhatsAppNumber: (String) -> Unit,
    onWhatsAppBusinessNumber: (String) -> Unit = {},
    onWhatsAppDirectWithMsg: (String, String?, String) -> Unit = { _, _, _ -> },
    onOpenSystemSetting: (SystemSettingShortcut) -> Unit = {},
    onStartTimer: (Int, String, Boolean) -> Unit = { _, _, _ -> },
    onSetAlarm: (Int, Int, String, Boolean) -> Unit = { _, _, _, _ -> },
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
    var universalAliases by remember { mutableStateOf(initialUniversalAliases) }
    var aliasEditTarget by remember { mutableStateOf<UniversalAlias?>(null) }
    var aliasEditText by remember { mutableStateOf(TextFieldValue("")) }
    var usageMap by remember { mutableStateOf(initialUsage) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var inlineQuickAdd by remember {
        mutableStateOf<PaletteItem.QuickAddAction?>(
            if (initialOpenQuickAdd) {
                PaletteItem.QuickAddAction(
                    kind = QuickAddKind.BOOKMARK,
                    title = "Quick Add",
                    subtitle = "",
                    isReadyToCommit = false,
                )
            } else null
        )
    }
    var contacts by remember { mutableStateOf(initialCachedContacts) }
    var installedApps by remember { mutableStateOf(initialCachedApps) }
    var appShortcuts by remember { mutableStateOf(initialCachedShortcuts) }
    var floccusBookmarks by remember { mutableStateOf(initialCachedFloccus) }
    var storageFiles by remember { mutableStateOf(initialCachedFiles) }
    var scopedFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var dictionaryMatches by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var activeDictEntry by remember { mutableStateOf<DictionaryEntry?>(null) }
    var ytHistoryMatches by remember { mutableStateOf<List<YouTubeWatchEntry>>(emptyList()) }
    var ytHistoryTotalCount by remember { mutableStateOf(initialYtHistoryCount) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }
    var expandedFolderChildren by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var previewImageFile by remember { mutableStateOf<LocalFileItem?>(null) }
    var clipText by remember { mutableStateOf("") }
    var isCalcMode by remember { mutableStateOf(initialOpenCalcMode) }
    var calcExpression by remember { mutableStateOf("") }
    var calcTape by remember { mutableStateOf<List<com.sujan.pipe.domain.CalcTapeEntry>>(emptyList()) }
    val focusManager = LocalFocusManager.current
    val dictInstalled = initialDictInstalled

    val enterCalcMode: (String) -> Unit = { seedExpr ->
        inlineQuickAdd = null
        aliasEditTarget = null
        errorMsg = null
        statusMsg = null
        if (seedExpr.isNotBlank()) {
            calcExpression = com.sujan.pipe.domain.PipeCalculator.prettifyExpression(seedExpr)
        }
        isCalcMode = true
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    val exitCalcMode: () -> Unit = {
        isCalcMode = false
        val t = input.text.trimStart().lowercase()
        if (t.startsWith("=") || t == "calc" || t.startsWith("calc ") || t == "calculator" || t.startsWith("calculator ")) {
            input = TextFieldValue("")
        }
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = isCalcMode) {
        exitCalcMode()
    }

    // Synchronized 185ms FastOutSlowIn entrance curve matching Android system IME animation
    val enterAnim = remember { Animatable(0f) }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeTargetRefPx = remember(density) { with(density) { 260.dp.toPx().coerceAtLeast(1f) } }
    val slideDistancePx = remember(density, bottomAnchored) {
        with(density) { (if (bottomAnchored) 22.dp else -18.dp).toPx() }
    }

    val parsedCommand = remember(input.text, activeBookmarks, activeEngines, prefixes) {
        QueryRouter.parsePromptCommand(input.text, activeBookmarks, activeEngines, prefixes)
    }

    // Read clipboard ONLY when entering clipboard/quick-add/direct-number mode (zero IPC on startup or normal typing)
    LaunchedEffect(parsedCommand.mode, inlineQuickAdd != null) {
        if (parsedCommand.mode == PromptIntentMode.CLIPBOARD_ONLY ||
            parsedCommand.mode == PromptIntentMode.ADD_COMMAND ||
            parsedCommand.mode == PromptIntentMode.DIRECT_NUMBER_ONLY ||
            inlineQuickAdd != null
        ) {
            clipText = runCatching { clipboard.getText()?.text?.trim().orEmpty() }.getOrDefault("")
        }
    }

    // Strictly on-demand dictionary seek only when `def <word>` is active
    LaunchedEffect(parsedCommand.mode, parsedCommand.filterQuery, dictInstalled) {
        if (parsedCommand.mode == PromptIntentMode.DICTIONARY_ONLY && dictInstalled) {
            dictionaryMatches = searchDictionaryOnDemand(parsedCommand.filterQuery)
        } else if (parsedCommand.mode != PromptIntentMode.DICTIONARY_ONLY && dictionaryMatches.isNotEmpty()) {
            dictionaryMatches = emptyList()
        }
    }

    // On-demand offline YouTube watch history search (`yth <query>` or under `yt`/`ytt`/`ytw`/`ytm`)
    val isYtEngineMode = parsedCommand.mode == PromptIntentMode.ENGINE_ONLY &&
        parsedCommand.matchedEngine?.keyword?.lowercase() in setOf("yt", "ytt", "ytw", "ytm")
    LaunchedEffect(parsedCommand.mode, parsedCommand.filterQuery, isYtEngineMode, ytHistoryTotalCount) {
        if (parsedCommand.mode == PromptIntentMode.YOUTUBE_HISTORY_ONLY) {
            val (matches, total) = searchYouTubeHistoryOnDemand(parsedCommand.filterQuery, 10)
            ytHistoryMatches = matches
            ytHistoryTotalCount = total
        } else if (isYtEngineMode) {
            val (matches, total) = searchYouTubeHistoryOnDemand(parsedCommand.filterQuery, 3)
            ytHistoryMatches = matches
            ytHistoryTotalCount = total
        } else if (ytHistoryMatches.isNotEmpty()) {
            ytHistoryMatches = emptyList()
        }
    }

    var results by remember {
        mutableStateOf(
            if (initialQuery.isBlank() && !showIdleSuggestions) {
                emptyList()
            } else {
                QueryRouter.buildPaletteResults(
                    rawInput = initialQuery,
                    engines = activeEngines,
                    bookmarks = activeBookmarks,
                    contacts = initialCachedContacts,
                    storageFiles = initialCachedFiles,
                    scopedFolderFiles = emptyList(),
                    history = history,
                    usageMap = initialUsage,
                    installedApps = initialCachedApps,
                    clipboardText = "",
                    prefixes = prefixes,
                    dictionaryMatches = emptyList(),
                    isDictionaryInstalled = dictInstalled,
                    whatsAppFlavor = whatsAppFlavor,
                    appShortcuts = initialCachedShortcuts,
                    universalAliases = initialUniversalAliases,
                    youtubeHistoryMatches = emptyList(),
                    youtubeHistoryTotalCount = initialYtHistoryCount,
                    isYouTubeListenerEnabled = initialYtListenerEnabled,
                    floccusBookmarks = initialCachedFloccus,
                    includeFloccusInUnified = floccusUnifiedSearch,
                )
            }
        )
    }

    LaunchedEffect(input.text, showIdleSuggestions, activeEngines, activeBookmarks, contacts, installedApps, appShortcuts, universalAliases, storageFiles, scopedFiles, history, usageMap, clipText, prefixes, dictionaryMatches, dictInstalled, ytHistoryMatches, ytHistoryTotalCount, floccusBookmarks) {
        if (input.text.isBlank() && !showIdleSuggestions) {
            if (results.isNotEmpty()) results = emptyList()
            return@LaunchedEffect
        }
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
                prefixes = prefixes,
                dictionaryMatches = dictionaryMatches,
                isDictionaryInstalled = dictInstalled,
                whatsAppFlavor = whatsAppFlavor,
                appShortcuts = appShortcuts,
                universalAliases = universalAliases,
                youtubeHistoryMatches = ytHistoryMatches,
                youtubeHistoryTotalCount = ytHistoryTotalCount,
                isYouTubeListenerEnabled = initialYtListenerEnabled,
                floccusBookmarks = floccusBookmarks,
                includeFloccusInUnified = floccusUnifiedSearch,
            )
        }
        results = computed
    }

    LaunchedEffect(Unit) {
        // 1. Trigger keyboard and synchronized 185ms FastOutSlowIn entrance simultaneously on Frame #1
        //    (unless launched directly into built-in T9 Calculator mode from the widget `[=]` button)
        runCatching {
            if (initialOpenCalcMode) {
                keyboardController?.hide()
            } else {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
        enterAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 185, easing = FastOutSlowInEasing),
        )
        // 2. Only load cold caches AFTER the 185ms entrance + keyboard animation is 100% finished
        delay(30L)
        val freshFloccus = loadFloccus()
        if (freshFloccus != floccusBookmarks) floccusBookmarks = freshFloccus
        if (contacts.isEmpty() || installedApps.isEmpty() || appShortcuts.isEmpty() || storageFiles.isEmpty()) {
            val loadedContacts = if (contacts.isEmpty()) loadContacts() else contacts
            val loadedApps = if (installedApps.isEmpty()) loadApps() else installedApps
            val loadedShortcuts = if (appShortcuts.isEmpty()) loadShortcuts() else appShortcuts
            val loadedFiles = if (storageFiles.isEmpty()) loadFiles(null) else storageFiles
            contacts = loadedContacts
            installedApps = loadedApps
            appShortcuts = loadedShortcuts
            storageFiles = loadedFiles
        }
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

    val saveOverlayAlias: (UniversalAlias, String) -> Unit = { target, rawAlias ->
        val cleanAlias = rawAlias.trim().lowercase().filter { !it.isWhitespace() }
        val updated = if (cleanAlias.isEmpty()) {
            universalAliases.filterNot { it.targetKey == target.targetKey }
        } else {
            val withoutDup = universalAliases.filterNot {
                it.targetKey == target.targetKey || it.alias.equals(cleanAlias, ignoreCase = true)
            }
            withoutDup + target.copy(alias = cleanAlias)
        }
        universalAliases = updated
        onSaveUniversalAliases(updated)
        aliasEditTarget = null
        statusMsg = if (cleanAlias.isEmpty()) "ok: removed alias for ${target.label}" else "ok: set [@$cleanAlias] -> ${target.label}"
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
        statusMsg = "ok: saved bookmark [${prefixes.bookmarksClean} ${cleanAlias.ifEmpty { cleanTitle }}]"
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
        statusMsg = "ok: saved pipe [${prefixes.pipesClean} $cleanKey] $cleanName"
    }

    val executeItem: (PaletteItem) -> Unit = { item ->
        usageMap = onRecordUsage(item.usageKey)
        when (item) {
            is PaletteItem.CalculatorAction -> {
                enterCalcMode(item.expression)
            }
            is PaletteItem.DirectUrlAction -> {
                if (!onDispatchUrl(item.displayHost, item.targetUrl)) errorMsg = "! no browser found"
            }
            is PaletteItem.SystemSettingAction -> {
                onOpenSystemSetting(item.shortcut)
            }
            is PaletteItem.AppShortcutAction -> {
                onLaunchAppShortcut(item.shortcut)
            }
            is PaletteItem.DirectNumberAction -> {
                when (item.primaryMode) {
                    DirectNumberPrimaryMode.WHATSAPP_PERSONAL ->
                        onWhatsAppDirectWithMsg(item.normalizedWhatsAppDigits, "com.whatsapp", item.messageText)
                    DirectNumberPrimaryMode.WHATSAPP_BUSINESS ->
                        onWhatsAppDirectWithMsg(item.normalizedWhatsAppDigits, "com.whatsapp.w4b", item.messageText)
                    DirectNumberPrimaryMode.CALL ->
                        onDialNumber(item.rawPhone)
                    DirectNumberPrimaryMode.SMS ->
                        onSmsNumber(item.rawPhone)
                }
            }
            is PaletteItem.TimerAlarmAction -> {
                if (item.isAlarm) {
                    onSetAlarm(item.hour24, item.minute, item.label, true)
                } else {
                    onStartTimer(item.totalSeconds, item.label, true)
                }
            }
            is PaletteItem.DictionaryAction -> {
                if (item.isSetupPrompt) {
                    onOpenFullApp(input.text)
                } else if (item.entry != null) {
                    activeDictEntry = item.entry
                }
            }
            is PaletteItem.ClipboardAction -> {
                when (item.kind) {
                    ClipActionKind.OPEN_URL -> if (!onDispatchUrl(item.payloadText, item.payloadText)) errorMsg = "! no browser found"
                    ClipActionKind.COPY_CLEAN_URL -> {
                        clipboard.setText(AnnotatedString(item.payloadText))
                        statusMsg = "ok: copied clean URL"
                    }
                    ClipActionKind.SAVE_BOOKMARK -> {
                        val inferred = QueryRouter.inferTitleFromTarget("bm", item.payloadText)
                        inlineQuickAdd = PaletteItem.QuickAddAction(
                            kind = QuickAddKind.BOOKMARK,
                            title = "Save Bookmark",
                            subtitle = item.payloadText,
                            isReadyToCommit = false,
                            arg1 = inferred.lowercase().take(5),
                            arg2 = inferred,
                            arg3 = item.payloadText,
                        )
                    }
                    ClipActionKind.CALL_NUMBER -> onDialNumber(item.payloadText)
                    ClipActionKind.WA_NUMBER -> onWhatsAppNumber(item.payloadText)
                    ClipActionKind.WAB_NUMBER -> onWhatsAppBusinessNumber(item.payloadText)
                    ClipActionKind.SAVE_CONTACT -> {
                        inlineQuickAdd = PaletteItem.QuickAddAction(
                            kind = QuickAddKind.CONTACT,
                            title = "Save Contact",
                            subtitle = item.payloadText,
                            isReadyToCommit = false,
                            arg2 = item.payloadText,
                        )
                    }
                    ClipActionKind.COPY_TEXT -> {
                        if (item.payloadText.isNotEmpty()) {
                            clipboard.setText(AnnotatedString(item.payloadText))
                            statusMsg = "ok: copied \"${item.payloadText.take(28)}\""
                        }
                    }
                }
            }
            is PaletteItem.EngineAction -> {
                val raw = if (item.query.isNotEmpty()) "${item.engine.keyword} ${item.query}" else item.engine.keyword
                if (!onDispatchUrl(raw, item.targetUrl)) errorMsg = "! no browser found"
            }
            is PaletteItem.BookmarkAction -> {
                val bm = item.bookmark
                val ok = if (bm.kind == BookmarkKind.LINK) onDispatchUrl(bm.alias, bm.target) else onOpenPath(bm.target)
                if (!ok) errorMsg = "! could not open ${bm.title}"
            }
            is PaletteItem.FloccusBookmarkAction -> {
                if (item.isSetupPrompt || item.bookmark == null) {
                    onOpenYouTubeSettings()
                } else {
                    if (!onDispatchUrl(item.bookmark.title, item.bookmark.url)) errorMsg = "! no browser found"
                }
            }
            is PaletteItem.FileAction -> {
                if (!onOpenPath(item.file.absolutePath)) errorMsg = "! could not open ${item.file.name}"
            }
            is PaletteItem.ContactAction -> {
                when (item.preferredAction) {
                    ContactQuickAction.WHATSAPP -> onWhatsAppContact(item.contact)
                    ContactQuickAction.WHATSAPP_BUSINESS -> onWhatsAppBusinessContact(item.contact)
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
            is PaletteItem.YouTubeWatchAction -> {
                if (item.isSetupPrompt || item.entry == null) {
                    onOpenYouTubeSettings()
                } else {
                    if (!item.entry.hasDirectVideoId) {
                        statusMsg = "resolving video link..."
                    }
                    onOpenYouTubeWatch(item.entry, true)
                }
            }
        }
    }

    var lastOverlayAutoAlias by remember { mutableStateOf("") }
    LaunchedEffect(input.text, results, autoLaunchExactAlias, universalAliases) {
        val raw = input.text.trim().lowercase()
        if (raw.isEmpty()) {
            lastOverlayAutoAlias = ""
            return@LaunchedEffect
        }
        if (autoLaunchExactAlias && raw != lastOverlayAutoAlias && aliasEditTarget == null && inlineQuickAdd == null) {
            val matched = universalAliases.firstOrNull { it.aliasLower == raw }
            val top = results.firstOrNull()
            if (matched != null && top != null && top.usageKey == matched.targetKey) {
                lastOverlayAutoAlias = raw
                executeItem(top)
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
        PromptIntentMode.CONTACTS_ONLY -> "${prefixes.contactsClean}:contacts"
        PromptIntentMode.FILES_ONLY -> "${prefixes.filesClean}:files"
        PromptIntentMode.BOOKMARKS_ONLY -> "${prefixes.bookmarksClean}:bookmarks"
        PromptIntentMode.FLOCCUS_ONLY -> "${prefixes.floccusClean}:floccus"
        PromptIntentMode.PIPES_LIST -> "${prefixes.pipesClean}:pipes"
        PromptIntentMode.APPS_ONLY -> "${prefixes.appsClean}:launcher"
        PromptIntentMode.HISTORY_ONLY -> "${prefixes.historyClean}:history"
        PromptIntentMode.YOUTUBE_HISTORY_ONLY -> "${prefixes.youtubeHistoryClean}:watch"
        PromptIntentMode.DICTIONARY_ONLY -> "${prefixes.dictionaryClean}:dict"
        PromptIntentMode.CLIPBOARD_ONLY -> "${prefixes.clipboardClean}:tools"
        PromptIntentMode.SYSTEM_SETTINGS_ONLY -> "${prefixes.systemSettingsClean}:sys"
        PromptIntentMode.DIRECT_NUMBER_ONLY -> "wa:num"
        PromptIntentMode.TIMER_ALARM_ONLY -> "clk:time"
        PromptIntentMode.CALCULATOR_ONLY -> "=:calc"
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
        val imeFraction = (imeBottomPx.toFloat() / imeTargetRefPx).coerceIn(0f, 1f)
        val syncProgress = maxOf(imeFraction, enterAnim.value).coerceIn(0f, 1f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (isCalcMode) 520.dp else 420.dp)
                .graphicsLayer {
                    alpha = syncProgress
                    translationY = (1f - syncProgress) * slideDistancePx
                    val scale = 0.97f + (0.03f * syncProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .animateContentSize(
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
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
            if (isCalcMode) {
                com.sujan.pipe.ui.PipeCalculatorT9Panel(
                    expression = calcExpression,
                    tape = calcTape,
                    onExpressionChange = {
                        calcExpression = it
                        statusMsg = null
                    },
                    onCommitResult = { entry ->
                        calcTape = (listOf(entry) + calcTape.filterNot { it.expression == entry.expression && it.plainValue == entry.plainValue }).take(10)
                    },
                    onCopyResult = { plain ->
                        clipboard.setText(AnnotatedString(plain))
                        statusMsg = "ok: copied $plain"
                    },
                    onExitToQwerty = exitCalcMode,
                    isOverlayStyle = true,
                    keySurfaceColor = glassSurface,
                    keyBorderColor = glassBorder,
                    cornerRadiusDp = cornerRadiusDp,
                )
                if (statusMsg != null) {
                    AsciiText(
                        text = statusMsg!!,
                        style = AsciiType.small,
                        color = c.good,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
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
                                    val res = QueryRouter.resolve(input.text, activeEngines, prefixes)
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
                    label = "=",
                    primary = isCalcMode,
                    onClick = { enterCalcMode("") },
                    spokenLabel = "open t9 calculator",
                )

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

            if (aliasEditTarget != null) {
                val target = aliasEditTarget!!
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(glassSurface, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiText(
                            text = "alias for [${target.category}] ${target.label}",
                            style = AsciiType.small,
                            color = c.accent2,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        AsciiButton("x", { aliasEditTarget = null })
                    }
                    AsciiTextField(
                        label = "type quick alias (no spaces) to jump to #1",
                        promptPrefix = "@",
                        value = aliasEditText,
                        onValueChange = { aliasEditText = it },
                        placeholder = "e.g. wcfg, mix, citi, dev",
                        trailingControl = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (target.alias.isNotBlank()) {
                                    AsciiButton("rm", { saveOverlayAlias(target, "") })
                                }
                                AsciiButton(
                                    label = "save",
                                    primary = true,
                                    onClick = { saveOverlayAlias(target, aliasEditText.text) },
                                )
                            }
                        },
                    )
                }
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
                        statusMsg = "ok: deleted bookmark [${prefixes.bookmarksClean} ${bm.alias}]"
                    },
                    onDeleteEngine = { eng ->
                        if (activeEngines.size > 1) {
                            val updated = activeEngines.filterNot { it.id == eng.id }
                            activeEngines = updated
                            onSaveEnginesList(updated)
                            statusMsg = "ok: deleted pipe [${prefixes.pipesClean} ${eng.keyword}]"
                        }
                    },
                    onCancel = { inlineQuickAdd = null },
                )
            }

            if (inlineQuickAdd == null && aliasEditTarget == null && shouldShowList && results.isNotEmpty()) {
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
                        val itemCustomAlias = universalAliases.firstOrNull { it.targetKey == item.usageKey }?.alias
                        val aliasTarget = remember(item, universalAliases) {
                            extractAliasTargetInfo(item, universalAliases)
                        }
                        SleekOverlayRow(
                            item = item,
                            isTop = isTop,
                            highlightBg = glassSurface,
                            highlightQuery = parsedCommand.filterQuery,
                            prefixes = prefixes,
                            customAlias = itemCustomAlias,
                            expandedFolderPath = expandedFolderPath,
                            expandedFolderChildren = expandedFolderChildren,
                            onExecute = { executeItem(item) },
                            onOpenAliasEditor = if (aliasTarget != null) {
                                {
                                    aliasEditTarget = aliasTarget
                                    aliasEditText = TextFieldValue(aliasTarget.alias, TextRange(aliasTarget.alias.length))
                                }
                            } else null,
                            onSwipeRightSecondary = {
                                when (item) {
                                    is PaletteItem.ContactAction -> onWhatsAppContact(item.contact)
                                    is PaletteItem.AppAction -> onAppInfoApp(item.app.packageName)
                                    is PaletteItem.AppShortcutAction -> onAppInfoApp(item.shortcut.packageName)
                                    is PaletteItem.FileAction -> {
                                        if (item.file.isDirectory) {
                                            expandedFolderPath = if (expandedFolderPath == item.file.absolutePath) null else item.file.absolutePath
                                        } else {
                                            onShareFile(item.file.absolutePath)
                                        }
                                    }
                                    is PaletteItem.DirectNumberAction -> {
                                        val altPkg = if (item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_PERSONAL) "com.whatsapp.w4b" else "com.whatsapp"
                                        onWhatsAppDirectWithMsg(item.normalizedWhatsAppDigits, altPkg, item.messageText)
                                    }
                                    is PaletteItem.TimerAlarmAction -> {
                                        if (item.isAlarm) onSetAlarm(item.hour24, item.minute, item.label, false)
                                        else onStartTimer(item.totalSeconds, item.label, false)
                                    }
                                    else -> executeItem(item)
                                }
                            },
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
                            onEditBookmark = { bm ->
                                inlineQuickAdd = PaletteItem.QuickAddAction(
                                    kind = QuickAddKind.BOOKMARK,
                                    title = "Edit Bookmark [${prefixes.bookmarksClean} ${bm.alias}]",
                                    subtitle = bm.target,
                                    isReadyToCommit = false,
                                    arg1 = bm.alias,
                                    arg2 = bm.title,
                                    arg3 = bm.target,
                                    bookmarkKind = bm.kind,
                                    editingId = bm.id,
                                )
                            },
                            onEditEngine = { eng ->
                                inlineQuickAdd = PaletteItem.QuickAddAction(
                                    kind = QuickAddKind.PIPE,
                                    title = "Edit Pipe [${prefixes.pipesClean} ${eng.keyword}]",
                                    subtitle = eng.urlTemplate,
                                    isReadyToCommit = false,
                                    arg1 = eng.keyword,
                                    arg2 = eng.name,
                                    arg3 = eng.urlTemplate,
                                    editingId = eng.id,
                                )
                            },
                            onDeleteBookmark = { bm ->
                                val updated = activeBookmarks.filterNot { it.id == bm.id }
                                activeBookmarks = updated
                                onSaveBookmarksList(updated)
                                statusMsg = "ok: deleted [${prefixes.bookmarksClean} ${bm.alias}]"
                            },
                            onDeleteEngine = if (activeEngines.size > 1) {
                                { eng ->
                                    val updated = activeEngines.filterNot { it.id == eng.id }
                                    activeEngines = updated
                                    onSaveEnginesList(updated)
                                    statusMsg = "ok: deleted [${prefixes.pipesClean} ${eng.keyword}]"
                                }
                            } else null,
                            onAppInfo = { pkg -> onAppInfoApp(pkg) },
                            onUninstallApp = { pkg -> onUninstallApp(pkg) },
                            onCallContact = onCallContact,
                            onSmsContact = onSmsContact,
                            onWhatsAppContact = onWhatsAppContact,
                            onWhatsAppBusinessContact = onWhatsAppBusinessContact,
                            onDirectWhatsAppPersonal = { digits, msg -> onWhatsAppDirectWithMsg(digits, "com.whatsapp", msg) },
                            onDirectWhatsAppBusiness = { digits, msg -> onWhatsAppDirectWithMsg(digits, "com.whatsapp.w4b", msg) },
                            onDirectDial = onDialNumber,
                            onDirectSms = onSmsNumber,
                            onDirectSaveContact = { rawPhone ->
                                inlineQuickAdd = PaletteItem.QuickAddAction(
                                    kind = QuickAddKind.CONTACT,
                                    title = "Save Contact",
                                    subtitle = rawPhone,
                                    isReadyToCommit = false,
                                    arg2 = rawPhone,
                                )
                            },
                            onOpenTimerAlarmClockUi = { tItem ->
                                if (tItem.isAlarm) onSetAlarm(tItem.hour24, tItem.minute, tItem.label, false)
                                else onStartTimer(tItem.totalSeconds, tItem.label, false)
                            },
                            onResumeYouTubeWatch = { entry ->
                                usageMap = onRecordUsage("ytw:${entry.id}")
                                if (!entry.hasDirectVideoId) statusMsg = "resolving video link..."
                                onOpenYouTubeWatch(entry, true)
                            },
                            onDeleteYouTubeWatch = { entry ->
                                val remaining = onDeleteYouTubeWatch(entry)
                                ytHistoryTotalCount = remaining
                                ytHistoryMatches = ytHistoryMatches.filterNot { it.id == entry.id }
                                statusMsg = "ok: removed \"${entry.title.take(24)}\""
                            },
                        )
                    }
                }
            }
            }
        }
    }

    if (activeDictEntry != null) {
        val entry = activeDictEntry!!
        AsciiDictionaryDetailDialog(
            entry = entry,
            onCopy = {
                clipboard.setText(AnnotatedString("${entry.word} (${entry.partOfSpeech}): ${entry.definition}"))
                statusMsg = "ok: copied ${entry.word}"
                activeDictEntry = null
            },
            onPipeToWiki = {
                val url = "https://en.wikipedia.org/w/index.php?search=${java.net.URLEncoder.encode(entry.word, "UTF-8")}"
                activeDictEntry = null
                onDispatchUrl("w ${entry.word}", url)
            },
            onDismiss = { activeDictEntry = null },
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

@Composable
private fun SleekOverlayRow(
    item: PaletteItem,
    isTop: Boolean,
    highlightBg: Color,
    highlightQuery: String,
    prefixes: PrefixConfig,
    customAlias: String? = null,
    expandedFolderPath: String?,
    expandedFolderChildren: List<LocalFileItem>,
    onExecute: () -> Unit,
    onOpenAliasEditor: (() -> Unit)? = null,
    onSwipeRightSecondary: (() -> Unit)? = null,
    onToggleFolderPeek: (String) -> Unit,
    onOpenChildFile: (LocalFileItem) -> Unit,
    onShareFile: (LocalFileItem) -> Unit,
    onPreviewImage: (LocalFileItem) -> Unit,
    onBookmarkFile: (LocalFileItem) -> Unit,
    onEditBookmark: ((PipeBookmark) -> Unit)? = null,
    onEditEngine: ((SearchEngine) -> Unit)? = null,
    onDeleteBookmark: ((PipeBookmark) -> Unit)? = null,
    onDeleteEngine: ((SearchEngine) -> Unit)? = null,
    onAppInfo: (String) -> Unit = {},
    onUninstallApp: (String) -> Unit = {},
    onCallContact: (ContactItem) -> Unit,
    onSmsContact: (ContactItem) -> Unit,
    onWhatsAppContact: (ContactItem) -> Unit,
    onWhatsAppBusinessContact: (ContactItem) -> Unit = {},
    onDirectWhatsAppPersonal: (String, String) -> Unit = { _, _ -> },
    onDirectWhatsAppBusiness: (String, String) -> Unit = { _, _ -> },
    onDirectDial: (String) -> Unit = {},
    onDirectSms: (String) -> Unit = {},
    onDirectSaveContact: (String) -> Unit = {},
    onOpenTimerAlarmClockUi: (PaletteItem.TimerAlarmAction) -> Unit = {},
    onResumeYouTubeWatch: (YouTubeWatchEntry) -> Unit = {},
    onDeleteYouTubeWatch: (YouTubeWatchEntry) -> Unit = {},
) {
    val c = LocalAscii.current
    val clipboard = LocalClipboardManager.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val rowShape = RoundedCornerShape(8.dp)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 52.dp.toPx() } }
    val rowBg = when {
        pressed || isTop -> highlightBg
        else -> Color.Transparent
    }
    val ptr = if (isTop) ">" else " "
    val aliasSuffix = if (!customAlias.isNullOrBlank()) " (@$customAlias)" else ""
    val swipeMod = Modifier.pointerInput(item.usageKey, onOpenAliasEditor, onSwipeRightSecondary) {
        var accumX = 0f
        var triggered = false
        detectHorizontalDragGestures(
            onDragStart = { accumX = 0f; triggered = false },
            onDragEnd = { accumX = 0f; triggered = false },
            onDragCancel = { accumX = 0f; triggered = false },
            onHorizontalDrag = { change, dragAmount ->
                accumX += dragAmount
                if (!triggered && accumX <= -swipeThresholdPx && onOpenAliasEditor != null) {
                    triggered = true
                    change.consume()
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    onOpenAliasEditor()
                } else if (!triggered && accumX >= swipeThresholdPx && onSwipeRightSecondary != null) {
                    triggered = true
                    change.consume()
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    onSwipeRightSecondary()
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxWidth().then(swipeMod)) {
    when (item) {
        is PaletteItem.DirectUrlAction -> {
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                AsciiFaviconBadge(url = item.targetUrl, fallbackText = item.displayHost.removePrefix("www."), fallbackColor = c.green)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText("Open ${item.displayHost}$aliasSuffix", style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(item.targetUrl, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
            }
        }

        is PaletteItem.SystemSettingAction -> {
            val sc = item.shortcut
            val dispTitle = "${sc.title}$aliasSuffix"
            val titleAnn = remember(dispTitle, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(dispTitle, highlightQuery, c.fg, c.yellow)
            }
            val badgeCol = if (sc.internalPipeSection != null) c.accent else c.accent2
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
                AsciiText("$ptr [${prefixes.systemSettingsClean}:${sc.alias}] ", style = AsciiType.small, color = if (pressed) c.bg else badgeCol)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(sc.subtitle, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
            }
        }

        is PaletteItem.AppShortcutAction -> {
            val sc = item.shortcut
            val dispTitle = "${sc.appLabel} - ${sc.shortLabel}$aliasSuffix"
            val titleAnn = remember(dispTitle, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(dispTitle, highlightQuery, c.fg, c.yellow)
            }
            val badgeCol = when (sc.kind) {
                AppShortcutKind.APP_PREFERENCES, AppShortcutKind.APP_SYSTEM_INFO -> c.accent2
                else -> c.accent
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
                AsciiText("$ptr [${prefixes.appsClean}:${sc.kind.badge}] ", style = AsciiType.small, color = if (pressed) c.bg else badgeCol)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(sc.longLabel, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
            }
        }

        is PaletteItem.DirectNumberAction -> {
            val titleText = "${item.primaryMode.label}: +${item.normalizedWhatsAppDigits}"
            val subText = if (item.messageText.isNotBlank()) "msg: \"${item.messageText}\" - raw: ${item.rawPhone}" else "choose [wa], [wa.b], [call], [sms], or [+c]"
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
                AsciiText("$ptr [${item.primaryMode.badge}] ", style = AsciiType.small, color = if (pressed) c.bg else c.good)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleText, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(subText, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                AsciiButton("wa", { onDirectWhatsAppPersonal(item.normalizedWhatsAppDigits, item.messageText) }, primary = isTop && item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_PERSONAL)
                AsciiButton("wa.b", { onDirectWhatsAppBusiness(item.normalizedWhatsAppDigits, item.messageText) }, primary = isTop && item.primaryMode == DirectNumberPrimaryMode.WHATSAPP_BUSINESS)
                AsciiButton("call", { onDirectDial(item.rawPhone) }, primary = isTop && item.primaryMode == DirectNumberPrimaryMode.CALL)
                AsciiButton("+c", { onDirectSaveContact(item.rawPhone) })
            }
        }

        is PaletteItem.TimerAlarmAction -> {
            val badge = if (item.isAlarm) "alm" else "tmr"
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
                AsciiText("$ptr [$badge] ", style = AsciiType.small, color = if (pressed) c.bg else c.warn)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(item.title, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(item.subtitle, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                AsciiButton("ui", { onOpenTimerAlarmClockUi(item) })
                AsciiButton("start", onExecute, primary = isTop)
            }
        }

        is PaletteItem.DictionaryAction -> {
            val d = item.entry
            val titleText = if (item.isSetupPrompt || d == null) {
                "Link Offline Dictionary (.tsv / .json / .gz)"
            } else {
                "${d.word} (${d.partOfSpeech})"
            }
            val subText = if (item.isSetupPrompt || d == null) {
                "tap to open Settings & select downloaded dictionary file"
            } else {
                d.definition
            }
            val titleAnn = remember(titleText, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(titleText, highlightQuery, c.fg, c.yellow)
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
                AsciiText("$ptr [${prefixes.dictionaryClean}] ", style = AsciiType.small, color = if (pressed) c.bg else c.accent3)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(subText, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                if (d != null) {
                    AsciiButton("cp", { clipboard.setText(AnnotatedString("${d.word}: ${d.definition}")) })
                }
            }
        }

        is PaletteItem.ClipboardAction -> {
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
                AsciiText("$ptr [${prefixes.clipboardClean}] ", style = AsciiType.small, color = c.cyan)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(item.title, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(item.subtitle, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                AsciiButton("run", onExecute, primary = isTop)
            }
        }

        is PaletteItem.ContactAction -> {
            val contact = item.contact
            val pref = item.preferredAction
            val dispName = "${contact.displayName}$aliasSuffix"
            val titleAnn = remember(dispName, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(dispName, highlightQuery, c.fg, c.yellow)
            }
            val subAnn = remember(contact.phoneNumber, highlightQuery, pressed, c.dim, c.accent2, c.bg) {
                buildFzfHighlightedText(contact.phoneNumber, highlightQuery, c.dim, c.cyan)
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (pressed) c.bg else c.good)
                AsciiContactAvatar(contact = contact)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(subAnn, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                AsciiButton("call", { onCallContact(contact) }, primary = isTop && pref == ContactQuickAction.CALL)
                AsciiButton("sms", { onSmsContact(contact) }, primary = isTop && pref == ContactQuickAction.SMS)
                AsciiButton("wa", { onWhatsAppContact(contact) }, primary = isTop && pref == ContactQuickAction.WHATSAPP)
                AsciiButton("wa.b", { onWhatsAppBusinessContact(contact) }, primary = isTop && pref == ContactQuickAction.WHATSAPP_BUSINESS)
            }
        }

        is PaletteItem.FileAction -> {
            val f = item.file
            val isExpanded = f.isDirectory && expandedFolderPath == f.absolutePath
            val dispName = "${f.name}$aliasSuffix"
            val titleAnn = remember(dispName, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(dispName, highlightQuery, c.fg, c.yellow)
            }
            val subText = "${f.parentName} - ${f.formattedSize}"
            val subAnn = remember(subText, highlightQuery, pressed, c.dim, c.accent2, c.bg) {
                buildFzfHighlightedText(subText, highlightQuery, c.dim, c.cyan)
            }
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
                        text = "$ptr ",
                        style = AsciiType.small,
                        color = if (isTop) c.purple else c.dim,
                    )
                    AsciiFileTypeBadge(
                        name = f.name,
                        isDirectory = f.isDirectory,
                        filePath = f.absolutePath,
                        isImageFile = f.isImageFile,
                        onImageClick = { onPreviewImage(f) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        AsciiText(
                            text = titleAnn,
                            style = if (isTop) AsciiType.title else AsciiType.body,
                            color = c.fg,
                            maxLines = 1,
                        )
                        AsciiText(
                            text = subAnn,
                            style = AsciiType.small,
                            color = c.dim,
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
                            AsciiText("`-- (empty folder)", style = AsciiType.small, color = c.dim)
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
                                    AsciiFileTypeBadge(
                                        name = child.name,
                                        isDirectory = child.isDirectory,
                                        filePath = child.absolutePath,
                                        isImageFile = child.isImageFile,
                                        onImageClick = { onPreviewImage(child) },
                                    )
                                    AsciiText(
                                        text = "|-- ${child.name}",
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

        is PaletteItem.CalculatorAction -> {
            val titleText = when {
                item.isValid -> "= ${item.formattedValue}"
                item.expression.isBlank() -> "Open T9 Calculator"
                else -> "! ${item.errorMessage ?: "invalid expression"}"
            }
            val subText = if (item.expression.isBlank()) {
                "tap or press RET to switch keyboard to T9 keypad"
            } else {
                "${com.sujan.pipe.domain.PipeCalculator.prettifyExpression(item.expression)} • tap to open T9 pad"
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                AsciiText("[=] ", style = AsciiType.small, color = c.accent)
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleText, style = AsciiType.title, color = if (item.isValid) c.good else c.fg, maxLines = 1)
                    AsciiText(subText, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                if (item.isValid) {
                    AsciiButton("cp", { clipboard.setText(AnnotatedString(item.plainValue)) })
                }
                AsciiButton("t9", onExecute, primary = true)
            }
        }

        is PaletteItem.FloccusBookmarkAction -> {
            val fb = item.bookmark
            val titleText = if (item.isSetupPrompt || fb == null) item.setupTitle else "${fb.title}$aliasSuffix"
            val subText = if (item.isSetupPrompt || fb == null) item.setupSubtitle else fb.subtitleDisplay
            val titleAnn = remember(titleText, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(titleText, highlightQuery, c.fg, c.yellow)
            }
            val subAnn = remember(subText, highlightQuery, pressed, c.dim, c.cyan, c.bg) {
                buildFzfHighlightedText(subText, highlightQuery, c.dim, c.cyan)
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                if (fb != null) {
                    AsciiFaviconBadge(url = fb.url, fallbackText = prefixes.floccusClean, fallbackColor = c.accent3)
                } else {
                    AsciiText("[${prefixes.floccusClean}] ", style = AsciiType.small, color = c.accent3)
                }
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(subAnn, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                if (fb != null) {
                    AsciiButton("cp", { clipboard.setText(AnnotatedString(fb.url)) })
                } else {
                    AsciiButton("set", onExecute, primary = true)
                }
            }
        }

        is PaletteItem.BookmarkAction -> {
            val bm = item.bookmark
            val isExpanded = bm.kind == BookmarkKind.FOLDER && expandedFolderPath == bm.target
            val baseTitle = if (bm.alias.isNotBlank()) "${bm.title} (${bm.alias})" else bm.title
            val rawTitle = "$baseTitle$aliasSuffix"
            val titleAnn = remember(rawTitle, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(rawTitle, highlightQuery, c.fg, c.yellow)
            }
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
                    val bPre = prefixes.bookmarksClean
                    val badge = when (bm.kind) {
                        BookmarkKind.FOLDER -> "[$bPre:d/]"
                        BookmarkKind.FILE -> "[$bPre:f.]"
                        BookmarkKind.LINK -> "[$bPre:bm]"
                    }
                    AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                    if (bm.kind == BookmarkKind.LINK) {
                        AsciiFaviconBadge(url = bm.target, fallbackText = bm.alias.ifBlank { bm.title }, fallbackColor = c.cyan)
                    } else {
                        AsciiFileTypeBadge(
                            name = bm.target.substringAfterLast('/').ifBlank { bm.title },
                            isDirectory = bm.kind == BookmarkKind.FOLDER,
                            filePath = bm.target,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AsciiText(
                            text = titleAnn,
                            style = if (isTop) AsciiType.title else AsciiType.body,
                            color = c.fg,
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
                    if (onEditBookmark != null) {
                        AsciiButton("edit", { onEditBookmark(bm) })
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
                                    text = "|-- ${child.name}",
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

        // Feature #7: App Management Actions (`[@+]`, `[info]`, and `[unin]`) directly on App rows in the simple overlay!
        is PaletteItem.AppAction -> {
            val app = item.app
            val dispLabel = "${app.label}$aliasSuffix"
            val titleAnn = remember(dispLabel, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(dispLabel, highlightQuery, c.fg, c.yellow)
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
                AsciiText("$ptr [${prefixes.appsClean}] ", style = AsciiType.small, color = if (pressed) c.bg else c.accent)
                AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                AsciiButton("info", { onAppInfo(app.packageName) }, spokenLabel = "app info for ${app.label}")
                AsciiButton("unin", { onUninstallApp(app.packageName) }, spokenLabel = "uninstall ${app.label}")
            }
        }

        is PaletteItem.EngineAction -> {
            val eng = item.engine
            val desc = (if (item.isHome) "${eng.name} (open home)" else "${eng.name}: \"${item.query}\"") + aliasSuffix
            val titleAnn = remember(desc, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(desc, highlightQuery, c.fg, c.yellow)
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                AsciiFaviconBadge(url = eng.homeUrl, fallbackText = eng.keyword, fallbackColor = c.forRole(eng.colorRole))
                AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                if (item.isHome) {
                    if (onEditEngine != null) {
                        AsciiButton("edit", { onEditEngine(eng) })
                    }
                    if (onDeleteEngine != null) {
                        AsciiButton("x", { onDeleteEngine(eng) })
                    }
                } else {
                    AsciiText("pipe", style = AsciiType.small, color = c.dim)
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
                    AsciiText(item.title, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(item.subtitle, style = AsciiType.small, color = c.dim, maxLines = 1)
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
                AsciiText(item.displayTitle, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1, modifier = Modifier.weight(1f))
                AsciiButton("del", onExecute, primary = isTop)
            }
        }

        is PaletteItem.HistoryAction -> {
            val h = item.entry
            val rawDesc = "${h.rawInput} -> ${h.engineName.lowercase()}"
            val titleAnn = remember(rawDesc, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(rawDesc, highlightQuery, c.fg, c.yellow)
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                AsciiFaviconBadge(url = h.resolvedUrl, fallbackText = h.engineKeyword.ifBlank { h.engineName }, fallbackColor = c.purple)
                AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }

        is PaletteItem.YouTubeWatchAction -> {
            val w = item.entry
            val titleText = if (item.isSetupPrompt || w == null) item.setupTitle else "${w.title}$aliasSuffix"
            val subText = if (item.isSetupPrompt || w == null) item.setupSubtitle else w.subtitleSummary()
            val titleAnn = remember(titleText, highlightQuery, pressed, c.fg, c.accent, c.bg) {
                buildFzfHighlightedText(titleText, highlightQuery, c.fg, c.yellow)
            }
            val subAnn = remember(subText, highlightQuery, pressed, c.dim, c.cyan, c.bg) {
                buildFzfHighlightedText(subText, highlightQuery, c.dim, c.cyan)
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
                AsciiText("$ptr ", style = AsciiType.small, color = if (isTop) c.purple else c.dim)
                AsciiYouTubeThumbBadge(thumbnailPath = w?.thumbnailPath.orEmpty())
                Column(modifier = Modifier.weight(1f)) {
                    AsciiText(titleAnn, style = if (isTop) AsciiType.title else AsciiType.body, color = c.fg, maxLines = 1)
                    AsciiText(subAnn, style = AsciiType.small, color = c.dim, maxLines = 1)
                }
                if (w != null) {
                    if (w.hasResumePosition) {
                        AsciiButton("res", { onResumeYouTubeWatch(w) }, primary = isTop)
                    }
                    AsciiButton("cp", { clipboard.setText(AnnotatedString(w.watchUrl)) })
                    AsciiButton("x", { onDeleteYouTubeWatch(w) })
                } else {
                    AsciiButton("set", onExecute, primary = true)
                }
            }
        }
    }    }
}
