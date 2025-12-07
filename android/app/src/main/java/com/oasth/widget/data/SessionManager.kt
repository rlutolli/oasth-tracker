package com.oasth.widget.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages OASTH session using WebView for proper JavaScript execution
 */
class SessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "oasth_session"
        private const val KEY_PHP_SESSION_ID = "php_session_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_CREATED_AT = "created_at"
        private const val OASTH_URL = "https://telematics.oasth.gr/"
        private const val SESSION_TIMEOUT_MS = 45000L
        
        // Static token proved to be working
        private const val STATIC_TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Memory cache
    @Volatile private var cachedSession: SessionData? = null
    
    /**
     * Get valid session, refreshing via WebView if needed
     */
    suspend fun getSession(): SessionData {
        // Check memory cache
        cachedSession?.let { session ->
            if (session.isValid()) {
                Log.d(TAG, "Using memory-cached session")
                return session
            }
        }
        
        // Check disk cache
        getCachedSession()?.let { session ->
            if (session.isValid()) {
                Log.d(TAG, "Using disk-cached session")
                cachedSession = session
                return session
            }
        }
        
        // Need fresh session from WebView
        Log.d(TAG, "Need fresh session, launching WebView...")
        val freshSession = refreshSession()
        
        return freshSession ?: throw Exception("Failed to obtain OASTH session")
    }
    
    private fun getCachedSession(): SessionData? {
        val sessionId = prefs.getString(KEY_PHP_SESSION_ID, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0)
        return SessionData(sessionId, token, createdAt)
    }
    
    private fun saveSession(session: SessionData) {
        prefs.edit()
            .putString(KEY_PHP_SESSION_ID, session.phpSessionId)
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_CREATED_AT, session.createdAt)
            .apply()
        cachedSession = session
        Log.d(TAG, "Session saved: ${session.phpSessionId.take(8)}...")
    }
    
    /**
     * Refresh session using WebView to properly execute JavaScript
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun refreshSession(): SessionData? = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                var webView: WebView? = null
                var hasResumed = false
                
                fun resumeOnce(session: SessionData?) {
                    if (!hasResumed && continuation.isActive) {
                        hasResumed = true
                        webView?.destroy()
                        webView = null
                        if (session != null) {
                            saveSession(session)
                        }
                        continuation.resume(session)
                    }
                }
                
                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    
                    // Explicitly enable cookies
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                    
                    webView?.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d(TAG, "Page loaded: $url")
                            
                            // Wait for JavaScript to fully initialize
                            mainHandler.postDelayed({
                                extractCredentials(view) { session ->
                                    resumeOnce(session ?: getFallbackSession())
                                }
                            }, 4000) 
                        }
                        
                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(view: WebView?, errorCode: Int, desc: String?, failingUrl: String?) {
                            Log.e(TAG, "WebView error: $errorCode - $desc")
                            resumeOnce(getFallbackSession())
                        }
                    }
                    
                    // Clear old cookies first
                    CookieManager.getInstance().removeAllCookies { 
                        Log.d(TAG, "Cookies cleared, loading page...")
                        webView?.loadUrl("https://telematics.oasth.gr/en/")
                    }
                    
                    continuation.invokeOnCancellation {
                        mainHandler.post {
                            webView?.stopLoading()
                            webView?.destroy()
                            webView = null
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "WebView setup failed: ${e.message}")
                    resumeOnce(null)
                }
            }
        }
    }
    
    /**
     * Extract BOTH token AND session from WebView
     */
    private fun extractCredentials(webView: WebView?, callback: (SessionData?) -> Unit) {
        if (webView == null) {
            callback(null)
            return
        }
        
        // JavaScript to extract the CSRF token from jQuery's ajax settings
        val jsCode = """
            (function() {
                try {
                    // Try jQuery first
                    if (typeof $ !== 'undefined' && $.ajaxSettings && $.ajaxSettings.headers) {
                        return $.ajaxSettings.headers['X-CSRF-Token'] || '';
                    }
                    // Try window.token
                    if (typeof window.token !== 'undefined') {
                        return window.token;
                    }
                    // Try finding it in page source
                    var scripts = document.getElementsByTagName('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var match = scripts[i].innerHTML.match(/['"]X-CSRF-Token['"]\s*:\s*['"]([a-f0-9]{64})['"]/i);
                        if (match) return match[1];
                    }
                    return '';
                } catch(e) {
                    return '';
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { tokenResult ->
            val token = tokenResult?.trim('"')?.takeIf { 
                it.isNotEmpty() && it.length == 64 && it.matches(Regex("[a-f0-9]+"))
            }
            
            Log.d(TAG, "Extracted token: ${token?.take(16) ?: "NONE"}...")
            
            // Get PHPSESSID from cookies
            val cookies = CookieManager.getInstance().getCookie(OASTH_URL)
            val phpSessionId = extractPhpSessionId(cookies)
            
            Log.d(TAG, "Extracted PHPSESSID: ${phpSessionId?.take(8) ?: "NONE"}...")
            
            if (phpSessionId != null && token != null) {
                callback(SessionData(
                    phpSessionId = phpSessionId,
                    token = token,
                    createdAt = System.currentTimeMillis()
                ))
            } else if (phpSessionId != null) {
                // Fallback: USE STATIC TOKEN (Proven to work, unlike SHA-256)
                Log.d(TAG, "Using STATIC fallback token")
                callback(SessionData(
                    phpSessionId = phpSessionId,
                    token = STATIC_TOKEN,
                    createdAt = System.currentTimeMillis()
                ))
            } else {
                Log.e(TAG, "Failed to extract credentials")
                callback(null)
            }
        }
    }
    
    private fun extractPhpSessionId(cookies: String?): String? {
        if (cookies.isNullOrEmpty()) return null
        
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("PHPSESSID=") }
            ?.substringAfter("PHPSESSID=")
            ?.takeIf { it.isNotEmpty() }
    }
    
    fun clearSession() {
        cachedSession = null
        prefs.edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
    }
    
    private fun getFallbackSession(): SessionData {
        Log.w(TAG, "Using EMERGENCY FALLBACK session")
        return SessionData(
            phpSessionId = "h2daist5tpv86h10aotc6lpch4",
            token = STATIC_TOKEN,
            createdAt = System.currentTimeMillis()
        )
    }
}
