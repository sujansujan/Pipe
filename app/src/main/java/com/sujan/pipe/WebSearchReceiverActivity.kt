package com.sujan.pipe

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.sujan.pipe.data.DeviceIntegrationManager
import com.sujan.pipe.data.OfflineYouTubeHistoryManager
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.BookmarkKind

/**
 * Registered as a full Android Browser (`http` and `https` with `CATEGORY_BROWSABLE`)
 * AND System Search Receiver (`ACTION_WEB_SEARCH`, `ACTION_SEARCH`, `pipe://search?q=%s`).
 *
 * - If the incoming intent is a search query (or a search URL from Google/Bing/DuckDuckGo/pipe),
 *   it resolves the keyword prefix (`yt lofi`, `dl`, etc.) and forwards via `DeviceIntegrationManager.openUrlExcludingSelf`.
 * - If the incoming intent is a regular non-search webpage URL (because `pipe` is set as Default Browser),
 *   it forwards the URL directly to the user's real browser or native app without looping.
 */
class WebSearchReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = PipeRepository(applicationContext)
        val engines = repo.loadEngines()
        val bookmarks = repo.loadBookmarks()

        val rawQuery = extractIncomingSearchQuery(intent)

        if (!rawQuery.isNullOrBlank()) {
            // Check if the query is an exact bookmark alias first (e.g. `dl`)
            val cmd = QueryRouter.parsePromptCommand(rawQuery, bookmarks)
            val exactBm = cmd.exactBookmark
            if (exactBm != null) {
                val opened = if (exactBm.kind == BookmarkKind.LINK) {
                    DeviceIntegrationManager.openUrlExcludingSelf(this, exactBm.target)
                } else {
                    DeviceIntegrationManager.openLocalFileOrFolder(this, exactBm.target)
                }
                if (opened) {
                    finish()
                    return
                }
            }

            // Otherwise resolve via search engine keyword router
            val resolution = QueryRouter.resolve(rawQuery, engines)
            repo.recordResolutionInHistory(rawQuery, resolution)
            if (DeviceIntegrationManager.openUrlExcludingSelf(this, resolution.targetUrl)) {
                finish()
                return
            }
        }

        // If `pipe` is Default Browser and received a direct webpage link (e.g. https://github.com/sujansujan),
        // forward straight to the real browser / native app excluding `com.sujan.pipe`.
        val incomingUriString = intent?.dataString
        if (intent?.action == Intent.ACTION_VIEW && !incomingUriString.isNullOrBlank()) {
            if (OfflineYouTubeHistoryManager.extractYouTubeVideoId(incomingUriString) != null) {
                OfflineYouTubeHistoryManager.recordYouTubeUrlInteraction(
                    context = applicationContext,
                    rawUrl = incomingUriString,
                    sourceApp = "Link",
                )
            }
            if (DeviceIntegrationManager.openUrlExcludingSelf(this, incomingUriString)) {
                finish()
                return
            }
        }

        // Fallback: open the floating overlay prompt
        startActivity(
            Intent(this, PipeOverlayActivity::class.java).apply {
                if (!rawQuery.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_PREFILL_QUERY, rawQuery)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        finish()
    }

    private fun extractIncomingSearchQuery(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> {
                intent.getStringExtra(SearchManager.QUERY)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            Intent.ACTION_VIEW -> {
                QueryRouter.extractQueryFromSearchUri(intent.dataString)
            }
            else -> intent.getStringExtra(SearchManager.QUERY)
        }?.trim()
    }
}
