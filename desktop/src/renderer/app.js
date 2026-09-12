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
  const chkBadges = document.getElementById('chk-badges');
  const badgesStatus = document.getElementById('badges-status');
  const btnRefreshBadges = document.getElementById('btn-refresh-badges');

  let currentZoom = 1.0;

  function renderBadgesStatus(cfg) {
    if (!cfg || !badgesStatus) return;
    if (!cfg.badgesEnabled) {
      badgesStatus.textContent = 'Метки выключены';
      return;
    }
    const n = Array.isArray(cfg.badgesCache) ? cfg.badgesCache.length : 0;
    if (n > 0) {
      badgesStatus.textContent = `Загружено меток: ${n}`;
    } else {
      badgesStatus.textContent = 'Включено, список ещё не загружен';
    }
  }

  function applyZoom() {
    const clamped = Math.round(currentZoom * 10) / 10;
    currentZoom = clamped;
    webview.setZoomFactor(clamped);
    zoomLabel.textContent = `${Math.round(clamped * 100)}%`;
  }

  function persistZoom() {
    if (window.VirtualMaxAPI) {
      window.VirtualMaxAPI.updateConfig({ textZoom: Math.round(currentZoom * 100) });
    }
  }

  // Load configuration
  if (window.VirtualMaxAPI) {
    try {
      const cfg = await window.VirtualMaxAPI.getConfig();
      if (cfg) {
        chkMic.checked = !!cfg.allowMic;
        chkCamera.checked = !!cfg.allowCamera;
        chkNotifications.checked = !!cfg.allowNotifications;
        chkGhost.checked = cfg.ghostMode !== false;
        chkBadges.checked = !!cfg.badgesEnabled;
        renderBadgesStatus(cfg);
        if (cfg.textZoom) {
          currentZoom = cfg.textZoom / 100;
          applyZoom();
        }
      }
    } catch (e) {}

    // Real-time blocked event listener
    window.VirtualMaxAPI.onBlockedEvent((data) => {
      txtShieldStatus.textContent = `🛡️ VirtualMax: ${data.count} заблокировано`;
      if (data.log && data.log.length > 0) {
        liveLogBox.textContent = '';
        data.log.forEach((item) => {
          const row = document.createElement('div');
          row.textContent = `• ${item}`;
          liveLogBox.appendChild(row);
        });
      }
    });

    // Обновление статуса меток при фоновой перекачке списка.
    window.VirtualMaxAPI.onConfigUpdated((cfg) => {
      if (cfg) {
        chkBadges.checked = !!cfg.badgesEnabled;
        renderBadgesStatus(cfg);
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
        ghostMode: chkGhost.checked,
        badgesEnabled: chkBadges.checked
      });
      const cfg = await window.VirtualMaxAPI.getConfig();
      renderBadgesStatus(cfg);
    }
  }

  [chkMic, chkCamera, chkNotifications, chkGhost, chkBadges].forEach((chk) => {
    chk.addEventListener('change', saveConfig);
  });

  // Перекачать список меток вручную.
  btnRefreshBadges.addEventListener('click', async () => {
    if (!window.VirtualMaxAPI) return;
    badgesStatus.textContent = 'Загружаю список меток…';
    const cfg = await window.VirtualMaxAPI.refreshBadges();
    renderBadgesStatus(cfg);
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

  // Zoom controls (70–160%, сохраняется между запусками)
  btnZoomIn.addEventListener('click', () => {
    if (currentZoom < 1.6 - 1e-9) {
      currentZoom = Math.min(1.6, Math.round((currentZoom + 0.1) * 10) / 10);
      applyZoom();
      persistZoom();
    }
  });

  btnZoomOut.addEventListener('click', () => {
    if (currentZoom > 0.7 + 1e-9) {
      currentZoom = Math.max(0.7, Math.round((currentZoom - 0.1) * 10) / 10);
      applyZoom();
      persistZoom();
    }
  });

  // Settings Overlay
  function openSettings() {
    settingsOverlay.classList.remove('hidden');
  }
  function closeSettings() {
    settingsOverlay.classList.add('hidden');
  }

  btnToggleSettings.addEventListener('click', openSettings);
  btnCloseSettings.addEventListener('click', closeSettings);
  btnBackToChat.addEventListener('click', closeSettings);
  // Клик по статус-бейджу щита также открывает настройки/журнал.
  const shieldBadge = document.getElementById('btn-shield-status');
  if (shieldBadge) shieldBadge.addEventListener('click', openSettings);

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
    const key = e.key.toLowerCase();
    if (e.key === 'F5' || ((e.ctrlKey || e.metaKey) && key === 'r')) {
      e.preventDefault();
      webview.reload();
    } else if (e.key === 'Escape') {
      closeSettings();
    } else if ((e.ctrlKey || e.metaKey) && (key === '=' || key === '+')) {
      e.preventDefault();
      btnZoomIn.click();
    } else if ((e.ctrlKey || e.metaKey) && key === '-') {
      e.preventDefault();
      btnZoomOut.click();
    } else if ((e.ctrlKey || e.metaKey) && key === '0') {
      e.preventDefault();
      currentZoom = 1;
      applyZoom();
      persistZoom();
    } else if ((e.ctrlKey || e.metaKey) && key === ',') {
      e.preventDefault();
      openSettings();
    } else if (e.altKey && key === 'arrowleft') {
      if (webview.canGoBack()) webview.goBack();
    } else if (e.altKey && key === 'arrowright') {
      if (webview.canGoForward()) webview.goForward();
    }
  });
});
