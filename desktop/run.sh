#!/bin/bash
# VirtualMax Desktop — Запуск клиента (Linux / macOS)
cd "$(dirname "$0")"

echo "=== Запуск VirtualMax Desktop ==="
if [ ! -d "node_modules" ]; then
    echo "[+] Установка зависимостей..."
    npm install
fi

npm start