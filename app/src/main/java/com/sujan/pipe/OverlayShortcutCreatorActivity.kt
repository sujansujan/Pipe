package com.sujan.pipe

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Handles `android.intent.action.CREATE_SHORTCUT` so gesture apps like UbikiTouch (`ubitouch`),
 * Edge Gestures, Tasker, or custom launchers can bind a direct shortcut to `PipeOverlayActivity`.
 */
class OverlayShortcutCreatorActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchOverlayIntent = Intent(this, PipeOverlayActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }

        val resultIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchOverlayIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.overlay_shortcut_label))
            putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(
                    applicationContext,
                    R.mipmap.ic_launcher,
                ),
            )
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
