package androidx.window.extensions.layout;
/** A capability descriptor for a fold the display supports. Flat display -> the
 *  device has no fold, so hasProperties is false. */
public class DisplayFoldFeature {
    public static final int TYPE_HINGE = 1;
    public static final int TYPE_SCREEN_FOLD_CREASE = 2;
    public static final int FOLD_PROPERTY_SUPPORTS_HALF_OPENED = 1;
    private final int mType;
    private final java.util.Set<Integer> mProperties;
    public DisplayFoldFeature(int type, java.util.Set<Integer> properties) {
        mType = type; mProperties = properties != null ? properties : java.util.Collections.emptySet();
    }
    public int getType() { return mType; }
    public java.util.Set<Integer> getProperties() { return mProperties; }
    public boolean hasProperties(int... properties) {
        if (properties == null) return false;
        for (int p : properties) if (!mProperties.contains(p)) return false;
        return properties.length > 0;
    }
    public static final class Builder {
        private final int mType;
        private final java.util.Set<Integer> mProps = new java.util.HashSet<>();
        public Builder(int type) { mType = type; }
        public Builder addProperties(int... properties) {
            if (properties != null) for (int p : properties) mProps.add(p);
            return this;
        }
        public DisplayFoldFeature build() { return new DisplayFoldFeature(mType, mProps); }
    }
}
