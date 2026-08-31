document.addEventListener('DOMContentLoaded', async () => {
  const webview = document.getElementById('max-webview');
  const txtShieldStatus = document.getElementById('txt-shield-status');
  const zoomLabel = document.getElementById('zoom-label');
  const liveLogBox = document.getElementById('live-log-box');

  const btnBack = document.getElementById('btn-back');
  const btnForward = document.getElementById('btn-forward');
  const btnReload = document.getElementById('btn-reload');
  const btnHome = document.getElementById('btn-home');

  const btnZoomIn = document.getElementById('btn-zoom-in');
  const btnZoomOut = document.getElementById('btn-zoom-out');

  const settingsOverlay = document.getElementById('settings-overlay');
  const btnToggleSettings = document.getElementById('btn-toggle-settings');
  const btnCloseSettings = document.getElementById('btn-close-settings');
  const btnBackToChat = document.getElementById('btn-back-to-chat');

  const btnTopClear = document.getElementById('btn-top-clear');
  const btnPurgeData = document.getElementById('btn-purge-data');

  const chkMic = document.getElementById('chk-mic');
  const chkCamera = document.getElementById('chk-camera');
  const chkNotifications = document.getElementById('chk-notifications');
  const chkGhost = document.getElementById('chk-ghost');

  let currentZoom = 1.0;

  // Load configuration
  if (window.VirtualMaxAPI) {
    try {
      const cfg = await window.VirtualMaxAPI.getConfig();
      if (cfg) {
        chkMic.checked = !!cfg.allowMic;
        chkCamera.checked = !!cfg.allowCamera;
        chkNotifications.checked = !!cfg.allowNotifications;
        chkGhost.checked = !!cfg.ghostMode;
      }
    } catch (e) {}

    // Real-time blocked event listener
    window.VirtualMaxAPI.onBlockedEvent((data) => {
      txtShieldStatus.textContent = `🛡️ VirtualMax: ${data.count} заблокировано`;
      if (data.log && data.log.length > 0) {
        liveLogBox.innerHTML = data.log.map(item => `<div>• ${item}</div>`).join('');
      }
    });
  }

  // Save configuration changes
  async function saveConfig() {
    if (window.VirtualMaxAPI) {
      await window.VirtualMaxAPI.updateConfig({
        allowMic: chkMic.checked,
        allowCamera: chkCamera.checked,
        allowNotifications: chkNotifications.checked,
        ghostMode: chkGhost.checked
      });
    }
  }

  [chkMic, chkCamera, chkNotifications, chkGhost].forEach(chk => {
    chk.addEventListener('change', saveConfig);
  });

  // Navigation
  btnBack.addEventListener('click', () => {
    if (webview.canGoBack()) webview.goBack();
  });

  btnForward.addEventListener('click', () => {
    if (webview.canGoForward()) webview.goForward();
  });

  btnReload.addEventListener('click', () => {
    webview.reload();
  });

  btnHome.addEventListener('click', () => {
    webview.loadURL('https://web.max.ru');
  });

  // Zoom controls
  btnZoomIn.addEventListener('click', () => {
    if (currentZoom < 1.6) {
      currentZoom += 0.1;
      webview.setZoomFactor(currentZoom);
      zoomLabel.textContent = `${Math.round(currentZoom * 100)}%`;
    }
  });

  btnZoomOut.addEventListener('click', () => {
    if (currentZoom > 0.7) {
      currentZoom -= 0.1;
      webview.setZoomFactor(currentZoom);
      zoomLabel.textContent = `${Math.round(currentZoom * 100)}%`;
    }
  });

  // Settings Overlay toggle
  function openSettings() {
    settingsOverlay.classList.remove('hidden');
  }

  function closeSettings() {
    settingsOverlay.classList.add('hidden');
  }

  btnToggleSettings.addEventListener('click', openSettings);
  btnCloseSettings.addEventListener('click', closeSettings);
  btnBackToChat.addEventListener('click', closeSettings);

  // Clear Session & Cache
  async function clearSession() {
    if (confirm('Вы действительно хотите удалить все cookies, историю и кэш мессенджера? Потребуется повторный вход.')) {
      if (window.VirtualMaxAPI) {
        await window.VirtualMaxAPI.clearData();
      }
      txtShieldStatus.textContent = '🛡️ VirtualMax: 0 трекеров заблокировано';
      liveLogBox.innerHTML = '<div class="log-empty">Сессия и кэш очищены. Защита активна.</div>';
      closeSettings();
      webview.loadURL('https://web.max.ru');
    }
  }

  btnTopClear.addEventListener('click', clearSession);
  btnPurgeData.addEventListener('click', clearSession);

  // Hotkeys
  window.addEventListener('keydown', (e) => {
    if (e.key === 'F5' || (e.ctrlKey && e.key.toLowerCase() === 'r')) {
      webview.reload();
    } else if (e.key === 'Escape') {
      closeSettings();
    } else if (e.ctrlKey && e.key === '=') {
      btnZoomIn.click();
    } else if (e.ctrlKey && e.key === '-') {
      btnZoomOut.click();
    }
  });
});
