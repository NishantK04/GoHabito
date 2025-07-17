package com.nishant.gohabito

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MissionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MissionWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_mission)

            // Load progress data from SharedPreferences
            val prefs = context.getSharedPreferences("MissionPrefs", Context.MODE_PRIVATE)
            val missionSet = prefs.getStringSet("missions", emptySet()) ?: emptySet()
            val total = missionSet.size
            val completed = missionSet.count {
                val parts = it.split("##")
                parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
            }

            views.setTextViewText(R.id.missionHeading, "Today's Mission")
            views.setTextViewText(R.id.widgetProgressText, "Progress: $completed/$total")

            // Notify before setting adapter to force refresh
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.missionListView)

            // Set RemoteAdapter (with unique intent per widget to prevent caching)
            val intent = Intent(context, MissionWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = android.net.Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME)) // Ensures uniqueness
            }
            views.setRemoteAdapter(R.id.missionListView, intent)

            // Open MainActivity on widget tap
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
