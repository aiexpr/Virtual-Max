#!/bin/bash
# ============================================================
# VirtualMax — Автономная сборка APK без Gradle
# ============================================================
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=== [1/7] Генерация иконок VirtualMax ==="
# Устанавливаем Pillow если его нет (для локальной сборки)
pip3 install --quiet --break-system-packages --user Pillow 2>/dev/null || true
python3 tools/gen_icons.py

echo "=== [2/7] Подготовка рабочих директорий ==="
rm -rf build
mkdir -p build/gen build/bin/classes ../releases

# ------------------------------------------------------------
# Поиск инструментов Android SDK
# ------------------------------------------------------------
find_tool() {
    local name="$1"
    # 1. build-tools (последняя версия)
    local bt=$(ls -d ${ANDROID_HOME:-/opt/android-sdk}/build-tools/*/ 2>/dev/null | sort -V | tail -n1)
    if [ -x "${bt}${name}" ]; then echo "${bt}${name}"; return 0; fi
    # 2. command line tools
    if command -v "$name" >/dev/null; then command -v "$name"; return 0; fi
    # 3. Legacy/Arena paths
    for p in /home/user/android-tools /home/runner/android-tools /usr/lib/android-sdk/build-tools/debian; do
        if [ -x "$p/$name" ]; then echo "$p/$name"; return 0; fi
    done
    return 1
}

AAPT=$(find_tool aapt || echo "aapt")
ZIPALIGN=$(find_tool zipalign || echo "zipalign")
APKSIGNER=$(find_tool apksigner || echo "apksigner")

# Поиск android.jar
ANDROID_JAR=$(ls ${ANDROID_HOME:-/opt/android-sdk}/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -n1 || echo "/home/user/android-tools/android.jar")

# Поиск D8 (r8.jar)
R8_JAR=$(ls ${ANDROID_HOME:-/opt/android-sdk}/build-tools/*/lib/r8.jar 2>/dev/null | tail -n1 || echo "/home/user/android-tools/r8.jar")

echo "    Используем aapt: $AAPT"
echo "    Используем jar:  $ANDROID_JAR"

# ------------------------------------------------------------
echo "=== [3/7] Генерация R.java ресурсов через aapt ==="
"$AAPT" package -m -J build/gen \
    -M app/src/main/AndroidManifest.xml \
    -S app/src/main/res \
    -I "$ANDROID_JAR"

echo "=== [4/7] Компиляция Java исходного кода ==="
javac -encoding UTF-8 -source 8 -target 8 \
    -d build/bin/classes \
    -cp "$ANDROID_JAR" \
    build/gen/com/virtualmax/privacy/R.java \
    app/src/main/java/com/virtualmax/privacy/*.java

echo "=== [5/7] Генерация Dalvik/ART classes.dex (D8) ==="
if [ -f "$R8_JAR" ]; then
    java -cp "$R8_JAR" com.android.tools.r8.D8 \
        --min-api 21 \
        --output build/bin \
        --lib "$ANDROID_JAR" \
        build/bin/classes/com/virtualmax/privacy/*.class
else
    echo "⚠️ r8.jar не найден, попытка использовать системный d8..."
    d8 --min-api 21 --output build/bin --lib "$ANDROID_JAR" build/bin/classes/com/virtualmax/privacy/*.class
fi

echo "=== [6/7] Упаковка APK контейнера (Resources + DEX) ==="
"$AAPT" package -f \
    -M app/src/main/AndroidManifest.xml \
    -S app/src/main/res \
    -I "$ANDROID_JAR" \
    -F build/base.apk

cd build/bin && "$AAPT" add ../base.apk classes.dex && cd ../..

echo "=== [7/7] Zipalign и Подпись APK ==="
"$ZIPALIGN" -f -p 4 build/base.apk build/aligned.apk

# ------------------------------------------------------------------
# Подпись (Security):
# Пароль и алиас читаются из переменных окружения (задающихся в CI из
# GitHub Secrets). Для локальной сборки при отсутствии переменных
# используется значение по умолчанию (совпадает с существующим ключом).
# НЕ задавайте пароль в открытом виде в версионируемых файлах.
# ------------------------------------------------------------------
KEYSTORE_PASS="${KEYSTORE_PASS:-virtualmax123}"
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-virtualmax}"

if [ ! -f "keystore.jks" ]; then
    keytool -genkeypair -v -keystore keystore.jks -alias "$KEYSTORE_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -dname "CN=VirtualMax, OU=Privacy, O=VirtualMax, L=Moscow, ST=Moscow, C=RU"
fi

"$APKSIGNER" sign --ks keystore.jks --ks-pass pass:"$KEYSTORE_PASS" --ks-key-alias "$KEYSTORE_ALIAS" --key-pass pass:"$KEYSTORE_PASS" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out ../releases/VirtualMax.apk build/aligned.apk

echo "🎉 УСПЕШНО! Релиз: releases/VirtualMax.apk"
ls -lh ../releases/VirtualMax.apk