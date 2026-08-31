#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=== [1/7] Генерация иконок VirtualMax ==="
python3 tools/gen_icons.py

echo "=== [2/7] Подготовка рабочих директорий ==="
rm -rf build
mkdir -p build/gen build/bin/classes ../releases

echo "=== [3/7] Генерация R.java ресурсов через aapt ==="
aapt package -m -J build/gen \
    -M app/src/main/AndroidManifest.xml \
    -S app/src/main/res \
    -I /home/user/android-tools/android.jar || aapt package -m -J build/gen -M app/src/main/AndroidManifest.xml -S app/src/main/res -I /home/runner/android-tools/android.jar

echo "=== [4/7] Компиляция Java исходного кода ==="
javac -encoding UTF-8 -source 8 -target 8 \
    -d build/bin/classes \
    -cp /home/user/android-tools/android.jar:/home/runner/android-tools/android.jar \
    build/gen/com/virtualmax/privacy/R.java \
    app/src/main/java/com/virtualmax/privacy/*.java

echo "=== [5/7] Генерация Dalvik/ART classes.dex (D8) ==="
java -cp /home/user/android-tools/r8.jar:/home/runner/android-tools/r8.jar com.android.tools.r8.D8 \
    --min-api 21 \
    --output build/bin \
    --lib /home/user/android-tools/android.jar \
    build/bin/classes/com/virtualmax/privacy/*.class || java -cp /home/runner/android-tools/r8.jar com.android.tools.r8.D8 --min-api 21 --output build/bin --lib /home/runner/android-tools/android.jar build/bin/classes/com/virtualmax/privacy/*.class

echo "=== [6/7] Упаковка APK контейнера (Resources + DEX) ==="
aapt package -f \
    -M app/src/main/AndroidManifest.xml \
    -S app/src/main/res \
    -I /home/user/android-tools/android.jar \
    -F build/base.apk || aapt package -f -M app/src/main/AndroidManifest.xml -S app/src/main/res -I /home/runner/android-tools/android.jar -F build/base.apk

cd build/bin && aapt add ../base.apk classes.dex && cd ../..

echo "=== [7/7] Zipalign и Подпись APK (v1+v2+v3) ==="
zipalign -f -p 4 build/base.apk build/aligned.apk

if [ ! -f "keystore.jks" ]; then
    keytool -genkeypair -v -keystore keystore.jks -alias virtualmax -keyalg RSA -keysize 2048 -validity 10000 -storepass virtualmax123 -keypass virtualmax123 -dname "CN=VirtualMax, OU=Privacy, O=VirtualMax, L=Moscow, ST=Moscow, C=RU"
fi

apksigner sign --ks keystore.jks --ks-pass pass:virtualmax123 --ks-key-alias virtualmax --key-pass pass:virtualmax123 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out ../releases/VirtualMax.apk build/aligned.apk

echo "=== Верификация цифровой подписи ==="
apksigner verify -v ../releases/VirtualMax.apk

echo "🎉 УСПЕШНО! Релизный файл готов: releases/VirtualMax.apk"
ls -lh ../releases/VirtualMax.apk
