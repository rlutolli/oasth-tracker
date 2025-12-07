package com.oasth.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.oasth.widget.R
import com.oasth.widget.data.WidgetConfigRepository
import com.oasth.widget.ui.MainActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Home screen widget provider for bus arrivals with time-based messages
 */
class BusWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_REFRESH = "com.oasth.widget.ACTION_REFRESH"
        private val executor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
        
        private const val STATIC_TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val manager = AppWidgetManager.getInstance(context)
                updateWidget(context, manager, widgetId)
            }
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val configRepo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            configRepo.deleteConfig(widgetId)
        }
    }
    
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val configRepo = WidgetConfigRepository(context)
        val config = configRepo.getConfig(widgetId)
        
        if (config == null) {
            showNotConfigured(context, appWidgetManager, widgetId)
            return
        }
        
        showLoading(context, appWidgetManager, widgetId, config.stopName)
        
        executor.execute {
            try {
                val arrivals = fetchArrivals(context, config.stopCode)
                mainHandler.post {
                    showArrivals(context, appWidgetManager, widgetId, config.stopName, arrivals)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showError(context, appWidgetManager, widgetId, config.stopName)
                }
            }
        }
    }
    
    private fun fetchArrivals(context: Context, stopCode: String): List<BusArrivalSimple> {
        val prefs = context.getSharedPreferences("oasth_session", Context.MODE_PRIVATE)
        val phpSessionId = prefs.getString("php_session_id", null)
        val token = prefs.getString("token", STATIC_TOKEN) ?: STATIC_TOKEN
        
        if (phpSessionId == null) {
            return emptyList()
        }
        
        val url = URL("https://telematics.oasth.gr/api/?act=getStopArrivals&p1=$stopCode")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-CSRF-Token", token)
            connection.setRequestProperty("Cookie", "PHPSESSID=$phpSessionId")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                return parseArrivals(response)
            }
        } finally {
            connection.disconnect()
        }
        
        return emptyList()
    }
    
    private fun parseArrivals(json: String): List<BusArrivalSimple> {
        val arrivals = mutableListOf<BusArrivalSimple>()
        
        val pattern = """"bline_id"\s*:\s*"([^"]+)"[^}]*"btime2"\s*:\s*(\d+)""".toRegex()
        
        pattern.findAll(json).forEach { match ->
            val lineId = match.groupValues[1]
            val mins = match.groupValues[2].toIntOrNull() ?: 0
            arrivals.add(BusArrivalSimple(lineId, mins))
        }
        
        return arrivals.sortedBy { it.minutes }
    }
    
    private fun getTimeBasedMessage(context: Context): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when {
            hour in 0..4 -> context.getString(R.string.no_buses_night)
            hour in 5..5 -> context.getString(R.string.no_buses_early)
            else -> context.getString(R.string.no_buses)
        }
    }
    
    private fun getCurrentTimeString(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
    
    private fun showNotConfigured(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, context.getString(R.string.tap_to_configure))
        views.setTextViewText(R.id.arrivals_text, "")
        views.setTextViewText(R.id.refresh_hint, "")
        
        val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        views.setTextViewText(R.id.refresh_hint, "")
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
    
    private fun showArrivals(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        stopName: String,
        arrivals: List<BusArrivalSimple>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, stopName)
        
        val arrivalsText = if (arrivals.isEmpty()) {
            getTimeBasedMessage(context)
        } else {
            arrivals
                .groupBy { it.lineId }
                .entries
                .take(4)
                .joinToString("\n") { (line, buses) ->
                    val times = buses.take(2).joinToString(", ") { "${it.minutes}'" }
                    "$line  →  $times"
                }
        }
        
        views.setTextViewText(R.id.arrivals_text, arrivalsText)
        views.setTextViewText(R.id.refresh_hint, "↻ ${getCurrentTimeString()}")
        
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
    
    private fun showError(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        stopName: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.stop_name, stopName)
        views.setTextViewText(R.id.arrivals_text, context.getString(R.string.error_loading))
        views.setTextViewText(R.id.refresh_hint, "")
        
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
    
    data class BusArrivalSimple(val lineId: String, val minutes: Int)
}
