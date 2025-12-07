package com.oasth.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.WidgetConfigRepository
import com.oasth.widget.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen widget provider for bus arrivals
 */
class BusWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "BusWidgetProvider"
        const val ACTION_REFRESH = "com.oasth.widget.ACTION_REFRESH"
        private const val MAX_ARRIVALS = 4
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (widgetId in appWidgetIds) {
            updateWidgetAsync(context, appWidgetManager, widgetId)
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
                updateWidgetAsync(context, manager, widgetId)
            }
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val configRepo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            configRepo.deleteConfig(widgetId)
        }
    }
    
    private fun updateWidgetAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val configRepo = WidgetConfigRepository(context)
        val config = configRepo.getConfig(widgetId)
        
        if (config == null) {
            Log.d(TAG, "Widget $widgetId not configured")
            showNotConfigured(context, appWidgetManager, widgetId)
            return
        }
        
        Log.d(TAG, "Updating widget $widgetId for stop ${config.stopCode}")
        
        // Show loading state immediately
        showLoading(context, appWidgetManager, widgetId, config.stopName)
        
        // Use goAsync() to allow background work in BroadcastReceiver
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching arrivals for ${config.stopCode}")
                val sessionManager = SessionManager(context.applicationContext)
                val api = OasthApi(sessionManager)
                val arrivals = api.getArrivals(config.stopCode)
                
                Log.d(TAG, "Got ${arrivals.size} arrivals")
                
                withContext(Dispatchers.Main) {
                    showArrivals(context, appWidgetManager, widgetId, config.stopName, config.stopCode, arrivals)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching arrivals", e)
                withContext(Dispatchers.Main) {
                    showError(context, appWidgetManager, widgetId, config.stopName, config.stopCode, e.message)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun showNotConfigured(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, context.getString(R.string.tap_to_configure))
        views.setTextViewText(R.id.arrivals_text, "")
        
        // Click to open config
        val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, widgetId, configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
    
    private fun showLoading(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        stopName: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, stopName)
        views.setTextViewText(R.id.arrivals_text, context.getString(R.string.loading))
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
    
    private fun showArrivals(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        stopName: String,
        stopCode: String,
        arrivals: List<BusArrival>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, stopName)
        
        val arrivalsText = if (arrivals.isEmpty()) {
            context.getString(R.string.no_buses)
        } else {
            arrivals.take(MAX_ARRIVALS)
                .groupBy { it.lineId }
                .map { (lineId, buses) ->
                    val times = buses.sortedBy { it.estimatedMinutes }
                        .take(2)
                        .joinToString(", ") { "${it.estimatedMinutes}'" }
                    "$lineId: $times"
                }
                .joinToString("\n")
        }
        
        views.setTextViewText(R.id.arrivals_text, arrivalsText)
        
        // Tap opens main app
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("stopCode", stopCode)
            putExtra("stopName", stopName)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context, widgetId, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, mainPendingIntent)
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
    
    private fun showError(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        stopName: String,
        stopCode: String,
        errorMessage: String?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, stopName)
        views.setTextViewText(R.id.arrivals_text, "⚠ ${errorMessage ?: "Tap to retry"}")
        
        // Tap to refresh
        val refreshIntent = Intent(context, BusWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, widgetId, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
