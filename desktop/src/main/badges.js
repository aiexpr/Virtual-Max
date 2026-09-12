/**
 * VirtualMax — загрузка централизованного списка меток пользователей.
 *
 * Скачивает JSON-файл с метками в ОСНОВНОМ процессе (вне страницы мессенджера),
 * поэтому не упирается в CORS/CSP web.max.ru и не зависит от блокировщиков.
 *
 * Формат файла (см. badges.example.json):
 *   { "users": [ { "id": "...", "name": "...", "check": true,
 *                  "label": "Проверен", "color": "#00b0ff", "title": "..." } ] }
 * Допускается как объект с массивом `users`, так и просто массив.
 */

const BADGES_TIMEOUT_MS = 10000;
const BADGES_MAX_ITEMS = 5000;

/** Нормализует ответ сервера в плоский массив меток. */
function normalizeBadges(data) {
  let users = Array.isArray(data) ? data : (data && Array.isArray(data.users) ? data.users : null);
  if (!users) return null;
  return users
    .filter((u) => u && (u.id || u.name))
    .slice(0, BADGES_MAX_ITEMS);
}

/** Загружает и валидирует список меток по URL. Бросает исключение при ошибке. */
async function fetchBadgesList(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), BADGES_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      cache: 'no-store',
      headers: { 'User-Agent': 'VirtualMax' }
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    const list = normalizeBadges(data);
    if (!list) throw new Error('Неверный формат списка меток');
    return list;
  } finally {
    clearTimeout(timer);
  }
}

module.exports = {
  fetchBadgesList
};
