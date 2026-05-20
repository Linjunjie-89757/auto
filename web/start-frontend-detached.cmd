@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "OUT_LOG=%SCRIPT_DIR%frontend-live.out.log"
set "ERR_LOG=%SCRIPT_DIR%frontend-live.err.log"

where npm.cmd >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm.cmd was not found in PATH.
  exit /b 1
)

if exist "%OUT_LOG%" del /f /q "%OUT_LOG%" >nul 2>nul
if exist "%ERR_LOG%" del /f /q "%ERR_LOG%" >nul 2>nul

echo Starting frontend dev server in minimized window...
start "" /min cmd.exe /k ""%SCRIPT_DIR%run-local-dev.cmd""
echo Logs:
echo   %OUT_LOG%
echo   %ERR_LOG%
exit /b 0
