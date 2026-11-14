package com.sujan.pipe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sujan.pipe.MainActivity
import com.sujan.pipe.R

class PipeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_pipe)
            views.setOnClickPendingIntent(R.id.widget_prompt, pending(context, id * 10 + 1, ""))
            views.setOnClickPendingIntent(R.id.widget_chip_g, pending(context, id * 10 + 2, "g "))
            views.setOnClickPendingIntent(R.id.widget_chip_yt, pending(context, id * 10 + 3, "yt "))
            views.setOnClickPendingIntent(R.id.widget_chip_gh, pending(context, id * 10 + 4, "gh "))
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun pending(context: Context, code: Int, prefill: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PREFILL_QUERY, prefill)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
