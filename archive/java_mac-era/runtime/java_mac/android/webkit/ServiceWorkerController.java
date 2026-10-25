package android.webkit;

// android.webkit.ServiceWorkerController (AOSP API-24).
// Entry point to manage PWA service workers. Abstract in AOSP. No engine yet, so
// getServiceWorkerWebSettings returns a concrete no-op settings object and
// setServiceWorkerClient stores nothing actionable. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/ServiceWorkerController.java
public abstract class ServiceWorkerController {

    public static ServiceWorkerController getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final ServiceWorkerController INSTANCE = new EmptyController();
    }

    public abstract void setServiceWorkerClient(ServiceWorkerClient client);

    public abstract ServiceWorkerWebSettings getServiceWorkerWebSettings();

    private static final class EmptyController extends ServiceWorkerController {
        private final ServiceWorkerWebSettings settings = new ServiceWorkerWebSettings() {
            private int cacheMode = WebSettings.LOAD_DEFAULT;
            private boolean allowContent = true;
            private boolean allowFile = false;
            private boolean blockNetwork = false;
            @Override public void setCacheMode(int mode) { cacheMode = mode; }
            @Override public int getCacheMode() { return cacheMode; }
            @Override public void setAllowContentAccess(boolean allow) { allowContent = allow; }
            @Override public boolean getAllowContentAccess() { return allowContent; }
            @Override public void setAllowFileAccess(boolean allow) { allowFile = allow; }
            @Override public boolean getAllowFileAccess() { return allowFile; }
            @Override public void setBlockNetworkLoads(boolean flag) { blockNetwork = flag; }
            @Override public boolean getBlockNetworkLoads() { return blockNetwork; }
        };
        @Override public void setServiceWorkerClient(ServiceWorkerClient client) {}
        @Override public ServiceWorkerWebSettings getServiceWorkerWebSettings() { return settings; }
    }
}
