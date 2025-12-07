package com.oasth.widget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.oasth.widget.R
import com.oasth.widget.data.WidgetConfigRepository

class BusWidgetProvider : AppWidgetProvider() {


    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widgets: ${appWidgetIds.toList()}")
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted: ${appWidgetIds.toList()}")
        val configRepo = WidgetConfigRepository(context)
        appWidgetIds.forEach { configRepo.deleteConfig(it) }
    }

    companion object {
        private const val TAG = "BusWidgetProvider"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget: $appWidgetId")
            
            val configRepo = WidgetConfigRepository(context)
            val config = configRepo.getConfig(appWidgetId)
            
            if (config == null) {
                Log.w(TAG, "No config for widget $appWidgetId")
                val views = RemoteViews(context.packageName, R.layout.widget_bus)
                views.setTextViewText(R.id.widget_stop_name, "Tap to configure")
                views.setTextViewText(R.id.widget_empty, "Tap to configure")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }
            
            Log.d(TAG, "Config: stopCode=${config.stopCode}, name=${config.stopName}")
            
            // Set up RemoteViews with adapter
            val intent = Intent(context, BusRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            
            val views = RemoteViews(context.packageName, R.layout.widget_bus)
            views.setTextViewText(R.id.widget_stop_name, config.stopName)
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            
            // Refresh button
            val refreshIntent = Intent(context, BusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // IMPORTANT: Trigger data refresh!
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            Log.d(TAG, "notifyAppWidgetViewDataChanged called")
        }
    }
}
