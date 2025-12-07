package com.oasth.widget.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.runBlocking

/**
 * Service that provides RemoteViews for the widget ListView
 */
class BusRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d(TAG, "onGetViewFactory called")
        return BusRemoteViewsFactory(applicationContext, intent)
    }
    
    companion object {
        private const val TAG = "BusRemoteViewsService"
    }
}

/**
 * Factory that creates RemoteViews for each bus arrival in the list
 */
class BusRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {
    
    companion object {
        private const val TAG = "BusRemoteViewsFactory"
    }
    
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    
    private val arrivals = mutableListOf<BusArrival>()
    private val sessionManager = SessionManager(context)
    private val api = OasthApi(sessionManager)
    private val configRepo = WidgetConfigRepository(context)
    
    override fun onCreate() {
        Log.d(TAG, "onCreate for widget $appWidgetId")
    }
    
    override fun onDataSetChanged() {
        Log.d(TAG, "=== onDataSetChanged START ===")
        
        arrivals.clear()
        
        val config = configRepo.getConfig(appWidgetId)
        if (config == null) {
            Log.w(TAG, "No config found for widget $appWidgetId")
            return
        }
        
        Log.d(TAG, "Fetching arrivals for stop: ${config.stopCode}")
        
        try {
            val result = runBlocking {
                api.getArrivals(config.stopCode)
            }
            
            Log.d(TAG, "Got ${result.size} arrivals")
            arrivals.addAll(result.sortedBy { it.estimatedMinutes })
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching arrivals: ${e.message}", e)
        }
        
        Log.d(TAG, "=== onDataSetChanged END (${arrivals.size} items) ===")
    }
    
    override fun onDestroy() {
        arrivals.clear()
    }
    
    override fun getCount(): Int = arrivals.size
    
    override fun getViewAt(position: Int): RemoteViews? {
        if (position >= arrivals.size) return null
        
        val arrival = arrivals[position]
        
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            // Show line ID and arrival time
            setTextViewText(R.id.item_line, arrival.lineId)
            setTextViewText(R.id.item_time, "${arrival.estimatedMinutes}'")
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = false
}
