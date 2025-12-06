package com.example.oasthwidget

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class OasthApiClient {
    private val client = OkHttpClient()

    // Use PC's LAN IP for physical device. Emulator 10.0.2.2 won't work on phone.
    // private val BASE_URL = "http://10.0.2.2:5000" 
    private val BASE_URL = "http://10.177.195.166:5000"

    fun getStopArrivals(stopId: Int, stopCode: String): List<BusArrival>? {
        val request = Request.Builder()
            .url("$BASE_URL/arrivals/$stopId")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("OasthApiClient", "Server Error: ${response.code}")
                    return null
                }

                val jsonString = response.body?.string() ?: return null
                android.util.Log.d("OasthApiClient", "Raw Response: $jsonString")

                // Handle top-level JSON Array (e.g. "[]")
                if (jsonString.trim().startsWith("[")) {
                     val jsonArray = org.json.JSONArray(jsonString)
                     val list = mutableListOf<BusArrival>()
                     for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(i) ?: continue
                        list.add(BusArrival(
                            minutes = item.optString("btime2", "0").toIntOrNull() ?: 0,
                            routeCode = item.optString("route_code", "?"),
                            vehicleCode = item.optString("veh_code", "?"),
                            stopCode = stopCode
                        ))
                     }
                     return list
                }

                // Handle top-level JSON Object (e.g. { "arrivals": [...] })
                val jsonObject = JSONObject(jsonString)
                val arrivalsArray = jsonObject.optJSONArray("arrivals")
                
                if (arrivalsArray != null) {
                    val list = mutableListOf<BusArrival>()
                    for (i in 0 until arrivalsArray.length()) {
                        val item = arrivalsArray.getJSONObject(i)
                        list.add(BusArrival(
                            minutes = item.optString("btime2", "0").toIntOrNull() ?: 0,
                            routeCode = item.optString("route_code", "?"),
                            vehicleCode = item.optString("veh_code", "?"),
                            stopCode = stopCode
                        ))
                    }
                    return list
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OasthApiClient", "Exception: ${e.message}", e)
            e.printStackTrace()
        }
        return null
    }

    fun searchStop(stopCode: String): String? {
        val request = Request.Builder()
            .url("$BASE_URL/search/$stopCode")
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonString = response.body?.string() ?: return null
                val jsonObject = JSONObject(jsonString)
                if (jsonObject.optString("status") == "success") {
                    return jsonObject.optString("internal_id")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

data class BusArrival(
    val minutes: Int,
    val routeCode: String,
    val vehicleCode: String,
    val stopCode: String // Added to identify source stop
)
