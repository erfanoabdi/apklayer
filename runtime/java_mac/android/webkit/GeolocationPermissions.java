package android.webkit;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// android.webkit.GeolocationPermissions (AOSP API-5).
// Tracks per-origin geolocation grants. WebChromeClient.onGeolocationPermissions-
// ShowPrompt hands the page's Callback here. We keep a real in-memory grant set
// so allow/clear round-trip; with no web engine the grants are not yet consulted
// by a page. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/GeolocationPermissions.java
public class GeolocationPermissions {

    // Callback the page's prompt uses to report the user's decision.
    public interface Callback {
        void invoke(String origin, boolean allow, boolean retain);
    }

    private static final GeolocationPermissions INSTANCE = new GeolocationPermissions();

    public static GeolocationPermissions getInstance() { return INSTANCE; }

    // origin -> allowed
    private final java.util.Map<String, Boolean> origins = new ConcurrentHashMap<>();

    public void getOrigins(ValueCallback<Set<String>> callback) {
        if (callback != null) {
            callback.onReceiveValue(new HashSet<>(origins.keySet()));
        }
    }

    public void getAllowed(String origin, ValueCallback<Boolean> callback) {
        if (callback != null) {
            callback.onReceiveValue(Boolean.TRUE.equals(origins.get(origin)));
        }
    }

    public void clear(String origin) {
        if (origin != null) origins.remove(origin);
    }

    public void allow(String origin) {
        if (origin != null) origins.put(origin, Boolean.TRUE);
    }

    public void clearAll() { origins.clear(); }
}
