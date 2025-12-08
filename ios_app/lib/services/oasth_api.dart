import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/models.dart';
import 'session_manager.dart';

/// OASTH API client with extensive debug logging
class OasthApi {
  static const String _baseUrl = 'https://telematics.oasth.gr';
  static const String _apiUrl = '$_baseUrl/api/';
  
  final SessionManager _sessionManager;
  
  OasthApi(this._sessionManager);
  
  /// Get arrivals for a specific stop
  Future<List<BusArrival>> getArrivals(String stopCode) async {
    debugPrint('[OasthApi] ========== GET ARRIVALS ==========');
    debugPrint('[OasthApi] Stop code: $stopCode');
    
    try {
      debugPrint('[OasthApi] Getting session...');
      final session = await _sessionManager.getSession();
      debugPrint('[OasthApi] Got session, making API request...');
      
      final url = '$_apiUrl?act=getStopArrivals&p1=$stopCode';
      debugPrint('[OasthApi] URL: $url');
      
      final headers = {
        'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'X-CSRF-Token': session.token,
        'Cookie': 'PHPSESSID=${session.phpSessionId}',
        'Origin': _baseUrl,
        'Referer': '$_baseUrl/',
      };
      
      debugPrint('[OasthApi] Headers:');
      debugPrint('[OasthApi]   X-CSRF-Token: ${session.token.substring(0, 10)}...');
      debugPrint('[OasthApi]   Cookie: PHPSESSID=${session.phpSessionId.substring(0, 10)}...');
      
      final response = await http.post(
        Uri.parse(url),
        headers: headers,
      );
      
      debugPrint('[OasthApi] Response status: ${response.statusCode}');
      debugPrint('[OasthApi] Response body length: ${response.body.length}');
      debugPrint('[OasthApi] Response body (first 500 chars): ${response.body.substring(0, response.body.length.clamp(0, 500))}');
      
      // Check for unauthorized
      if (response.statusCode == 401 || 
          response.body.toLowerCase().contains('unauthorized') ||
          response.body.toLowerCase().contains('not authorized')) {
        debugPrint('[OasthApi] ⚠️ UNAUTHORIZED - Session may be expired');
        throw Exception('Unauthorized - session expired');
      }
      
      if (response.statusCode != 200) {
        debugPrint('[OasthApi] ⚠️ Non-200 status code: ${response.statusCode}');
        throw Exception('HTTP ${response.statusCode}');
      }
      
      debugPrint('[OasthApi] Parsing JSON response...');
      final List<dynamic> data = jsonDecode(response.body);
      debugPrint('[OasthApi] ✓ Parsed ${data.length} arrivals');
      
      final arrivals = data.map((item) => BusArrival.fromJson(item)).toList();
      
      for (int i = 0; i < arrivals.length && i < 3; i++) {
        debugPrint('[OasthApi]   ${arrivals[i].displayLine}: ${arrivals[i].estimatedMinutes} min');
      }
      if (arrivals.length > 3) {
        debugPrint('[OasthApi]   ... and ${arrivals.length - 3} more');
      }
      
      debugPrint('[OasthApi] ========== END GET ARRIVALS ==========');
      return arrivals;
      
    } catch (e, stackTrace) {
      debugPrint('[OasthApi] ✗ ERROR: $e');
      debugPrint('[OasthApi] Stack trace: $stackTrace');
      debugPrint('[OasthApi] ========== END GET ARRIVALS (ERROR) ==========');
      rethrow;
    }
  }
  
  /// Get stop info by code
  Future<String?> getStopInfo(String stopCode) async {
    debugPrint('[OasthApi] Getting stop info for: $stopCode');
    
    try {
      final session = await _sessionManager.getSession();
      
      final response = await http.post(
        Uri.parse('$_apiUrl?act=getStopArrivals&p1=$stopCode'),
        headers: {
          'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)',
          'Accept': 'application/json',
          'X-Requested-With': 'XMLHttpRequest',
          'X-CSRF-Token': session.token,
          'Cookie': 'PHPSESSID=${session.phpSessionId}',
        },
      );
      
      debugPrint('[OasthApi] Stop info response: ${response.statusCode}');
      
      // Try to extract stop description
      final regex = RegExp(r'"bstop_descr"\s*:\s*"([^"]+)"');
      final match = regex.firstMatch(response.body);
      final stopName = match?.group(1);
      debugPrint('[OasthApi] Extracted stop name: $stopName');
      return stopName;
    } catch (e) {
      debugPrint('[OasthApi] Error getting stop info: $e');
      return null;
    }
  }
}
