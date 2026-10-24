package androidx.window.extensions.layout;
import java.util.Collections;
import java.util.List;
/** The display features intersecting a window. Empty on a flat display. */
public class WindowLayoutInfo {
    private final List<DisplayFeature> mDisplayFeatures;
    public WindowLayoutInfo(List<DisplayFeature> displayFeatures) {
        mDisplayFeatures = displayFeatures != null ? displayFeatures : Collections.emptyList();
    }
    public List<DisplayFeature> getDisplayFeatures() { return mDisplayFeatures; }
}
