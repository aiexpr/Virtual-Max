# Добавление CI/CD workflow-файлов в GitHub

Файлы `build-android.yml` и `build-desktop.yml` **нельзя** запушнить из-под приложения Arena (у него нет права `workflows`). Остальные правки (UI + подпись) уже на ветке `arena/01a05653-virtual-max`. Осталось добавить эти два файла вручную через веб-интерфейс GitHub.

## Шаг 1. Добавить файл build-android.yml

1. Откройте в браузере: **https://github.com/aiexpr/Virtual-Max**
2. Вверху выберите ветку **`main`**.
3. Нажмите **Add file → Create new file**.
4. В поле имени файла введите: `.github/workflows/build-android.yml`
5. Вставьте содержимое (ниже) целиком.
6. Нажмите **Commit changes** → **Commit directly to the `main` branch** → **Commit changes**.

## Шаг 2. Добавить файл build-desktop.yml
Повторите те же действия с именем `.github/workflows/build-desktop.yml` и содержимым из второго блока ниже.

---

## Файл `.github/workflows/build-android.yml`

```yaml
# ============================================================
# VirtualMax — CI/CD: сборка подписанного APK для Android
# Запуск: push тега v* или вручную (workflow_dispatch).
# Принципы: Privacy-First, минимальный артефакт, детерминизм.
# ============================================================
name: Build Android APK

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

# Создание/обновление GitHub Release требует прав на запись в репозиторий.
# actions: write — для загрузки/скачивания артефактов.
permissions:
  contents: write
  actions: write

jobs:
  build-android:
    name: Build Android APK
    runs-on: ubuntu-latest
    steps:
      # 1. Клонирование репозитория
      - name: Checkout repository
        uses: actions/checkout@v4

      # Валидация: версия тега должна совпадать с versionName в build.gradle.
      - name: Validate version matches tag
        if: startsWith(github.ref, 'refs/tags/v')
        shell: bash
        run: |
          TAG="${GITHUB_REF_NAME#v}"
          APP_VERSION="$(grep -m1 'versionName' mobile/app/build.gradle | sed -E 's/.*"([^"]+)".*/\1/')"
          echo "Tag version: ${TAG}  |  App versionName: ${APP_VERSION}"
          if [ "${TAG}" != "${APP_VERSION}" ]; then
            echo "::error::Tag v${TAG} does not match versionName ${APP_VERSION} in mobile/app/build.gradle. Please bump the app version (and versionCode) before tagging."
            exit 1
          fi

      # 2. JDK 17 (Temurin) — требуется для javac/D8/apksigner
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      # 3. Android SDK (command-line tools) + установка SDK-компонентов
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Accept Android SDK licenses
        run: yes | sdkmanager --licenses

      # Фиксируем версии SDK/build-tools (детерминизм сборки).
      - name: Install pinned Android SDK components
        run: |
          sdkmanager "platforms;android-34" "build-tools;34.0.0"

      # 4. Ключ подписи.
      #    Приоритет: секрет KEYSTORE_JKS (base64) -> упакованный в репо keystore.jks.
      #    Это позволяет хранить приватный ключ вне git при наличии секрета.
      - name: Prepare signing keystore (from secret if provided)
        shell: bash
        env:
          KEYSTORE_JKS: ${{ secrets.KEYSTORE_JKS }}
        run: |
          if [ -n "$KEYSTORE_JKS" ]; then
            printf '%s' "$KEYSTORE_JKS" | base64 -d > mobile/keystore.jks
            echo "Using keystore from secret 'KEYSTORE_JKS'."
          else
            echo "No 'KEYSTORE_JKS' secret set — using committed mobile/keystore.jks."
          fi

      # 5. Сборка кастомным скриптом (БЕЗ Gradle)
      - name: Build APK with custom script
        shell: bash
        env:
          # Пароль кейсторы НЕ зашит в YAML — берём из GitHub Secrets.
          KEYSTORE_PASS: ${{ secrets.KEYSTORE_PASS }}
          # Алиас ключа. Передаём явно для детерминизма подписи.
          KEYSTORE_ALIAS: virtualmax
        run: |
          cd mobile
          chmod +x build.sh
          ./build.sh

      # 6. Проверка артефакта
      - name: Verify artifact exists and is non-empty
        shell: bash
        run: |
          ls -lh releases/VirtualMax.apk
          test -s releases/VirtualMax.apk
          file releases/VirtualMax.apk || true

      # 7. Загрузка артефакта в Actions (не только на теге — удобно для ручной проверки)
      - name: Upload VirtualMax.apk artifact
        uses: actions/upload-artifact@v4
        with:
          name: VirtualMax.apk
          path: releases/VirtualMax.apk
          if-no-files-found: error

      # 8. GitHub Release (только на теге v*)
      - name: Create GitHub Release
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v2
        with:
          files: releases/VirtualMax.apk
          fail_on_unmatched_files: true
          generate_release_notes: true
```

---

## Файл `.github/workflows/build-desktop.yml`

```yaml
# ============================================================
# VirtualMax — CI/CD: сборка установщиков Desktop (Electron)
# Windows (.exe nsis + portable), Linux (.AppImage + .deb), macOS (.dmg + .zip)
# Запуск: push тега v* или вручную (workflow_dispatch).
# Принципы: Privacy-First, детерминизм, нативная сборка на каждой ОС.
# ============================================================
name: Build Desktop Installers

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

# Создание/обновление GitHub Release требует прав на запись в репозиторий.
# actions: write — для загрузки/скачивания артефактов.
permissions:
  contents: write
  actions: write

jobs:
  # Каждая ОС собирает свой нативный набор установщиков (быстрее и надёжнее,
  # чем кросс-компиляция всех платформ с одной ОС).
  build-desktop:
    name: Build (${{ matrix.platform }})
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: ubuntu-latest
            platform: linux
            script: build:linux
          - os: windows-latest
            platform: windows
            script: build:win
          - os: macos-latest
            platform: macos
            script: build:mac
    steps:
      # 1. Клонирование репозитория
      - name: Checkout repository
        uses: actions/checkout@v4

      # 2. Node 20 + кэш npm по desktop/package-lock.json
      - name: Set up Node.js 20
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: desktop/package-lock.json

      # Валидация: версия тега должна совпадать с version в package.json.
      # Проверяется один раз на linux-агенте (package.json одинаков для всех ОС).
      - name: Validate version matches tag
        if: startsWith(github.ref, 'refs/tags/v') && matrix.platform == 'linux'
        shell: bash
        run: |
          TAG="${GITHUB_REF_NAME#v}"
          APP_VERSION="$(node -p "require('./desktop/package.json').version")"
          echo "Tag version: ${TAG}  |  App version: ${APP_VERSION}"
          if [ "${TAG}" != "${APP_VERSION}" ]; then
            echo "::error::Tag v${TAG} does not match version ${APP_VERSION} in desktop/package.json. Please bump the version before tagging."
            exit 1
          fi

      # 3. Установка зависимостей (детерминированно из package-lock.json)
      - name: Install dependencies
        working-directory: desktop
        run: npm ci

      # 4. Сборка установщиков для текущей ОС.
      #    macOS: подпись/нотаризация выполняется только при наличии
      #    Apple-секретов (CSC_LINK/CSC_KEY_PASSWORD/APPLE_ID/...).
      #    Без них сборка .dmg/.zip идёт с ad-hoc подписью и НЕ блокируется.
      - name: Build installers (${{ matrix.script }})
        working-directory: desktop
        env:
          CSC_LINK: ${{ secrets.CSC_LINK }}
          CSC_KEY_PASSWORD: ${{ secrets.CSC_KEY_PASSWORD }}
          APPLE_ID: ${{ secrets.APPLE_ID }}
          APPLE_APP_SPECIFIC_PASSWORD: ${{ secrets.APPLE_APP_SPECIFIC_PASSWORD }}
          APPLE_TEAM_ID: ${{ secrets.APPLE_TEAM_ID }}
        run: npm run ${{ matrix.script }}

      # 5. Проверка, что артефакты собраны
      - name: Verify desktop artifacts
        shell: bash
        run: |
          echo "--- desktop/dist contents ---"
          ls -la desktop/dist

      # 6. Загрузка артефактов в Actions (всегда, удобно для ручной проверки)
      - name: Upload desktop artifacts
        uses: actions/upload-artifact@v4
        with:
          name: VirtualMax-desktop-${{ matrix.platform }}
          path: |
            desktop/dist/*.exe
            desktop/dist/*.AppImage
            desktop/dist/*.deb
            desktop/dist/*.dmg
            desktop/dist/*.zip
          if-no-files-found: warn

  # 7. Единый релиз: собирает артефакты со всех ОС в один GitHub Release.
  #    Запускается только на теге v* и только после успешной сборки всех ОС.
  release:
    name: Create GitHub Release
    if: startsWith(github.ref, 'refs/tags/v')
    needs: build-desktop
    runs-on: ubuntu-latest
    steps:
      - name: Download all desktop artifacts
        uses: actions/download-artifact@v4
        with:
          path: artifacts

      - name: List downloaded artifacts
        shell: bash
        run: find artifacts -type f | sort

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          # Используем рекурсивный glob: upload-artifact сохраняет вложенную
          # структуру каталогов внутри артефакта.
          files: artifacts/**/*
          fail_on_unmatched_files: true
          generate_release_notes: true
```

---

## Шаг 3. Добавьте GitHub Secrets

После добавления файлов зайдите в **Settings → Secrets and variables → Actions → New repository secret**:

| Секрет | Обязательность | Значение |
| --- | --- | --- |
| `KEYSTORE_PASS` | Обязательно | Пароль keystore (для текущего ключа: `virtualmax123`; после ротации — новый). |
| `KEYSTORE_JKS` | Рекомендуется | `base64` содержимого `mobile/keystore.jks`. `Linux/macOS: cat mobile/keystore.jks | base64 -w0` |
| `CSC_LINK`, `CSC_KEY_PASSWORD` | Опционально | Подпись macOS/Windows (иначе macOS собирается с ad-hoc подписью). |
| `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` | Опционально | Нотаризация macOS. |

## Шаг 4. Запустите сборку

```bash
git tag v1.2.0
git push origin v1.2.0
```
Либо вручную: **Actions → Build Android APK / Build Desktop Installers → Run workflow**.

> ⚠️ Версия тега должна совпадать с версией приложения (сейчас всё на `1.2.0`).
