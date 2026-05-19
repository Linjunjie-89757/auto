@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

where npm.cmd >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm.cmd was not found in PATH.
  echo [ERROR] Install Node.js or add it to PATH first.
  pause
  exit /b 1
)

echo Starting frontend dev server on http://localhost:4173/
call npm.cmd run dev
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo [ERROR] Frontend exited with code %EXIT_CODE%.
  pause
)

exit /b %EXIT_CODE%
