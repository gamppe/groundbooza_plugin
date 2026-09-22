@echo off
timeout /t 8 >nul
call java25
cd /d "%~dp0magic-server"
java -jar paper.jar --nogui
