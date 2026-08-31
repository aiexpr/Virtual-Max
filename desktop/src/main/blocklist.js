/**
 * AnonMax Desktop - Blocklists & Rules
 */

const BLOCKED_DOMAINS = [
  // VK / Mail.ru трекеры
  'top-fwz1.mail.ru',
  'counter.yadro.ru',
  't.mail.ru',
  'ad.mail.ru',
  'stat.vk.com',
  'stats.vk-portal.net',
  'trk.mail.ru',
  'target.my.com',
  'rb.mail.ru',
  'ok.ru/counter',
  'pulse.mail.ru',
  'mstat.my.com',
  'stat.mail.ru',
  'vk.com/rtrg',
  'vk.com/c_stat.php',
  'vk.com/ads.php',
  'vk.com/stat_out.php',
  'connect.vk.com/stat',

  // Yandex Метрика, WebVisor, AppMetrica
  'mc.yandex.ru',
  'metrika.yandex.ru',
  'an.yandex.ru',
  'yandex.ru/clck',
  'appmetrica.yandex.net',
  'awaps.yandex.net',
  'bs.yandex.ru',
  'mc.webvisor.org',
  'adfox.yandex.ru',

  // Sentry, Google Analytics, крэш-логгеры
  'sentry.io',
  '*.sentry.io',
  'google-analytics.com',
  'www.google-analytics.com',
  'googletagmanager.com',
  'clarity.ms',
  'hotjar.com',
  'mixpanel.com',
  'amplitude.com',
  'segment.io',
  'bugsnag.com',
  'datadoghq.com',
  'app.adjust.com',
  'appsflyer.com',

  // Дополнительные сети аналитики и рекламных трекеров
  'tns-counter.ru',
  'tm.tns-counter.ru',
  'top100.rambler.ru',
  'adriver.ru',
  'rutarget.ru',
  'yandexads.com',
  'ads.yandex.ru',
  'googleadservices.com',
  'adservice.google.com',
  'doubleclick.net',
  'criteo.com',
  'moatads.com',
  'scorecardresearch.com',
  'taboola.com',
  'outbrain.com',
  'newrelic.com',
  'cdn-api.newrelic.com',
  'sentry-cdn.com',
  'browser.sentry-cdn.com'
];

const BLOCKED_URL_PATTERNS = [
  /\/api\/v?\d*\/(?:telemetry|metrics|analytics|stats|collector|event(?:s)?|beacon|client_log|crash_report)/i,
  /\/(?:telemetry|tracking|collector|beacon|webvisor|metrika|c_stat|stat_out|log_event)/i,
  /\/pixel\.(?:png|gif|jpg|svg)/i
];

const STRIP_PARAMS = [
  'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content',
  'utm_referrer', 'yclid', 'gclid', 'fbclid', 'vk_ref', '_openstat',
  'from', 'ref', 'stat_id', 'device_id', 'fingerprint', 'fp'
];

function shouldBlockUrl(urlString) {
  try {
    const parsed = new URL(urlString);
    const hostname = parsed.hostname.toLowerCase();
    const pathname = parsed.pathname.toLowerCase();

    for (const domain of BLOCKED_DOMAINS) {
      if (domain.startsWith('*.')) {
        const root = domain.slice(2);
        if (hostname.endsWith(root)) {
          return { blocked: true, domain: hostname, category: 'Трекер / Аналитика' };
        }
      } else if (hostname === domain || hostname.endsWith('.' + domain)) {
        return { blocked: true, domain: hostname, category: 'VK / Yandex Аналитика' };
      }
    }

    for (const pattern of BLOCKED_URL_PATTERNS) {
      if (pattern.test(pathname) || pattern.test(parsed.search)) {
        return { blocked: true, domain: hostname, category: 'Телеметрия API' };
      }
    }

    return { blocked: false, domain: hostname };
  } catch (err) {
    return { blocked: false, domain: '' };
  }
}

function cleanUrlParams(urlString) {
  try {
    const parsed = new URL(urlString);
    let changed = false;
    for (const param of STRIP_PARAMS) {
      if (parsed.searchParams.has(param)) {
        parsed.searchParams.delete(param);
        changed = true;
      }
    }
    return changed ? parsed.toString() : urlString;
  } catch {
    return urlString;
  }
}

module.exports = {
  shouldBlockUrl,
  cleanUrlParams
};
