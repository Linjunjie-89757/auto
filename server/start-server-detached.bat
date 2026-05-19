@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "JAVA_EXE="
if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe"
if not defined JAVA_EXE set "JAVA_EXE=java"

set "APP_JAR=%SCRIPT_DIR%target\auto-platform-0.0.1-SNAPSHOT.jar"
set "OUT_LOG=%SCRIPT_DIR%server-live.out.log"
set "ERR_LOG=%SCRIPT_DIR%server-live.err.log"

echo Building backend jar...
call "%SCRIPT_DIR%mvnw.cmd" -DskipTests package
if errorlevel 1 (
  echo.
  echo [ERROR] Backend build failed.
  pause
  exit /b 1
)

if not exist "%APP_JAR%" (
  echo [ERROR] Missing server jar after build: "%APP_JAR%"
  pause
  exit /b 1
)

if exist "%SCRIPT_DIR%data\auto-platform.lock.db" del /f /q "%SCRIPT_DIR%data\auto-platform.lock.db" >nul 2>nul
if exist "%OUT_LOG%" del /f /q "%OUT_LOG%" >nul 2>nul
if exist "%ERR_LOG%" del /f /q "%ERR_LOG%" >nul 2>nul

echo Starting backend in background...
start "" /b "%JAVA_EXE%" -jar "%APP_JAR%" 1>>"%OUT_LOG%" 2>>"%ERR_LOG%"
echo Logs:
echo   %OUT_LOG%
echo   %ERR_LOG%
exit /b 0
