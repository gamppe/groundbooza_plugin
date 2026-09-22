@echo off
REM 마법전쟁 서버만 따로 띄웁니다 (이벤트 때만 사용).
REM Velocity/main/farm 은 start.bat 로 이미 떠 있어야 합니다.
call java25
cd /d "%~dp0magic-server"
java -jar paper.jar --nogui
