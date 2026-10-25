package android.webkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// android.webkit.CookieManager (AOSP API-1).
// Manages cookies the browser engine uses. Since 2026-08-12 this is backed by
// the SAME per-app WKHTTPCookieStore the WKWebView engine navigates with
// (webview_bridge.mm), which is the property OAuth flows depend on: a cookie
// the login page sets is immediately visible to getCookie(), exactly as
// Android's CookieManager and WebView share one Chromium cookie jar. The
// synchronous getters park the CALLING guest thread on a bounded native wait
// while the AppKit main queue answers (one-directional block, no cycle).
// When the native engine is unavailable (headless diagnostic runtime) the
// old in-memory store keeps setCookie/getCookie round-tripping in-session.
//
// Mirrors the public API surface from
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/CookieManager.java
public abstract class CookieManager {

    public static CookieManager getInstance() {
        return Holder.INSTANCE;
    }

    // Lazy singleton so the abstract type can be referenced without forcing
    // construction; matches AOSP's "singleton from the factory" shape while
    // letting us pick the backing store once, at first use.
    private static final class Holder {
        static final CookieManager INSTANCE;
        static {
            CookieManager impl;
            if (MacApkWebViewBridge.engineAvailable()) {
                MacApkWebViewBridge.ensureDataStore();
                impl = new WkCookieManager();
            } else {
                impl = new InMemoryCookieManager();
            }
            INSTANCE = impl;
        }
    }

    // WKHTTPCookieStore-backed implementation: the engine's own jar.
    private static final class WkCookieManager extends CookieManager {
        private volatile boolean acceptCookie = true;

        @Override public void setAcceptCookie(boolean accept) { acceptCookie = accept; }
        @Override public boolean acceptCookie() { return acceptCookie; }

        @Override public void setAcceptThirdPartyCookies(WebView webview, boolean accept) { }
        @Override public boolean acceptThirdPartyCookies(WebView webview) { return acceptCookie; }

        @Override public void setCookie(String url, String value) {
            if (!acceptCookie || url == null || value == null) return;
            MacApkWebViewBridge.nativeCookieSet(url, value);
        }

        @Override public void setCookie(String url, String value, ValueCallback<Boolean> callback) {
            setCookie(url, value);
            if (callback != null) callback.onReceiveValue(Boolean.TRUE);
        }

        @Override public String getCookie(String url) {
            if (url == null) return null;
            return MacApkWebViewBridge.nativeCookieGet(url);
        }

        @Override public void removeSessionCookie() {
            // AOSP removes only session (no-expiry) cookies here; WebKit's
            // store API cannot filter server-side, and the dominant caller
            // intent is "log me out", which removeAll satisfies.
            MacApkWebViewBridge.nativeCookieRemoveAll();
        }

        @Override public void removeSessionCookies(ValueCallback<Boolean> callback) {
            removeSessionCookie();
            if (callback != null) callback.onReceiveValue(Boolean.TRUE);
        }

        @Override public void removeAllCookie() {
            MacApkWebViewBridge.nativeCookieRemoveAll();
        }

        @Override public void removeAllCookies(ValueCallback<Boolean> callback) {
            removeAllCookie();
            if (callback != null) callback.onReceiveValue(Boolean.TRUE);
        }

        @Override public void removeExpiredCookie() { /* WebKit expires automatically */ }

        @Override public boolean hasCookies() {
            return MacApkWebViewBridge.nativeCookieHas();
        }

        @Override public void flush() { /* WKWebsiteDataStore persists automatically */ }
    }

    public abstract void setAcceptCookie(boolean accept);

    public abstract boolean acceptCookie();

    public abstract void setAcceptThirdPartyCookies(WebView webview, boolean accept);

    public abstract boolean acceptThirdPartyCookies(WebView webview);

    public abstract void setCookie(String url, String value);

    public abstract void setCookie(String url, String value, ValueCallback<Boolean> callback);

    public abstract String getCookie(String url);

    public abstract void removeSessionCookie();

    public abstract void removeSessionCookies(ValueCallback<Boolean> callback);

    public abstract void removeAllCookie();

    public abstract void removeAllCookies(ValueCallback<Boolean> callback);

    public abstract void removeExpiredCookie();

    public abstract boolean hasCookies();

    public abstract void flush();

    // setAcceptFileSchemeCookies / allowFileSchemeCookies are static in AOSP.
    public static boolean allowFileSchemeCookies() {
        return InMemoryCookieManager.sAllowFileScheme;
    }

    public static void setAcceptFileSchemeCookies(boolean accept) {
        InMemoryCookieManager.sAllowFileScheme = accept;
    }

    // Concrete in-memory backing store. Cookies are stored per-host as a
    // name->value map so repeated setCookie of the same name overwrites,
    // which matches browser semantics closely enough for session sharing.
    private static final class InMemoryCookieManager extends CookieManager {
        static volatile boolean sAllowFileScheme = false;

        private volatile boolean acceptCookie = true;
        // host -> (cookieName -> cookieValue)
        private final Map<String, Map<String, String>> store = new ConcurrentHashMap<>();

        @Override public void setAcceptCookie(boolean accept) { this.acceptCookie = accept; }
        @Override public boolean acceptCookie() { return acceptCookie; }

        @Override public void setAcceptThirdPartyCookies(WebView webview, boolean accept) { }
        @Override public boolean acceptThirdPartyCookies(WebView webview) { return acceptCookie; }

        @Override public void setCookie(String url, String value) {
            if (!acceptCookie || url == null || value == null) return;
            String host = hostOf(url);
            // value is a Set-Cookie style string: "name=val; Path=/; ..."
            String pair = value.split(";", 2)[0].trim();
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            store.computeIfAbsent(host, k -> new ConcurrentHashMap<>()).put(name, v);
        }

        @Override public void setCookie(String url, String value, ValueCallback<Boolean> callback) {
            setCookie(url, value);
            if (callback != null) callback.onReceiveValue(Boolean.TRUE);
        }

        @Override public String getCookie(String url) {
            if (url == null) return null;
            Map<String, String> jar = store.get(hostOf(url));
            if (jar == null || jar.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : jar.entrySet()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            return sb.toString();
        }

        @Override public void removeSessionCookie() { store.clear(); }

        @Override public void removeSessionCookies(ValueCallback<Boolean> callback) {
            boolean had = !store.isEmpty();
            store.clear();
            if (callback != null) callback.onReceiveValue(had);
        }

        @Override public void removeAllCookie() { store.clear(); }

        @Override public void removeAllCookies(ValueCallback<Boolean> callback) {
            boolean had = !store.isEmpty();
            store.clear();
            if (callback != null) callback.onReceiveValue(had);
        }

        @Override public void removeExpiredCookie() { /* no expiry tracking in-memory */ }

        @Override public boolean hasCookies() { return !store.isEmpty(); }

        @Override public void flush() { /* in-memory; nothing to persist */ }

        // Extracts the host from an http(s)/file URL for the cookie jar key.
        // Falls back to the raw string when it is not a recognizable URL so
        // round-tripping still works for opaque scheme inputs.
        private static String hostOf(String url) {
            try {
                java.net.URI u = java.net.URI.create(url);
                String h = u.getHost();
                if (h != null) return h.toLowerCase();
            } catch (RuntimeException ignored) {
            }
            return url;
        }
    }
}
