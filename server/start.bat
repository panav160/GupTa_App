@echo off
title GupTa Server
echo ============================================
echo  Starting GupTa Servers...
echo ============================================

:: Use bundled Python — no system Python required
set PYTHON=%~dp0python\python.exe

if not exist "%PYTHON%" (
    echo ERROR: Bundled Python not found.
    echo Please re-download the GupTa server package.
    pause
    exit /b 1
)

:: Check setup has been run
"%PYTHON%" -c "import faster_whisper" >nul 2>&1
if %errorlevel% neq 0 (
    echo Dependencies not installed. Running setup first...
    call "%~dp0setup.bat"
)

:: ── Detect McAfee and warn user ───────────────────────────────────────────────
sc query "McShield" >nul 2>&1
if %errorlevel% equ 0 (
    echo.
    echo  [!] McAfee detected. If a popup appears asking about Python,
    echo      click ALLOW. You will only be asked once.
    echo.
)

:: ── Detect Norton and warn user ──────────────────────────────────────────────
sc query "Norton Security" >nul 2>&1
if %errorlevel% equ 0 (
    echo.
    echo  [!] Norton detected. If a popup appears, click ALLOW for Python.
    echo.
)

:: Start Whisper HTTP server (port 8081)
echo [1/3] Starting Whisper model server on port 8081...
start "Whisper HTTP" /MIN cmd /c ""%PYTHON%" "%~dp0whisper_http_server.py" > "%~dp0whisper_http.log" 2>&1"

:: Wait for model to load
echo  Waiting for model to load (this may take a minute)...
:wait_loop
timeout /t 2 /nobreak >nul
findstr "Model loaded" "%~dp0whisper_http.log" >nul 2>&1
if errorlevel 1 goto wait_loop

:: Start bridge server (port 8765)
echo [2/3] Starting command bridge on port 8765...
start "Bridge 8765" /MIN cmd /c ""%PYTHON%" "%~dp0bridge_server.py" > "%~dp0bridge.log" 2>&1"

:: Start wake bridge (port 8766)
echo [3/3] Starting wake-word bridge on port 8766...
start "Wake 8766" /MIN cmd /c ""%PYTHON%" "%~dp0wake_bridge.py" > "%~dp0wake.log" 2>&1"

:: Wait for bridges to start
timeout /t 3 /nobreak >nul

:: Print QR connection string
echo.
echo ============================================
echo  SCAN THIS IN THE GUPTA APP (Settings > QR)
echo ============================================
"%PYTHON%" -c "import sys; sys.path.insert(0, r'%~dp0'); import security; print(security.qr_string(8765))"
echo ============================================
echo.
echo  All servers running!
echo.
echo  If your phone cannot connect:
echo    1. Make sure phone and laptop are on the same WiFi
echo    2. If McAfee/Norton asked about Python, click Allow
echo    3. Re-scan the QR code in the app
echo.
echo  Close this window to stop all servers.
echo ============================================

pause

:: Cleanup on close
echo Shutting down servers...
taskkill /f /fi "WINDOWTITLE eq Whisper HTTP*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Bridge 8765*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Wake 8766*" >nul 2>&1
echo Done.
