const { contextBridge, ipcRenderer } = require('electron');
const { injectAntiTracking } = require('./anti-tracking');

try {
  injectAntiTracking();
} catch (e) {
  console.error('[VirtualMax Preload] Error:', e);
}

contextBridge.exposeInMainWorld('VirtualMaxAPI', {
  getConfig: () => ipcRenderer.invoke('virtualmax:get-config'),
  updateConfig: (config) => ipcRenderer.invoke('virtualmax:update-config', config),
  clearData: () => ipcRenderer.invoke('virtualmax:clear-data'),
  onBlockedEvent: (callback) => {
    ipcRenderer.on('virtualmax:blocked-event', (event, data) => callback(data));
  }
});
