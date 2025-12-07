package com.oasth.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.oasth.widget.R
import com.oasth.widget.data.StopRepository
import com.oasth.widget.data.WidgetConfigRepository

/**
 * Home screen widget provider with ListView for bus arrivals
 */
class BusWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "BusWidgetProvider"
        const val ACTION_REFRESH = "com.oasth.widget.ACTION_REFRESH"
        
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget: $appWidgetId")
            
            val configRepo = WidgetConfigRepository(context)
            val config = configRepo.getConfig(appWidgetId)
            
            // Layout is widget_bus
            val views = RemoteViews(context.packageName, R.layout.widget_bus)
            
            if (config == null) {
                Log.w(TAG, "No config for widget $appWidgetId")
                // Show "Loading..." or "Tap to Configure" in the empty view
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.tap_to_configure))
                views.setViewVisibility(R.id.widget_list, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                
                // Tap to configure
                val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }
            
            Log.d(TAG, "Config loaded for widget $appWidgetId")
            
            // Set up RemoteViews with adapter for ListView
            val intent = Intent(context, BusRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.loading))
            
            // Refresh button
            val refreshIntent = Intent(context, BusWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)
            
            // Allow tapping header to config (optional, kept for UX)
            // But we don't have a specific header ID anymore except title row. 
            // Let's bind config intent to title
            val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, configPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // Trigger data refresh
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            Log.d(TAG, "notifyAppWidgetViewDataChanged called")
        }
    }
    
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
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        Log.d(TAG, "onReceive: ${intent.action}")
        
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val manager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, manager, widgetId)
            }
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted: ${appWidgetIds.toList()}")
        val configRepo = WidgetConfigRepository(context)
        appWidgetIds.forEach { configRepo.deleteConfig(it) }
    }
}
