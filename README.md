# 🛡️ VirtualMax — Приватный клиент-песочница для мессенджера МАКС

<p align="center">
  <img src="desktop/assets/icon.png" width="128" height="128" alt="VirtualMax Logo">
  <br>
  <b>Защищенный кроссплатформенный клиент для мессенджера МАКС (web.max.ru) с нулевым сбором данных</b>
  <br>
  <i>Блокировка телеметрии VK/Яндекс • Режим Невидимки • Управление датчиками • Android • Windows • Linux • macOS</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-5.0%2B-00e676?style=for-the-badge&logo=android&logoColor=white" alt="Android Version">
  <img src="https://img.shields.io/badge/Windows-10%20%2F%2011-0078d4?style=for-the-badge&logo=windows&logoColor=white" alt="Windows">
  <img src="https://img.shields.io/badge/Linux-AppImage%20%2F%20deb-fcc624?style=for-the-badge&logo=linux&logoColor=black" alt="Linux">
  <img src="https://img.shields.io/badge/macOS-Intel%20%26%20Apple%20Silicon-black?style=for-the-badge&logo=apple&logoColor=white" alt="macOS">
  <img src="https://img.shields.io/badge/License-AGPL--3.0-purple?style=for-the-badge" alt="License">
</p>

---

## 🔒 Ключевые возможности

1. **🛡️ Сетевая и аппаратная защита (Zero-Telemetry)**:
   - Полное уничтожение всех запросов к трекерам `VK / Mail.ru` (`top-fwz1.mail.ru`, `counter.yadro.ru`, `stat.vk.com`, `t.mail.ru`), **Яндекс Метрике**, **WebVisor** (`mc.yandex.ru`), **Sentry** и **AppMetrica**.
   - Блокировка внутренних эндпоинтов метрик (`/api/telemetry`, `/api/metrics`, `/log`).
   - Защита от утечки локального и VPN IP-адреса через WebRTC (`default_public_interface_only`).
   - Нейтрализация фоновых маяков `navigator.sendBeacon()`.

2. **🎛️ Аппаратные переключатели разрешений**:
   - 🎙️ **Микрофон** (Вкл/Выкл) — доступ к аудиозвонкам и голосовым сообщениям.
   - 📷 **Камера** (Вкл/Выкл) — доступ к видеозвонкам и фото.
   - 🔔 **Уведомления** (Вкл/Выкл) — системные push-уведомления.
   - 👻 **Режим Невидимки (Ghost Mode)** — подавление статуса «Печатает...» в чатах.

3. **🔗 Защита внешних ссылок (Link Sanitizer)**:
   - При переходе по ссылкам из сообщений приложение автоматически вырезает трекинг-метки (`utm_*`, `yclid`, `vk_ref`, `fbclid`, `gclid`) и открывает их во внешнем браузере.

4. **🔍 Масштабирование текста и Режим ПК**:
   - Регулировка размера шрифта (`-` / `+` от 70% до 160%) и переключение между мобильным и ПК режимами.

5. **📥 Менеджер загрузок медиа**:
   - Прямое сохранение голосовых сообщений, видео и файлов в папку «Загрузки».

6. **🧹 Экстренная очистка (Panic Wipe)**:
   - Удаление всех cookies, LocalStorage и кэша сессии в один клик.

---

## 📦 Репозиторий и структура файлов

```text
VirtualMax/
├── .github/workflows/
│   ├── build-android.yml    # CI/CD автосборка подписанного APK для Android
│   └── build-desktop.yml    # CI/CD автосборка .exe, .AppImage, .deb, .dmg для ПК
├── mobile/                  # Исходный код приложения для Android
│   ├── app/                 # Java код, ресурсы, манифест
│   ├── build.gradle         # Gradle конфигурация
│   └── build.sh             # Автономный скрипт сборки APK
├── desktop/                 # Исходный код приложения для ПК (Electron)
│   ├── src/                 # Главный процесс, Preload, UI
│   ├── assets/              # Иконки (icon.png, icon.ico)
│   ├── package.json         # Зависимости и конфигурация сборщика
│   ├── run.sh               # Запуск для Linux/macOS
│   └── run.bat              # Запуск для Windows
├── releases/                # Готовые релизные файлы
│   └── VirtualMax.apk       # Скомпилированный подписанный APK для Android
├── .gitignore
├── LICENSE                  # AGPL-3.0 License
├── SECURITY.md              # Политика безопасности
└── README.md                # Главная документация
```

---

## 🚀 Как запустить и собрать

### 📱 Смартфон (Android):
- **Готовый APK:** Файл `releases/VirtualMax.apk` готов к установке на телефон.
- **Сборка из исходников:**
  ```bash
  cd mobile
  chmod +x build.sh
  ./build.sh
  ```

### 💻 Компьютер (Windows, Linux, macOS):
- **Быстрый запуск:**
  - *Windows:* дважды кликните по `desktop/run.bat`
  - *Linux / macOS:* запустите `./desktop/run.sh`
- **Сборка релизных установщиков:**
  ```bash
  cd desktop
  npm install
  npm run build:win    # .exe установщик и portable для Windows
  npm run build:linux  # .AppImage и .deb для Linux
  npm run build:mac    # .dmg для macOS
  ```

---

---

## 🤖 CI/CD — автоматические сборки

Сборка релизов выполняется **GitHub Actions** (файлы `.github/workflows/build-android.yml`
и `.github/workflows/build-desktop.yml`) при создании тега `v*` или вручную через вкладку **Actions**.

### Что собирается
- **Android:** подписанный `VirtualMax.apk` кастомным скриптом (`mobile/build.sh`, **без Gradle**).
- **Desktop:** установщики `electron-builder` нативно на каждой ОС — `.exe` (nsis) + portable
  (Windows), `.AppImage` + `.deb` (Linux), `.dmg` + `.zip` (macOS).

### Требуемые GitHub Secrets
Пароль ключа подписи **не хранится** в YAML; он передаётся в сборку из секретов репозитория:

| Секрет | Обязательность | Назначение |
| --- | --- | --- |
| `KEYSTORE_PASS` | Обязательно | Пароль `mobile/keystore.jks` (alias `virtualmax`). Если не задан — используется значение по умолчанию (совпадает с текущим ключом). |
| `KEYSTORE_JKS` | Опционально (рекомендуется) | `base64`-представление самого keystore. При наличии перезаписывает `mobile/keystore.jks` — приватный ключ не хранится в git. |
| `CSC_LINK` / `CSC_KEY_PASSWORD` | Опционально | Подпись macOS/Windows-установщиков. Без них macOS собирается с `ad-hoc` подписью (пайплайн не блокируется). |
| `APPLE_ID` / `APPLE_APP_SPECIFIC_PASSWORD` / `APPLE_TEAM_ID` | Опционально | Нотаризация macOS. |

> ⚠️ Версия тега `v*` должна совпадать с номером версии приложения. CI проверяет это
> и останавливается с понятным сообщением при несовпадении (`mobile/app/build.gradle`,
> `mobile/app/src/main/AndroidManifest.xml`, `desktop/package.json`).

---

## 📈 История версий

### v1.2.0 (текущая)
- Исправлена критическая ошибка компиляции Android (обработчик тумблера «Режим Невидимки»).
- Расширены списки блокировки трекеров на **обеих платформах** — добавлены `tns-counter.ru`, `top100.rambler.ru`, `adriver.ru`, `rutarget.ru`, `yandexads.com`, `googleadservices.com`, `doubleclick.net`, `criteo.com`, `moatads.com`, `scorecardresearch.com`, `taboola.com`, `outbrain.com`, `newrelic.com`, `sentry-cdn.com` и др.
- Тумблер «Режим Невидимки» (Ghost Mode) теперь полностью функционален на Android.
- Обновлена брендированная иконка (белый пузырёк МАКС с изумрудной литерой «V»).
- Добавлена автоматическая CI/CD-сборка релизов (`.github/workflows/`).

### v1.1.0
- Поддержка SOCKS5/HTTP прокси, маскировка параметров железа, нейтрализация `sendBeacon`.

---

## ⚖️ Лицензия и Отказ от ответственности
- Проект распространяется под свободной лицензией **GNU Affero General Public License v3.0 (AGPL-3.0)**.
- **Отказ от ответственности:** VirtualMax — независимый проект с открытым исходным кодом, созданный в исследовательских целях и для защиты конфиденциальности пользователей. Не аффилирован с VK или платформой МАКС.
