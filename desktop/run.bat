@echo off
title VirtualMax Desktop
cd /d "%~dp0"

if not exist "node_modules" (
    echo [+] Installing dependencies...
    call npm install
)

echo [+] Launching VirtualMax Desktop...
call npm start
pause