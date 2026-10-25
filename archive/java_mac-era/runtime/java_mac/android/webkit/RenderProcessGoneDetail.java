package android.webkit;

// android.webkit.RenderProcessGoneDetail (AOSP API-26).
// Passed to WebViewClient.onRenderProcessGone. Abstract in AOSP. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/RenderProcessGoneDetail.java
public abstract class RenderProcessGoneDetail {

    public abstract boolean didCrash();

    public abstract int rendererPriorityAtExit();
}
