package com.oasth.widget.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Repository for resolving Street IDs (visible on signs) to API IDs (for getStopArrivals).
 * Also provides stop names for display.
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

    // Map: Street ID -> API ID
    private var apiIdMap: Map<String, String>? = null

    // Map: Street ID -> Stop Description
    private var stopNameMap: Map<String, String>? = null

    /**
     * Resolves a Street ID to an API ID for getStopArrivals.
     */
    fun getApiId(streetId: String): String {
        ensureLoaded()
        
        val apiId = apiIdMap?.get(streetId)
        if (apiId != null) {
            Log.d(TAG, "Mapped StreetID $streetId -> API ID $apiId")
            return apiId
        }

        Log.d(TAG, "No mapping for $streetId, assuming it's already an API ID")
        return streetId
    }

    /**
     * Gets the stop name/description for a Street ID.
     */
    fun getStopName(streetId: String): String? {
        ensureLoaded()
        return stopNameMap?.get(streetId)
    }

    /**
     * @deprecated Use getApiId instead. Kept for backward compatibility.
     */
    fun getInternalId(inputCode: String): String {
        return getApiId(inputCode)
    }

    private fun ensureLoaded() {
        if (apiIdMap != null) return

        Log.d(TAG, "Loading stops.json...")
        val apiMap = mutableMapOf<String, String>()
        val nameMap = mutableMapOf<String, String>()
        
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
                    apiMap[streetId] = apiIdsArray.getString(0)
                }

                // Get stop description
                val stopDescr = stopObject.optString("StopDescr", "")
                if (stopDescr.isNotEmpty()) {
                    nameMap[streetId] = stopDescr
                }
            }

            apiIdMap = apiMap
            stopNameMap = nameMap
            Log.d(TAG, "Loaded ${apiMap.size} stops, ${nameMap.size} names")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading stops.json: ${e.message}")
            apiIdMap = emptyMap()
            stopNameMap = emptyMap()
        }
    }

    companion object {
        private const val TAG = "StopRepository"
    }
}
