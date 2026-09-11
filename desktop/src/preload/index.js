const { contextBridge, ipcRenderer } = require('electron');
const { buildSandboxJs } = require('./anti-tracking');

let lastConfig = null;

async function refreshConfig() {
  try {
    lastConfig = await ipcRenderer.invoke('virtualmax:get-config');
  } catch (e) {}
  return lastConfig;
}

function sandboxCode() {
  const cfg = lastConfig || {};
  const ghost = cfg.ghostMode !== false;                  // по умолчанию Невидимка вкл
  const blockNotifs = cfg.allowNotifications === false;  // блокировать, если тумблер выключен
  return buildSandboxJs(ghost, blockNotifs);
}

function injectIntoWebview(wv) {
  if (!wv || typeof wv.executeJavaScript !== 'function') return;
  try {
    wv.executeJavaScript(sandboxCode()).catch(() => {});
  } catch (e) {
    console.error('[VirtualMax Preload] Injection error:', e);
  }
}

function attachToWebview() {
  const wv = document.getElementById('max-webview');
  if (!wv || wv.__vmAttached) return;
  wv.__vmAttached = true;

  const inject = () => injectIntoWebview(wv);
  // Инъекция на каждый загруз/переход страницы мессенджера
  wv.addEventListener('dom-ready', inject);
  wv.addEventListener('did-navigate', inject);
  wv.addEventListener('did-navigate-in-page', inject);
  wv.addEventListener('did-finish-load', inject);

  // Восстановление сохранённого масштаба.
  const applyZoom = () => {
    const pct = lastConfig && lastConfig.textZoom ? Number(lastConfig.textZoom) : 100;
    if (pct >= 70 && pct <= 160 && typeof wv.setZoomFactor === 'function') {
      wv.setZoomFactor(pct / 100);
    }
  };
  wv.addEventListener('dom-ready', applyZoom);

  // target=_blank и внешние ссылки из гостевой страницы — в системный браузер
  // (трекинг-метки вырезаются в основном процессе).
  wv.addEventListener('new-window', (e) => {
    try {
      if (e && e.url) ipcRenderer.invoke('virtualmax:open-external', e.url);
    } catch (_) {}
  });
  // Современный Electron дополнительно эмитит 'will-frame-navigate';
  // подстраховка для внешних http(s)-ссылок, которые webview пытается открыть сам.
  wv.addEventListener('will-navigate', (e) => {
    try {
      if (e.url && !/^https?:\/\/([\w-]+\.)*max\.ru(\/|$|:)/i.test(e.url)) {
        e.preventDefault && e.preventDefault();
        ipcRenderer.invoke('virtualmax:open-external', e.url);
      }
    } catch (_) {}
  });

  // Реакция на смену настроек (Ghost Mode / Уведомления) без перезагрузки
  ipcRenderer.on('virtualmax:config-updated', () => {
    refreshConfig().then(() => {
      injectIntoWebview(wv);
      applyZoom();
    });
  });
}

window.addEventListener('DOMContentLoaded', () => {
  refreshConfig().then(attachToWebview);
});
window.addEventListener('load', () => {
  refreshConfig().then(attachToWebview);
});

contextBridge.exposeInMainWorld('VirtualMaxAPI', {
  getConfig: () => ipcRenderer.invoke('virtualmax:get-config'),
  updateConfig: (config) => ipcRenderer.invoke('virtualmax:update-config', config),
  clearData: () => ipcRenderer.invoke('virtualmax:clear-data'),
  openExternal: (url) => ipcRenderer.invoke('virtualmax:open-external', url),
  onBlockedEvent: (callback) => {
    ipcRenderer.on('virtualmax:blocked-event', (event, data) => callback(data));
  }
});
