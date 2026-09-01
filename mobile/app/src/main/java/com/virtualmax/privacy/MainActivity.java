package com.virtualmax.privacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private ScrollView settingsContainer;
    private TextView tvShieldStatus, tvZoomValue, tvBlockedLog;
    private Button btnBack, btnReload, btnTopToggle;
    private Button btnNavMessenger, btnNavSettings, btnNavClear;
    private Button btnSettingsClear, btnSettingsBackToChat;
    private Button btnZoomMinus, btnZoomPlus;

    private Switch switchMic, switchCamera, switchNotifications, switchGhost, switchDesktop;

    private int blockedCount = 0;
    private int currentZoom = 100;
    private final LinkedList<String> blockedLogList = new LinkedList<>();

    private SharedPreferences prefs;
    private static final String PREFS_NAME = "virtualmax_settings";
    private static final String KEY_MIC = "allow_mic";
    private static final String KEY_CAMERA = "allow_camera";
    private static final String KEY_NOTIFICATIONS = "allow_notifications";
    private static final String KEY_GHOST = "ghost_mode";
    private static final String KEY_DESKTOP = "desktop_mode";
    private static final String KEY_ZOOM = "text_zoom";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";
    private static final String KEY_PROXY_ENABLED = "proxy_enabled";

    private static final String TARGET_URL = "https://web.max.ru";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentZoom = prefs.getInt(KEY_ZOOM, 100);

        // View Binding
        webView = findViewById(R.id.webView);
        settingsContainer = findViewById(R.id.settingsContainer);
        tvShieldStatus = findViewById(R.id.tvShieldStatus);
        tvZoomValue = findViewById(R.id.tvZoomValue);
        tvBlockedLog = findViewById(R.id.tvBlockedLog);

        btnBack = findViewById(R.id.btnBack);
        btnReload = findViewById(R.id.btnReload);
        btnTopToggle = findViewById(R.id.btnTopToggle);

        btnNavMessenger = findViewById(R.id.btnNavMessenger);
        btnNavSettings = findViewById(R.id.btnNavSettings);
        btnNavClear = findViewById(R.id.btnNavClear);

        btnSettingsClear = findViewById(R.id.btnSettingsClear);
        btnSettingsBackToChat = findViewById(R.id.btnSettingsBackToChat);

        btnZoomMinus = findViewById(R.id.btnZoomMinus);
        btnZoomPlus = findViewById(R.id.btnZoomPlus);

        switchMic = findViewById(R.id.switchMic);
        switchCamera = findViewById(R.id.switchCamera);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchGhost = findViewById(R.id.switchGhost);
        switchDesktop = findViewById(R.id.switchDesktop);

        setupWebView();
        setupProxy();
        setupControls();
        setupSettingsToggles();

        applySystemInsets();

        webView.loadUrl(TARGET_URL);
    }

    /**
     * Учитываем системные панели (статус-бар и жест-навигацию), чтобы контент
     * и нижняя панель не обрезались системной навигацией на современных
     * устройствах (edge-to-edge).
     */
    private void applySystemInsets() {
        View root = findViewById(R.id.root);
        View topBar = findViewById(R.id.topBar);
        View bottomBar = findViewById(R.id.bottomBar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    int left = insets.getSystemWindowInsetLeft();
                    int right = insets.getSystemWindowInsetRight();

                    topBar.setPadding(left, top, right, 0);
                    bottomBar.setPadding(left, 0, right, bottom);
                    return insets;
                }
            });
        }
    }

    private void setupProxy() {
        if (prefs.getBoolean(KEY_PROXY_ENABLED, false)) {
            String host = prefs.getString(KEY_PROXY_HOST, "");
            int port = Integer.parseInt(prefs.getString(KEY_PROXY_PORT, "0"));
            if (!host.isEmpty() && port > 0) {
                System.setProperty("http.proxyHost", host);
                System.setProperty("http.proxyPort", String.valueOf(port));
                System.setProperty("https.proxyHost", host);
                System.setProperty("https.proxyPort", String.valueOf(port));
            }
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(currentZoom);

        boolean isDesktop = prefs.getBoolean(KEY_DESKTOP, false);
        settings.setUserAgentString(isDesktop ? UA_DESKTOP : UA_MOBILE);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean allowMic = prefs.getBoolean(KEY_MIC, true);
                        boolean allowCamera = prefs.getBoolean(KEY_CAMERA, false);

                        List<String> granted = new ArrayList<>();
                        for (String res : request.getResources()) {
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && allowMic) {
                                granted.add(res);
                            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && allowCamera) {
                                granted.add(res);
                            }
                        }

                        if (!granted.isEmpty()) {
                            request.grant(granted.toArray(new String[0]));
                        } else {
                            request.deny();
                        }
                    }
                });
            }

            // Брендированные диалоги для JS alert()/confirm() из мессенджера:
            // вместо системного серого окна показываем тёмную панель в стиле VirtualMax.
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this, R.style.VirtualMaxDialog)
                            .setTitle("VirtualMax")
                            .setMessage(message)
                            .setPositiveButton("ОК", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    result.confirm();
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    result.cancel();
                                }
                            })
                            .show();
                    }
                });
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this, R.style.VirtualMaxDialog)
                            .setTitle("VirtualMax")
                            .setMessage(message)
                            .setPositiveButton("Да", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    result.confirm();
                                }
                            })
                            .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    result.cancel();
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    result.cancel();
                                }
                            })
                            .show();
                    }
                });
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // Ссылки вида target=_blank открываем во внешнем браузере с очисткой трекинг-меток
                WebView newWebView = new WebView(view.getContext());
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        try {
                            String cleanUrl = PrivacyInterceptor.cleanTrackingParams(url);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl));
                            startActivity(intent);
                        } catch (Exception ignored) {}
                        return true;
                    }
                });
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    final String url = request.getUrl().toString();
                    WebResourceResponse blocked = PrivacyInterceptor.shouldIntercept(url);
                    if (blocked != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                blockedCount++;
                                tvShieldStatus.setText("🛡️ VirtualMax: " + blockedCount);
                                recordBlockedLog(url);
                            }
                        });
                        return blocked;
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (!url.contains("max.ru")) {
                        try {
                            String cleanUrl = PrivacyInterceptor.cleanTrackingParams(url);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl));
                            startActivity(intent);
                            return true;
                        } catch (Exception e) {
                            // fallback
                        }
                    }
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectSandbox();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSandbox();
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimetype);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Загрузка через VirtualMax...");
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    request.setTitle(filename);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(MainActivity.this, "Загрузка: " + filename, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void injectSandbox() {
        try {
            boolean ghost = prefs.getBoolean(KEY_GHOST, true);
            boolean notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true);
            String js = PrivacyInterceptor.buildSandboxJs(ghost, !notifications);
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private void recordBlockedLog(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() != null ? uri.getHost() : url;
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String entry = "[" + time + "] " + host;

            blockedLogList.addFirst(entry);
            if (blockedLogList.size() > 6) {
                blockedLogList.removeLast();
            }

            StringBuilder sb = new StringBuilder();
            for (String item : blockedLogList) {
                sb.append("• ").append(item).append("\n");
            }
            tvBlockedLog.setText(sb.toString().trim());
        } catch (Exception ignored) {}
    }

    private void setupControls() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (settingsContainer.getVisibility() == View.VISIBLE) {
                    showMessengerView();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                }
            }
        });

        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessengerView();
                webView.reload();
            }
        });

        btnTopToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSettingsView();
            }
        });

        btnNavMessenger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessengerView();
            }
        });

        btnNavSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsView();
            }
        });

        btnNavClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearData();
            }
        });

        btnSettingsClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearData();
            }
        });

        btnSettingsBackToChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessengerView();
            }
        });

        btnZoomMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentZoom > 70) {
                    currentZoom -= 10;
                    updateZoom();
                }
            }
        });

        btnZoomPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentZoom < 160) {
                    currentZoom += 10;
                    updateZoom();
                }
            }
        });

        tvZoomValue.setText("Текущий масштаб: " + currentZoom + "%");
    }

    private void updateZoom() {
        prefs.edit().putInt(KEY_ZOOM, currentZoom).apply();
        webView.getSettings().setTextZoom(currentZoom);
        tvZoomValue.setText("Текущий масштаб: " + currentZoom + "%");
    }

    private void setupSettingsToggles() {
        switchMic.setChecked(prefs.getBoolean(KEY_MIC, true));
        switchCamera.setChecked(prefs.getBoolean(KEY_CAMERA, false));
        switchNotifications.setChecked(prefs.getBoolean(KEY_NOTIFICATIONS, true));
        switchGhost.setChecked(prefs.getBoolean(KEY_GHOST, true));
        switchDesktop.setChecked(prefs.getBoolean(KEY_DESKTOP, false));

        switchMic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(KEY_MIC, isChecked).apply();
                Toast.makeText(MainActivity.this, isChecked ? "🎙️ Микрофон: РАЗРЕШЕН" : "🎙️ Микрофон: ЗАБЛОКИРОВАН", Toast.LENGTH_SHORT).show();
            }
        });

        switchCamera.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(KEY_CAMERA, isChecked).apply();
                Toast.makeText(MainActivity.this, isChecked ? "📷 Камера: РАЗРЕШЕНА" : "📷 Камера: ЗАБЛОКИРОВАНА", Toast.LENGTH_SHORT).show();
            }
        });

        switchNotifications.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply();
                injectSandbox();
                Toast.makeText(MainActivity.this, isChecked ? "🔔 Уведомления: ВКЛЮЧЕНЫ" : "🔔 Уведомления: ВЫКЛЮЧЕНЫ", Toast.LENGTH_SHORT).show();
            }
        });

        switchGhost.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(KEY_GHOST, isChecked).apply();
                injectSandbox();
                Toast.makeText(MainActivity.this, isChecked ? "👻 Режим Невидимки: ВКЛЮЧЁН" : "👻 Режим Невидимки: ВЫКЛЮЧЕН", Toast.LENGTH_SHORT).show();
            }
        });

        switchDesktop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(KEY_DESKTOP, isChecked).apply();
                webView.getSettings().setUserAgentString(isChecked ? UA_DESKTOP : UA_MOBILE);
                Toast.makeText(MainActivity.this, isChecked ? "🖥️ Режим ПК" : "📱 Мобильный режим", Toast.LENGTH_SHORT).show();
                webView.reload();
            }
        });
    }

    private void showMessengerView() {
        settingsContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        btnNavMessenger.setTextColor(Color.parseColor("#00e676"));
        btnNavSettings.setTextColor(Color.parseColor("#f1f5f9"));
        btnTopToggle.setText("⚙️ Настройки");
    }

    private void showSettingsView() {
        webView.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.VISIBLE);
        btnNavMessenger.setTextColor(Color.parseColor("#f1f5f9"));
        btnNavSettings.setTextColor(Color.parseColor("#00e676"));
        btnTopToggle.setText("💬 Мессенджер");
    }

    private void toggleSettingsView() {
        if (settingsContainer.getVisibility() == View.VISIBLE) {
            showMessengerView();
        } else {
            showSettingsView();
        }
    }

    private void confirmClearData() {
        new AlertDialog.Builder(MainActivity.this)
            .setTitle("Очистка данных")
            .setMessage("Удалить все cookies, кэш и историю сессии мессенджера?")
            .setPositiveButton("Очистить", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    webView.clearCache(true);
                    webView.clearHistory();
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    blockedCount = 0;
                    blockedLogList.clear();
                    tvBlockedLog.setText("Слежка и метрики блокируются в реальном времени.");
                    tvShieldStatus.setText("🛡️ VirtualMax: 0");
                    showMessengerView();
                    webView.loadUrl(TARGET_URL);
                    Toast.makeText(MainActivity.this, "Сессия и кэш очищены!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (settingsContainer.getVisibility() == View.VISIBLE) {
            showMessengerView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
