@echo off
echo ============================================
echo  GupTa - First Time Setup
echo ============================================
echo.

:: Use bundled Python — no system Python required
set PYTHON=%~dp0python\python.exe

if not exist "%PYTHON%" (
    echo ERROR: Bundled Python not found.
    echo Please re-download the GupTa server package.
    pause
    exit /b 1
)

echo Installing server dependencies...
"%PYTHON%" -m pip install -r "%~dp0requirements.txt" --no-warn-script-location -q
if %errorlevel% neq 0 (
    echo ERROR: Failed to install dependencies.
    echo Check your internet connection and try again.
    pause
    exit /b 1
)

echo.
echo Adding Windows Firewall exceptions for GupTa ports...

:: Remove old rules if they exist (clean slate)
netsh advfirewall firewall delete rule name="GupTa Server" >nul 2>&1

:: Allow inbound TCP on ports 8765 and 8766 for bundled Python
netsh advfirewall firewall add rule name="GupTa Server" dir=in action=allow protocol=TCP localport=8765,8766 program="%~dp0python\python.exe" enable=yes >nul 2>&1

if %errorlevel% equ 0 (
    echo  Firewall rules added successfully.
) else (
    echo  Could not add firewall rules automatically.
    echo  If the phone cannot connect, manually allow Python through your firewall.
)

echo.
echo ============================================
echo  Setup complete!
echo  Click Start Server to launch.
echo ============================================
