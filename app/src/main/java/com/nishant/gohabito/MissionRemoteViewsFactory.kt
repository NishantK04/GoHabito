package com.nishant.gohabito

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class MissionRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val missions = mutableListOf<MissionHabit>()

    override fun onCreate() {
        // No-op, used only if any resources need initialization
    }

    override fun onDataSetChanged() {
        // Load from SharedPreferences
        val prefs = context.applicationContext.getSharedPreferences("MissionPrefs", Context.MODE_PRIVATE)
        val missionSet = prefs.getStringSet("missions", emptySet()) ?: emptySet()

        missions.clear()

        missionSet.forEach { entry ->
            val parts = entry.split("##")
            if (parts.size == 3) {
                val name = parts[0]
                val title = parts[1]
                val checked = parts[2].toBooleanStrictOrNull() ?: false
                missions.add(MissionHabit(name, title, checked))
            }
        }
    }

    override fun getCount(): Int = missions.size

    override fun getViewAt(position: Int): RemoteViews {
        val habit = missions[position]
        val views = RemoteViews(context.packageName, R.layout.item_mission_widget)

        // Left: Static mission icon
        views.setImageViewResource(R.id.missionIcon, R.drawable.habitlogo)

        // Middle: Mission name
        views.setTextViewText(R.id.itemText, habit.name)

        // Right: Show/hide completion icon
        if (habit.checked) {
            views.setViewVisibility(R.id.completedIcon, View.VISIBLE)
            views.setImageViewResource(R.id.completedIcon, R.drawable.check)
        } else {
            views.setViewVisibility(R.id.completedIcon, View.GONE)
        }

        return views
    }


    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        missions.clear()
    }
}
