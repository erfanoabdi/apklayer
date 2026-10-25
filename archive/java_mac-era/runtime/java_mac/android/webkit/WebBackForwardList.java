package android.webkit;

import java.io.Serializable;

// android.webkit.WebBackForwardList (AOSP API-1).
// Returned by WebView.copyBackForwardList / saveState / restoreState. Abstract +
// Cloneable + Serializable in AOSP. With no navigation history yet it is empty.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebBackForwardList.java
public abstract class WebBackForwardList implements Cloneable, Serializable {

    public abstract int getCurrentIndex();

    public abstract WebHistoryItem getCurrentItem();

    public abstract WebHistoryItem getItemAtIndex(int index);

    public abstract int getSize();

    protected abstract WebBackForwardList clone();
}
