/**
 * VirtualMax Desktop — In-Page Anti-Tracking, Anti-Fingerprinting & Ghost Mode.
 *
 * Возвращает JavaScript-песочницу для инъекции в страницу мессенджера МАКС
 * внутри <webview>. Песочница строится с учётом текущих настроек:
 *   - ghostMode           — Режим Невидимки: глушить «Печатает...» / online-присутствие
 *   - blockNotifications  — блокировать Notification API (тумблер уведомлений выключен)
 */

function buildSandboxJs(ghostMode, blockNotifications) {
  const ghost = ghostMode === true;
  const blockNotifs = blockNotifications === true;

  return `(function () {
    'use strict';
    if (window.__VIRTUALMAX_SHIELD__) return;
    window.__VIRTUALMAX_SHIELD__ = true;

    console.log('%c[VirtualMax Desktop]%c Privacy Sandbox Active', 'background: #00e676; color: #070a0f; font-weight: bold; padding: 2px 6px; border-radius: 4px;', 'color: #00e676; font-weight: bold;');

    var GHOST = ${ghost};
    var BLOCK_NOTIFS = ${blockNotifs};

    var TELEMETRY = /(?:telemetry|metrics|analytics|collector|webvisor|c_stat(?:\\.php)?|stat_out|client_log|crash_report|top-fwz1|mc\\.yandex|sentry\\.io|mail\\.ru\\/counter|pixel(?:\\.(?:png|gif|jpg|svg))?)/i;
    var TYPING = /(?:typing|set_activity|activity_status|online_status|presence)/i;
    var OK = JSON.stringify({ status: 'ok', success: true });

    // 1. Neutralize navigator.sendBeacon
    if (navigator.sendBeacon) {
      navigator.sendBeacon = function () { return true; };
    }

    // 2. Intercept fetch (telemetry + ghost typing)
    var oFetch = window.fetch;
    window.fetch = function () {
      var u = '';
      try { u = typeof arguments[0] === 'string' ? arguments[0] : (arguments[0] && arguments[0].url) || ''; } catch (e) {}
      if (TELEMETRY.test(u) || (GHOST && TYPING.test(u))) {
        return Promise.resolve(new Response(OK, { status: 200, headers: { 'Content-Type': 'application/json' } }));
      }
      return oFetch.apply(this, arguments);
    };

    // 3. Intercept XMLHttpRequest (fake 200 OK)
    try {
      var oOpen = XMLHttpRequest.prototype.open;
      var oSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.open = function (m, u) { this.__vmUrl = String(u || ''); return oOpen.apply(this, arguments); };
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
              try { if (self.onreadystatechange) self.onreadystatechange(); } catch (e) {}
              try { if (self.onload) self.onload(); } catch (e) {}
            }, 0);
            return;
          } catch (e) {}
        }
        return oSend.apply(this, arguments);
      };
    } catch (e) {}

    // 4. WebSocket Ghost Mode (suppresses typing packets)
    if (GHOST) {
      try {
        var OWS = window.WebSocket;
        function VMWS() {
          var inst;
          try { inst = new (Function.prototype.bind.apply(OWS, [null].concat([].slice.call(arguments)))); } catch (e) { inst = new OWS(); }
          var oS = inst.send.bind(inst);
          inst.send = function (data) {
            if (typeof data === 'string') {
              try {
                var j = JSON.parse(data);
                var t = String((j && (j.type || j.event || j.action || j.method)) || '');
                if (/typing/i.test(t)) return;
              } catch (e) {}
            }
            return oS(data);
          };
          return inst;
        }
        VMWS.prototype = OWS.prototype;
        VMWS.CONNECTING = OWS.CONNECTING; VMWS.OPEN = OWS.OPEN; VMWS.CLOSING = OWS.CLOSING; VMWS.CLOSED = OWS.CLOSED;
        window.WebSocket = VMWS;
      } catch (e) {}
    }

    // 5. Anti-Fingerprint: Canvas noise (getImageData + toDataURL)
    try {
      var NOISE = Math.random() >= 0.5 ? 1 : -1;
      var oGID = CanvasRenderingContext2D.prototype.getImageData;
      CanvasRenderingContext2D.prototype.getImageData = function () {
        var d = oGID.apply(this, arguments);
        if (d && d.data) { for (var i = 0; i < d.data.length; i += 16) { d.data[i] = (d.data[i] + NOISE + 256) % 256; } }
        return d;
      };
      var oTDU = HTMLCanvasElement.prototype.toDataURL;
      HTMLCanvasElement.prototype.toDataURL = function () {
        try {
          var ctx = this.getContext && this.getContext('2d');
          if (ctx && this.width > 0 && this.height > 0 && this.width * this.height < 16777216) {
            var d = oGID.call(ctx, 0, 0, this.width, this.height);
            if (d && d.data && d.data.length > 0) { d.data[0] = (d.data[0] + NOISE + 256) % 256; ctx.putImageData(d, 0, 0); }
          }
        } catch (e) {}
        return oTDU.apply(this, arguments);
      };
    } catch (e) {}

    // 6. Anti-Fingerprint: AudioContext noise
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (AC && AC.prototype && AC.prototype.createAnalyser) {
        var oGFFD = AnalyserNode.prototype.getFloatFrequencyData;
        AnalyserNode.prototype.getFloatFrequencyData = function (arr) {
          oGFFD.call(this, arr);
          for (var i = 0; i < arr.length; i += 7) { arr[i] += (i % 2 ? 0.01 : -0.01); }
        };
      }
    } catch (e) {}

    // 7. Anti-Fingerprint: WebGL GPU masking
    try {
      var oGP = WebGLRenderingContext.prototype.getParameter;
      WebGLRenderingContext.prototype.getParameter = function (p) {
        if (p === 37445) { return 'VirtualMax (Browser Vendor)'; }
        if (p === 37446) { return 'VirtualMax Shield GPU'; }
        return oGP.call(this, p);
      };
      if (typeof WebGL2RenderingContext !== 'undefined') {
        var oGP2 = WebGL2RenderingContext.prototype.getParameter;
        WebGL2RenderingContext.prototype.getParameter = function (p) {
          if (p === 37445) { return 'VirtualMax (Browser Vendor)'; }
          if (p === 37446) { return 'VirtualMax Shield GPU'; }
          return oGP2.call(this, p);
        };
      }
    } catch (e) {}

    // 8. Mask device parameters
    try {
      Object.defineProperty(navigator, 'hardwareConcurrency', { get: function () { return 8; }, configurable: true });
      try { Object.defineProperty(navigator, 'deviceMemory', { get: function () { return 8; }, configurable: true }); } catch (e) {}
      try { delete navigator.getBattery; navigator.getBattery = undefined; } catch (e) {}
    } catch (e) {}

    // 9. Motion sensors protection
    window.addEventListener('deviceorientation', function (e) { e.stopImmediatePropagation(); }, true);
    window.addEventListener('devicemotion', function (e) { e.stopImmediatePropagation(); }, true);

    // 10. Notifications toggle
    if (BLOCK_NOTIFS && ('Notification' in window)) {
      try { Notification.requestPermission = function () { return Promise.resolve('denied'); }; } catch (e) {}
    }
  })();`;
}

module.exports = {
  buildSandboxJs
};