package android.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MediaRouter2 discovery preference (which route types to scan for). Mac.apk
 * has no external media routes; the preference is stored and honoured by an
 * empty route set, matching a device with no discoverable routes.
 *
 * Source: frameworks/base/media/java/android/media/RouteDiscoveryPreference.java
 */
public final class RouteDiscoveryPreference implements android.os.Parcelable {

    public static final RouteDiscoveryPreference EMPTY =
            new RouteDiscoveryPreference(Collections.<String>emptyList(), false);

    private final List<String> mPreferredFeatures;
    private final boolean mActiveScan;

    RouteDiscoveryPreference(List<String> preferredFeatures, boolean activeScan) {
        mPreferredFeatures = preferredFeatures != null
                ? new ArrayList<>(preferredFeatures) : Collections.<String>emptyList();
        mActiveScan = activeScan;
    }

    public List<String> getPreferredFeatures() { return mPreferredFeatures; }

    public boolean shouldPerformActiveScan() { return mActiveScan; }

    // AOSP's RouteDiscoveryPreference also carries mPackageOrder,
    // mAllowedPackages and mExtras (RouteDiscoveryPreference.java L67-75);
    // this class only tracks preferredFeatures + activeScan, so this writes
    // our own two-field order rather than AOSP's exact wire shape.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeStringList(mPreferredFeatures);
        dest.writeBoolean(mActiveScan);
    }

    public static final android.os.Parcelable.Creator<RouteDiscoveryPreference> CREATOR =
            new android.os.Parcelable.Creator<RouteDiscoveryPreference>() {
        @Override public RouteDiscoveryPreference createFromParcel(android.os.Parcel in) {
            List<String> preferredFeatures = new ArrayList<>();
            in.readStringList(preferredFeatures);
            boolean activeScan = in.readBoolean();
            return new RouteDiscoveryPreference(preferredFeatures, activeScan);
        }
        @Override public RouteDiscoveryPreference[] newArray(int size) {
            return new RouteDiscoveryPreference[size];
        }
    };

    public static final class Builder {
        private final List<String> mPreferredFeatures = new ArrayList<>();
        private boolean mActiveScan;

        public Builder(List<String> preferredFeatures, boolean activeScan) {
            if (preferredFeatures != null) mPreferredFeatures.addAll(preferredFeatures);
            mActiveScan = activeScan;
        }
        public Builder(RouteDiscoveryPreference preference) {
            mPreferredFeatures.addAll(preference.mPreferredFeatures);
            mActiveScan = preference.mActiveScan;
        }
        /** API 30. Replaces the preferred feature list wholesale. AOSP:
         *  "mPreferredFeatures = new ArrayList<>(preferredFeatures)"
         *  (RouteDiscoveryPreference.java L300-306); it requires non-null. */
        public Builder setPreferredFeatures(List<String> preferredFeatures) {
            java.util.Objects.requireNonNull(preferredFeatures,
                    "preferredFeatures must not be null");
            mPreferredFeatures.clear();
            mPreferredFeatures.addAll(preferredFeatures);
            return this;
        }
        public Builder setShouldPerformActiveScan(boolean activeScan) {
            mActiveScan = activeScan; return this;
        }
        public RouteDiscoveryPreference build() {
            return new RouteDiscoveryPreference(mPreferredFeatures, mActiveScan);
        }
    }
}
