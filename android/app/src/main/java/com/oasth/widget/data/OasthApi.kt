package com.oasth.widget.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * OASTH API client using native HTTP with session credentials
 */
class OasthApi(private val sessionManager: SessionManager) {
    
    companion object {
        private const val TAG = "OasthApi"
        private const val BASE_URL = "https://telematics.oasth.gr/api/"
        private const val GITHUB_API = "https://api.github.com/repos/rlutolli/oasth-tracker/releases/latest"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Get arrivals for a specific stop
     */
    suspend fun getArrivals(stopCode: String): List<BusArrival> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting arrivals for stop: $stopCode")
        val session = sessionManager.getSession()
        
        val request = Request.Builder()
            .url("${BASE_URL}?act=getStopArrivals&p1=$stopCode")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-CSRF-Token", session.token)
            .addHeader("Cookie", "PHPSESSID=${session.phpSessionId}")
            .addHeader("Origin", "https://telematics.oasth.gr")
            .addHeader("Referer", "https://telematics.oasth.gr/en/")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        Log.d(TAG, "Response code: ${response.code}")
        
        if (response.code == 401) {
            Log.d(TAG, "Session expired, refreshing...")
            sessionManager.refreshSession()
            return@withContext getArrivals(stopCode)
        }
        
        val body = response.body?.string() ?: "[]"
        Log.d(TAG, "Response body: ${body.take(200)}")
        
        try {
            val type = object : TypeToken<List<BusArrival>>() {}.type
            val result = gson.fromJson<List<BusArrival>>(body, type) ?: emptyList()
            Log.d(TAG, "Parsed ${result.size} arrivals")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            emptyList()
        }
    }
    
    /**
     * Get stop info by code - returns stop name if available
     */
    suspend fun getStopInfo(stopCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val session = sessionManager.getSession()
            
            val request = Request.Builder()
                .url("${BASE_URL}?act=getStopBySIP&p1=$stopCode")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("X-CSRF-Token", session.token)
                .addHeader("Cookie", "PHPSESSID=${session.phpSessionId}")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            
            // Parse response - format varies, try to extract stop name
            if (body.contains("StopDescr")) {
                val regex = """"StopDescr"\s*:\s*"([^"]+)"""".toRegex()
                regex.find(body)?.groupValues?.get(1)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stop info", e)
            null
        }
    }
    
    /**
     * Get all bus lines
     */
    suspend fun getLines(): List<BusLine> = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession()
        
        val request = Request.Builder()
            .url("${BASE_URL}?act=webGetLines")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-CSRF-Token", session.token)
            .addHeader("Cookie", "PHPSESSID=${session.phpSessionId}")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
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
     * Check for app updates from GitHub releases
     * Returns new version tag if update available, null otherwise
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
            
            // Extract tag_name from response
            val regex = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
            val latestVersion = regex.find(body)?.groupValues?.get(1) ?: return@withContext null
            
            // Compare versions (simple string comparison)
            if (latestVersion > currentVersion) {
                latestVersion
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            null
        }
    }
}
