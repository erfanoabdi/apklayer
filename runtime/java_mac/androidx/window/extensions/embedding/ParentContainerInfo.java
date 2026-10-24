package androidx.window.extensions.embedding;

public final class ParentContainerInfo {
    ParentContainerInfo() {}
    // The parent container's configuration: a copy of the process
    // configuration, as a WM Extensions provider hands androidx.window a
    // populated one -- not `new Configuration()`, which is AOSP's all-UNDEFINED
    // delta under MACAPK_CONFIG_UNSET_CTOR (0 dp wide, density 0).
    public android.content.res.Configuration getConfiguration() {
        return new android.content.res.Configuration(
            android.content.res.Resources.getSystem().getConfiguration());
    }
    public androidx.window.extensions.layout.WindowLayoutInfo getWindowLayoutInfo() {
        return new androidx.window.extensions.layout.WindowLayoutInfo(java.util.Collections.emptyList());
    }
    public android.view.WindowMetrics getWindowMetrics() { return null; }
}
