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
     * Get Smart Configuration (List of Stops)
     * Handles migration from legacy comma-separated string.
     */
    fun getSmartConfig(widgetId: Int): List<StopConfigItem> {
        val config = getConfig(widgetId) ?: return emptyList()
        
        // 1. Try new JSON format
        if (config.configJson.isNotEmpty()) {
            return try {
                val type = object : com.google.gson.reflect.TypeToken<List<StopConfigItem>>() {}.type
                gson.fromJson(config.configJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        // 2. Fallback / Migration from Legacy
        if (config.stopCode.isNotEmpty()) {
            val codes = config.stopCode.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val names = config.stopName.split(",").map { it.trim() }
            
            // Reconstruct list
            return codes.mapIndexed { index, code ->
                val name = names.getOrElse(index) { "Stop $code" }
                // Legacy filters applied to ALL stops (rough approximation)
                val lines = if (config.lineFilters.isNotEmpty()) {
                    config.lineFilters.split(",").map { it.trim() }
                } else {
                    emptyList()
                }
                StopConfigItem(code, name, lines)
            }
        }
        
        return emptyList()
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
