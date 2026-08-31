package com.virtualmax.privacy;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.regex.Pattern;

public class PrivacyInterceptor {

    private static final Pattern[] BLOCKED_PATTERNS = new Pattern[] {
        // VK / Mail.ru трекеры
        Pattern.compile("top-fwz1\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("counter\\.yadro\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("t\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ad\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("stat(?:s)?\\.vk(?:-portal)?\\.(?:com|net)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("trk\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("target\\.my\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("rb\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ok\\.ru/counter", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pulse\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vk\\.com/(?:rtrg|c_stat\\.php|ads\\.php|stat_out\\.php|al_stat\\.php)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("connect\\.vk\\.com/stat", Pattern.CASE_INSENSITIVE),

        // Yandex Метрика, WebVisor, AppMetrica
        Pattern.compile("mc\\.yandex\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("metrika\\.yandex\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("an\\.yandex\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("yandex\\.ru/clck", Pattern.CASE_INSENSITIVE),
        Pattern.compile("appmetrica\\.yandex\\.net", Pattern.CASE_INSENSITIVE),
        Pattern.compile("awaps\\.yandex\\.net", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bs\\.yandex\\.ru", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mc\\.webvisor\\.org", Pattern.CASE_INSENSITIVE),

        // Global Trackers & Crash Loggers
        Pattern.compile("sentry\\.io", Pattern.CASE_INSENSITIVE),
        Pattern.compile("google-analytics\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("googletagmanager\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("clarity\\.ms", Pattern.CASE_INSENSITIVE),
        Pattern.compile("hotjar\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mixpanel\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("amplitude\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("app\\.adjust\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("appsflyer\\.com", Pattern.CASE_INSENSITIVE),

        // Внутренние эндпоинты телеметрии MAX
        Pattern.compile("/api/v?\\d*/(?:telemetry|metrics|analytics|stats|collector|event(?:s)?|beacon|client_log|crash_report)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/(?:telemetry|tracking|collector|beacon|webvisor|metrika|c_stat|stat_out|log_event)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/pixel\\.(?:png|gif|jpg|svg)", Pattern.CASE_INSENSITIVE)
    };

    private static final String[] TRACKING_PARAMS = new String[] {
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_referrer", "yclid", "gclid", "fbclid", "vk_ref", "_openstat",
        "from", "ref", "stat_id", "device_id", "fingerprint", "fp"
    };

    public static final String INJECTED_SANDBOX_JS = 
        "(function() {" +
        "  if (window.__VIRTUALMAX_SHIELD__) return;" +
        "  window.__VIRTUALMAX_SHIELD__ = true;" +
        "  console.log('[VirtualMax Shield] Active');" +
        "  if (navigator.sendBeacon) {" +
        "    navigator.sendBeacon = function() { return true; };" +
        "  }" +
        "  var origFetch = window.fetch;" +
        "  window.fetch = async function(...args) {" +
        "    var url = args[0] ? (typeof args[0] === 'string' ? args[0] : args[0].url) : '';" +
        "    if (typeof url === 'string' && /(?:telemetry|metrics|analytics|collector|webvisor|c_stat)/i.test(url)) {" +
        "      return new Response(JSON.stringify({ status: 'ok', success: true }), { status: 200, headers: {'Content-Type': 'application/json'} });" +
        "    }" +
        "    return origFetch.apply(this, args);" +
        "  };" +
        "  try {" +
        "    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });" +
        "    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });" +
        "    delete navigator.getBattery;" +
        "    window.addEventListener('deviceorientation', e => e.stopImmediatePropagation(), true);" +
        "    window.addEventListener('devicemotion', e => e.stopImmediatePropagation(), true);" +
        "  } catch(e){}" +
        "})();";

    public static WebResourceResponse shouldIntercept(String url) {
        if (url == null) return null;

        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(url).find()) {
                return new WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    new ByteArrayInputStream("".getBytes())
                );
            }
        }
        return null;
    }

    public static String cleanTrackingParams(String urlStr) {
        try {
            Uri uri = Uri.parse(urlStr);
            if (uri.isOpaque() || uri.getQuery() == null) return urlStr;

            Uri.Builder builder = uri.buildUpon().clearQuery();
            Set<String> queryNames = uri.getQueryParameterNames();
            boolean changed = false;

            for (String param : queryNames) {
                boolean isTracking = false;
                for (String t : TRACKING_PARAMS) {
                    if (t.equalsIgnoreCase(param)) {
                        isTracking = true;
                        changed = true;
                        break;
                    }
                }
                if (!isTracking) {
                    for (String val : uri.getQueryParameters(param)) {
                        builder.appendQueryParameter(param, val);
                    }
                }
            }

            return changed ? builder.build().toString() : urlStr;
        } catch (Exception e) {
            return urlStr;
        }
    }
}
