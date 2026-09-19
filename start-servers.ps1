# Starts Velocity proxy + main-server + farm-server as three tabs in a single
# Windows Terminal window (instead of three separate windows).
# Run this via start.bat, or directly: powershell -ExecutionPolicy Bypass -File start-servers.ps1

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

& wt.exe `
    new-tab --title "Velocity"    "$root\run-velocity.bat" `; `
    new-tab --title "Main Server" "$root\run-main.bat" `; `
    new-tab --title "Farm Server" "$root\run-farm.bat"

Write-Host "Launched Velocity / Main Server / Farm Server as tabs in one Windows Terminal window."
Write-Host "Players connect to: <this PC's address>:25565"
