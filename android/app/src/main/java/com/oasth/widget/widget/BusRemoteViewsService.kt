package com.oasth.widget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.res.ResourcesCompat
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.LineRepository
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

    companion object {
        private const val TAG = "BusRemoteViewsService"
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
    private val lineRepo = LineRepository(context)

    private var customTypeface: Typeface? = null

    override fun onCreate() {
        Log.d(TAG, "onCreate for widget $appWidgetId")
        try {
            // Load VT323 font from res/font using ResourcesCompat
            customTypeface = ResourcesCompat.getFont(context, R.font.vt323_regular)
        } catch (e: Exception) {
            Log.e(TAG, "Could not load font: ${e.message}")
        }
    }

    private fun textAsBitmap(
        text: CharSequence,
        sizeSp: Float,
        color: Int,
        maxWidthDp: Int? = null,
        alignment: android.text.Layout.Alignment = android.text.Layout.Alignment.ALIGN_CENTER
    ): Bitmap {
        val paint = android.text.TextPaint()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.textSize = sizeSp * context.resources.displayMetrics.scaledDensity
        paint.color = color
        paint.typeface = customTypeface ?: Typeface.MONOSPACE

        // Determine available width
        val widthPx = if (maxWidthDp != null) {
            (maxWidthDp * context.resources.displayMetrics.density).toInt()
        } else {
            // If no max width, measure text.
            // StaticLayout handles CharSequence.
            android.text.Layout.getDesiredWidth(text, paint).toInt() + 20
        }

        val spacingMult = 1f
        val spacingAdd = 0f
        val includePad = false

        val builder = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(alignment)
            .setLineSpacing(spacingAdd, spacingMult)
            .setIncludePad(includePad)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)

        val layout = builder.build()

        // Calculate dimensions
        val height = layout.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw
        layout.draw(canvas)

        return bitmap
    }
    
    override fun onDataSetChanged() {
        Log.d(TAG, "=== onDataSetChanged START ===")
        Log.d(TAG, "Widget ID: $appWidgetId")
        
        items.clear()
        
        val config = configRepo.getConfig(appWidgetId) ?: return

        // 1. Get Smart Config
        val stopItems = configRepo.getSmartConfig(appWidgetId)

        // 2. Loop through config items
        runBlocking {
            for (item in stopItems) {
                val streetId = item.streetId

                // Check Network Availability
                if (!com.oasth.widget.utils.NetworkUtils.isNetworkAvailable(context)) {
                    Log.e(TAG, "No network connection for $streetId")
                    items.add(WidgetItem.Header(item.stopName))
                    items.add(WidgetItem.Row("", "No Internet"))
                    continue
                }

                try {
                    Log.d(TAG, "Processing StreetID: '$streetId'")

                    val apiId = stopRepo.getApiId(streetId)

                    // Fetch Arrivals with Timeout
                    val result = kotlinx.coroutines.withTimeout(15000L) {
                        api.getArrivals(apiId)
                    }

                    // Deduplicate
                    val unique = result.distinctBy {
                        if (it.vehicleCode.isNotBlank()) it.vehicleCode else it.hashCode()
                    }

                    // Filter: Use item.selectedLines
                    val allowedLines = item.selectedLines.toSet()
                    val validArrivals = unique.filter {
                        allowedLines.isEmpty() || allowedLines.contains(it.displayLine)
                    }

                    if (validArrivals.isNotEmpty()) {
                        // Header
                        items.add(WidgetItem.Header("${item.stopName} ($streetId)"))

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
                        items.add(WidgetItem.Header("${item.stopName} ($streetId)"))
                        items.add(WidgetItem.Row("", "No buses"))
                    }

                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Timeout fetching $streetId")
                    items.add(WidgetItem.Header(item.stopName))
                    items.add(WidgetItem.Row("Err", "Timeout"))
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching for $streetId: ${e.message}")
                    items.add(WidgetItem.Header(item.stopName))
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
