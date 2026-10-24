// android.content.QuickViewConstants -- the feature names an app puts in
// Intent.EXTRA_QUICK_VIEW_FEATURES to tell a quick-viewer which affordances to
// offer.
//
// AOSP ref: frameworks/base/core/java/android/content/QuickViewConstants.java
// (android16-release). Values verbatim.
package android.content;

public class QuickViewConstants {
    private QuickViewConstants() {}

    public static final String FEATURE_VIEW = "android:view";
    public static final String FEATURE_EDIT = "android:edit";
    public static final String FEATURE_DELETE = "android:delete";
    public static final String FEATURE_DOWNLOAD = "android:download";
    public static final String FEATURE_SEND = "android:send";
    public static final String FEATURE_PRINT = "android:print";
}
