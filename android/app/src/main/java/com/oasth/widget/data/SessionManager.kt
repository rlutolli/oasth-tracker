package com.oasth.widget.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages OASTH session via WebView for initial auth, then caches credentials.
 */
class SessionManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "oasth_session"
        private const val KEY_PHP_SESSION_ID = "php_session_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_CREATED_AT = "created_at"
        
        // Static token discovered through reverse-engineering
        private const val STATIC_TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"
        
        private const val OASTH_URL = "https://telematics.oasth.gr/en/"
        private const val SESSION_TIMEOUT_MS = 30000L // Increased to 30 seconds
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Get valid session, refreshing if needed
     */
    suspend fun getSession(): SessionData {
        val cached = getCachedSession()
        if (cached != null && cached.isValid()) {
            return cached
        }
        
        return refreshSession() ?: throw Exception("Failed to get session")
    }
    
    /**
     * Get cached session if exists
     */
    private fun getCachedSession(): SessionData? {
        val phpSessionId = prefs.getString(KEY_PHP_SESSION_ID, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0)
        
        return SessionData(phpSessionId, token, createdAt)
    }
    
    /**
     * Save session to SharedPreferences
     */
    private fun saveSession(session: SessionData) {
        prefs.edit()
            .putString(KEY_PHP_SESSION_ID, session.phpSessionId)
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_CREATED_AT, session.createdAt)
            .apply()
    }
    
    /**
     * Refresh session using WebView on main thread
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun refreshSession(): SessionData? = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                try {
                    val webView = WebView(context)
                    
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile"
                    }
                    
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            // Wait for JavaScript to execute
                            mainHandler.postDelayed({
                                extractCredentials(view ?: webView) { session ->
                                    webView.destroy()
                                    if (session != null) {
                                        saveSession(session)
                                    }
                                    if (continuation.isActive) {
                                        continuation.resume(session)
                                    }
                                }
                            }, 3000) // Wait 3 seconds for JS to set cookies
                        }
                        
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            webView.destroy()
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                    
                    // Clear existing cookies and load page
                    CookieManager.getInstance().removeAllCookies(null)
                    webView.loadUrl(OASTH_URL)
                    
                    continuation.invokeOnCancellation {
                        mainHandler.post { webView.destroy() }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
    
    /**
     * Extract token and PHPSESSID from WebView
     */
    private fun extractCredentials(webView: WebView, callback: (SessionData?) -> Unit) {
        // Extract JavaScript token
        webView.evaluateJavascript("window.token || '$STATIC_TOKEN'") { tokenResult ->
            val token = tokenResult?.trim('"') ?: STATIC_TOKEN
            
            // Get PHPSESSID from cookies
            val cookies = CookieManager.getInstance().getCookie(OASTH_URL)
            val phpSessionId = extractPhpSessionId(cookies)
            
            if (phpSessionId != null && token.isNotEmpty()) {
                callback(SessionData(
                    phpSessionId = phpSessionId,
                    token = token,
                    createdAt = System.currentTimeMillis()
                ))
            } else {
                // Fallback: use static token if we got cookies at least
                if (phpSessionId != null) {
                    callback(SessionData(
                        phpSessionId = phpSessionId,
                        token = STATIC_TOKEN,
                        createdAt = System.currentTimeMillis()
                    ))
                } else {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Parse PHPSESSID from cookie string
     */
    private fun extractPhpSessionId(cookies: String?): String? {
        if (cookies.isNullOrEmpty()) return null
        
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("PHPSESSID=") }
            ?.substringAfter("PHPSESSID=")
    }
    
    /**
     * Clear cached session
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
