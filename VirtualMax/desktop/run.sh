#!/bin/bash
# AnonMax Desktop - Запуск клиента
cd "$(dirname "$0")"

echo "=== Запуск AnonMax Desktop ==="
if [ ! -d "node_modules" ]; then
    echo "[+] Установка зависимостей..."
    npm install
fi

npm start
