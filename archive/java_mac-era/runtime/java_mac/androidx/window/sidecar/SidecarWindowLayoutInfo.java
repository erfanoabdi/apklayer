package androidx.window.sidecar;
import java.util.ArrayList;
import java.util.List;
/** The display features in a window. Empty on a flat display. */
public final class SidecarWindowLayoutInfo {
    /** Public field, as in the AOSP sidecar surface. */
    public List<SidecarDisplayFeature> displayFeatures = new ArrayList<>();
    public SidecarWindowLayoutInfo() {}
}
