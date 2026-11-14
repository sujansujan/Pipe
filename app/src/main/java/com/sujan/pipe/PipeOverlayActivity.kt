package com.sujan.pipe

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.SearchEngine
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiPipePill
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiTextField
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii
import com.sujan.pipe.ui.ascii.PipeTheme

/**
 * Translucent floating ASCII command overlay (`~/pipe/overlay`).
 * Can be bound directly in UbikiTouch (`Shortcuts -> pipe overlay` or `Activities -> PipeOverlayActivity`),
 * Quick Settings tile, home-screen widget, or system Digital Assistant gesture (`ACTION_ASSIST`).
 */
class PipeOverlayActivity : ComponentActivity() {

    private val repository by lazy { PipeRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = extractPrefill(intent)
        val engines = repository.loadEngines()
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
                    onDispatch = { rawInput ->
                        val resolution = QueryRouter.resolve(rawInput, engines)
                        val ok = launchExternalUrl(resolution.targetUrl)
                        if (ok) {
                            repository.recordResolutionInHistory(rawInput, resolution)
                            finish()
                        }
                        ok
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
    }

    private fun extractPrefill(intent: Intent?): String {
        if (intent == null) return ""
        return intent.getStringExtra(MainActivity.EXTRA_PREFILL_QUERY)
            ?: intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayPromptWindow(
    initialQuery: String,
    engines: List<SearchEngine>,
    onDispatch: (String) -> Boolean,
    onOpenFullApp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAscii.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var input by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
    }

    // Outer scrim: tapping outside the floating ASCII box dismisses the overlay immediately
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
        // Inner floating terminal card: consumes clicks so tapping inside doesn't dismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg)
                .border(2.dp, c.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Compact Overlay Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "~/pipe/overlay",
                    style = AsciiType.title,
                    color = c.accent2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsciiButton(
                        label = "full",
                        onClick = { onOpenFullApp(input.text) },
                        spokenLabel = "open full pipe app",
                    )
                    AsciiButton(
                        label = "x",
                        onClick = onDismiss,
                        spokenLabel = "close overlay",
                    )
                }
            }

            AsciiTextField(
                label = null,
                promptPrefix = "$ ",
                value = input,
                onValueChange = {
                    input = it
                    errorMsg = null
                },
                placeholder = "yt lofi  |  g phones",
                errorText = errorMsg,
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (!onDispatch(input.text)) {
                            errorMsg = "! no browser or app found"
                        }
                    }
                ),
                trailingControl = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (input.text.isNotEmpty()) {
                            AsciiButton(
                                label = "c",
                                onClick = { input = TextFieldValue("") },
                                spokenLabel = "clear input",
                            )
                        }
                        AsciiButton(
                            label = "| ->",
                            onClick = {
                                if (!onDispatch(input.text)) {
                                    errorMsg = "! no browser or app found"
                                }
                            },
                            primary = true,
                            spokenLabel = "pipe query to ${resolution.engine.name}",
                        )
                    }
                },
            )

            // Live 1-line pipeline preview
            AsciiText(
                text = "+--> ${resolution.pipelinePreview}",
                style = AsciiType.small,
                color = if (resolution.matchedByKeyword) c.accent else c.dim,
                maxLines = 1,
            )

            // Keyword pills
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEach { eng ->
                    val active = resolution.matchedByKeyword && resolution.engine.id == eng.id
                    AsciiPipePill(
                        keyword = eng.keyword,
                        name = eng.name.lowercase(),
                        selected = active,
                        isDefault = eng.isDefault,
                        accentColor = c.forRole(eng.colorRole),
                        onClick = {
                            val trimmedStart = input.text.trimStart()
                            val tokens = trimmedStart.split(Regex("\\s+"), limit = 2)
                            val first = tokens.firstOrNull().orEmpty()
                            val rem = if (tokens.size > 1) tokens[1] else ""
                            val matchesAny = engines.any { it.keyword.equals(first, ignoreCase = true) }
                            val next = when {
                                trimmedStart.isEmpty() -> "${eng.keyword} "
                                matchesAny && rem.isNotEmpty() -> "${eng.keyword} $rem"
                                !trimmedStart.contains(' ') -> "${eng.keyword} "
                                else -> "${eng.keyword} $trimmedStart"
                            }
                            input = TextFieldValue(next, TextRange(next.length))
                            runCatching { focusRequester.requestFocus() }
                        },
                    )
                }
            }
        }
    }
}
