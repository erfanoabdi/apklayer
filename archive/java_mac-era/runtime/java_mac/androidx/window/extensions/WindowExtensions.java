package androidx.window.extensions;
/** The root of the WindowManager extensions surface. Mac.apk is a flat single-
 *  display device: the layout component reports no fold, embedding reports no
 *  split, area reports no rear display. */
public interface WindowExtensions {
    int VENDOR_API_LEVEL_1 = 1;
    int VENDOR_API_LEVEL_2 = 2;
    default int getVendorApiLevel() { return VENDOR_API_LEVEL_2; }
    androidx.window.extensions.layout.WindowLayoutComponent getWindowLayoutComponent();
    androidx.window.extensions.embedding.ActivityEmbeddingComponent getActivityEmbeddingComponent();
    androidx.window.extensions.area.WindowAreaComponent getWindowAreaComponent();
}
