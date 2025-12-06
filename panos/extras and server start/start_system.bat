@echo off
echo ==============================================
echo   OASTH Widget Backend System
echo ==============================================
echo.
echo Starting Python Server on Port 5000...
echo (Ensure your Android device can reach this PC IP)
echo.
echo Press Ctrl+C to stop.
echo.

cd /d "%~dp0"
python server.py

pause
