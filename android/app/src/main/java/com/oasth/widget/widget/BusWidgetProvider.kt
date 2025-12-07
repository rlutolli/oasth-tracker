package com.oasth.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.WidgetConfigRepository
import com.oasth.widget.ui.MainActivity
import java.util.concurrent.Executors

/**
 * Home screen widget provider for bus arrivals
 */
class BusWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "BusWidgetProvider"
        const val ACTION_REFRESH = "com.oasth.widget.ACTION_REFRESH"
        private const val MAX_ARRIVALS = 4
        
        private val executor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (widgetId in appWidgetIds) {
            updateWidgetBackground(context.applicationContext, appWidgetManager, widgetId)
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
                updateWidgetBackground(context.applicationContext, manager, widgetId)
            }
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val configRepo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            configRepo.deleteConfig(widgetId)
        }
    }
    
    private fun updateWidgetBackground(
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
        
        // Show loading state immediately on main thread
        showLoading(context, appWidgetManager, widgetId, config.stopName)
        
        // Fetch data in background thread
        executor.execute {
            try {
                Log.d(TAG, "Fetching arrivals for ${config.stopCode}")
                
                // Simple synchronous HTTP request
                val arrivals = fetchArrivalsSync(context, config.stopCode)
                
                Log.d(TAG, "Got ${arrivals.size} arrivals")
                
                // Update widget on main thread
                mainHandler.post {
                    showArrivals(context, appWidgetManager, widgetId, config.stopName, config.stopCode, arrivals)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching arrivals", e)
                mainHandler.post {
                    showError(context, appWidgetManager, widgetId, config.stopName, config.stopCode, e.message)
                }
            }
        }
    }
    
    private fun fetchArrivalsSync(context: Context, stopCode: String): List<BusArrival> {
        // Get cached session or use static token
        val prefs = context.getSharedPreferences("oasth_session", Context.MODE_PRIVATE)
        val phpSessionId = prefs.getString("php_session_id", null)
        val token = prefs.getString("token", "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137")
        
        if (phpSessionId == null) {
            Log.d(TAG, "No session cached, returning empty")
            return emptyList()
        }
        
        val url = java.net.URL("https://telematics.oasth.gr/api/?act=getStopArrivals&p1=$stopCode")
        val connection = url.openConnection() as java.net.HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-CSRF-Token", token)
            connection.setRequestProperty("Cookie", "PHPSESSID=$phpSessionId")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Response: ${response.take(200)}")
                return parseArrivals(response)
            }
        } finally {
            connection.disconnect()
        }
        
        return emptyList()
    }
    
    private fun parseArrivals(json: String): List<BusArrival> {
        val arrivals = mutableListOf<BusArrival>()
        
        try {
            // Simple JSON parsing - look for bline_id and btime2
            val pattern = """"bline_id"\s*:\s*"([^"]+)".*?"btime2"\s*:\s*(\d+)""".toRegex()
            
            pattern.findAll(json).forEach { match ->
                val lineId = match.groupValues[1]
                val mins = match.groupValues[2].toIntOrNull() ?: 0
                arrivals.add(BusArrival(
                    lineId = lineId,
                    lineDescr = lineId,
                    routeCode = "",
                    vehicleCode = "",
                    estimatedMinutes = mins
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
        }
        
        return arrivals
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
        views.setTextViewText(R.id.arrivals_text, "Tap to retry")
        
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
