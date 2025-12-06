package com.example.oasthwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class BusRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return BusRemoteViewsFactory(this.applicationContext, intent)
    }
}

class BusRemoteViewsFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private val appWidgetId: Int = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    private val items = mutableListOf<BusArrival>()
    private val apiClient = OasthApiClient()

    override fun onCreate() {
        // Initialize nothing
    }

    override fun onDataSetChanged() {
        // This is called when we refresh the widget
        items.clear()
        
        // Load configured stops from SharedPrefs
        // Format: "CODE:ID,CODE:ID"
        val prefs = context.getSharedPreferences("OasthWidgetPrefs", Context.MODE_PRIVATE)
        val configString = prefs.getString("stops_$appWidgetId", null)
        
        if (configString.isNullOrEmpty()) {
            // Default Fallback (Code 16008 -> ID 3344) if not configured
            // This ensures existing widgets still work
            fetchForStop("16008", 3344, "")
            return
        }
        
        val stops = configString.split(",")
        for (stopEntry in stops) {
            val parts = stopEntry.split(":")
            if (parts.size >= 2) {
                val code = parts[0]
                val id = parts[1].toIntOrNull()
                val filter = if (parts.size >= 3) parts[2] else ""
                
                if (id != null) {
                    fetchForStop(code, id, filter)
                }
            }
        }
    }
    
    private fun fetchForStop(code: String, id: Int, filter: String) {
        val arrivals = apiClient.getStopArrivals(id, code)
        
        // Prepare list of valid items (filtered or all)
        val finalItems = if (arrivals != null) {
            val list = if (filter.isNotEmpty()) {
                arrivals.filter { it.routeCode == filter }
            } else {
                arrivals
            }
            // Deduplicate by Vehicle Code to prevent "ghost" duplicates from API
            // But keep items with no vehicle code just in case
            list.distinctBy { if (it.vehicleCode.length > 1) it.vehicleCode else it.toString() + Math.random() }
        } else {
            emptyList()
        }

        if (finalItems.isNotEmpty()) {
            items.addAll(finalItems)
        } else {
            // Add Placeholder if no buses found
            items.add(BusArrival(
                minutes = -1, // -1 indicates "No Data" / "Placeholder"
                routeCode = if (filter.isNotEmpty()) filter else "All",
                vehicleCode = "-",
                stopCode = code
            ))
        }
    }

    override fun onDestroy() {
        items.clear()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_item) 
        
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        
        views.setTextViewText(R.id.widget_item_stop, item.stopCode)
        
        if (item.minutes < 0) {
            // Placeholder State
            if (item.routeCode == "All") {
                 views.setTextViewText(R.id.widget_item_route, "No Buses")
            } else {
                 views.setTextViewText(R.id.widget_item_route, "Line ${item.routeCode}")
            }
            views.setTextViewText(R.id.widget_item_time, "-")
            views.setTextColor(R.id.widget_item_time, android.graphics.Color.LTGRAY)
        } else {
            // Normal State
            views.setTextViewText(R.id.widget_item_route, "Line ${item.routeCode}")
            views.setTextViewText(R.id.widget_item_time, "${item.minutes}'")
            views.setTextColor(R.id.widget_item_time, android.graphics.Color.parseColor("#00BD37"))
        }
        
        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false // Items change often
    }
}
