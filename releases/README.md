# 📦 Релизы и Готовые Сборки (VirtualMax Releases)

В этой папке собраны готовые к использованию скомпилированные программы (без необходимости устанавливать `npm` или `Node.js`):

### 📱 Для смартфонов:
- **`VirtualMax.apk`** *(~70 КБ)* — Готовый установочный файл для Android (5.0 — 15+).

> ℹ️ **Примечание:** зафиксированный в репозитории `VirtualMax.apk` — это последняя успешно собранная локальная сборка (v1.1.0). Актуальная версия исходного кода (v1.2.0) автоматически компилируется в свежий APK и установщики в GitHub Actions при создании тега `v*` — см. раздел «Как создаются релизы» ниже.

### 💻 Для компьютеров (Windows, Linux, macOS):
- **`VirtualMax-<версия>-Windows-x64.zip`** — Портативная версия для Windows 10/11 (распакуйте и дважды кликните `VirtualMax.exe`).
- **`VirtualMax-<версия>-Linux.AppImage`** — Автономный исполняемый файл для Linux (Ubuntu, Debian, Fedora, Arch).
- **`VirtualMax-<версия>-Linux.deb`** — Пакет для Debian/Ubuntu.
- **`VirtualMax-<версия>-mac.dmg`** — Установщик для macOS (Intel и Apple Silicon).

---

## ⚙️ Как создаются релизы (CI/CD)

Релизные сборки собираются автоматически облачными серверами **GitHub Actions**
(файлы `.github/workflows/build-android.yml` и `.github/workflows/build-desktop.yml`):

1. Создайте тег версии в репозитории:
   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```
2. В разделе **Actions** автоматически запустятся сборки `.apk`, `.exe`, `.AppImage`, `.deb` и `.dmg`.
3. Для каждого тега будет создан **GitHub Release** со всеми готовыми установщиками.

Также любую сборку можно запустить вручную из вкладки **Actions** (кнопка *Run workflow*).

### 📱 Сборка APK локально (без Gradle, минимальный размер):
```bash
cd mobile
chmod +x build.sh
./build.sh          # требуется JDK 17 и Android SDK (build-tools 34.0.0, platform android-34)
```

### 💻 Сборка Desktop локально:
```bash
cd desktop
npm install
npm run build:win    # .exe и portable для Windows
npm run build:linux  # .AppImage и .deb для Linux
npm run build:mac    # .dmg для macOS
```

---

## 📝 История версий

### v1.2.0
- ✅ Исправлена критическая ошибка компиляции Android (обработчик тумблера «Режим Невидимки»).
- ✅ Расширены списки блокировки трекеров на **обеих платформах** (Android `PrivacyInterceptor.java` + Desktop `blocklist.js`): добавлены `tns-counter.ru`, `top100.rambler.ru`, `adriver.ru`, `rutarget.ru`, `yandexads.com`, `googleadservices.com`, `doubleclick.net`, `criteo.com`, `moatads.com`, `scorecardresearch.com`, `taboola.com`, `outbrain.com`, `newrelic.com`, `sentry-cdn.com` и др.
- ✅ Тумблер «Режим Невидимки» (Ghost Mode) теперь полностью функционален на Android.
- ✅ Обновлена брендированная иконка (белый пузырёк МАКС с изумрудной литерой «V»).
- ✅ Добавлена автоматическая CI/CD-сборка релизов (`.github/workflows/`).

### v1.1.0
- Поддержка SOCKS5/HTTP прокси, маскировка параметров железа, нейтрализация `sendBeacon`.

---

## ⚖️ Лицензия

Проект распространяется под **GNU Affero General Public License v3.0 (AGPL-3.0)**.
VirtualMax — независимый проект с открытым исходным кодом, созданный в исследовательских целях и для защиты конфиденциальности пользователей. Не аффилирован с VK или платформой МАКС.
