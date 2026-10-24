package android.webkit;

import android.graphics.Bitmap;

// android.webkit.WebIconDatabase (AOSP API-1, deprecated API-19).
// Legacy favicon cache, fully deprecated. Abstract in AOSP. We resolve it as a
// no-op singleton. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebIconDatabase.java
@Deprecated
public abstract class WebIconDatabase {

    // Deprecated favicon-loaded callback.
    public interface IconListener {
        void onReceivedIcon(String url, Bitmap icon);
    }

    public static WebIconDatabase getInstance() { return Holder.INSTANCE; }

    private static final class Holder {
        static final WebIconDatabase INSTANCE = new EmptyIconDatabase();
    }

    public abstract void open(String path);
    public abstract void close();
    public abstract void removeAllIcons();
    public abstract void requestIconForPageUrl(String url, IconListener listener);
    public abstract void bulkRequestIconForPageUrl(android.content.ContentResolver cr,
                                                   String where, IconListener listener);
    public abstract void retainIconForPageUrl(String url);
    public abstract void releaseIconForPageUrl(String url);

    private static final class EmptyIconDatabase extends WebIconDatabase {
        @Override public void open(String path) {}
        @Override public void close() {}
        @Override public void removeAllIcons() {}
        @Override public void requestIconForPageUrl(String url, IconListener listener) {}
        @Override public void bulkRequestIconForPageUrl(android.content.ContentResolver cr,
                                                        String where, IconListener listener) {}
        @Override public void retainIconForPageUrl(String url) {}
        @Override public void releaseIconForPageUrl(String url) {}
    }
}
