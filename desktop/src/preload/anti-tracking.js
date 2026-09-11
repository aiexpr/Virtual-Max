/**
 * VirtualMax Desktop — In-Page Anti-Tracking, Anti-Fingerprinting & Ghost Mode.
 *
 * Возвращает JavaScript-песочницу для инъекции в страницу мессенджера МАКС
 * внутри <webview>. Песочница строится с учётом текущих настроек:
 *   - ghostMode           — Режим Невидимки: глушить «Печатает...» / присутствие
 *   - blockNotifications  — блокировать Notification API (тумблер выключен)
 *
 * Повторная инъекция при смене настроек не переустанавливает хуки, а
 * обновляет флаги через window.__VM_SET__ (без наложения перехватчиков).
 */

function buildSandboxJs(ghostMode, blockNotifications) {
  const ghost = ghostMode === true;
  const blockNotifs = blockNotifications === true;

  return `(function () {
    'use strict';

    // Предкомпилированные регулярные выражения (один раз на страницу).
    var TELEMETRY = /(?:telemetry|metrics|analytics|collector|webvisor|c_stat(?:\\.php)?|stat_out|client_log|crash_report|top-fwz1|mc\\.yandex|sentry\\.io|mail\\.ru\\/counter|pixel(?:\\.(?:png|gif|jpg|svg|webp))?)/i;
    var TYPING = /(?:typing|set_activity|activity_status|online_status|presence|chat_composing|user_typing)/i;
    var OK = JSON.stringify({ status: 'ok', success: true });

    if (window.__VIRTUALMAX_SHIELD__) {
      // Хуки уже установлены — просто обновляем настройки на лету.
      window.__VM_SET__(${ghost}, ${blockNotifs});
      return;
    }
    window.__VIRTUALMAX_SHIELD__ = true;

    var GHOST = ${ghost};
    var BLOCK_NOTIFS = ${blockNotifs};
    window.__VM_SET__ = function (g, n) { GHOST = g; BLOCK_NOTIFS = n; };

    console.log('%c[VirtualMax]%c Privacy Sandbox Active', 'background:#00e676;color:#070a0f;font-weight:bold;padding:2px 6px;border-radius:4px;', 'color:#00e676;font-weight:bold;');

    // 1. Нейтрализация navigator.sendBeacon
    if (navigator.sendBeacon) {
      navigator.sendBeacon = function () { return true; };
    }

    // 2. Перехват fetch (телеметрия + в Ghost Mode — статусы печати)
    var oFetch = window.fetch;
    if (oFetch) {
      window.fetch = function () {
        var u = '';
        try {
          u = typeof arguments[0] === 'string' ? arguments[0] : (arguments[0] && arguments[0].url) || '';
        } catch (e) {}
        if (TELEMETRY.test(u) || (GHOST && TYPING.test(u))) {
          return Promise.resolve(new Response(OK, { status: 200, headers: { 'Content-Type': 'application/json' } }));
        }
        return oFetch.apply(this, arguments);
      };
    }

    // 3. Перехват XMLHttpRequest (фейковый 200 OK)
    try {
      var oOpen = XMLHttpRequest.prototype.open;
      var oSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.open = function (m, u) {
        this.__vmUrl = String(u || '');
        return oOpen.apply(this, arguments);
      };
      XMLHttpRequest.prototype.send = function () {
        var u = this.__vmUrl || '';
        if (TELEMETRY.test(u) || (GHOST && TYPING.test(u))) {
          var self = this;
          try {
            Object.defineProperty(self, 'readyState', { value: 4, configurable: true });
            Object.defineProperty(self, 'status', { value: 200, configurable: true });
            Object.defineProperty(self, 'statusText', { value: 'OK', configurable: true });
            Object.defineProperty(self, 'responseText', { value: OK, configurable: true });
            Object.defineProperty(self, 'response', { value: OK, configurable: true });
            setTimeout(function () {
              try { if (typeof self.onreadystatechange === 'function') self.onreadystatechange(); } catch (e) {}
              try { if (typeof self.onload === 'function') self.onload(); } catch (e) {}
            }, 0);
            return;
          } catch (e) {}
        }
        return oSend.apply(this, arguments);
      };
    } catch (e) {}

    // 4. Ghost Mode: WebSocket — подавление пакетов typing/composing
    if (window.WebSocket) {
      try {
        var OWS = window.WebSocket;
        function VMWS() {
          var inst;
          try {
            inst = new (Function.prototype.bind.apply(OWS, [null].concat([].slice.call(arguments))));
          } catch (e) { inst = new OWS(arguments[0]); }
          var oS = inst.send.bind(inst);
          inst.send = function (data) {
            if (GHOST && typeof data === 'string') {
              try {
                var j = JSON.parse(data);
                var t = String((j && (j.type || j.event || j.action || j.method || j.cmd)) || '');
                if (TYPING.test(t)) return;
              } catch (e) {}
            }
            return oS(data);
          };
          return inst;
        }
        VMWS.prototype = OWS.prototype;
        VMWS.CONNECTING = OWS.CONNECTING;
        VMWS.OPEN = OWS.OPEN;
        VMWS.CLOSING = OWS.CLOSING;
        VMWS.CLOSED = OWS.CLOSED;
        window.WebSocket = VMWS;
      } catch (e) {}
    }

    // 5. Anti-Fingerprint: Canvas.
    // ВАЖНО для производительности:
    //  - getImageData: шум с большим шагом (каждый 32-й байт), без аллокаций;
    //  - toDataURL/toBlob: полный readback выполняется ОДИН РАЗ на канвас
    //    (WeakSet) и только для небольших канвасов (<=100k пикс.). Иначе
    //    тяжёлый readback на каждый вызов тормозит анимации/стикеры.
    try {
      var NOISE = Math.random() >= 0.5 ? 1 : -1;
      var touched = new WeakSet();
      var oGID = CanvasRenderingContext2D.prototype.getImageData;
      CanvasRenderingContext2D.prototype.getImageData = function () {
        var d = oGID.apply(this, arguments);
        if (d && d.data) {
          for (var i = 0; i < d.data.length; i += 32) {
            d.data[i] = (d.data[i] + NOISE + 256) % 256;
          }
        }
        return d;
      };
      function vmTouch(c) {
        try {
          if (touched.has(c)) return;
          touched.add(c);
          var w = c.width, h = c.height;
          if (w > 0 && h > 0 && w * h <= 100000) {
            var ctx = c.getContext('2d');
            if (ctx) {
              var d = oGID.call(ctx, 0, 0, w, h);
              if (d && d.data && d.data.length > 3) {
                d.data[0] = (d.data[0] + NOISE + 256) % 256;
                ctx.putImageData(d, 0, 0);
              }
            }
          }
        } catch (e) {}
      }
      var oTDU = HTMLCanvasElement.prototype.toDataURL;
      HTMLCanvasElement.prototype.toDataURL = function () {
        vmTouch(this);
        return oTDU.apply(this, arguments);
      };
      if (HTMLCanvasElement.prototype.toBlob) {
        var oTB = HTMLCanvasElement.prototype.toBlob;
        HTMLCanvasElement.prototype.toBlob = function () {
          vmTouch(this);
          return oTB.apply(this, arguments);
        };
      }
    } catch (e) {}

    // 6. Anti-Fingerprint: AudioContext
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (AC && AC.prototype && typeof AnalyserNode !== 'undefined' &&
          AnalyserNode.prototype && AnalyserNode.prototype.getFloatFrequencyData) {
        var oGFFD = AnalyserNode.prototype.getFloatFrequencyData;
        AnalyserNode.prototype.getFloatFrequencyData = function (arr) {
          oGFFD.call(this, arr);
          for (var i = 0; i < arr.length; i += 8) arr[i] += (i % 2 ? 0.01 : -0.01);
        };
      }
    } catch (e) {}

    // 7. Anti-Fingerprint: маскировка WebGL GPU
    try {
      function vmMaskGP(p) {
        if (p === 37445) return 'VirtualMax (Browser Vendor)';
        if (p === 37446) return 'VirtualMax Shield GPU';
        return null;
      }
      var oGP = WebGLRenderingContext.prototype.getParameter;
      WebGLRenderingContext.prototype.getParameter = function (p) {
        var f = vmMaskGP(p);
        return f !== null ? f : oGP.call(this, p);
      };
      if (typeof WebGL2RenderingContext !== 'undefined') {
        var oGP2 = WebGL2RenderingContext.prototype.getParameter;
        WebGL2RenderingContext.prototype.getParameter = function (p) {
          var f = vmMaskGP(p);
          return f !== null ? f : oGP2.call(this, p);
        };
      }
    } catch (e) {}

    // 8. Маскировка параметров устройства
    try {
      Object.defineProperty(navigator, 'hardwareConcurrency', { get: function () { return 8; }, configurable: true });
      try { Object.defineProperty(navigator, 'deviceMemory', { get: function () { return 8; }, configurable: true }); } catch (e) {}
      try { if (navigator.getBattery) navigator.getBattery = function () { return Promise.reject(); }; } catch (e) {}
    } catch (e) {}

    // 9. Защита сенсоров движения
    window.addEventListener('deviceorientation', function (e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('devicemotion', function (e) { e.stopImmediatePropagation(); }, true);

    // 10. Блокировка Web Notification (когда тумблер выключен) —
    //     применяется на лету при любой инъекции.
    function applyNotifPolicy() {
      if (BLOCK_NOTIFS && 'Notification' in window && window.Notification !== VMBlockedNotification) {
        window.Notification = VMBlockedNotification;
      }
    }
    function VMBlockedNotification() { this.close = function () {}; }
    VMBlockedNotification.permission = 'denied';
    VMBlockedNotification.requestPermission = function () { return Promise.resolve('denied'); };
    VMBlockedNotification.prototype.close = function () {};
    applyNotifPolicy();
    window.__VM_APPLY_NOTIF__ = applyNotifPolicy;
  })();`;
}

module.exports = {
  buildSandboxJs
};
