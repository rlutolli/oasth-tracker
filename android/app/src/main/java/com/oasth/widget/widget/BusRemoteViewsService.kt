package com.oasth.widget.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.StopRepository
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.runBlocking

/**
 * Service that provides RemoteViews for the widget ListView
 */
class BusRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return BusRemoteViewsFactory(applicationContext, intent)
    }
}

/**
 * Sealed class to represent different row types in the widget list
 */
sealed class WidgetItem {
    data class Header(val stopName: String) : WidgetItem()
    data class Row(val lineId: String, val times: String) : WidgetItem()
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
    
    // Using simple list of WidgetItem (Header or Row)
    private val items = mutableListOf<WidgetItem>()
    
    private val sessionManager = SessionManager(context)
    private val api = OasthApi(sessionManager)
    private val configRepo = WidgetConfigRepository(context)
    private val stopRepo = StopRepository(context)
    
    override fun onCreate() {
        Log.d(TAG, "onCreate for widget $appWidgetId")
    }
    
    override fun onDataSetChanged() {
        Log.d(TAG, "=== onDataSetChanged START ===")
        
        items.clear()
        
        val config = configRepo.getConfig(appWidgetId)
        if (config == null) {
            return
        }
        
        // 1. Parse Stops (Comma separated Street IDs)
        val streetIds = config.stopCode.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Log.d(TAG, "Config: rawCode='${config.stopCode}', parsedIds=$streetIds")
        
        // 2. Parse Filters (e.g. "1403:01N,05; 1024:10")
        val filterMap = mutableMapOf<String, Set<String>>()
        if (config.lineFilters.isNotEmpty()) {
            val rules = config.lineFilters.split(";")
            for (rule in rules) {
                if (rule.contains(":")) {
                    val parts = rule.split(":")
                    if (parts.size == 2) {
                        val sId = parts[0].trim()
                        val lines = parts[1].split(",").map { it.trim() }.toSet()
                        filterMap[sId] = lines
                    }
                }
            }
        }
        Log.d(TAG, "Filters: raw='${config.lineFilters}', map=$filterMap")
        
        // 3. Update Loop
        runBlocking {
            for (streetId in streetIds) {
                try {
                    Log.d(TAG, "Processing StreetID: '$streetId'")
                    
                    // Resolve ID and Name locally
                    val apiId = stopRepo.getApiId(streetId)
                    val stopName = stopRepo.getStopName(streetId) ?: "Stop $streetId"
                    
                    // Fetch Arrivals and deduplicate by Vehicle Code
                    val arrivals = api.getArrivals(apiId).distinctBy { 
                        if (it.vehicleCode.isNotBlank()) it.vehicleCode else it.hashCode() 
                    }
                    Log.d(TAG, "Fetched ${arrivals.size} arrivals for $streetId (API: $apiId)")
                    
                    // Filter
                    val validArrivals = if (filterMap.containsKey(streetId)) {
                        val allowedLines = filterMap[streetId]!!
                        val filtered = arrivals.filter { it.displayLine in allowedLines }
                        Log.d(TAG, "Filtered $streetId: kept ${filtered.size} of ${arrivals.size}. Allowed: $allowedLines")
                        filtered
                    } else {
                        arrivals
                    }
                    
                    if (validArrivals.isNotEmpty()) {
                        // Header with Stop Code
                        items.add(WidgetItem.Header("$stopName ($streetId)"))
                        
                        // Group by Line
                        val grouped = validArrivals.groupBy { it.displayLine }
                        
                        // Sort Line Groups by their NEAREST arrival time
                        val sortedLines = grouped.keys.sortedBy { line ->
                            grouped[line]?.minOfOrNull { it.estimatedMinutes } ?: Int.MAX_VALUE
                        }
                        
                        for (line in sortedLines) {
                            val lineArrivals = grouped[line] ?: emptyList()
                            val sortedArrivals = lineArrivals.sortedBy { it.estimatedMinutes }
                            
                            val timeString = sortedArrivals.joinToString(", ") { "${it.estimatedMinutes}'" }
                            items.add(WidgetItem.Row(line, timeString))
                        }
                    } else {
                        // User request: just "same line vertically".
                        // If no buses, let's show one header and "No buses" row so user knows it loaded
                         items.add(WidgetItem.Header("$stopName ($streetId)"))
                         items.add(WidgetItem.Row("", "No buses"))
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching for $streetId: ${e.message}")
                    items.add(WidgetItem.Header("Stop $streetId"))
                    items.add(WidgetItem.Row("Err", "Load failed"))
                }
            }
        }
        
        Log.d(TAG, "=== onDataSetChanged END (${items.size} items) ===")
    }
    
    override fun onDestroy() {
        items.clear()
    }
    
    override fun getCount(): Int {
        return items.size
    }
    
    override fun getViewAt(position: Int): RemoteViews? {
        if (position >= items.size) return null
        
        return when (val item = items[position]) {
            is WidgetItem.Header -> {
                RemoteViews(context.packageName, R.layout.widget_header).apply {
                    setTextViewText(R.id.header_text, item.stopName)
                    setOnClickFillInIntent(R.id.header_root, Intent())
                }
            }
            is WidgetItem.Row -> {
                RemoteViews(context.packageName, R.layout.widget_item).apply {
                    if (item.lineId.isEmpty()) {
                        // "No buses" row, hide line number
                        setViewVisibility(R.id.item_line, View.GONE)
                    } else {
                        setViewVisibility(R.id.item_line, View.VISIBLE)
                        setTextViewText(R.id.item_line, item.lineId)
                    }
                    
                    setTextViewText(R.id.item_destination, item.times) // Showing times in destination field
                    setViewVisibility(R.id.item_time, View.GONE)       // Hide original time field
                    
                    setOnClickFillInIntent(R.id.item_root, Intent())
                }
            }
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 2
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = false
}
