@echo off
title GupTa Server Installer

:: Self-elevate if not admin (needed for firewall rules + Program Files)
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell start-process "%~f0" -verb runas
    exit /b
)

cd /d "%~dp0"

set DEST=%ProgramFiles%\GupTaServer

echo Installing to %DEST%...
mkdir "%DEST%" 2>nul
xcopy "%~dp0*" "%DEST%" /E /I /Y >nul

echo Creating virtual environment and installing dependencies...
cd /d "%DEST%"
python -m venv venv
call venv\Scripts\activate.bat
pip install -r requirements.txt

echo.
echo Adding Windows Firewall exceptions for GupTa ports...

:: Remove old rules (clean slate)
netsh advfirewall firewall delete rule name="GupTa Server" >nul 2>&1

:: Allow inbound TCP on ports 8765 and 8766 for venv Python
netsh advfirewall firewall add rule name="GupTa Server" dir=in action=allow protocol=TCP localport=8765,8766 program="%DEST%\venv\Scripts\python.exe" enable=yes >nul 2>&1

if %errorlevel% equ 0 (
    echo  Firewall rules added successfully.
) else (
    echo  Could not add firewall rules - you may see a prompt on first run.
)

echo Creating desktop shortcut...

set VBS=%TEMP%\mklnk.vbs
echo Set ws = WScript.CreateObject("WScript.Shell") > "%VBS%"
echo dt = ws.SpecialFolders("Desktop") >> "%VBS%"
echo Set sc = ws.CreateShortcut(dt ^& "\GupTa Server.lnk") >> "%VBS%"
echo sc.TargetPath = "%DEST%\start.bat" >> "%VBS%"
echo sc.WorkingDirectory = "%DEST%" >> "%VBS%"
echo sc.Description = "GupTa Server" >> "%VBS%"
echo sc.Save >> "%VBS%"

cscript /nologo "%VBS%"
del "%VBS%" 2>nul

echo.
echo ============================================
echo  Installed!
echo  Shortcut "GupTa Server" added to Desktop.
echo.
echo  Double-click it to start the server, then
echo  scan the QR code shown in the GupTa app.
echo ============================================
pause
