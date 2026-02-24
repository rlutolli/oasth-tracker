package com.oasth.widget.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OASTH API client using POST requests with session auto-refresh
 */
class OasthApi(private val sessionManager: SessionManager) {
    
    companion object {
        private const val TAG = "OasthApi"
        private const val BASE_URL = "https://telematics.oasth.gr"
        private const val API_URL = "$BASE_URL/api/"
        private const val GITHUB_API = "https://api.github.com/repos/rlutolli/oasth-tracker/releases/latest"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // Prevent infinite recursion on 401
    @Volatile
    private var isRetrying = false
    
    /**
     * Get arrivals for a specific stop using POST
     */
    suspend fun getArrivals(stopCode: String): List<BusArrival> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting arrivals for stop: $stopCode")
        
        try {
            val session = sessionManager.getSession()
            
            val request = Request.Builder()
                .url("${API_URL}?act=getStopArrivals&p1=$stopCode")
                .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("X-CSRF-Token", session.token)
                .addHeader("Cookie", "PHPSESSID=${session.phpSessionId}")
                .addHeader("Origin", BASE_URL)
                .addHeader("Referer", "$BASE_URL/")
                .build()
            
            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: "[]"
            response.close()

            Log.d(TAG, "Response code: $code, body length: ${body.length}")

            // Check for 401 or unauthorized response
            if (code == 401 || body.contains("unauthorized", ignoreCase = true) || body.contains("not authorized", ignoreCase = true)) {
                if (!isRetrying) {
                    Log.w(TAG, "Unauthorized - refreshing session and retrying...")
                    isRetrying = true
                    sessionManager.refreshSession()
                    val result = getArrivals(stopCode)
                    isRetrying = false
                    return@withContext result
                } else {
                    Log.e(TAG, "Still unauthorized after refresh, giving up")
                    isRetrying = false
                    return@withContext emptyList()
                }
                return@withContext emptyList()
            }
            
            try {
                val type = object : TypeToken<List<BusArrival>>() {}.type
                val arrivals = gson.fromJson<List<BusArrival>>(body, type) ?: emptyList()
                Log.d(TAG, "Parsed ${arrivals.size} arrivals")
                arrivals
            } catch (e: Exception) {
                Log.e(TAG, "Parse error: ${e.message}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get stop info by code
     */
    suspend fun getStopInfo(stopCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val session = sessionManager.getSession()
            
            val request = Request.Builder()
                .url("${API_URL}?act=getStopNameAndXY&p1=$stopCode")
                .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .addHeader("Accept", "application/json")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("X-CSRF-Token", session.token)
                .addHeader("Cookie", "PHPSESSID=${session.phpSessionId}")
                .addHeader("Origin", BASE_URL)
                .addHeader("Referer", "$BASE_URL/")
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            response.close()

            val regex = """"stop_descr"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
            regex.find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stop info: ${e.message}")
            null
        }
    }
    
    /**
     * Get all bus lines
     */
    suspend fun getLines(): List<BusLine> = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession()

        val request = Request.Builder()
            .url("${API_URL}?act=webGetLines")
            .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-CSRF-Token", session.token)
            .addHeader("Cookie", "PHPSESSID=${session.phpSessionId}")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "[]"

        try {
            val type = object : TypeToken<List<BusLine>>() {}.type
            gson.fromJson<List<BusLine>>(body, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check for app updates
     */
    suspend fun checkForUpdate(currentVersion: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            response.close()

            val regex = """"tag_name"\s*:\s*"v?([^"]+)"""".toRegex()
            val latestVersion = regex.find(body)?.groupValues?.get(1) ?: return@withContext null

            if (isNewerVersion(latestVersion, currentVersion)) {
                latestVersion
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
            null
        }
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
