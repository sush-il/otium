package dev.sushil.focusblock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.sushil.focusblock.FocusBlockApp
import dev.sushil.focusblock.R
import dev.sushil.focusblock.ui.MainActivity
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

/** Small home-screen widget: shows how many profiles are active, taps through to the app. */
class BlockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // A widget update is a short, infrequent callback, so a quick blocking read is fine here
        val active = runBlocking {
            val now = LocalDateTime.now()
            (context.applicationContext as FocusBlockApp).container.profileRepository.all().count { it.isEnforcing(now) }
        }
        val subtitle = when (active) {
            0 -> "No active profiles"
            1 -> "1 profile active"
            else -> "$active profiles active"
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_block).apply {
                setTextViewText(R.id.widget_subtitle, subtitle)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Ask the system to redraw all widgets, e.g. after profiles change. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BlockWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, BlockWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    },
                )
            }
        }
    }
}
