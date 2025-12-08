import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/models.dart';

/// Manages OASTH session - cookies and CSRF token
class SessionManager extends ChangeNotifier {
  static const String _prefsKeySession = 'oasth_session';
  static const String _oasthUrl = 'https://telematics.oasth.gr/en/';
  
  SessionData? _session;
  bool _isLoading = false;
  String? _error;
  
  SessionData? get session => _session;
  bool get isLoading => _isLoading;
  bool get isValid => _session?.isValid ?? false;
  String? get error => _error;
  
  SessionManager() {
    debugPrint('[SessionManager] Initializing...');
    _loadCachedSession();
  }
  
  /// Load cached session from SharedPreferences
  Future<void> _loadCachedSession() async {
    try {
      debugPrint('[SessionManager] Loading cached session...');
      final prefs = await SharedPreferences.getInstance();
      final sessionJson = prefs.getString(_prefsKeySession);
      
      if (sessionJson != null) {
        debugPrint('[SessionManager] Found cached session JSON');
        _session = SessionData.fromJson(jsonDecode(sessionJson));
        
        if (_session!.isValid) {
          debugPrint('[SessionManager] Cached session is valid');
          debugPrint('[SessionManager] PHPSESSID: ${_session!.phpSessionId.substring(0, 8)}...');
          debugPrint('[SessionManager] Token: ${_session!.token.substring(0, 8)}...');
        } else {
          debugPrint('[SessionManager] Cached session expired');
          _session = null;
        }
      } else {
        debugPrint('[SessionManager] No cached session found');
      }
      notifyListeners();
    } catch (e) {
      debugPrint('[SessionManager] Error loading cached session: $e');
    }
  }
  
  /// Save session to SharedPreferences
  Future<void> _saveSession(SessionData session) async {
    try {
      debugPrint('[SessionManager] Saving session to cache...');
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_prefsKeySession, jsonEncode(session.toJson()));
      debugPrint('[SessionManager] Session saved successfully');
    } catch (e) {
      debugPrint('[SessionManager] Error saving session: $e');
    }
  }
  
  /// Get valid session, throw if none available
  Future<SessionData> getSession() async {
    debugPrint('[SessionManager] getSession() called');
    
    if (_session != null && _session!.isValid) {
      debugPrint('[SessionManager] Returning valid cached session');
      return _session!;
    }
    
    debugPrint('[SessionManager] No valid session available');
    throw Exception('No valid session. Please connect via the app first.');
  }
  
  /// Set session manually (from WebView extraction or test)
  Future<void> setSession(String phpSessionId, String token) async {
    debugPrint('[SessionManager] Setting new session...');
    debugPrint('[SessionManager] PHPSESSID: ${phpSessionId.substring(0, 8)}...');
    debugPrint('[SessionManager] Token: ${token.substring(0, 8)}...');
    
    _session = SessionData(
      phpSessionId: phpSessionId,
      token: token,
      createdAt: DateTime.now(),
    );
    await _saveSession(_session!);
    _error = null;
    debugPrint('[SessionManager] Session set and saved');
    notifyListeners();
  }
  
  /// Set session for testing/debug with manually obtained values
  Future<void> setTestSession(String phpSessionId, String token) async {
    debugPrint('[SessionManager] Setting TEST session');
    await setSession(phpSessionId, token);
  }
  
  /// Clear session
  Future<void> clearSession() async {
    debugPrint('[SessionManager] Clearing session...');
    _session = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefsKeySession);
    debugPrint('[SessionManager] Session cleared');
    notifyListeners();
  }
  
  /// Static URL for WebView
  static String get loginUrl => _oasthUrl;
}
