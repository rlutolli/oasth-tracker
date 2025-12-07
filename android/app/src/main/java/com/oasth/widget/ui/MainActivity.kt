package com.oasth.widget.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.oasth.widget.R
import com.oasth.widget.data.SessionManager
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

/**
 * Main app activity showing info, widgets, version, and credits
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val CURRENT_VERSION = "1.2.0"
        private const val GITHUB_API = "https://api.github.com/repos/rlutolli/oasth-tracker/releases/latest"
        private const val GITHUB_RELEASES = "https://github.com/rlutolli/oasth-tracker/releases"
    }
    
    private lateinit var statusText: TextView
    private lateinit var widgetsText: TextView
    private lateinit var versionText: TextView
    private lateinit var updateButton: Button
    private lateinit var creditsText: TextView
    private lateinit var configRepo: WidgetConfigRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.status_text)
        widgetsText = findViewById(R.id.widgets_text)
        versionText = findViewById(R.id.update_text)
        updateButton = findViewById(R.id.update_button)
        creditsText = findViewById(R.id.credits_text)
        configRepo = WidgetConfigRepository(this)
        
        updateButton.visibility = View.GONE
        updateButton.setOnClickListener { openGithubReleases() }
        
        // Set credits text
        creditsText.text = getString(R.string.credits)
    }
    
    override fun onResume() {
        super.onResume()
        loadStatus()
    }
    
    private fun loadStatus() {
        refreshWidgetList()
        checkSession()
        checkForUpdates()
    }
    
    private fun refreshWidgetList() {
        val widgetIds = configRepo.getAllWidgetIds()
        
        widgetsText.text = if (widgetIds.isEmpty()) {
            getString(R.string.no_widgets_configured)
        } else {
            buildString {
                appendLine(getString(R.string.configured_widgets, widgetIds.size))
                appendLine()
                for (id in widgetIds) {
                    val config = configRepo.getConfig(id)
                    if (config != null) {
                        appendLine("• ${config.stopName} (${config.stopCode})")
                    }
                }
            }
        }
    }
    
    private fun checkSession() {
        statusText.text = getString(R.string.checking_session)
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sessionManager = SessionManager(this@MainActivity)
                val session = sessionManager.getSession()
                
                if (session.isValid()) {
                    statusText.text = getString(R.string.session_active)
                } else {
                    statusText.text = "Session expired, refreshing..."
                }
            } catch (e: Exception) {
                statusText.text = getString(R.string.session_error, e.message ?: "Unknown error")
            }
        }
    }
    
    private fun checkForUpdates() {
        versionText.text = getString(R.string.version_checking)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latestVersion = fetchLatestVersion()
                
                CoroutineScope(Dispatchers.Main).launch {
                    if (latestVersion != null && isNewerVersion(latestVersion, CURRENT_VERSION)) {
                        versionText.text = getString(R.string.version_update_available, latestVersion)
                        updateButton.visibility = View.VISIBLE
                    } else {
                        versionText.text = "${getString(R.string.version_up_to_date)} (v$CURRENT_VERSION)"
                        updateButton.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    versionText.text = "v$CURRENT_VERSION"
                    updateButton.visibility = View.GONE
                }
            }
        }
    }
    
    private fun fetchLatestVersion(): String? {
        return try {
            val url = URL(GITHUB_API)
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val response = connection.getInputStream().bufferedReader().readText()
            val regex = """"tag_name"\s*:\s*"v?([^"]+)"""".toRegex()
            regex.find(response)?.groupValues?.get(1)
        } catch (e: Exception) {
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
    
    private fun openGithubReleases() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES))
        startActivity(intent)
    }
}
