@echo off
cd /d D:\Project\auto\server
"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" -cp "D:\Project\auto\server\target\classes;D:\Project\auto\server\target\tmp-boot-libs\*" com.company.autoplatform.AutoPlatformApplication 1>>"D:\Project\auto\server\server-live.out.log" 2>>"D:\Project\auto\server\server-live.err.log"

