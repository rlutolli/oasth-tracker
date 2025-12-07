package com.oasth.widget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.oasth.widget.R
import com.oasth.widget.data.StopRepository
import com.oasth.widget.data.WidgetConfig
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Configuration activity for setting up a widget
 * Supports Multiple Stops (comma-separated) and Line Filtering.
 */
class WidgetConfigActivity : AppCompatActivity() {
    
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var configRepo: WidgetConfigRepository
    private lateinit var stopRepo: StopRepository
    
    private lateinit var stopCodeInput: EditText
    private lateinit var stopNameInput: EditText
    private lateinit var lineFilterInput: EditText
    private lateinit var saveButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        
        setResult(RESULT_CANCELED)
        
        configRepo = WidgetConfigRepository(this)
        stopRepo = StopRepository(this)
        
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
            // Note: existingConfig.stopCode stores API IDs. 
            // Ideally we should reverse map to Street IDs for display, 
            // but for now we just show what's saved (API IDs or Street IDs if they differ).
            // Since we save API IDs, showing them might be confusing if user entered Street IDs.
            // But we don't have easy reverse mapping yet. 
            // Let's assume advanced users or just show simple text.
            stopCodeInput.setText(existingConfig.stopCode)
            stopNameInput.setText(existingConfig.stopName)
            lineFilterInput.setText(existingConfig.lineFilters)
        }
        
        saveButton.setOnClickListener {
            saveConfiguration()
        }
    }
    
    private fun saveConfiguration() {
        val inputStopCodes = stopCodeInput.text.toString().trim()
        val inputStopName = stopNameInput.text.toString().trim()
        val inputFilters = lineFilterInput.text.toString().trim()
        
        if (inputStopCodes.isEmpty()) {
            Toast.makeText(this, R.string.enter_stop_code, Toast.LENGTH_SHORT).show()
            return
        }
        
        saveButton.isEnabled = false
        saveButton.text = getString(R.string.save) // "Save"
        
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Parse multiple stops to get names
            val streetIds = inputStopCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val autoNames = mutableListOf<String>()
            
            for (streetId in streetIds) {
                // Try to get name locally
                val name = stopRepo.getStopName(streetId) ?: "Stop $streetId"
                autoNames.add(name)
            }
            
            val finalName = if (inputStopName.isNotEmpty()) inputStopName else autoNames.joinToString(", ")
            
            // 2. Save Config (Store Street IDs, resolve at runtime)
            val config = WidgetConfig(
                widgetId = widgetId,
                stopCode = inputStopCodes, // Save as comma-separated Street IDs
                stopName = finalName,
                lineFilters = inputFilters
            )
            configRepo.saveConfig(config)
            
            // 3. Update Widget
            val intent = Intent(this@WidgetConfigActivity, BusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            sendBroadcast(intent)
            
            // 4. Finish
            CoroutineScope(Dispatchers.Main).launch {
                val resultIntent = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
