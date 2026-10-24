package android.webkit;

import android.content.Context;

// android.webkit.CookieSyncManager (AOSP API-1, deprecated API-21).
// Legacy cookie-persistence pump; superseded by CookieManager.flush(). Apps
// targeting old SDKs still call createInstance/getInstance/sync. We keep a
// resolvable singleton; CookieManager already persists in-memory. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/CookieSyncManager.java
@Deprecated
public final class CookieSyncManager implements Runnable {

    private static CookieSyncManager sInstance;

    private CookieSyncManager() {}

    public static synchronized CookieSyncManager createInstance(Context context) {
        if (sInstance == null) sInstance = new CookieSyncManager();
        return sInstance;
    }

    public static synchronized CookieSyncManager getInstance() {
        if (sInstance == null) {
            // AOSP throws IllegalStateException if createInstance was not called
            // first; mirror that contract.
            throw new IllegalStateException(
                    "CookieSyncManager::createInstance() needs to be called "
                    + "before CookieSyncManager::getInstance()");
        }
        return sInstance;
    }

    public void sync() { CookieManager.getInstance().flush(); }

    public void stopSync() {}

    public void startSync() {}

    public void resetSync() {}

    /**
     * AOSP's CookieSyncManager extends the @hide WebSyncManager, which
     * implements Runnable: run() is the sync THREAD BODY -- a loop that waits
     * on a condition and calls syncFromRamToFlash() whenever a sync is due.
     * Since API 21 that background thread does nothing useful (Chromium
     * persists cookies itself), and AOSP's run() body is a no-op.
     *
     * Mac.apk is in the same position: CookieManager is backed by
     * WKHTTPCookieStore, which persists on its own schedule. So there is
     * nothing for a pump thread to do -- but run() must still EXIST, because
     * the class is declared Runnable in the public API and an app is free to
     * hand the singleton to a Thread. Doing the one useful thing (a flush)
     * rather than spinning is both cheaper and closer to the observable
     * behaviour: after run(), cookies are on disk.
     */
    @Override
    public void run() { syncFromRamToFlash(); }

    /**
     * AOSP declares this PROTECTED (see core/api/current.txt:
     * "method @Deprecated protected void syncFromRamToFlash()"), inherited
     * from WebSyncManager. It is the actual RAM->disk step that run() drives.
     */
    protected void syncFromRamToFlash() {
        CookieManager.getInstance().flush();
    }
}
