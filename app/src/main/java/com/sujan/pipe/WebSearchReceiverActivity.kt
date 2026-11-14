package com.sujan.pipe

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.QueryRouter

/**
 * Invisible zero-UI router that lets `pipe` act as the user's Default Search Engine:
 * 1. Android System Search (`Intent.ACTION_WEB_SEARCH` and `Intent.ACTION_SEARCH` with `SearchManager.QUERY`)
 * 2. Custom Browser Search Engine URLs:
 *    - `pipe://search?q=%s`
 *    - `https://pipe.local/search?q=%s` or `http://pipe.local/search?q=%s`
 *
 * Immediately parses the keyword prefix (e.g. `yt lofi`), substitutes `{query}`,
 * and forwards to the target service via `ACTION_VIEW`.
 */
class WebSearchReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawQuery = extractIncomingSearchQuery(intent)
        val repo = PipeRepository(applicationContext)
        val engines = repo.loadEngines()

        if (!rawQuery.isNullOrBlank()) {
            val resolution = QueryRouter.resolve(rawQuery, engines)
            repo.recordResolutionInHistory(rawQuery, resolution)
            val launched = try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(resolution.targetUrl)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
            if (launched) {
                finish()
                return
            }
        }

        // If invoked with an empty query (or if no browser handled the URL), open the floating overlay prompt
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
                val dataUri: Uri? = intent.data
                dataUri?.getQueryParameter("q")
                    ?: dataUri?.getQueryParameter("query")
                    ?: dataUri?.getQueryParameter("s")
                    ?: dataUri?.getQueryParameter("text")
                    ?: QueryRouter.extractQueryFromSearchUri(intent.dataString)
            }
            else -> intent.getStringExtra(SearchManager.QUERY)
        }?.trim()
    }
}
