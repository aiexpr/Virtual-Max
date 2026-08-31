/**
 * In-Page Anti-Tracking, Anti-Fingerprinting & Ghost Mode
 */

function injectAntiTracking() {
  const code = `
  (function() {
    'use strict';

    if (window.__ANONMAX_INJECTED__) return;
    window.__ANONMAX_INJECTED__ = true;

    console.log('%c[AnonMax Desktop]%c Privacy Sandbox Active', 'background: #00e676; color: #070a0f; font-weight: bold; padding: 2px 6px; border-radius: 4px;', 'color: #00e676; font-weight: bold;');

    // 1. Нейтрализация sendBeacon
    if (navigator.sendBeacon) {
      const origSendBeacon = navigator.sendBeacon.bind(navigator);
      navigator.sendBeacon = function(url, data) {
        const u = String(url).toLowerCase();
        if (
          u.includes('telemetry') || u.includes('metric') || u.includes('analytics') ||
          u.includes('stat') || u.includes('log') || u.includes('collector') ||
          u.includes('beacon') || u.includes('yandex') || u.includes('mail.ru') || u.includes('sentry')
        ) {
          return true; // Возвращаем true без отправки
        }
        return origSendBeacon(url, data);
      };
    }

    // 2. Перехват fetch и XMLHttpRequest (Телеметрия + Ghost Typing)
    const TELEMETRY_REGEX = /(?:telemetry|metrics|analytics|collector|webvisor|c_stat|c_stat\.php|stat_out|client_log|crash_report|top-fwz1|mc\.yandex|sentry\.io)/i;
    const TYPING_REGEX = /(?:typing|set_activity|activity_status|online_status)/i;

    const origFetch = window.fetch;
    window.fetch = async function(...args) {
      const url = args[0] ? (typeof args[0] === 'string' ? args[0] : args[0].url) : '';
      const urlStr = typeof url === 'string' ? url : '';

      if (TELEMETRY_REGEX.test(urlStr) || TYPING_REGEX.test(urlStr)) {
        return new Response(JSON.stringify({ status: 'ok', success: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      return origFetch.apply(this, args);
    };

    const origXHR = window.XMLHttpRequest;
    function SandboxedXHR() {
      const xhr = new origXHR();
      const origOpen = xhr.open;
      let isIntercepted = false;

      xhr.open = function(method, url, ...rest) {
        const urlStr = String(url);
        if (TELEMETRY_REGEX.test(urlStr) || TYPING_REGEX.test(urlStr)) {
          isIntercepted = true;
        }
        return origOpen.call(xhr, method, url, ...rest);
      };

      const origSend = xhr.send;
      xhr.send = function(body) {
        if (isIntercepted) {
          Object.defineProperty(xhr, 'readyState', { value: 4, writable: true });
          Object.defineProperty(xhr, 'status', { value: 200, writable: true });
          Object.defineProperty(xhr, 'responseText', { value: '{"status":"ok","success":true}', writable: true });
          Object.defineProperty(xhr, 'response', { value: '{"status":"ok","success":true}', writable: true });
          if (typeof xhr.onload === 'function') xhr.onload();
          if (typeof xhr.onreadystatechange === 'function') xhr.onreadystatechange();
          return;
        }
        return origSend.call(xhr, body);
      };

      return xhr;
    }
    window.XMLHttpRequest = SandboxedXHR;

    // 3. WebSocket Ghost Mode (Подавление пакетов typing)
    const OrigWebSocket = window.WebSocket;
    window.WebSocket = function(...args) {
      const ws = new OrigWebSocket(...args);
      const origSend = ws.send.bind(ws);
      ws.send = function(data) {
        if (typeof data === 'string') {
          try {
            const parsed = JSON.parse(data);
            if (parsed.type === 'typing' || parsed.action === 'typing' || parsed.event === 'typing_start') {
              return; // Глушим пакет печати
            }
          } catch(e) {}
        }
        return origSend(data);
      };
      return ws;
    };

    // 4. Anti-Fingerprint Canvas Noise
    const noise = { r: 1, g: -1, b: 0 };
    const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
    CanvasRenderingContext2D.prototype.getImageData = function(...args) {
      const imgData = origGetImageData.apply(this, args);
      if (imgData.data.length >= 4) {
        imgData.data[0] = (imgData.data[0] + noise.r + 256) % 256;
        imgData.data[1] = (imgData.data[1] + noise.g + 256) % 256;
      }
      return imgData;
    };

    // 5. Маскировка параметров Navigator
    try {
      Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8, configurable: true });
      Object.defineProperty(navigator, 'deviceMemory', { get: () => 8, configurable: true });
      if ('getBattery' in navigator) {
        delete navigator.getBattery;
      }
    } catch(e) {}

  })();
  `;

  const script = document.createElement('script');
  script.textContent = code;
  (document.head || document.documentElement).appendChild(script);
  script.remove();
}

module.exports = {
  injectAntiTracking
};
