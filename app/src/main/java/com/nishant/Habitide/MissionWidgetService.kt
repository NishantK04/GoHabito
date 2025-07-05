package com.nishant.Habitide

import android.content.Intent
import android.widget.RemoteViewsService
import android.util.Log

class MissionWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d("MissionWidgetService", "Creating RemoteViewsFactory")
        return MissionRemoteViewsFactory(applicationContext)
    }
}
