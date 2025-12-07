package com.oasth.widget.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * Stores widget configurations (stop code, name) per widget instance
 */
class WidgetConfigRepository(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "widget_configs"
        private const val KEY_PREFIX = "widget_"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    /**
     * Save configuration for a widget
     */
    fun saveConfig(config: WidgetConfig) {
        prefs.edit()
            .putString("${KEY_PREFIX}${config.widgetId}", gson.toJson(config))
            .apply()
    }
    
    /**
     * Get configuration for a widget
     */
    fun getConfig(widgetId: Int): WidgetConfig? {
        val json = prefs.getString("${KEY_PREFIX}$widgetId", null) ?: return null
        return try {
            gson.fromJson(json, WidgetConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete configuration for a widget
     */
    fun deleteConfig(widgetId: Int) {
        prefs.edit()
            .remove("${KEY_PREFIX}$widgetId")
            .apply()
    }
    
    /**
     * Get all configured widget IDs
     */
    fun getAllWidgetIds(): List<Int> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { it.removePrefix(KEY_PREFIX).toIntOrNull() }
    }
}
