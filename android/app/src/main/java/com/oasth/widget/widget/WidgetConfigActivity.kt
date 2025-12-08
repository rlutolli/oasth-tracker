package com.oasth.widget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import com.oasth.widget.R
import com.oasth.widget.data.WidgetConfig
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

/**
 * Configuration activity for setting up a widget's stop code
 */
class WidgetConfigActivity : AppCompatActivity() {
    
    companion object {
        // Static token removed
        private const val DEFAULT_TOKEN = ""
    }
    
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var configRepo: WidgetConfigRepository
    
    private lateinit var stopCodeInput: EditText
    private lateinit var stopNameInput: EditText
    private lateinit var lineFilterInput: EditText
    private lateinit var saveButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        
        setResult(RESULT_CANCELED)
        
        configRepo = WidgetConfigRepository(this)
        
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        stopCodeInput = findViewById(R.id.stop_code_input)
        stopNameInput = findViewById(R.id.stop_name_input)
        lineFilterInput = findViewById(R.id.line_filter_input)
        saveButton = findViewById(R.id.save_button)
        
        val existingConfig = configRepo.getConfig(widgetId)
        if (existingConfig != null) {
            stopCodeInput.setText(existingConfig.stopCode)
            stopNameInput.setText(existingConfig.stopName)
            lineFilterInput.setText(existingConfig.lineFilter)
        }
        
        saveButton.setOnClickListener {
            saveConfiguration()
        }
    }
    
    private fun saveConfiguration() {
        val stopCode = stopCodeInput.text.toString().trim()
        var stopName = stopNameInput.text.toString().trim()
        val lineFilter = lineFilterInput.text.toString().trim()
        
        if (stopCode.isEmpty()) {
            Toast.makeText(this, R.string.enter_stop_code, Toast.LENGTH_SHORT).show()
            return
        }
        
        saveButton.isEnabled = false
        saveButton.text = getString(R.string.fetching_stop_name)
        
        CoroutineScope(Dispatchers.IO).launch {
// Logic removed to allow saving empty name
            // if (stopName.isEmpty()) {
            //    stopName = fetchStopName(stopCode) ?: "Stop $stopCode"
            // }
            
            val finalStopName = stopName
            
            CoroutineScope(Dispatchers.Main).launch {
                stopNameInput.setText(finalStopName)
                
                val config = WidgetConfig(
                    widgetId = widgetId,
                    stopCode = stopCode,
                    stopName = finalStopName,
                    lineFilter = lineFilter
                )
                configRepo.saveConfig(config)
                
                // Trigger widget update
                val intent = Intent(this@WidgetConfigActivity, BusWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                }
                sendBroadcast(intent)
                
                val resultIntent = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
    
    private fun fetchStopName(stopCode: String): String? {
        return try {
            val prefs = getSharedPreferences("oasth_session", MODE_PRIVATE)
            val phpSessionId = prefs.getString("php_session_id", null) ?: return null
            val token = prefs.getString("token", null) ?: DEFAULT_TOKEN
            
            // Try to get stop name from arrivals response
            val url = URL("https://telematics.oasth.gr/api/?act=getStopArrivals&p1=$stopCode")
            val connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-CSRF-Token", token)
            connection.setRequestProperty("Cookie", "PHPSESSID=$phpSessionId")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                
                // Try to extract stop description from response
                val stopDescrPattern = """"stopDescr"\s*:\s*"([^"]+)"""".toRegex()
                val match = stopDescrPattern.find(response)
                
                if (match != null) {
                    return match.groupValues[1]
                }
                
                // Fallback: try bstop_descr
                val bstopPattern = """"bstop_descr"\s*:\s*"([^"]+)"""".toRegex()
                bstopPattern.find(response)?.groupValues?.get(1)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
