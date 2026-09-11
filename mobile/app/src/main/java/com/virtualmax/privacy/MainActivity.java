package com.virtualmax.privacy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    // ------------------------------------------------------------ views
    private WebView webView;
    private View topBar, bottomBar, settingsContainer;
    private ProgressBar progressBar;
    private Button btnRestoreUi;
    private TextView tvShieldStatus, tvZoomValue, tvBlockedLog;
    private Button btnBack, btnForward, btnReload, btnTopToggle, btnHideUI;
    private Button btnNavMessenger, btnNavSettings, btnNavClear;
    private Button btnSettingsClear, btnSettingsBackToChat;
    private Button btnZoomMinus, btnZoomPlus;
    private Switch switchMic, switchCamera, switchNotifications, switchGhost,
            switchDesktop, switchBlocking;

    // ------------------------------------------------------------ state
    private final AtomicInteger blockedCount = new AtomicInteger(0);
    private int currentZoom = 100;
    private boolean blockingEnabled = true;
    private final LinkedList<String> blockedLogList = new LinkedList<String>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean shieldUiScheduled = false;

    private SharedPreferences prefs;
    private static final String PREFS_NAME = "virtualmax_settings";
    private static final String KEY_MIC = "allow_mic";
    private static final String KEY_CAMERA = "allow_camera";
    private static final String KEY_NOTIFICATIONS = "allow_notifications";
    private static final String KEY_GHOST = "ghost_mode";
    private static final String KEY_DESKTOP = "desktop_mode";
    private static final String KEY_BLOCKING = "blocking_enabled";
    private static final String KEY_ZOOM = "text_zoom";
    private static final String KEY_UI_VISIBLE = "ui_visible";

    private boolean uiVisible = true;

    // ------------------------------------------------------------ runtime permission / chooser
    private static final int REQ_WEB_PERMISSIONS = 1001;
    private static final int REQ_STORAGE = 1002;
    private static final int REQ_NOTIFICATIONS = 1003;
    private static final int REQ_FILE_CHOOSER = 1004;

    private PermissionRequest pendingWebPermission;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private Runnable pendingDownload;

    private SimpleDateFormat timeFormat;

    private static final String TARGET_URL = "https://web.max.ru";
    private static final String UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Mobile; rv:126.0) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentZoom = prefs.getInt(KEY_ZOOM, 100);
        uiVisible = prefs.getBoolean(KEY_UI_VISIBLE, true);
        blockingEnabled = prefs.getBoolean(KEY_BLOCKING, true);
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        bindViews();
        setupWebView();
        setupControls();
        setupSettingsToggles();
        applySystemInsets();

        if (!uiVisible) {
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            btnRestoreUi.setVisibility(View.VISIBLE);
        }

        webView.loadUrl(TARGET_URL);
    }

    private void bindViews() {
        webView = findViewById(R.id.webView);
        settingsContainer = findViewById(R.id.settingsContainer);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        progressBar = findViewById(R.id.progressBar);
        btnRestoreUi = findViewById(R.id.btnRestoreUi);
        tvShieldStatus = findViewById(R.id.tvShieldStatus);
        tvZoomValue = findViewById(R.id.tvZoomValue);
        tvBlockedLog = findViewById(R.id.tvBlockedLog);

        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);
        btnTopToggle = findViewById(R.id.btnTopToggle);
        btnHideUI = findViewById(R.id.btnHideUI);

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
        switchBlocking = findViewById(R.id.switchBlocking);
    }

    /**
     * Учитываем системные панели (статус-бар и жестовую навигацию), чтобы
     * контент не обрезался на edge-to-edge экранах.
     */
    private void applySystemInsets() {
        View root = findViewById(R.id.root);
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

    private void setupWebView() {
        // Один фоновый поток рендерера, аппаратное ускорение уже включено в манифесте.
        webView.setBackgroundColor(Color.rgb(7, 10, 15));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(currentZoom);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Дорендеринг соседних экранов — заметно плавнее скролл ленты чатов.
        settings.setOffscreenPreRaster(true);
        settings.setSupportMultipleWindows(true);

        CookieManager.getInstance().setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // В фоне важность рендерера снимается — экономим батарею и память.
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }

        boolean isDesktop = prefs.getBoolean(KEY_DESKTOP, false);
        settings.setUserAgentString(isDesktop ? UA_DESKTOP : UA_MOBILE);

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleWebPermissionRequest(request);
                    }
                });
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, final JsResult result) {
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
            public boolean onJsConfirm(WebView view, String url, final String message, final JsResult result) {
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
                final WebView newWebView = new WebView(view.getContext());
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        if (request.getUrl() == null) return true;
                        final String popupUrl = request.getUrl().toString();
                        String popupHost = Uri.parse(popupUrl).getHost();
                        final boolean popupIsMax = popupHost != null
                            && (popupHost.equals("max.ru") || popupHost.endsWith(".max.ru"));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (popupIsMax) {
                                    // Внутренние ссылки MAX открываем в основном WebView.
                                    webView.loadUrl(popupUrl);
                                } else {
                                    openExternal(PrivacyInterceptor.cleanTrackingParams(popupUrl));
                                }
                            }
                        });
                        return true;
                    }
                });
                return true;
            }

            // Отправка фото/видео/файлов в мессенджер.
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                launchFilePicker(fileChooserParams);
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                    progressBar.setProgress(100);
                } else {
                    if (progressBar.getVisibility() != View.VISIBLE) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    progressBar.setProgress(newProgress);
                }
                updateNavButtons();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!blockingEnabled || request == null || request.getUrl() == null) {
                    return null;
                }
                final String url = request.getUrl().toString();
                WebResourceResponse blocked = PrivacyInterceptor.shouldIntercept(url);
                if (blocked != null) {
                    registerBlocked(url);
                    return blocked;
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                return routeUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                injectSandbox();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectSandbox();
                progressBar.setVisibility(View.GONE);
                updateNavButtons();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Рендерер убит системой (нехватка памяти). Убираем мёртвый WebView
                // и пересоздаём экран — приложение не падает.
                if (view != null && view.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) view.getParent()).removeView(view);
                    view.destroy();
                }
                Toast.makeText(MainActivity.this, "Процесс браузера перезапущен",
                    Toast.LENGTH_SHORT).show();
                recreate();
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, final String userAgent, final String contentDisposition,
                                        final String mimetype, long contentLength) {
                prepareDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
    }

    // ============================================================ permissions

    /**
     * Запрос устройственных разрешений для веб-API (микрофон/камера).
     * Тумблеры в настройках — политика приложения, runtime-разрешения — политика ОС.
     */
    private void handleWebPermissionRequest(final PermissionRequest request) {
        boolean allowMic = prefs.getBoolean(KEY_MIC, true);
        boolean allowCamera = prefs.getBoolean(KEY_CAMERA, false);

        final List<String> wanted = new ArrayList<String>();
        final List<String> osPerms = new ArrayList<String>();

        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && allowMic) {
                wanted.add(res);
                if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    osPerms.add(Manifest.permission.RECORD_AUDIO);
                }
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && allowCamera) {
                wanted.add(res);
                if (!hasPermission(Manifest.permission.CAMERA)) {
                    osPerms.add(Manifest.permission.CAMERA);
                }
            }
        }

        if (wanted.isEmpty()) {
            request.deny();
            return;
        }

        if (!osPerms.isEmpty()) {
            pendingWebPermission = request;
            requestPermissions(osPerms.toArray(new String[0]), REQ_WEB_PERMISSIONS);
            return;
        }
        request.grant(wanted.toArray(new String[0]));
    }

    private boolean hasPermission(String perm) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestOsPermission(String perm, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[] { perm }, requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_WEB_PERMISSIONS) {
            boolean micGranted = hasPermission(Manifest.permission.RECORD_AUDIO);
            boolean camGranted = hasPermission(Manifest.permission.CAMERA);

            // Синхронизация тумблеров после запроса из настроек.
            if (pendingWebPermission == null) {
                if (switchMic.isChecked() != micGranted) switchMic.setChecked(micGranted);
                if (switchCamera.isChecked() != camGranted) switchCamera.setChecked(camGranted);
                prefs.edit()
                    .putBoolean(KEY_MIC, micGranted)
                    .putBoolean(KEY_CAMERA, camGranted)
                    .apply();
                return;
            }

            List<String> granted = new ArrayList<String>();
            for (String res : pendingWebPermission.getResources()) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)
                    && prefs.getBoolean(KEY_MIC, true) && micGranted) {
                    granted.add(res);
                } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)
                    && prefs.getBoolean(KEY_CAMERA, false) && camGranted) {
                    granted.add(res);
                }
            }
            if (granted.isEmpty()) {
                pendingWebPermission.deny();
                Toast.makeText(this, "Разрешение не выдано системой", Toast.LENGTH_SHORT).show();
            } else {
                pendingWebPermission.grant(granted.toArray(new String[0]));
            }
            pendingWebPermission = null;
            return;
        }

        if (requestCode == REQ_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                prefs.edit().putBoolean(KEY_NOTIFICATIONS, false).apply();
                switchNotifications.setChecked(false);
                Toast.makeText(this, "Системные уведомления запрещены", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == REQ_STORAGE) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingDownload != null) {
                Runnable r = pendingDownload;
                pendingDownload = null;
                r.run();
            } else {
                pendingDownload = null;
                Toast.makeText(this, "Нужен доступ к хранилищу для загрузки", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ============================================================ file chooser

    private void launchFilePicker(WebChromeClient.FileChooserParams params) {
        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] acceptTypes = params.getAcceptTypes();
        String mime = "*/*";
        boolean wantsImage = false;
        boolean wantsVideo = false;
        if (acceptTypes != null && acceptTypes.length > 0 && acceptTypes[0] != null
            && acceptTypes[0].trim().length() > 0) {
            mime = acceptTypes[0];
            String m = mime.toLowerCase(Locale.ROOT);
            wantsImage = m.startsWith("image/") || m.equals("image");
            wantsVideo = m.startsWith("video/") || m.equals("video");
            if (wantsImage) mime = "image/*";
            else if (wantsVideo) mime = "video/*";
        }
        contentIntent.setType(mime);
        contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
            params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);

        List<Intent> extras = new ArrayList<Intent>();
        cameraImageUri = null;

        if (params.isCaptureEnabled() && wantsImage) {
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME,
                        "VM_" + System.currentTimeMillis() + ".jpg");
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    cameraImageUri = getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    if (camera.resolveActivity(getPackageManager()) != null) {
                        extras.add(camera);
                    }
                } catch (Exception e) {
                    cameraImageUri = null;
                }
            }
        }
        if (params.isCaptureEnabled() && wantsVideo) {
            Intent video = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (video.resolveActivity(getPackageManager()) != null) {
                extras.add(video);
            }
        }

        Intent chooser = Intent.createChooser(contentIntent, "Выберите файл");
        if (!extras.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toArray(new Intent[0]));
        }
        try {
            startActivityForResult(chooser, REQ_FILE_CHOOSER);
        } catch (Exception e) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
            Toast.makeText(this, "Нет приложения для выбора файлов", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE_CHOOSER || filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            // Камера пишет в заранее выданный MediaStore Uri (data может быть null).
            if ((results == null || results.length == 0) && cameraImageUri != null) {
                results = new Uri[] { cameraImageUri };
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraImageUri = null;
    }

    // ============================================================ downloads

    private void prepareDownload(final String url, final String userAgent,
                                 final String contentDisposition, final String mimetype) {
        // Android 9 и ниже: запись в публичную папку Download требует разрешения.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            pendingDownload = new Runnable() {
                @Override
                public void run() {
                    enqueueDownload(url, userAgent, contentDisposition, mimetype);
                }
            };
            requestOsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQ_STORAGE);
            return;
        }
        enqueueDownload(url, userAgent, contentDisposition, mimetype);
    }

    private void enqueueDownload(String url, String userAgent,
                                 String contentDisposition, String mimetype) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimetype != null) request.setMimeType(mimetype);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Загрузка через VirtualMax…");
            String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
            request.setTitle(filename);
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            request.allowScanningByMediaScanner();

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "⬇ Загрузка: " + filename, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================ navigation / routing

    /**
     * @return true если URL перехвачен (внешние ссылки уходят в браузер,
     *              трекинг-метки вырезаются).
     */
    private boolean routeUrl(String url) {
        if (url == null) return false;
        String host = Uri.parse(url).getHost();
        boolean isMax = host != null && (host.equals("max.ru") || host.endsWith(".max.ru"));
        if (isMax) return false;

        if (url.startsWith("http://") || url.startsWith("https://")) {
            openExternal(PrivacyInterceptor.cleanTrackingParams(url));
            return true;
        }
        // intent:, market:, tel:, mailto: — отдаём системе.
        openExternal(url);
        return true;
    }

    private void openExternal(String url) {
        if (url == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    // ============================================================ shield log / sandbox

    /** Потокобезопасная регистрация заблокированного запроса (вызов с binder-потока). */
    private void registerBlocked(final String url) {
        blockedCount.incrementAndGet();
        String host;
        try {
            Uri uri = Uri.parse(url);
            host = uri.getHost() != null ? uri.getHost() : url;
        } catch (Exception e) {
            host = url;
        }
        final String time;
        synchronized (timeFormat) {
            time = timeFormat.format(new Date());
        }
        final String entry = "[" + time + "] " + host;
        synchronized (blockedLogList) {
            blockedLogList.addFirst(entry);
            while (blockedLogList.size() > 7) blockedLogList.removeLast();
        }
        if (!shieldUiScheduled) {
            shieldUiScheduled = true;
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    shieldUiScheduled = false;
                    int shown = blockedCount.get();
                    tvShieldStatus.setText("🛡️ " + shown);
                    tvShieldStatus.setContentDescription("Заблокировано трекеров: " + shown);
                    StringBuilder sb = new StringBuilder();
                    synchronized (blockedLogList) {
                        for (String item : blockedLogList) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append("• ").append(item);
                        }
                    }
                    tvBlockedLog.setText(sb.length() > 0 ? sb.toString()
                        : "Трекеры блокируются автоматически");
                }
            }, 300);
        }
    }

    private void injectSandbox() {
        try {
            boolean ghost = prefs.getBoolean(KEY_GHOST, true);
            boolean blockNotifs = !prefs.getBoolean(KEY_NOTIFICATIONS, true);
            webView.evaluateJavascript(
                PrivacyInterceptor.buildSandboxJs(ghost, blockNotifs), null);
        } catch (Exception ignored) { }
    }

    // ============================================================ controls

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

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoForward()) webView.goForward();
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
        tvShieldStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsView();
            }
        });

        btnHideUI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideUiCompletely();
            }
        });
        btnRestoreUi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreUi();
            }
        });

        btnNavMessenger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showMessengerView(); }
        });
        btnNavSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showSettingsView(); }
        });
        btnNavClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { confirmClearData(); }
        });
        btnSettingsClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { confirmClearData(); }
        });
        btnSettingsBackToChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showMessengerView(); }
        });

        btnZoomMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentZoom >= 80) currentZoom -= 10;
                else currentZoom = 70;
                updateZoom();
            }
        });
        btnZoomPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentZoom <= 150) currentZoom += 10;
                else if (currentZoom < 160) currentZoom = 160;
                updateZoom();
            }
        });

        tvZoomValue.setText(currentZoom + "%");
        showMessengerView();
    }

    private void updateNavButtons() {
        btnForward.setEnabled(webView.canGoForward());
        btnForward.setAlpha(webView.canGoForward() ? 1f : 0.35f);
        btnBack.setAlpha(webView.canGoBack() ? 1f : 0.6f);
    }

    private void updateZoom() {
        if (currentZoom < 70) currentZoom = 70;
        if (currentZoom > 160) currentZoom = 160;
        prefs.edit().putInt(KEY_ZOOM, currentZoom).apply();
        webView.getSettings().setTextZoom(currentZoom);
        tvZoomValue.setText(currentZoom + "%");
    }

    private void setupSettingsToggles() {
        switchMic.setChecked(prefs.getBoolean(KEY_MIC, true));
        switchCamera.setChecked(prefs.getBoolean(KEY_CAMERA, false));
        switchNotifications.setChecked(prefs.getBoolean(KEY_NOTIFICATIONS, true));
        switchGhost.setChecked(prefs.getBoolean(KEY_GHOST, true));
        switchDesktop.setChecked(prefs.getBoolean(KEY_DESKTOP, false));
        switchBlocking.setChecked(prefs.getBoolean(KEY_BLOCKING, true));

        switchMic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, final boolean on) {
                prefs.edit().putBoolean(KEY_MIC, on).apply();
                if (on && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    requestOsPermission(Manifest.permission.RECORD_AUDIO, REQ_WEB_PERMISSIONS);
                }
                Toast.makeText(MainActivity.this,
                    on ? "🎙️ Микрофон разрешён" : "🎙️ Микрофон заблокирован",
                    Toast.LENGTH_SHORT).show();
            }
        });

        switchCamera.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, final boolean on) {
                prefs.edit().putBoolean(KEY_CAMERA, on).apply();
                if (on && !hasPermission(Manifest.permission.CAMERA)) {
                    requestOsPermission(Manifest.permission.CAMERA, REQ_WEB_PERMISSIONS);
                }
                Toast.makeText(MainActivity.this,
                    on ? "📷 Камера разрешена" : "📷 Камера заблокирована",
                    Toast.LENGTH_SHORT).show();
            }
        });

        switchNotifications.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(KEY_NOTIFICATIONS, on).apply();
                if (on && Build.VERSION.SDK_INT >= 33
                    && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                    requestOsPermission(Manifest.permission.POST_NOTIFICATIONS, REQ_NOTIFICATIONS);
                }
                injectSandbox();
                Toast.makeText(MainActivity.this,
                    on ? "🔔 Уведомления включены" : "🔔 Уведомления выключены",
                    Toast.LENGTH_SHORT).show();
            }
        });

        switchGhost.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(KEY_GHOST, on).apply();
                injectSandbox();
                Toast.makeText(MainActivity.this,
                    on ? "👻 Невидимка включена" : "👻 Невидимка выключена",
                    Toast.LENGTH_SHORT).show();
            }
        });

        switchBlocking.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                blockingEnabled = on;
                prefs.edit().putBoolean(KEY_BLOCKING, on).apply();
                Toast.makeText(MainActivity.this,
                    on ? "🛡️ Блокировка трекеров включена" : "🛡️ Блокировка выключена",
                    Toast.LENGTH_SHORT).show();
            }
        });

        switchDesktop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(KEY_DESKTOP, on).apply();
                webView.getSettings().setUserAgentString(on ? UA_DESKTOP : UA_MOBILE);
                Toast.makeText(MainActivity.this,
                    on ? "🖥️ Режим ПК" : "📱 Мобильный режим",
                    Toast.LENGTH_SHORT).show();
                showMessengerView();
                webView.reload();
            }
        });
    }

    // ============================================================ view switching

    private void showMessengerView() {
        settingsContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        btnNavMessenger.setSelected(true);
        btnNavSettings.setSelected(false);
        btnNavClear.setSelected(false);
        btnTopToggle.setText("⚙");
    }

    private void showSettingsView() {
        webView.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.VISIBLE);
        btnNavMessenger.setSelected(false);
        btnNavSettings.setSelected(true);
        btnNavClear.setSelected(false);
        btnTopToggle.setText("💬");
    }

    private void toggleSettingsView() {
        if (settingsContainer.getVisibility() == View.VISIBLE) {
            showMessengerView();
        } else {
            showSettingsView();
        }
    }

    private void hideUiCompletely() {
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        btnRestoreUi.setVisibility(View.VISIBLE);
        uiVisible = false;
        prefs.edit().putBoolean(KEY_UI_VISIBLE, false).apply();
        Toast.makeText(this, "Панели скрыты. Нажмите 🛡 слева внизу, чтобы вернуть.",
            Toast.LENGTH_LONG).show();
    }

    private void restoreUi() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        btnRestoreUi.setVisibility(View.GONE);
        uiVisible = true;
        prefs.edit().putBoolean(KEY_UI_VISIBLE, true).apply();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(MainActivity.this, R.style.VirtualMaxDialog)
            .setTitle("Очистка данных")
            .setMessage("Удалить cookies, кэш и историю сессии мессенджера? Потребуется повторный вход.")
            .setPositiveButton("Очистить", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    webView.clearCache(true);
                    webView.clearHistory();
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    blockedCount.set(0);
                    synchronized (blockedLogList) { blockedLogList.clear(); }
                    tvBlockedLog.setText("Трекеры блокируются автоматически");
                    tvShieldStatus.setText("🛡️ Защита активна");
                    showMessengerView();
                    webView.loadUrl(TARGET_URL);
                    Toast.makeText(MainActivity.this, "Сессия и кэш очищены",
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // ============================================================ lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        if (settingsContainer != null && settingsContainer.getVisibility() == View.VISIBLE) {
            showMessengerView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
