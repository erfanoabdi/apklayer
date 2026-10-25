package android.webkit;

// android.webkit.SafeBrowsingResponse (AOSP API-27).
// Passed to WebViewClient.onSafeBrowsingHit. No Safe Browsing backend, so the
// response decision is recorded but not acted on. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/SafeBrowsingResponse.java
public abstract class SafeBrowsingResponse {

    public abstract void showInterstitial(boolean allowReporting);

    public abstract void proceed(boolean report);

    public abstract void backToSafety(boolean report);
}
