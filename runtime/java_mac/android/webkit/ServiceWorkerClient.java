package android.webkit;

// android.webkit.ServiceWorkerClient (AOSP API-24).
// Intercepts service-worker fetches; the app subclasses it. No engine yet, so
// it is never invoked. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/ServiceWorkerClient.java
public class ServiceWorkerClient {

    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
        return null;
    }
}
