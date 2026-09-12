# 🔧 Обновлённые workflow-файлы (перенести вручную)

> ⚠️ Эти файлы лежат здесь, а не в `.github/workflows/`, потому что токен,
> которым пушилась ветка, не имел права `workflows`. После слияния PR
> **скопируйте их в `.github/workflows/`** и удалите эту папку.

## Как применить

1. Замените содержимое:
   - `.github/workflows/build-android.yml` ← `workflow-fixes/build-android.yml`
   - `.github/workflows/build-desktop.yml` ← `workflow-fixes/build-desktop.yml`

   ```bash
   cp workflow-fixes/build-android.yml  .github/workflows/build-android.yml
   cp workflow-fixes/build-desktop.yml  .github/workflows/build-desktop.yml
   git add .github/workflows && git commit -m "ci: обновить workflow (fail-closed подпись, без Apple)"
   ```

2. Удалите эту папку:
   ```bash
   git rm -r workflow-fixes && git commit -m "chore: удалить временную папку workflow-fixes"
   ```

## Что изменено

### `build-android.yml`
- Ключ подписи теперь берётся **только** из секрета `KEYSTORE_JKS`; при его
  отсутствии сборка **падает** (`::error::` + `exit 1`), а не откатывается на
  закоммиченный ключ (который удалён из репозитория — см. SECURITY.md).

### `build-desktop.yml`
- Убраны неиспользуемые секреты подписи macOS (`APPLE_ID`,
  `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`, `CSC_LINK`,
  `CSC_KEY_PASSWORD`) — сборок для macOS нет.

## Не забудьте секреты

Для сборки Android в GitHub Secrets должны быть заданы:
- `KEYSTORE_JKS` — base64-ключ подписи;
- `KEYSTORE_PASS` — пароль ключа.

Сгенерировать их можно скриптом `mobile/tools/rotate_keystore.sh` (нужен JDK).
