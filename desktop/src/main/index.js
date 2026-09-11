const { app, BrowserWindow, ipcMain, session, shell, Menu } = require('electron');
const path = require('path');
const { configManager } = require('./config');
const { shouldBlockUrl, cleanUrlParams } = require('./blocklist');

let mainWindow = null;
const PARTITION_NAME = 'persist:virtualmax_desktop_session';
let blockedCount = 0;
const blockedLog = [];

// Любой popup/target=_blank из ЛЮБОГО webContents (включая гостевой <webview>)
// открывается в системном браузере с очисткой трекинг-меток; внутри приложения
// посторонние окна не создаются.
app.on('web-contents-created', (_event, contents) => {
  contents.setWindowOpenHandler(({ url }) => {
    openExternally(url);
    return { action: 'deny' };
  });
});

function createWindow() {
  configManager.init();

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 800,
    minHeight: 600,
    backgroundColor: '#070a0f',
    icon: path.join(__dirname, '../../assets/icon.png'),
    title: 'VirtualMax Desktop — Мессенджер МАКС (Приватный режим)',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      webviewTag: true,
      preload: path.join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  });

  const appSession = session.fromPartition(PARTITION_NAME);

  // WebRTC Leak Protection
  appSession.setWebRTCIPHandlingPolicy('default_public_interface_only');

  // Request Interception & Tracker Blocking
  appSession.webRequest.onBeforeRequest((details, callback) => {
    const { url } = details;
    const check = shouldBlockUrl(url);

    if (check.blocked) {
      blockedCount++;
      const time = new Date().toLocaleTimeString();
      const logItem = `[${time}] ${check.domain} (${check.category})`;
      blockedLog.unshift(logItem);
      if (blockedLog.length > 20) blockedLog.pop();

      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('virtualmax:blocked-event', {
          count: blockedCount,
          log: blockedLog
        });
      }
      return callback({ cancel: true });
    }

    callback({ cancel: false });
  });

  // Header Sanitizer
  appSession.webRequest.onBeforeSendHeaders((details, callback) => {
    const requestHeaders = { ...details.requestHeaders };
    requestHeaders['DNT'] = '1';
    requestHeaders['Sec-GPC'] = '1';
    delete requestHeaders['X-Client-Fingerprint'];
    callback({ cancel: false, requestHeaders });
  });

  // Proxy Support
  const cfg = configManager.getAll();
  if (cfg.proxyEnabled && cfg.proxyHost && cfg.proxyPort) {
    appSession.setProxy({
      proxyRules: `${cfg.proxyType}://${cfg.proxyHost}:${cfg.proxyPort}`,
      proxyBypassRules: '<local>'
    });
  }

  // Permission Request Handler
  appSession.setPermissionRequestHandler((webContents, permission, callback) => {
    const currentCfg = configManager.getAll();
    if (permission === 'media') {
      callback(currentCfg.allowMic || currentCfg.allowCamera);
      return;
    }
    if (permission === 'notifications') {
      callback(!!currentCfg.allowNotifications);
      return;
    }
    callback(false);
  });

  // External Links Handling (окна уровня основного процесса)
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    openExternally(url);
    return { action: 'deny' };
  });

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));

  setupMenu();
}

function setupMenu() {
  const template = [
    {
      label: 'VirtualMax',
      submenu: [
        { role: 'reload', label: 'Перезагрузить' },
        { role: 'forceReload', label: 'Принудительная перезагрузка' },
        { role: 'toggleDevTools', label: 'Инструменты разработчика' },
        { type: 'separator' },
        { role: 'quit', label: 'Выход' }
      ]
    },
    {
      label: 'Вид',
      submenu: [
        { role: 'resetZoom', label: 'Сбросить масштаб' },
        { role: 'zoomIn', label: 'Увеличить' },
        { role: 'zoomOut', label: 'Уменьшить' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: 'Полноэкранный режим' }
      ]
    }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// Открыть ссылку в системном браузере, предварительно вырезав трекинг-метки.
function openExternally(rawUrl) {
  try {
    const cleanUrl = cleanUrlParams(rawUrl);
    if (/^https?:\/\//i.test(cleanUrl)) {
      shell.openExternal(cleanUrl);
    }
  } catch (e) {
    console.error('[VirtualMax] openExternal failed:', e.message);
  }
}

// IPC Handlers
ipcMain.handle('virtualmax:open-external', (_event, url) => {
  openExternally(url);
  return true;
});

ipcMain.handle('virtualmax:get-config', () => {
  return configManager.getAll();
});

ipcMain.handle('virtualmax:update-config', (event, newConfig) => {
  configManager.update(newConfig);
  // Уведомляем preload/renderer — песочница переинъектируется с новыми настройками
  if (event.sender && !event.sender.isDestroyed()) {
    event.sender.send('virtualmax:config-updated', configManager.getAll());
  }
  return true;
});

ipcMain.handle('virtualmax:clear-data', async () => {
  const appSession = session.fromPartition(PARTITION_NAME);
  await appSession.clearStorageData();
  await appSession.clearCache();
  blockedCount = 0;
  blockedLog.length = 0;
  return true;
});

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
