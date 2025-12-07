package com.oasth.widget.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.res.ResourcesCompat
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.LineRepository
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.StopRepository
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
    
    private fun textAsBitmap(text: String, sizeSp: Float, color: Int, maxWidthDp: Int? = null): Bitmap {
        val paint = Paint() // No ANTI_ALIAS_FLAG for sharp pixels
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.textSize = sizeSp * context.resources.displayMetrics.scaledDensity
        paint.color = color
        paint.typeface = customTypeface ?: Typeface.MONOSPACE
        
        // Measure text
        var textWidth = paint.measureText(text)
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val textHeight = textBounds.height()
        
        // Scale down if maxWidth is provided and text is too wide (e.g. "01M")
        if (maxWidthDp != null) {
            val validWidthPx = maxWidthDp * context.resources.displayMetrics.density
            if (textWidth > validWidthPx) {
                val scale = validWidthPx / textWidth
                paint.textSize *= scale
                textWidth = paint.measureText(text) // Re-measure
            }
        }
        
        // Add some padding to height to avoid cutting off descenders
        val height = (paint.textSize * 1.2).toInt()
        val width = textWidth.toInt().coerceAtLeast(1)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw text centered vertically-ish (baseline)
        val baseline = height - (height - textHeight) / 2f - paint.descent() / 2f
        canvas.drawText(text, 0f, baseline + paint.textSize * 0.2f, paint) // Adjust baseline slightly
        
        return bitmap
    }
    
    override fun onDataSetChanged() {
        Log.d(TAG, "=== onDataSetChanged START ===")
        
        arrivals.clear()
        
        val config = configRepo.getConfig(appWidgetId)
        if (config == null) {
            Log.w(TAG, "No config found for widget $appWidgetId")
            return
        }
        
        // Convert Street ID to API ID using StopRepository
        val apiId = stopRepo.getApiId(config.stopCode)
        Log.d(TAG, "Fetching arrivals for stop: ${config.stopCode} -> API ID: $apiId")
        
        try {
            val result = runBlocking {
                api.getArrivals(apiId)
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
            val color = 0xFFFFAA00.toInt()
            
            // Line number (e.g., "14A", "31", "02K")
            // Pass maxWidthDp=44 to ensure it fits (scales down if needed)
            setImageViewBitmap(R.id.img_line, textAsBitmap(arrival.displayLine, 24f, color, 44))
            
            // Destination
            var destination = arrival.lineDescr
            if (destination.isEmpty()) {
                destination = lineRepo.getLineDescription(arrival.displayLine) ?: ""
            }
            setImageViewBitmap(R.id.img_destination, textAsBitmap(destination, 22f, color))
            
            // Sigma
            setImageViewBitmap(R.id.img_sigma, textAsBitmap("Σ", 22f, color))
            
            // Time
            val timeText = when {
                arrival.estimatedMinutes <= 0 -> "NOW"
                else -> "${arrival.estimatedMinutes}'"
            }
            setImageViewBitmap(R.id.img_time, textAsBitmap(timeText, 22f, color))
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = false
}
