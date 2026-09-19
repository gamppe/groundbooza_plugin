@echo off
timeout /t 5 >nul
call java25
cd /d "%~dp0main-server"
java -jar paper.jar --nogui
