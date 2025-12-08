package com.oasth.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.oasth.widget.R
import com.oasth.widget.data.StopRepository
import com.oasth.widget.data.WidgetConfigRepository

/**
 * Home screen widget provider with ListView for bus arrivals
 */
class BusWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "BusWidgetProvider"
        const val ACTION_REFRESH = "com.oasth.widget.ACTION_REFRESH"
        const val ACTION_TOGGLE_VISIBILITY = "com.oasth.widget.ACTION_TOGGLE_VISIBILITY"
        private const val PREFS_NAME = "widget_prefs"
        private const val PREF_PREFIX_KEY = "visibility_"
        
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget: $appWidgetId")
            
            val configRepo = WidgetConfigRepository(context)
            val config = configRepo.getConfig(appWidgetId)
            
            // Layout is widget_bus
            val views = RemoteViews(context.packageName, R.layout.widget_bus)
            
            if (config == null) {
                // Show "Tap to Configure"
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.tap_to_configure))
                views.setViewVisibility(R.id.widget_list, View.GONE)
                views.setViewVisibility(R.id.empty_view, View.VISIBLE) // Use empty_view container
                
                val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }
            
            // Check Visibility State
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isFocused = prefs.getBoolean(PREF_PREFIX_KEY + appWidgetId, false)
            
            // Set Background
            // Note: Since widget_root is a LinearLayout, setInt(..., "setBackgroundResource", ...) 
            // is needed because setImageViewResource is for ImageViews.
            // RemoteViews supports setInt for setBackgroundResource.
            views.setInt(R.id.widget_root, "setBackgroundResource", 
                if (isFocused) R.drawable.widget_background_focused else R.drawable.widget_background_transparent
            )
            
            // Set up List
            val intent = Intent(context, BusRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_view)
            
            // Toggle Intent (Refresh + Visibility)
            val toggleIntent = Intent(context, BusWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_VISIBILITY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://$appWidgetId") // Make unique
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // Mutable logic for fillIn? No, template is immutable usually ok?
                // Actually for setOnClickPendingIntent it can be immutable.
            )
            
            // Click on Root -> Toggle
            views.setOnClickPendingIntent(R.id.widget_root, togglePendingIntent)
            
            // Click on List Items -> Toggle (using Template)
            views.setPendingIntentTemplate(R.id.widget_list, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")
        
        if (intent.action == ACTION_TOGGLE_VISIBILITY || intent.action == ACTION_REFRESH) {
            // Haptic Feedback
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50) // Fallback for older devices
            }
        }
        
        if (intent.action == ACTION_TOGGLE_VISIBILITY) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Toggle State
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = prefs.getBoolean(PREF_PREFIX_KEY + widgetId, false)
                prefs.edit().putBoolean(PREF_PREFIX_KEY + widgetId, !current).apply()
                
                // Update
                val manager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, manager, widgetId)
            }
        }
        else if (intent.action == ACTION_REFRESH) {
             val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
             if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) updateAppWidget(context, AppWidgetManager.getInstance(context), widgetId)
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val configRepo = WidgetConfigRepository(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        appWidgetIds.forEach { 
            configRepo.deleteConfig(it)
            editor.remove(PREF_PREFIX_KEY + it)
        }
        editor.apply()
    }
}
