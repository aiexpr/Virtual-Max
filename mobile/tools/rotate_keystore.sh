#!/bin/bash
# ============================================================
# VirtualMax — Безопасная ротация ключа подписи APK
# ============================================================
# Назначение:
#   Текущий mobile/keystore.jks хранится в git с известным паролем
#   (virtualmax123) — это небезопасно. Скрипт генерирует НОВЫЙ ключ со
#   случайным паролем и печатает значения для GitHub Secrets:
#     - KEYSTORE_JKS     : base64-представление нового keystore
#     - KEYSTORE_PASS    : случайный пароль
#     - KEYSTORE_ALIAS   : алиас ключа
#
# ВАЖНО (критично!):
#   Ротация ключа меняет подпись APK. Уже установленное на устройстве
#   приложение, подписанное старым ключом, не обновится поверх — его нужно
#   УДАЛИТЬ и установить заново (Android отвергает смену подписи).
#   Убедитесь, что пользователи об этом предупреждены.
#
# Требуется JDK (keytool) в PATH. Запускать локально или на GitHub Runner.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."   # -> mobile/

OUT_KS="keystore.jks"
ALIAS="virtualmax"

# Генерируем случайный 24-символьный пароль (без небезопасных символов для shell)
PASS="$(openssl rand -base64 32 2>/dev/null | tr -dc 'A-Za-z0-9' | head -c 24)"
if [ -z "$PASS" ]; then
    PASS="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
fi

echo "=== Генерация нового ключа подписи ==="
# Чтобы не затирать старый ключ сразу, кладём рядом резервную копию
if [ -f "$OUT_KS" ]; then
    cp "$OUT_KS" "${OUT_KS}.backup"
    echo "Старый ключ сохранён как ${OUT_KS}.backup (можно удалить после проверки)."
fi

keytool -genkeypair -v \
    -keystore "$OUT_KS" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=VirtualMax, OU=Privacy, O=VirtualMax, L=Moscow, ST=Moscow, C=RU"

echo
echo "=== ГОТОВО. Добавьте эти значения в GitHub Secrets (Settings -> Secrets and variables -> Actions) ==="
echo
echo "KEYSTORE_PASS = $PASS"
echo "KEYSTORE_ALIAS = $ALIAS"
printf 'KEYSTORE_JKS   = '
base64 -w0 "$OUT_KS" 2>/dev/null || base64 "$OUT_KS" | tr -d '\n'
echo
echo
echo "Примечания:"
echo "  - Вставьте base64 одним сплошным блоком (без переводов строк)."
echo "  - После установки секретов удалите/не используйте старый keystore из git."
echo "  - Ключ подписи НЕ должен попадать в git-историю: добавьте mobile/keystore.jks в .gitignore."
