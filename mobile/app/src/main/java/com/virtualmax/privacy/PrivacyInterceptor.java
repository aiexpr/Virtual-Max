package com.virtualmax.privacy;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PrivacyInterceptor — сетевой фильтр и JavaScript-песочница VirtualMax.
 *
 * 1. Блокировка сетевых запросов к трекерам (VK/Mail.ru, Яндекс, глобальная
 *    аналитика) на уровне WebView, до выхода запроса в интернет.
 * 2. Injected DOM JS Sandbox: нейтрализация sendBeacon / fetch / XHR / WebSocket
 *    для телеметрии и «печати», анти-фингерпринт (Canvas, Audio, WebGL),
 *    маскировка параметров устройства, контроль Notification API.
 * 3. Очистка трекинг-параметров во внешних ссылках.
 *
 * ПРОИЗВОДИТЕЛЬНОСТЬ: shouldIntercept() вызывается для КАЖДОГО субресурса
 * страницы на рабочем потоке WebView. Поэтому здесь нет перебора regex по URL:
 * хост проверяется через суффиксные hash-lookup'ы по доменным меткам
 * (обычно 2–4 итерации), а три regex применяются только к хосту+пути.
 */
public class PrivacyInterceptor {

    /**
     * Домены трекеров. Совпадение засчитывается, если хост равен фрагменту
     * или является его поддоменом (суффиксная проверка по меткам).
     * Все значения уже в нижнем регистре и без слэшей.
     */
    private static final Set<String> BLOCKED_HOSTS;
    static {
        Set<String> s = new HashSet<String>(96);
        // --- VK / Mail.ru трекеры ---
        Collections.addAll(s,
            "top-fwz1.mail.ru", "counter.yadro.ru", "t.mail.ru", "ad.mail.ru",
            "trk.mail.ru", "target.my.com", "rb.mail.ru", "pulse.mail.ru",
            "mstat.my.com", "stat.mail.ru", "rs.mail.ru", "hb.bizmrg.com",
            "top.mail.ru", "top100.mail.ru",
            "stats.vk-portal.net", "stat.vk-portal.net", "stat.vk.com",
            "stat.vk.net", "appboy.com",
            // --- Yandex Метрика, WebVisor, AppMetrica / реклама ---
            "mc.yandex.ru", "metrika.yandex.ru", "metrika.yandex.com",
            "an.yandex.ru", "appmetrica.yandex.net", "awaps.yandex.net",
            "bs.yandex.ru", "mc.webvisor.org", "webvisor.com",
            "adfox.ru", "adfox.yandex.ru", "ads.yandex.ru",
            "yandexads.com", "advert.yandex.ru", "awaps.yandex.ru",
            // --- Sentry / crash loggers / RUM ---
            "sentry.io", "sentry-cdn.com", "ingest.sentry.io",
            "bugsnag.com", "newrelic.com", "nr-data.net",
            "datadoghq.com",
            // --- Google / глобальные рекламные сети ---
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "googleadservices.com", "adservice.google.com", "doubleclick.net",
            "googlesyndication.com", "adsystem.com",
            "adcolony.com", "app.adjust.com", "appsflyer.com",
            "scorecardresearch.com", "quantserve.com", "criteo.com", "criteo.net",
            "moatads.com", "taboola.com", "outbrain.com", "bidswitch.net",
            "casalemedia.com", "pubmatic.com", "rubiconproject.com",
            "openx.net", "smartadserver.com", "zedo.com",
            // --- Продуктовая аналитика ---
            "clarity.ms", "hotjar.com", "mixpanel.com", "amplitude.com",
            "segment.io", "segment.com", "fullstory.com", "heap.io",
            "kissmetrics.com", "optimizely.com", "mouseflow.com",
            "tns-counter.ru", "tm.tns-counter.ru", "top100.rambler.ru",
            "adriver.ru", "rutarget.ru"
        );
        BLOCKED_HOSTS = Collections.unmodifiableSet(s);
    }

    /**
     * Правила по хосту+пути: внутренние эндпоинты телеметрии (в т.ч. самого
     * MAX) и счётчики на хостинге, который нельзя блокировать целиком.
     */
    private static final Pattern[] URL_PATTERNS = new Pattern[] {
        Pattern.compile("/api/v?\\d*/(?:telemetry|metrics|analytics|stats|collector|events?|beacon|client_log|crash_report)"),
        Pattern.compile("/(?:telemetry|tracking|collector|beacon|webvisor|metrika|c_stat(?:\\.php)?|stat_out\\.php|al_stat\\.php|rtrg|ads\\.php|log_event|pixel\\.(?:png|gif|jpg|svg|webp))"),
        Pattern.compile("(?:^|\\.)(?:vk\\.com|ok\\.ru|connect\\.vk\\.com)/(?:rtrg|counter|stat|c_stat|ads|stat_out|al_stat|dk)")
    };

    private static final String[] TRACKING_PARAMS = new String[] {
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_referrer", "utm_id", "yclid", "gclid", "fbclid", "gbraid", "wbraid",
        "msclkid", "vk_ref", "_openstat", "from", "ref", "referrer", "stat_id",
        "device_id", "fingerprint", "fp", "yclid_mobile"
    };

    /** Пустой ответ 200 OK для заблокированного запроса (переиспользуется). */
    private static final WebResourceResponse EMPTY_RESPONSE =
        new WebResourceResponse("text/plain", "UTF-8",
            new ByteArrayInputStream(new byte[0]));

    /**
     * Строит JavaScript-песочницу с учётом текущих настроек пользователя.
     *
     * @param ghostMode          Режим Невидимки: глушить «Печатает...» / присутствие
     * @param blockNotifications Блокировать Notification API (тумблер выключен)
     */
    public static String buildSandboxJs(boolean ghostMode, boolean blockNotifications) {
        StringBuilder js = new StringBuilder(6 * 1024);
        js.append("(function(){");
        js.append("'use strict';");
        // Повторная инъекция (смена тумблеров) только обновляет флаги,
        // хуки не переустанавливаются (иначе они наслаивались бы друг на друга).
        js.append("if(window.__VIRTUALMAX_SHIELD__){window.__VM_SET__(");
        js.append(ghostMode).append(",").append(blockNotifications).append(");return;}");
        js.append("window.__VIRTUALMAX_SHIELD__=true;");

        js.append("var GHOST=").append(ghostMode).append(";");
        js.append("var BLOCK_NOTIFS=").append(blockNotifications).append(";");
        js.append("window.__VM_SET__=function(g,n){GHOST=g;BLOCK_NOTIFS=n;applyNotifPolicy();};");

        // Предкомпилированные регулярные выражения (создаются один раз на страницу).
        js.append("var TELEMETRY=/(?:telemetry|metrics|analytics|collector|webvisor|c_stat(?:\\.php)?|");
        js.append("stat_out|client_log|crash_report|top-fwz1|mc\\.yandex|sentry\\.io|mail\\.ru\\/counter|pixel(?:[\\.](?:png|gif|jpg|svg|webp))?)/i;");
        js.append("var TYPING=/(?:typing|set_activity|activity_status|online_status|presence|chat_composing|user_typing)/i;");
        js.append("var OK=JSON.stringify({status:'ok',success:true});");

        // 1. sendBeacon — всегда глушим.
        js.append("if(navigator.sendBeacon){navigator.sendBeacon=function(){return true;};}");

        // 2. fetch — перехват телеметрии и (в Ghost Mode) статусов печати.
        js.append("var oFetch=window.fetch;");
        js.append("if(oFetch){window.fetch=function(){");
        js.append("var u='';");
        js.append("try{u=typeof arguments[0]==='string'?arguments[0]:(arguments[0]&&arguments[0].url)||'';}catch(e){}");
        js.append("if(TELEMETRY.test(u)||(GHOST&&TYPING.test(u))){");
        js.append("return Promise.resolve(new Response(OK,{status:200,headers:{'Content-Type':'application/json'}}));");
        js.append("}");
        js.append("return oFetch.apply(this,arguments);");
        js.append("};}");

        // 3. XMLHttpRequest — фейковый статус 200 OK для телеметрии.
        js.append("try{");
        js.append("var oOpen=XMLHttpRequest.prototype.open;");
        js.append("var oSend=XMLHttpRequest.prototype.send;");
        js.append("XMLHttpRequest.prototype.open=function(m,u){this.__vmUrl=String(u||'');return oOpen.apply(this,arguments);};");
        js.append("XMLHttpRequest.prototype.send=function(){");
        js.append("var u=this.__vmUrl||'';");
        js.append("if(TELEMETRY.test(u)||(GHOST&&TYPING.test(u))){");
        js.append("var self=this;");
        js.append("try{");
        js.append("Object.defineProperty(self,'readyState',{value:4,configurable:true});");
        js.append("Object.defineProperty(self,'status',{value:200,configurable:true});");
        js.append("Object.defineProperty(self,'statusText',{value:'OK',configurable:true});");
        js.append("Object.defineProperty(self,'responseText',{value:OK,configurable:true});");
        js.append("Object.defineProperty(self,'response',{value:OK,configurable:true});");
        js.append("setTimeout(function(){");
        js.append("try{if(typeof self.onreadystatechange==='function'){self.onreadystatechange();}}catch(e){}");
        js.append("try{if(typeof self.onload==='function'){self.onload();}}catch(e){}");
        js.append("},0);");
        js.append("return;");
        js.append("}catch(e){}");
        js.append("}");
        js.append("return oSend.apply(this,arguments);");
        js.append("};");
        js.append("}catch(e){}");

        // 4. Ghost Mode: WebSocket — глушение пакетов typing/composing.
        //    Обёртка ставится всегда, живой флаг GHOST проверяется в send().
        js.append("if(window.WebSocket){");
        js.append("try{");
        js.append("var OWS=window.WebSocket;");
        js.append("function VMWS(){");
        js.append("var inst;");
        js.append("try{inst=new(Function.prototype.bind.apply(OWS,[null].concat([].slice.call(arguments))));}catch(e){inst=new OWS(arguments[0]);}");
        js.append("var oS=inst.send.bind(inst);");
        js.append("inst.send=function(data){");
        // Решение принимаем по ЖИВОМУ флагу GHOST, а не по значению на момент инсталляции.
        js.append("if(GHOST&&typeof data==='string'){");
        js.append("try{var j=JSON.parse(data);");
        js.append("var t=String((j&&(j.type||j.event||j.action||j.method||j.cmd))||'');");
        js.append("if(TYPING.test(t)){return;}");
        js.append("}catch(e){}");
        js.append("}");
        js.append("return oS(data);");
        js.append("};");
        js.append("return inst;");
        js.append("}");
        js.append("VMWS.prototype=OWS.prototype;");
        js.append("VMWS.CONNECTING=OWS.CONNECTING;VMWS.OPEN=OWS.OPEN;VMWS.CLOSING=OWS.CLOSING;VMWS.CLOSED=OWS.CLOSED;");
        js.append("window.WebSocket=VMWS;");
        js.append("}catch(e){}");
        js.append("}");

        // 5. Anti-Fingerprint: Canvas.
        // ВАЖНО для производительности на телефонах:
        //  - getImageData: шум с большим шагом (каждый 32-й байт), без аллокаций;
        //  - toDataURL/toBlob: полный readback делается ОДИН РАЗ на канвас
        //    (WeakSet) и только для небольших канвасов (<=100k пикс.).
        //    Раньше readback выполнялся на КАЖДЫЙ вызов и тормозил анимации,
        //    стикеры и спрайты мессенджера.
        js.append("try{");
        js.append("var NOISE=Math.random()>=0.5?1:-1;");
        js.append("var touched=new WeakSet();");
        js.append("var oGID=CanvasRenderingContext2D.prototype.getImageData;");
        js.append("CanvasRenderingContext2D.prototype.getImageData=function(){");
        js.append("var d=oGID.apply(this,arguments);");
        js.append("if(d&&d.data){for(var i=0;i<d.data.length;i+=32){d.data[i]=(d.data[i]+NOISE+256)%256;}}");
        js.append("return d;");
        js.append("};");
        js.append("function vmTouch(c){try{");
        js.append("if(touched.has(c)){return;}touched.add(c);");
        js.append("var w=c.width,h=c.height;");
        js.append("if(w>0&&h>0&&w*h<=100000){var ctx=c.getContext('2d');if(ctx){var d=oGID.call(ctx,0,0,w,h);if(d&&d.data&&d.data.length>3){d.data[0]=(d.data[0]+NOISE+256)%256;ctx.putImageData(d,0,0);}}}");
        js.append("}catch(e){}}");
        js.append("var oTDU=HTMLCanvasElement.prototype.toDataURL;");
        js.append("HTMLCanvasElement.prototype.toDataURL=function(){vmTouch(this);return oTDU.apply(this,arguments);};");
        js.append("if(HTMLCanvasElement.prototype.toBlob){var oTB=HTMLCanvasElement.prototype.toBlob;");
        js.append("HTMLCanvasElement.prototype.toBlob=function(){vmTouch(this);return oTB.apply(this,arguments);};}");
        js.append("}catch(e){}");

        // 6. Anti-Fingerprint: AudioContext (минимальный шум, без аллокаций).
        js.append("try{");
        js.append("var AC=window.AudioContext||window.webkitAudioContext;");
        js.append("if(AC&&AC.prototype&&(typeof AnalyserNode!=='undefined')&&AnalyserNode.prototype&&AnalyserNode.prototype.getFloatFrequencyData){");
        js.append("var oGFFD=AnalyserNode.prototype.getFloatFrequencyData;");
        js.append("AnalyserNode.prototype.getFloatFrequencyData=function(arr){oGFFD.call(this,arr);");
        js.append("for(var i=0;i<arr.length;i+=8){arr[i]+=(i%2?0.01:-0.01);}");
        js.append("};");
        js.append("}");
        js.append("}catch(e){}");

        // 7. Anti-Fingerprint: маскировка WebGL GPU.
        js.append("try{");
        js.append("function vmMaskGP(p){");
        js.append("if(p===37445){return 'VirtualMax (Browser Vendor)';}");
        js.append("if(p===37446){return 'VirtualMax Shield GPU';}");
        js.append("return null;");
        js.append("}");
        js.append("var oGP=WebGLRenderingContext.prototype.getParameter;");
        js.append("WebGLRenderingContext.prototype.getParameter=function(p){var f=vmMaskGP(p);return f!==null?f:oGP.call(this,p);};");
        js.append("if(typeof WebGL2RenderingContext!=='undefined'){");
        js.append("var oGP2=WebGL2RenderingContext.prototype.getParameter;");
        js.append("WebGL2RenderingContext.prototype.getParameter=function(p){var f=vmMaskGP(p);return f!==null?f:oGP2.call(this,p);};");
        js.append("}");
        js.append("}catch(e){}");

        // 8. Маскировка параметров устройства.
        js.append("try{");
        js.append("Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 8;},configurable:true});");
        js.append("try{Object.defineProperty(navigator,'deviceMemory',{get:function(){return 8;},configurable:true});}catch(e){}");
        js.append("try{if(navigator.getBattery){navigator.getBattery=function(){return Promise.reject();};}}catch(e){}");
        js.append("}catch(e){}");

        // 9. Защита сенсоров движения (два лёгких capture-listener'а).
        js.append("window.addEventListener('deviceorientation',function(e){e.stopImmediatePropagation();},true);");
        js.append("window.addEventListener('devicemotion',function(e){e.stopImmediatePropagation();},true);");

        // 10. Блокировка Web Notification (когда тумблер выключен).
        js.append("function VMBlockedNotification(){this.close=function(){};}");
        js.append("VMBlockedNotification.permission='denied';");
        js.append("VMBlockedNotification.requestPermission=function(){return Promise.resolve('denied');};");
        js.append("VMBlockedNotification.prototype.close=function(){};");
        js.append("function applyNotifPolicy(){");
        js.append("if(BLOCK_NOTIFS&&('Notification' in window)&&window.Notification!==VMBlockedNotification){");
        js.append("window.Notification=VMBlockedNotification;}");
        js.append("}");
        js.append("applyNotifPolicy();");

        js.append("})();");
        return js.toString();
    }

    /** Песочница по умолчанию: Ghost Mode вкл, уведомления разрешены. */
    public static final String INJECTED_SANDBOX_JS = buildSandboxJs(true, false);

    /**
     * Быстрая проверка URL: суффиксный hash-матчинг хоста + три regex по
     * хосту+пути. Никаких regex по полному URL.
     */
    public static WebResourceResponse shouldIntercept(String url) {
        if (url == null || url.length() == 0) return null;
        // Сразу пропускаем не-http(s): data:, blob:, about:, file:, chrome-*
        if (url.charAt(0) != 'h') return null;

        String host;
        String pathAndQuery;
        try {
            Uri uri = Uri.parse(url);
            host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            String p = uri.getPath();
            String q = uri.getQuery();
            pathAndQuery = (p == null ? "/" : p) + (q == null ? "" : "?" + q);
        } catch (Exception e) {
            return null;
        }

        // 1. Суффиксный матчинг хоста по доменным меткам: O(число меток).
        int idx = 0;
        while (true) {
            if (BLOCKED_HOSTS.contains(host.substring(idx))) return EMPTY_RESPONSE;
            int dot = host.indexOf('.', idx);
            if (dot < 0) break;
            idx = dot + 1;
        }

        // 2. Регэкспы по хосту+пути (внутренние эндпоинты телеметрии MAX,
        //    счётчики на vk.com/ok.ru и т.п.).
        String hostPath = host + pathAndQuery.toLowerCase(Locale.ROOT);
        for (Pattern pattern : URL_PATTERNS) {
            if (pattern.matcher(hostPath).find()) return EMPTY_RESPONSE;
        }
        return null;
    }

    /** Удаляет трекинг-параметры из URL внешних ссылок. */
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
