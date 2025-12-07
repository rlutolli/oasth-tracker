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

class BusRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d("BusRemoteViewsService", "onGetViewFactory called")
        return BusRemoteViewsFactory(applicationContext, intent)
    }
}

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
        Log.d(TAG, "Widget ID: $appWidgetId")
        
        arrivals.clear()
        
        val config = configRepo.getConfig(appWidgetId)
        if (config == null) {
            Log.w(TAG, "No config found for widget $appWidgetId")
            return
        }
        
        Log.d(TAG, "Fetching arrivals for stop: ${config.stopCode}")
        
        // IMPORTANT: Use runBlocking because this runs in a binder thread
        try {
            val result = runBlocking {
                api.getArrivals(config.stopCode)
            }
            
            Log.d(TAG, "Got ${result.size} arrivals from API")
            result.forEach { arr ->
                Log.d(TAG, "   → Line ${arr.routeCode}: ${arr.estimatedMinutes} min")
            }
            
            arrivals.addAll(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching arrivals: ${e.message}", e)
        }
        
        Log.d(TAG, "=== onDataSetChanged END (${arrivals.size} items) ===")
    }
    
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        arrivals.clear()
    }
    
    override fun getCount(): Int {
        Log.d(TAG, "getCount: ${arrivals.size}")
        return arrivals.size
    }
    
    override fun getViewAt(position: Int): RemoteViews? {
        Log.d(TAG, "getViewAt($position)")
        
        if (position >= arrivals.size) {
            return null
        }
        
        val arrival = arrivals[position]
        
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            setTextViewText(R.id.item_line, "Line ${arrival.routeCode}")
            setTextViewText(R.id.item_time, "${arrival.estimatedMinutes}'")
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = false
}
