package com.sujan.pipe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.sujan.pipe.data.ExternalTextMode
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.domain.QueryRouter

class TextPipeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingText = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim().orEmpty()

        val repo = PipeRepository(applicationContext)
        if (incomingText.isNotEmpty() && repo.loadExternalTextMode() == ExternalTextMode.DIRECT_PIPE) {
            val res = QueryRouter.resolve(incomingText, repo.loadEngines())
            val launched = try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(res.targetUrl)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                true
            } catch (_: ActivityNotFoundException) { false }
            if (launched) {
                finish()
                return
            }
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_PREFILL_QUERY, incomingText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        finish()
    }
}
