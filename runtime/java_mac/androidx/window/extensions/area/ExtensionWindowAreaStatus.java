package androidx.window.extensions.area;
/** Status of a window area (rear display). Mac.apk has none. */
public interface ExtensionWindowAreaStatus {
    int getWindowAreaStatus();
    android.util.DisplayMetrics getWindowAreaDisplayMetrics();
}
