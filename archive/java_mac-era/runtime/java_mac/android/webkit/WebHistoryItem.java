package android.webkit;

import android.graphics.Bitmap;

// android.webkit.WebHistoryItem (AOSP API-1).
// One entry in the back/forward list. Abstract in AOSP (Cloneable). Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebHistoryItem.java
public abstract class WebHistoryItem implements Cloneable {

    @Deprecated
    public abstract int getId();

    public abstract String getUrl();

    public abstract String getOriginalUrl();

    public abstract String getTitle();

    public abstract Bitmap getFavicon();

    protected abstract WebHistoryItem clone();
}
