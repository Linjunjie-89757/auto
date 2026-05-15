@echo off
setlocal
cd /d D:\Project\auto\server

if exist "D:\Project\auto\server\data\auto-platform.lock.db" (
  del /f /q "D:\Project\auto\server\data\auto-platform.lock.db"
)

"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" -cp "D:\Project\auto\server\target\classes;D:\Project\auto\server\target\tmp-boot-libs\*" com.company.autoplatform.AutoPlatformApplication
