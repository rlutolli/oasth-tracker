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
 * Configuration activity for setting up a widget's stop code
 * Updated to use StopRepository for ID mapping.
 */
class WidgetConfigActivity : AppCompatActivity() {
    
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var configRepo: WidgetConfigRepository
    private lateinit var stopRepo: StopRepository
    
    private lateinit var stopCodeInput: EditText
    private lateinit var stopNameInput: EditText
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
        saveButton = findViewById(R.id.save_button)
        
        val existingConfig = configRepo.getConfig(widgetId)
        if (existingConfig != null) {
            stopCodeInput.setText(existingConfig.stopCode)
            stopNameInput.setText(existingConfig.stopName)
        }
        
        saveButton.setOnClickListener {
            saveConfiguration()
        }
    }
    
    private fun saveConfiguration() {
        val inputCode = stopCodeInput.text.toString().trim()
        val inputName = stopNameInput.text.toString().trim()
        
        if (inputCode.isEmpty()) {
            Toast.makeText(this, "Please enter a stop code", Toast.LENGTH_SHORT).show()
            return
        }
        
        saveButton.isEnabled = false
        saveButton.text = "Saving..."
        
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Resolve Internal ID
            val internalId = stopRepo.getInternalId(inputCode)
            
            // 2. Determine Display Name
            val displayName = if (inputName.isNotEmpty()) {
                inputName
            } else {
                "Stop $inputCode"
            }
            
            val config = WidgetConfig(
                widgetId = widgetId,
                stopCode = internalId, // Save the Internal ID!
                stopName = displayName
            )
            configRepo.saveConfig(config)
            
            // 3. Update Widget
            val intent = Intent(this@WidgetConfigActivity, BusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            sendBroadcast(intent)
            
            // 4. Notify User in UI Thread
            CoroutineScope(Dispatchers.Main).launch {
                if (internalId != inputCode) {
                    Toast.makeText(applicationContext, "Found Internal ID: $internalId", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(applicationContext, "Configuration Saved", Toast.LENGTH_SHORT).show()
                }
                
                val resultIntent = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
