#!/bin/bash
# ============================================================
# VirtualMax — Автономная сборка APK без Gradle.
#
# ВНИМАНИЕ: в build-tools 30+ отсутствует бинарь `aapt` — работают
# только `aapt2 compile` и `aapt2 link`. Скрипт это учитывает.
# ============================================================
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=== [1/8] Генерация иконок VirtualMax ==="
# Иконки закоммичены в репозиторий — генерация нужна только при изменении логотипа.
if python3 -c "import PIL" 2>/dev/null; then
    python3 tools/gen_icons.py
else
    # Пытаемся поставить Pillow (для локальной сборки), при неудаче пропускаем.
    pip3 install --quiet --break-system-packages --user Pillow 2>/dev/null || true
    if python3 -c "import PIL" 2>/dev/null; then
        python3 tools/gen_icons.py
    else
        echo "    Pillow недоступен — использую закоммиченные иконки."
    fi
fi

echo "=== [2/8] Подготовка рабочих директорий ==="
rm -rf build
mkdir -p build/gen build/bin/classes releases

# ------------------------------------------------------------
# Поиск инструментов Android SDK
# ------------------------------------------------------------
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"

find_tool() {
    local name="$1"
    # 1. build-tools (самая свежая установленная версия)
    local bt
    bt=$(ls -d "$SDK_ROOT"/build-tools/*/ 2>/dev/null | sort -V | tail -n1 || true)
    if [ -n "${bt:-}" ] && [ -x "${bt}${name}" ]; then echo "${bt}${name}"; return 0; fi
    # 2. PATH
    if command -v "$name" >/dev/null 2>&1; then command -v "$name"; return 0; fi
    # 3. Legacy-пути
    for p in /home/user/android-tools /home/runner/android-tools /usr/lib/android-sdk/build-tools/debian; do
        if [ -x "$p/$name" ]; then echo "$p/$name"; return 0; fi
    done
    return 1
}

AAPT2=$(find_tool aapt2 || true)
ZIPALIGN=$(find_tool zipalign || true)
APKSIGNER=$(find_tool apksigner || true)
D8_BIN=$(find_tool d8 || true)

# android.jar — берём самую свежую платформу
ANDROID_JAR=$(ls "$SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -n1 || true)
if [ -z "${ANDROID_JAR:-}" ] || [ ! -f "$ANDROID_JAR" ]; then
    ANDROID_JAR="/home/user/android-tools/android.jar"
fi

# r8.jar (запасной способ запуска D8)
R8_JAR=$(ls "$SDK_ROOT"/build-tools/*/lib/r8.jar 2>/dev/null | sort -V | tail -n1 || true)
if [ -z "${R8_JAR:-}" ] && [ -f /home/user/android-tools/r8.jar ]; then
    R8_JAR=/home/user/android-tools/r8.jar
fi

[ -n "${AAPT2:-}" ]     || { echo "❌ aapt2 не найден в Android SDK (установите build-tools)"; exit 1; }
[ -n "${ZIPALIGN:-}" ]  || { echo "❌ zipalign не найден в Android SDK"; exit 1; }
[ -n "${APKSIGNER:-}" ] || { echo "❌ apksigner не найден в Android SDK"; exit 1; }
[ -f "$ANDROID_JAR" ]   || { echo "❌ android.jar не найден (установите platforms;android-34)"; exit 1; }

echo "    aapt2:       $AAPT2"
echo "    android.jar: $ANDROID_JAR"

# ------------------------------------------------------------
echo "=== [3/8] Компиляция ресурсов (aapt2 compile) ==="
"$AAPT2" compile --dir app/src/main/res -o build/res.zip

echo "=== [4/8] Линковка ресурсов и генерация R.java (aapt2 link) ==="
# AGP берёт package из namespace; автономный aapt2 требует атрибут package
# в манифесте. Подставляем его во временную копию, исходник не трогаем.
APP_PACKAGE="com.virtualmax.privacy"
# Версии — единый источник истины в app/build.gradle.
VERSION_NAME=$(grep -m1 'versionName' app/build.gradle | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE=$(grep -m1 'versionCode' app/build.gradle | sed -E 's/[^0-9]*([0-9]+).*/\1/')
MIN_SDK=$(grep -m1 'minSdk' app/build.gradle | sed -E 's/[^0-9]*([0-9]+).*/\1/')
TARGET_SDK=$(grep -m1 'targetSdk' app/build.gradle | sed -E 's/[^0-9]*([0-9]+).*/\1/')
echo "    Версия: ${VERSION_NAME} (${VERSION_CODE}), SDK ${MIN_SDK}…${TARGET_SDK}"
sed "0,/<manifest /s//<manifest package=\"${APP_PACKAGE}\" /" \
    app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest build/AndroidManifest.xml \
    -R build/res.zip \
    --java build/gen \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    --auto-add-overlay \
    -o build/base.apk

echo "=== [5/8] Компиляция Java исходного кода ==="
javac -encoding UTF-8 -source 8 -target 8 \
    -d build/bin/classes \
    -cp "$ANDROID_JAR" \
    build/gen/com/virtualmax/privacy/R.java \
    app/src/main/java/com/virtualmax/privacy/*.java

echo "=== [6/8] Генерация classes.dex (D8) ==="
CLASS_FILES=$(find build/bin/classes -name '*.class')
if [ -n "${D8_BIN:-}" ] && [ -x "$D8_BIN" ]; then
    "$D8_BIN" --min-api 21 --output build/bin --lib "$ANDROID_JAR" $CLASS_FILES
elif [ -n "${R8_JAR:-}" ] && [ -f "$R8_JAR" ]; then
    java -cp "$R8_JAR" com.android.tools.r8.D8 \
        --min-api 21 \
        --output build/bin \
        --lib "$ANDROID_JAR" \
        $CLASS_FILES
else
    echo "❌ D8 не найден (ни бинарь d8, ни lib/r8.jar)"; exit 1
fi

echo "=== [7/8] Упаковка classes.dex в APK и zipalign ==="
# Добавляем classes.dex в собранный aapt2 контейнер ресурсов.
if command -v zip >/dev/null 2>&1; then
    (cd build/bin && zip -q -X ../base.apk classes.dex)
else
    # JDK есть всегда — jar умеет обновлять zip-архивы.
    jar uf build/base.apk -C build/bin classes.dex
fi

"$ZIPALIGN" -f -p 4 build/base.apk build/aligned.apk

echo "=== [8/8] Подпись APK ==="
# ------------------------------------------------------------------
# Ключ и пароль подписи — секреты. Сборка НЕ генерирует ключ «на лету»
# и не подписывает «значениями по умолчанию»: без ключа и пароля она падает.
#   - локально: положите mobile/keystore.jks и задайте KEYSTORE_PASS;
#   - в CI: передайте base64-ключ через secret KEYSTORE_JKS и пароль KEYSTORE_PASS.
# См. SECURITY.md и mobile/tools/rotate_keystore.sh.
# ------------------------------------------------------------------
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-virtualmax}"

if [ -z "${KEYSTORE_PASS:-}" ]; then
    echo "❌ KEYSTORE_PASS не задан. Укажите пароль ключа подписи (см. SECURITY.md)." >&2
    exit 1
fi

if [ ! -f "keystore.jks" ]; then
    echo "❌ keystore.jks не найден. Положите ключ в mobile/keystore.jks (локально) " >&2
    echo "   или передайте base64-ключ через secret KEYSTORE_JKS (в CI)." >&2
    exit 1
fi

"$APKSIGNER" sign \
    --ks keystore.jks \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --ks-key-alias "$KEYSTORE_ALIAS" \
    --key-pass "pass:$KEYSTORE_PASS" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out releases/VirtualMax.apk build/aligned.apk

echo "🎉 УСПЕШНО! Релиз: releases/VirtualMax.apk"
ls -lh releases/VirtualMax.apk
