package android.content;

/**
 * A ContextWrapper whose base context can be swapped after construction.
 *
 * The reason this exists in AOSP (and why apps depend on it) is view
 * recycling: a WebView or a custom View is inflated against one Context, then
 * re-hosted under another, and the owner calls setBaseContext to re-point it
 * without rebuilding the view. ContextWrapper.attachBaseContext is protected
 * and AOSP's version also refuses to re-attach, so a public setter is the whole
 * point of this class.
 *
 * Source: frameworks/base/core/java/android/content/MutableContextWrapper.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/MutableContextWrapper.java
 */
public class MutableContextWrapper extends ContextWrapper {

    public MutableContextWrapper(Context base) {
        super(base);
    }

    /**
     * Point this wrapper at a new base context. Unlike
     * ContextWrapper.attachBaseContext this is public and may be called any
     * number of times.
     */
    public void setBaseContext(Context base) {
        attachBaseContext(base);
    }
}
