import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/session_manager.dart';
import '../services/widget_service.dart';
import '../models/models.dart';

/// Main activity matching Android's simple info screen
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const String currentVersion = '1.0.0';
  static const String githubReleases = 'https://github.com/rlutolli/oasth-tracker/releases';
  
  String _sessionStatus = 'Checking...';
  String _widgetsInfo = 'Loading...';
  String _versionInfo = 'v$currentVersion';
  bool _showUpdateButton = false;
  WidgetConfig? _config;

  @override
  void initState() {
    super.initState();
    WidgetService.initialize();
    _loadStatus();
  }

  Future<void> _loadStatus() async {
    await _loadWidgetConfig();
    await _checkSession();
  }

  Future<void> _loadWidgetConfig() async {
    final sessionManager = context.read<SessionManager>();
    final widgetService = WidgetService(sessionManager);
    final config = await widgetService.loadConfig();
    
    setState(() {
      _config = config;
      if (config != null && config.stopCode.isNotEmpty) {
        _widgetsInfo = '1 widget configured\n\n• ${config.stopName.isEmpty ? "Stop" : config.stopName} (${config.stopCode})';
        if (config.lineFilter.isNotEmpty) {
          _widgetsInfo += '\n  Filter: ${config.lineFilter}';
        }
      } else {
        _widgetsInfo = 'No widgets configured yet.\n\nLong-press home screen → + → OASTH Live';
      }
    });
  }

  Future<void> _checkSession() async {
    setState(() => _sessionStatus = 'Checking session...');
    
    try {
      final sessionManager = context.read<SessionManager>();
      debugPrint('[HomeScreen] Checking session validity...');
      
      if (sessionManager.isValid) {
        debugPrint('[HomeScreen] Session is valid');
        setState(() => _sessionStatus = '✓ Session active');
      } else {
        debugPrint('[HomeScreen] Session invalid or missing, need to acquire');
        setState(() => _sessionStatus = '⚠ Session expired - Tap to refresh');
      }
    } catch (e) {
      debugPrint('[HomeScreen] Session check error: $e');
      setState(() => _sessionStatus = '✗ Error: $e');
    }
  }

  Future<void> _openGithubReleases() async {
    final uri = Uri.parse(githubReleases);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121212),  // Android background
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // App Title
              const Text(
                'OASTH Live',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                'Real-time bus arrivals for Thessaloniki',
                style: TextStyle(
                  fontSize: 14,
                  color: Color(0xFFB0B0B0),
                ),
              ),
              const SizedBox(height: 32),

              // Session Status Section
              _buildSectionTitle('Session Status'),
              const SizedBox(height: 8),
              GestureDetector(
                onTap: _checkSession,
                child: Text(
                  _sessionStatus,
                  style: TextStyle(
                    fontSize: 14,
                    color: _sessionStatus.contains('✓') 
                        ? Colors.green 
                        : _sessionStatus.contains('⚠')
                            ? Colors.orange
                            : const Color(0xFFB0B0B0),
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Widgets Section
              _buildSectionTitle('Widgets'),
              const SizedBox(height: 8),
              Text(
                _widgetsInfo,
                style: const TextStyle(
                  fontSize: 14,
                  color: Color(0xFFB0B0B0),
                ),
              ),
              const SizedBox(height: 24),

              // Version Section
              _buildSectionTitle('Version & Updates'),
              const SizedBox(height: 8),
              Text(
                _versionInfo,
                style: const TextStyle(
                  fontSize: 14,
                  color: Color(0xFFB0B0B0),
                ),
              ),
              if (_showUpdateButton) ...[
                const SizedBox(height: 8),
                ElevatedButton(
                  onPressed: _openGithubReleases,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF1976D2),
                  ),
                  child: const Text('Download Update'),
                ),
              ],
              const SizedBox(height: 24),

              // How to Add Widget
              const Center(
                child: Text(
                  'Long-press home screen → + button → OASTH Live',
                  style: TextStyle(
                    fontSize: 12,
                    color: Color(0xFF707070),
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 32),

              // Credits
              const Center(
                child: Column(
                  children: [
                    Text(
                      'Made with ❤️ in Thessaloniki',
                      style: TextStyle(
                        fontSize: 12,
                        color: Color(0xFF707070),
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'github.com/rlutolli/oasth-tracker',
                      style: TextStyle(
                        fontSize: 11,
                        color: Color(0xFF707070),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.bold,
        color: Colors.white,
      ),
    );
  }
}
