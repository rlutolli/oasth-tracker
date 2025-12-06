package com.example.oasthwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class BusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val intent = Intent(context, BusRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_bus)
            views.setRemoteAdapter(R.id.bus_list, intent)
            views.setEmptyView(R.id.bus_list, R.id.empty_view)
            
            // PendingIntent for Refresh Button
            val refreshIntent = Intent(context, BusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 
                appWidgetId, 
                refreshIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.bus_list)
        }
    }
}
