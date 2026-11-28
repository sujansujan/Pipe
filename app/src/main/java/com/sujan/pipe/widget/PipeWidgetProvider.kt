package com.sujan.pipe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.sujan.pipe.MainActivity
import com.sujan.pipe.PipeOverlayActivity
import com.sujan.pipe.R
import com.sujan.pipe.data.PipeRepository
import com.sujan.pipe.ui.ascii.AsciiColors
import com.sujan.pipe.ui.ascii.ThemeMode
import kotlin.math.roundToInt

/**
 * Homescreen Search Widget styled 1:1 with Pipe's floating overlay popup.
 * - Live-syncs with Settings -> overlay & theme (card opacity, corner radius, force dark glass,
 *   color palette, prompt prefix, placeholder, `[all]` scope badge, and `[::]` button).
 * - Every tap opens [PipeOverlayActivity] directly as a floating overlay (never the full app):
 *   - Bar / `[all]`: opens search overlay
 *   - `[=]`: opens overlay directly in built-in T9 Calculator mode
 *   - `[+]`: opens overlay directly in Quick Add mode
 *   - `[::]`: opens overlay prefilled with `set ` for instant settings jump
 */
class PipeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id, appWidgetManager.getAppWidgetOptions(id))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        private const val ACTION_PREFIX = "com.sujan.pipe.widget.OVERLAY_"

        fun refreshAll(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(appCtx, PipeWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            for (id in ids) {
                updateSingleWidget(appCtx, manager, id, manager.getAppWidgetOptions(id))
            }
        }

        internal fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle?,
        ) {
            val repo = PipeRepository(context)
            val forceDark = repo.loadOverlayForceDark()
            val themeMode = if (forceDark) ThemeMode.Dark else repo.loadThemeMode()
            val isDark = when (themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> {
                    val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    nightFlags == Configuration.UI_MODE_NIGHT_YES
                }
            }
            val palette = repo.loadPaletteChoice().palette
            val colors: AsciiColors = if (isDark) palette.dark else palette.light

            val cardOpacity = repo.loadOverlayOpacity()
            val cornerRadiusDp = repo.loadOverlayCornerRadiusDp()
            val promptPrefix = repo.loadSearchBoxPrefix()
            val placeholderText = repo.loadSearchBoxPlaceholder()
            val showScopeBadge = repo.loadShowSearchBoxScopeBadge()
            val showMenuBtn = repo.loadShowSearchBoxMenuBtn()
            val settingsPrefix = repo.loadPrefixConfig().systemSettingsClean

            val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
            val widthDp = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0)
                .takeIf { it > 0 }
                ?: (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0).takeIf { it > 0 }
                ?: 328
            val heightDp = 52

            val bgBitmap = renderOverlayGlassBitmap(
                widthPx = (widthDp * density).roundToInt().coerceIn(240, 1400),
                heightPx = (heightDp * density).roundToInt().coerceIn(96, 320),
                cornerRadiusPx = cornerRadiusDp * density,
                strokeWidthPx = (1f * density).coerceAtLeast(1f),
                bgColorArgb = withAlpha(colors.bg.toArgb(), cardOpacity),
                borderColorArgb = withAlpha(colors.border.toArgb(), 0.75f),
            )

            val views = RemoteViews(context.packageName, R.layout.widget_pipe)
            views.setInt(R.id.widget_bg_image, "setBackgroundResource", 0)
            views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)

            // Prompt prefix
            if (promptPrefix.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_prefix, View.VISIBLE)
                views.setTextViewText(R.id.widget_prefix, promptPrefix)
                views.setTextColor(R.id.widget_prefix, colors.accent.toArgb())
            } else {
                views.setViewVisibility(R.id.widget_prefix, View.GONE)
            }

            // Placeholder
            views.setTextViewText(R.id.widget_prompt, placeholderText)
            views.setTextColor(R.id.widget_prompt, colors.dim.toArgb())

            // Scope badge
            views.setViewVisibility(R.id.widget_scope_badge, if (showScopeBadge) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_scope_badge, "[all]")
            views.setTextColor(R.id.widget_scope_badge, colors.dim.toArgb())

            // Action buttons (`[=]`, `[+]`, `[::]`) in exact AsciiButton cyan ink
            val btnColor = colors.cyan.toArgb()
            views.setTextColor(R.id.widget_btn_calc, btnColor)
            views.setTextColor(R.id.widget_btn_add, btnColor)
            views.setViewVisibility(R.id.widget_btn_menu, if (showMenuBtn) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.widget_btn_menu, btnColor)

            // Every tap opens PipeOverlayActivity as an overlay (never MainActivity)
            val openSearch = overlayPendingIntent(context, appWidgetId * 10 + 1, tag = "SEARCH")
            views.setOnClickPendingIntent(R.id.widget_root, openSearch)
            views.setOnClickPendingIntent(R.id.widget_card, openSearch)
            views.setOnClickPendingIntent(R.id.widget_prefix, openSearch)
            views.setOnClickPendingIntent(R.id.widget_prompt, openSearch)
            views.setOnClickPendingIntent(R.id.widget_scope_badge, openSearch)

            views.setOnClickPendingIntent(
                R.id.widget_btn_calc,
                overlayPendingIntent(context, appWidgetId * 10 + 2, tag = "CALC", openCalcMode = true),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_add,
                overlayPendingIntent(context, appWidgetId * 10 + 3, tag = "ADD", openQuickAdd = true),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_menu,
                overlayPendingIntent(context, appWidgetId * 10 + 4, tag = "SET", prefill = "$settingsPrefix "),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun overlayPendingIntent(
            context: Context,
            requestCode: Int,
            tag: String,
            prefill: String = "",
            openCalcMode: Boolean = false,
            openQuickAdd: Boolean = false,
        ): PendingIntent {
            val intent = Intent(context, PipeOverlayActivity::class.java).apply {
                action = "$ACTION_PREFIX$tag"
                if (prefill.isNotEmpty()) putExtra(MainActivity.EXTRA_PREFILL_QUERY, prefill)
                if (openCalcMode) putExtra(PipeOverlayActivity.EXTRA_OPEN_CALC_MODE, true)
                if (openQuickAdd) putExtra(PipeOverlayActivity.EXTRA_OPEN_QUICK_ADD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun withAlpha(argb: Int, alphaFraction: Float): Int {
            val a = (alphaFraction.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            return (argb and 0x00FFFFFF) or (a shl 24)
        }

        private fun renderOverlayGlassBitmap(
            widthPx: Int,
            heightPx: Int,
            cornerRadiusPx: Float,
            strokeWidthPx: Float,
            bgColorArgb: Int,
            borderColorArgb: Int,
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val halfStroke = strokeWidthPx / 2f
            val rect = RectF(halfStroke, halfStroke, widthPx - halfStroke, heightPx - halfStroke)
            val maxRadius = (minOf(rect.width(), rect.height()) / 2f).coerceAtLeast(0f)
            val radius = cornerRadiusPx.coerceIn(0f, maxRadius)

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = bgColorArgb
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = borderColorArgb
            }
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
            return bitmap
        }
    }
}
