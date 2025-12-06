package com.example.oasthwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast

class AppWidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var appWidgetText: EditText
    private lateinit var progressBar: ProgressBar

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_widget_configure)
        appWidgetText = findViewById(R.id.appwidget_text)
        progressBar = findViewById(R.id.loading_spinner)
        
        findViewById<View>(R.id.add_button).setOnClickListener {
            val context = this@AppWidgetConfigureActivity
            val widgetText = appWidgetText.text.toString()
            saveContext(context, widgetText)
        }

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

    private fun saveContext(context: Context, stopCodesInput: String) {
        // Expected format: "916", "916:57", "1308:01X"
        val rawEntries = stopCodesInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (rawEntries.isEmpty()) {
            Toast.makeText(context, "Please enter at least one code", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        
        Thread {
            val apiClient = OasthApiClient()
            val validStops = mutableListOf<String>()
            var errors = false
            
            for (entry in rawEntries) {
                // Check if user provided a line filter (e.g. "916:57")
                val parts = entry.split(":")
                val stopCode = parts[0].trim()
                val lineFilter = if (parts.size > 1) parts[1].trim() else ""
                
                val internalId = apiClient.searchStop(stopCode)
                if (internalId != null) {
                    // Save format: "USER_CODE:INTERNAL_ID:LINE_FILTER"
                    // Example: "916:3344:57" or "916:3344:"
                    validStops.add("$stopCode:$internalId:$lineFilter")
                } else {
                    errors = true
                }
            }
            
            runOnUiThread {
                progressBar.visibility = View.GONE
                
                if (validStops.isEmpty()) {
                     Toast.makeText(context, "Could not find any of those stops due to server error or invalid codes.", Toast.LENGTH_LONG).show()
                     return@runOnUiThread
                }
                
                if (errors) {
                    Toast.makeText(context, "Some stops were not found, saving valid ones only.", Toast.LENGTH_LONG).show()
                }

                val prefs = context.getSharedPreferences("OasthWidgetPrefs", 0)
                val editor = prefs.edit()
                editor.putString("stops_$appWidgetId", validStops.joinToString(","))
                editor.apply()

                // Push widget update to surface with newly set prefix
                val appWidgetManager = AppWidgetManager.getInstance(context)
                BusWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)

                // Make sure we pass back the original appWidgetId
                val resultValue = Intent()
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            }
        }.start()
    }
}
