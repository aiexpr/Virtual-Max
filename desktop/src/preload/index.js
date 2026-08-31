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
  const ghost = cfg.ghostMode !== false;                 // по умолчанию Режим Невидимки включён
  const blockNotifs = cfg.allowNotifications === false;   // блокировать, если тумблер выключен
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
  if (!wv) return;

  const inject = () => injectIntoWebview(wv);
  // Инъекция на каждый загруз/переход страницы мессенджера
  wv.addEventListener('dom-ready', inject);
  wv.addEventListener('did-navigate', inject);
  wv.addEventListener('did-navigate-in-page', inject);
  wv.addEventListener('did-finish-load', inject);

  // Реакция на смену настроек (Ghost Mode / Уведомления) без перезагрузки страницы
  ipcRenderer.on('virtualmax:config-updated', () => {
    refreshConfig().then(() => injectIntoWebview(wv));
  });
}

window.addEventListener('DOMContentLoaded', () => {
  refreshConfig().then(() => attachToWebview());
});
window.addEventListener('load', () => {
  refreshConfig().then(() => attachToWebview());
});

contextBridge.exposeInMainWorld('VirtualMaxAPI', {
  getConfig: () => ipcRenderer.invoke('virtualmax:get-config'),
  updateConfig: (config) => ipcRenderer.invoke('virtualmax:update-config', config),
  clearData: () => ipcRenderer.invoke('virtualmax:clear-data'),
  onBlockedEvent: (callback) => {
    ipcRenderer.on('virtualmax:blocked-event', (event, data) => callback(data));
  }
});