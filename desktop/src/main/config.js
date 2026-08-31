const fs = require('fs');
const path = require('path');
const { app } = require('electron');

const DEFAULT_CONFIG = {
  allowMic: true,
  allowCamera: false,
  allowNotifications: true,
  ghostMode: true,
  textZoom: 100,
  desktopMode: false,
  proxyEnabled: false,
  proxyType: 'socks5',
  proxyHost: '127.0.0.1',
  proxyPort: '9050',
  targetUrl: 'https://web.max.ru'
};

// Файлы конфигурации старых версий (для миграции)
const LEGACY_CONFIG_FILES = ['anonmax_desktop_config.json'];

class ConfigManager {
  constructor() {
    this.configPath = null;
    this.config = { ...DEFAULT_CONFIG };
  }

  init() {
    try {
      const userData = app.getPath('userData');
      this.configPath = path.join(userData, 'virtualmax_desktop_config.json');
      // Миграция со старого файла конфигурации
      if (!fs.existsSync(this.configPath)) {
        for (const legacy of LEGACY_CONFIG_FILES) {
          const legacyPath = path.join(userData, legacy);
          if (fs.existsSync(legacyPath)) {
            try { fs.renameSync(legacyPath, this.configPath); } catch (e) {}
            break;
          }
        }
      }
      this.load();
    } catch (e) {
      this.config = { ...DEFAULT_CONFIG };
    }
  }

  load() {
    if (!this.configPath) return;
    try {
      if (fs.existsSync(this.configPath)) {
        const raw = fs.readFileSync(this.configPath, 'utf8');
        this.config = { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
      } else {
        this.save();
      }
    } catch (e) {
      this.config = { ...DEFAULT_CONFIG };
    }
  }

  save() {
    if (!this.configPath) return;
    try {
      fs.writeFileSync(this.configPath, JSON.stringify(this.config, null, 2), 'utf8');
    } catch (e) {}
  }

  getAll() {
    return { ...this.config };
  }

  update(newConfig) {
    this.config = { ...this.config, ...newConfig };
    this.save();
  }
}

const configManager = new ConfigManager();

module.exports = {
  configManager
};
