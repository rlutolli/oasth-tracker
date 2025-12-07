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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
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
             // Note: paint.measureText(String) works, but for CharSequence we need to be careful?
             // StaticLayout handles CharSequence.
             // We'll estimate width if not provided.
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
        val width = layout.width.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw
        layout.draw(canvas)

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
            
            // Line number: Center
            setImageViewBitmap(R.id.img_line, textAsBitmap(arrival.displayLine, 24f, color, 44, android.text.Layout.Alignment.ALIGN_CENTER))
            
            // Destination: Left Aligned (ALIGN_NORMAL)
            var destination = arrival.lineDescr
            if (destination.isEmpty()) {
                destination = lineRepo.getLineDescription(arrival.displayLine) ?: ""
            }
            // Use 180dp max width, ALIGN_NORMAL so text starts at left
            setImageViewBitmap(R.id.img_destination, textAsBitmap(destination, 20f, color, 180, android.text.Layout.Alignment.ALIGN_NORMAL))
            
            // Sigma merged with Time
            // setImageViewBitmap(R.id.img_sigma, textAsBitmap("Σ", 22f, color))
            
            // Time: Center (fitCenter handles centering the bitmap, but let's center text inside too)
            // Enlarge the apostrophe
            val minText = when {
                arrival.estimatedMinutes <= 0 -> "NOW"
                else -> "Σ ${arrival.estimatedMinutes}'"
            }
            
            val spannableTime = SpannableString(minText)
            if (minText.endsWith("'")) {
                spannableTime.setSpan(
                    RelativeSizeSpan(1.2f),
                    minText.length - 1,
                    minText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            setImageViewBitmap(R.id.img_time, textAsBitmap(spannableTime, 24f, color, null, android.text.Layout.Alignment.ALIGN_CENTER))
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = false
}
