package com.oasth.widget

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oasth.widget.data.OasthApi
import com.oasth.widget.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugActivity : AppCompatActivity() {
    
    private lateinit var logView: TextView
    private lateinit var sessionManager: SessionManager
    private lateinit var api: OasthApi
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(applicationContext)
        api = OasthApi(sessionManager)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { 
            bottomMargin = 16 
        }
        
        Button(this).apply {
            text = "1. Test Session"
            layoutParams = params
            setOnClickListener { testSession() }
            layout.addView(this)
        }
        
        Button(this).apply {
            text = "2. Test API (Stop 3344)"
            layoutParams = params
            setOnClickListener { testApi("3344") }
            layout.addView(this)
        }
        
        Button(this).apply {
            text = "3. Clear & Refresh Session"
            layoutParams = params
            setOnClickListener { clearAndRefresh() }
            layout.addView(this)
        }
        
        Button(this).apply {
            text = "4. Force Widget Update"
            layoutParams = params
            setOnClickListener { forceWidgetUpdate() }
            layout.addView(this)
        }
        
        logView = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, 32, 0, 0)
        }
        
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(logView)
        }
        layout.addView(scroll)
        
        setContentView(layout)
        log("=== OASTH Widget Debug ===\nTap a button to test.\n")
    }
    
    private fun log(msg: String) {
        runOnUiThread {
            logView.append("$msg\n")
        }
        android.util.Log.d("DebugActivity", msg)
    }
    
    private fun testSession() {
        log("\n--- Testing Session ---")
        lifecycleScope.launch {
            try {
                val session = sessionManager.getSession()
                log("✅ Session OK!")
                log("   PHPSESSID: ${session.phpSessionId}")
                log("   Token: ${session.token.take(32)}...")
                log("   Created: ${java.util.Date(session.createdAt)}")
                log("   Valid: ${session.isValid()}")
            } catch (e: Exception) {
                log("❌ Session Error: ${e.message}")
            }
        }
    }
    
    private fun testApi(stopCode: String) {
        log("\n--- Testing API: Stop $stopCode ---")
        lifecycleScope.launch {
            try {
                log("Calling getArrivals()...")
                val arrivals = api.getArrivals(stopCode)
                
                if (arrivals.isNotEmpty()) {
                    log("✅ Got ${arrivals.size} arrivals!")
                    arrivals.forEach { arr ->
                        log("   🚌 Line ${arr.routeCode}: ${arr.estimatedMinutes} min (${arr.vehicleCode})")
                    }
                } else {
                    log("⚠️ Empty response (no buses)")
                }
            } catch (e: Exception) {
                log("❌ API Error: ${e.message}")
                log(e.stackTraceToString().take(300))
            }
        }
    }
    
    private fun clearAndRefresh() {
        log("\n--- Clearing Session ---")
        sessionManager.clearSession()
        log("Session cleared.")
        
        lifecycleScope.launch {
            log("Refreshing session via WebView...")
            val session = sessionManager.refreshSession()
            if (session != null) {
                log("✅ New session obtained!")
                log("   PHPSESSID: ${session.phpSessionId}")
                log("   Token: ${session.token.take(32)}...")
            } else {
                log("❌ Failed to get new session")
            }
        }
    }
    
    private fun forceWidgetUpdate() {
        log("\n--- Forcing Widget Update ---")
        try {
            val intent = android.content.Intent(this, com.oasth.widget.widget.BusWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = android.appwidget.AppWidgetManager.getInstance(application)
                    .getAppWidgetIds(android.content.ComponentName(application, com.oasth.widget.widget.BusWidgetProvider::class.java))
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                log("Widget IDs: ${ids.toList()}")
            }
            sendBroadcast(intent)
            log("✅ Widget update broadcast sent")
        } catch (e: Exception) {
            log("❌ Error: ${e.message}")
        }
    }
}
