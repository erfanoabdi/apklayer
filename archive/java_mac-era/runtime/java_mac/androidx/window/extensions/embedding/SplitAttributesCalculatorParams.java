package androidx.window.extensions.embedding;

public final class SplitAttributesCalculatorParams {
    SplitAttributesCalculatorParams() {}
    public android.view.WindowMetrics getParentWindowMetrics() { return null; }
    // A copy of the process configuration, not the all-UNDEFINED
    // `new Configuration()` delta (MACAPK_CONFIG_UNSET_CTOR); see
    // ParentContainerInfo.getConfiguration.
    public android.content.res.Configuration getParentConfiguration() {
        return new android.content.res.Configuration(
            android.content.res.Resources.getSystem().getConfiguration());
    }
    public androidx.window.extensions.layout.WindowLayoutInfo getParentWindowLayoutInfo() {
        return new androidx.window.extensions.layout.WindowLayoutInfo(java.util.Collections.emptyList());
    }
    public SplitAttributes getDefaultSplitAttributes() { return new SplitAttributes.Builder().build(); }
    public boolean areDefaultConstraintsSatisfied() { return false; }
    public String getSplitRuleTag() { return null; }
}
