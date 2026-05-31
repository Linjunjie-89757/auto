@echo off
setlocal

cd /d "%~dp0"

set "SPRING_PROFILES_ACTIVE=local-mysql"
set "DB_URL=jdbc:mysql://127.0.0.1:3307/auto_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
set "DB_USERNAME=auto_user"
set "DB_PASSWORD=auto123456"
set "DB_DRIVER=com.mysql.cj.jdbc.Driver"

"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" -jar "%~dp0target\auto-platform-0.0.1-SNAPSHOT.jar"
