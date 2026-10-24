package android.webkit;

// android.webkit.ServiceWorkerWebSettings (AOSP API-24).
// Settings for service-worker fetches. Abstract in AOSP. No engine yet; stores
// nothing meaningful but resolves. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/ServiceWorkerWebSettings.java
public abstract class ServiceWorkerWebSettings {

    public abstract void setCacheMode(int mode);
    public abstract int getCacheMode();
    public abstract void setAllowContentAccess(boolean allow);
    public abstract boolean getAllowContentAccess();
    public abstract void setAllowFileAccess(boolean allow);
    public abstract boolean getAllowFileAccess();
    public abstract void setBlockNetworkLoads(boolean flag);
    public abstract boolean getBlockNetworkLoads();
}
