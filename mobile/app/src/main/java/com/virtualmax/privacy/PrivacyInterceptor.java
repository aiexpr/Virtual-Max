package com.virtualmax.privacy;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PrivacyInterceptor — сетевой фильтр и JavaScript-песочница VirtualMax.
 *
 * 1. Блокировка сетевых запросов к трекерам (VK/Mail.ru, Яндекс, глобальная аналитика)
 *    на уровне WebView (shouldInterceptRequest), до выхода запроса в интернет.
 * 2. Injected DOM JS Sandbox: нейтрализация sendBeacon / fetch / XHR / WebSocket
 *    для телеметрии и «печати», анти-фингерпринт (Canvas, Audio, WebGL), маскировка
 *    параметров устройства, защита сенсоров движения, контроль Notification API.
 * 3. Очистка трекинг-параметров во внешних ссылках.
 */
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
        Pattern.compile("mstat\\.my\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("stat\\.mail\\.ru", Pattern.CASE_INSENSITIVE),
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
        Pattern.compile("adfox\\.yandex\\.ru", Pattern.CASE_INSENSITIVE),

        // Global Trackers & Crash Loggers
        Pattern.compile("sentry\\.io", Pattern.CASE_INSENSITIVE),
        Pattern.compile("google-analytics\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("googletagmanager\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("clarity\\.ms", Pattern.CASE_INSENSITIVE),
        Pattern.compile("hotjar\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mixpanel\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("amplitude\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("segment\\.io", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bugsnag\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("datadoghq\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("app\\.adjust\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("appsflyer\\.com", Pattern.CASE_INSENSITIVE),

        // Внутренние эндпоинты телеметрии MAX
        Pattern.compile("/api/v?\\d*/(?:telemetry|metrics|analytics|stats|collector|event(?:s)?|beacon|client_log|crash_report)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/(?:telemetry|tracking|collector|beacon|webvisor|metrika|c_stat|stat_out|log_event)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/pixel\\.(?:png|gif|jpg|svg)", Pattern.CASE_INSENSITIVE)
    };

    private static final String[] TRACKING_PARAMS = new String[] {
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_referrer", "utm_id", "yclid", "gclid", "fbclid", "gbraid", "wbraid",
        "vk_ref", "_openstat", "from", "ref", "stat_id", "device_id", "fingerprint", "fp"
    };

    /**
     * Строит JavaScript-песочницу с учётом текущих настроек пользователя.
     *
     * @param ghostMode          Режим Невидимки: глушить «Печатает...» / online-присутствие
     * @param blockNotifications Блокировать Notification API (тумблер уведомлений выключен)
     */
    public static String buildSandboxJs(boolean ghostMode, boolean blockNotifications) {
        StringBuilder js = new StringBuilder(4096);
        js.append("(function(){");
        js.append("if(window.__VIRTUALMAX_SHIELD__){return;}");
        js.append("window.__VIRTUALMAX_SHIELD__=true;");

        js.append("var GHOST=").append(ghostMode).append(";");
        js.append("var BLOCK_NOTIFS=").append(blockNotifications).append(";");

        // Регулярные выражения для телеметрии и «печати»
        js.append("var TELEMETRY=/(?:telemetry|metrics|analytics|collector|webvisor|c_stat(?:\\.php)?|");
        js.append("stat_out|client_log|crash_report|top-fwz1|mc\\.yandex|sentry\\.io|mail\\.ru\\/counter|pixel(?:[\\.](?:png|gif|jpg|svg))?)/i;");
        js.append("var TYPING=/(?:typing|set_activity|activity_status|online_status|presence)/i;");
        js.append("var OK=JSON.stringify({status:'ok',success:true});");

        // 1. sendBeacon — всегда глушим
        js.append("if(navigator.sendBeacon){navigator.sendBeacon=function(){return true;};}");

        // 2. fetch — перехват телеметрии и (в Ghost Mode) статусов печати
        js.append("var oFetch=window.fetch;");
        js.append("window.fetch=function(){");
        js.append("var u='';");
        js.append("try{u=typeof arguments[0]==='string'?arguments[0]:(arguments[0]&&arguments[0].url)||'';}catch(e){}");
        js.append("if(TELEMETRY.test(u)||(GHOST&&TYPING.test(u))){");
        js.append("return Promise.resolve(new Response(OK,{status:200,headers:{'Content-Type':'application/json'}}));");
        js.append("}");
        js.append("return oFetch.apply(this,arguments);");
        js.append("};");

        // 3. XMLHttpRequest — аналогичный перехват с фейковым статусом 200 OK
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
        js.append("try{if(self.onreadystatechange){self.onreadystatechange();}}catch(e){}");
        js.append("try{if(self.onload){self.onload();}}catch(e){}");
        js.append("},0);");
        js.append("return;");
        js.append("}catch(e){}");
        js.append("}");
        js.append("return oSend.apply(this,arguments);");
        js.append("};");
        js.append("}catch(e){}");

        // 4. Ghost Mode: WebSocket — глушение пакетов typing
        js.append("if(GHOST){");
        js.append("try{");
        js.append("var OWS=window.WebSocket;");
        js.append("function VMWS(){");
        js.append("var inst;");
        js.append("try{inst=new(Function.prototype.bind.apply(OWS,[null].concat([].slice.call(arguments))));}catch(e){inst=new OWS();}");
        js.append("var oS=inst.send.bind(inst);");
        js.append("inst.send=function(data){");
        js.append("if(typeof data==='string'){");
        js.append("try{var j=JSON.parse(data);");
        js.append("var t=String((j&&(j.type||j.event||j.action||j.method))||'');");
        js.append("if(/typing/i.test(t)){return;}");
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

        // 5. Anti-Fingerprint: Canvas микрошум (getImageData + toDataURL)
        js.append("try{");
        js.append("var NOISE=Math.random()>=0.5?1:-1;");
        js.append("var oGID=CanvasRenderingContext2D.prototype.getImageData;");
        js.append("CanvasRenderingContext2D.prototype.getImageData=function(){");
        js.append("var d=oGID.apply(this,arguments);");
        js.append("if(d&&d.data){for(var i=0;i<d.data.length;i+=16){d.data[i]=(d.data[i]+NOISE+256)%256;}}");
        js.append("return d;");
        js.append("};");
        js.append("var oTDU=HTMLCanvasElement.prototype.toDataURL;");
        js.append("HTMLCanvasElement.prototype.toDataURL=function(){");
        js.append("try{");
        js.append("var ctx=this.getContext&&this.getContext('2d');");
        js.append("if(ctx&&this.width>0&&this.height>0&&this.width*this.height<16777216){");
        js.append("var d=oGID.call(ctx,0,0,this.width,this.height);");
        js.append("if(d&&d.data&&d.data.length>0){d.data[0]=(d.data[0]+NOISE+256)%256;ctx.putImageData(d,0,0);}");
        js.append("}");
        js.append("}catch(e){}");
        js.append("return oTDU.apply(this,arguments);");
        js.append("};");
        js.append("}catch(e){}");

        // 6. Anti-Fingerprint: AudioContext микрошум (analyser)
        js.append("try{");
        js.append("var AC=window.AudioContext||window.webkitAudioContext;");
        js.append("if(AC&&AC.prototype&&AC.prototype.createAnalyser){");
        js.append("var oGFFD=AnalyserNode.prototype.getFloatFrequencyData;");
        js.append("AnalyserNode.prototype.getFloatFrequencyData=function(arr){oGFFD.call(this,arr);");
        js.append("for(var i=0;i<arr.length;i+=7){arr[i]+=(i%2?0.01:-0.01);}");
        js.append("};");
        js.append("}");
        js.append("}catch(e){}");

        // 7. Anti-Fingerprint: Маскировка WebGL GPU
        js.append("try{");
        js.append("var oGP=WebGLRenderingContext.prototype.getParameter;");
        js.append("WebGLRenderingContext.prototype.getParameter=function(p){");
        js.append("if(p===37445){return 'VirtualMax (Browser Vendor)';}");
        js.append("if(p===37446){return 'VirtualMax Shield GPU';}");
        js.append("return oGP.call(this,p);");
        js.append("};");
        js.append("if(typeof WebGL2RenderingContext!=='undefined'){");
        js.append("var oGP2=WebGL2RenderingContext.prototype.getParameter;");
        js.append("WebGL2RenderingContext.prototype.getParameter=function(p){");
        js.append("if(p===37445){return 'VirtualMax (Browser Vendor)';}");
        js.append("if(p===37446){return 'VirtualMax Shield GPU';}");
        js.append("return oGP2.call(this,p);");
        js.append("};");
        js.append("}");
        js.append("}catch(e){}");

        // 8. Маскировка параметров устройства
        js.append("try{");
        js.append("Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 8;},configurable:true});");
        js.append("try{Object.defineProperty(navigator,'deviceMemory',{get:function(){return 8;},configurable:true});}catch(e){}");
        js.append("try{delete navigator.getBattery;navigator.getBattery=undefined;}catch(e){}");
        js.append("}catch(e){}");

        // 9. Защита сенсоров движения (DeviceOrientation / DeviceMotion)
        js.append("window.addEventListener('deviceorientation',function(e){e.stopImmediatePropagation();},true);");
        js.append("window.addEventListener('devicemotion',function(e){e.stopImmediatePropagation();},true);");

        // 10. Управление уведомлениями (тумблер Настройки)
        js.append("if(BLOCK_NOTIFS&&('Notification' in window)){");
        js.append("try{Notification.requestPermission=function(){return Promise.resolve('denied');};}catch(e){}");
        js.append("}");

        js.append("})();");
        return js.toString();
    }

    /** Песочница по умолчанию: Ghost Mode вкл, уведомления не блокируются. */
    public static final String INJECTED_SANDBOX_JS = buildSandboxJs(true, false);

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