@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-portable.ps1"
if errorlevel 1 (
    echo.
    echo PC Hardware Analyzer could not be started. See the message above.
    pause
)

endlocal
