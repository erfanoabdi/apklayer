package androidx.window.extensions.layout;
import java.util.Collections;
import java.util.List;
/** The window features this device supports. Flat display -> no fold features. */
public class SupportedWindowFeatures {
    private final List<DisplayFoldFeature> mDisplayFoldFeatures;
    private SupportedWindowFeatures(List<DisplayFoldFeature> f) {
        mDisplayFoldFeatures = f != null ? f : Collections.emptyList();
    }
    public List<DisplayFoldFeature> getDisplayFoldFeatures() { return mDisplayFoldFeatures; }
    public static final class Builder {
        private final List<DisplayFoldFeature> mFeatures;
        public Builder(List<DisplayFoldFeature> displayFoldFeatures) {
            mFeatures = displayFoldFeatures != null ? displayFoldFeatures : Collections.emptyList();
        }
        public SupportedWindowFeatures build() { return new SupportedWindowFeatures(mFeatures); }
    }
}
