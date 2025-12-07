package com.oasth.widget.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Repository for resolving Street IDs (visible on signs) to API IDs (for getStopArrivals).
 * 
 * stops.json structure:
 * {
 *   "1403": {
 *     "StreetID": "1403",
 *     "StopDescr": "ΤΖΑΒΕΛΛΑ",
 *     "API_IDs": ["1306"]
 *   },
 *   ...
 * }
 */
class StopRepository(private val context: Context) {

    // Map: Street ID (e.g., "1403") -> First API ID (e.g., "1306")
    private var stopMap: Map<String, String>? = null

    /**
     * Resolves a Street ID (user-visible, e.g., "1403") to an API ID (for getStopArrivals, e.g., "1306").
     * If input is not found in the map, returns the input as-is.
     */
    fun getApiId(streetId: String): String {
        ensureLoaded()
        
        val apiId = stopMap?.get(streetId)
        if (apiId != null) {
            Log.d("StopRepository", "Mapped StreetID $streetId -> API ID $apiId")
            return apiId
        }
        
        Log.d("StopRepository", "No mapping for $streetId, assuming it's already an API ID")
        return streetId
    }

    /**
     * @deprecated Use getApiId instead. Kept for backward compatibility.
     */
    fun getInternalId(inputCode: String): String {
        return getApiId(inputCode)
    }

    private fun ensureLoaded() {
        if (stopMap != null) return
        
        Log.d("StopRepository", "Loading stops.json...")
        val map = mutableMapOf<String, String>()
        
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("stops.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            
            while (keys.hasNext()) {
                val streetId = keys.next()
                val stopObject = jsonObject.getJSONObject(streetId)
                
                // Get the first API_ID from the array
                val apiIdsArray: JSONArray? = stopObject.optJSONArray("API_IDs")
                if (apiIdsArray != null && apiIdsArray.length() > 0) {
                    val firstApiId = apiIdsArray.getString(0)
                    map[streetId] = firstApiId
                }
            }
            
            stopMap = map
            Log.d("StopRepository", "Loaded ${map.size} stops")
            
        } catch (e: Exception) {
            Log.e("StopRepository", "Error loading stops.json: ${e.message}")
            stopMap = emptyMap()
        }
    }
}
