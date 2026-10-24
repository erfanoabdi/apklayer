package android.webkit;

import java.util.HashMap;
import java.util.Map;

// android.webkit.WebStorage (AOSP API-7).
// Manages the JS Storage API (localStorage/IndexedDB) origins/quotas. No web
// engine yet, so there are no real origins; the data methods return empty/zero
// honestly and invoke their callbacks so callers do not hang. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebStorage.java
public class WebStorage {

    // Deprecated quota-update callback, kept for API completeness.
    public interface QuotaUpdater {
        void updateQuota(long newQuota);
    }

    // Per-origin usage/quota record (AOSP nested class).
    public static class Origin {
        protected String mOrigin = null;
        protected long mQuota = 0;
        protected long mUsage = 0;

        protected Origin(String origin, long quota, long usage) {
            mOrigin = origin;
            mQuota = quota;
            mUsage = usage;
        }

        protected Origin(String origin, long quota) { this(origin, quota, 0); }

        protected Origin(String origin) { this(origin, 0, 0); }

        public String getOrigin() { return mOrigin; }

        public long getQuota() { return mQuota; }

        public long getUsage() { return mUsage; }
    }

    private static final WebStorage INSTANCE = new WebStorage();

    public static WebStorage getInstance() { return INSTANCE; }

    public void getOrigins(ValueCallback<Map> callback) {
        if (callback != null) {
            callback.onReceiveValue(new HashMap<String, Origin>());
        }
    }

    public void getUsageForOrigin(String origin, ValueCallback<Long> callback) {
        if (callback != null) callback.onReceiveValue(0L);
    }

    public void getQuotaForOrigin(String origin, ValueCallback<Long> callback) {
        if (callback != null) callback.onReceiveValue(0L);
    }

    @Deprecated
    public void setQuotaForOrigin(String origin, long quota) { }

    public void deleteOrigin(String origin) { }

    public void deleteAllData() { }
}
