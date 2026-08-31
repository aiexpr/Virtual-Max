@echo off
title AnonMax Desktop
cd /d "%~dp0"

if not exist "node_modules" (
    echo [+] Installing dependencies...
    call npm install
)

echo [+] Launching AnonMax Desktop...
call npm start
pause
