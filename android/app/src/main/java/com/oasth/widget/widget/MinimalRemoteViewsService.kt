package com.oasth.widget.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.core.content.res.ResourcesCompat
import com.oasth.widget.R
import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.StopRepository
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.runBlocking

/**
 * Service for the Minimal widget's ListView
 */
class MinimalRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d(TAG, "onGetViewFactory called")
        return MinimalRemoteViewsFactory(applicationContext, intent)
    }
    
    companion object {
        private const val TAG = "MinimalRemoteViewsSvc"
    }
}

/**
 * Factory for the minimal widget - compact with urgency colors
 * Implements Panos's color-coding idea: red < 5min (urgent), green >= 5min (safe)
 */
class MinimalRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {
    
    companion object {
        private const val TAG = "MinimalViewsFactory"
        
        // Urgency colors from Panos's code
        private const val COLOR_URGENT = 0xFFFF5555.toInt()  // Neon Red (< 5 min)
        private const val COLOR_SAFE = 0xFF55FF55.toInt()    // Neon Green (>= 5 min)
        private const val COLOR_AMBER = 0xFFFFAA00.toInt()   // Amber (line number)
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
    
    private var customTypeface: Typeface? = null
    
    override fun onCreate() {
        Log.d(TAG, "onCreate for widget $appWidgetId")
        try {
            customTypeface = ResourcesCompat.getFont(context, R.font.vt323_regular)
        } catch (e: Exception) {
            Log.e(TAG, "Could not load font: ${e.message}")
        }
    }
    
    private fun textAsBitmap(
        text: CharSequence, 
        sizeSp: Float, 
        color: Int
    ): Bitmap {
        val paint = android.text.TextPaint()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.textSize = sizeSp * context.resources.displayMetrics.scaledDensity
        paint.color = color
        paint.typeface = customTypeface ?: Typeface.MONOSPACE

        val widthPx = android.text.Layout.getDesiredWidth(text, paint).toInt() + 16
        
        val builder = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
        
        val layout = builder.build()
        val height = layout.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layout.draw(canvas)

        return bitmap
    }
    
    /**
     * Get color based on urgency - idea from Panos's KDE widget
     * Red if < 5 minutes (need to hurry!), Green if >= 5 minutes (safe)
     */
    private fun getUrgencyColor(minutes: Int): Int {
        return if (minutes < 5) COLOR_URGENT else COLOR_SAFE
    }
    
    override fun onDataSetChanged() {
        Log.d(TAG, "=== onDataSetChanged START ===")
        
        arrivals.clear()
        
        val config = configRepo.getConfig(appWidgetId)
        if (config == null) {
            Log.w(TAG, "No config found for widget $appWidgetId")
            return
        }
        
        val apiId = stopRepo.getApiId(config.stopCode)
        Log.d(TAG, "Fetching arrivals for stop: ${config.stopCode} -> API ID: $apiId")
        
        // Get allowed lines filter (null = show all)
        val allowedLines = config.getAllowedLines()
        if (allowedLines != null) {
            Log.d(TAG, "Line filter active: $allowedLines")
        }
        
        try {
            val result = runBlocking {
                api.getArrivals(apiId)
            }
            
            Log.d(TAG, "Got ${result.size} arrivals from API")
            
            // Apply line filter if set
            val filtered = if (allowedLines != null) {
                result.filter { arrival ->
                    allowedLines.contains(arrival.displayLine.uppercase())
                }
            } else {
                result
            }
            
            Log.d(TAG, "After filtering: ${filtered.size} arrivals")
            arrivals.addAll(filtered.sortedBy { it.estimatedMinutes })
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
        
        return RemoteViews(context.packageName, R.layout.widget_minimal_item).apply {
            // Line number in amber
            setImageViewBitmap(
                R.id.img_line, 
                textAsBitmap(arrival.displayLine, 20f, COLOR_AMBER)
            )
            
            // Time with urgency color (Panos's idea!)
            val timeColor = getUrgencyColor(arrival.estimatedMinutes)
            val minText = when {
                arrival.estimatedMinutes <= 0 -> "NOW"
                else -> "${arrival.estimatedMinutes}'"
            }
            
            setImageViewBitmap(
                R.id.img_time, 
                textAsBitmap(minText, 20f, timeColor)
            )
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = false
}
