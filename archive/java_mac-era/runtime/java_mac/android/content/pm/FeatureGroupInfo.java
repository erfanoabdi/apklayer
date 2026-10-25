// android.content.pm.FeatureGroupInfo -- one <uses-feature-group> from a
// package's manifest: a set of features that must be satisfied TOGETHER.
//
// Mirrors AOSP frameworks/base core/java/android/content/pm/FeatureGroupInfo.java
// (android-11.0.0_r48). It exists here because PackageInfo.featureGroups is a
// public AOSP field of this type: an app reading `pi.featureGroups` links
// against the field descriptor `[Landroid/content/pm/FeatureGroupInfo;`, so the
// class has to be present for the read to resolve at all, even when the value
// is the usual null (no manifest declares a feature group unless it needs the
// grouped-OR semantics, and Play is the only consumer that acts on them).
package android.content.pm;

public final class FeatureGroupInfo implements android.os.Parcelable {

    /** The features that constitute this group. AOSP leaves this null when the
     *  group is empty rather than substituting an empty array. */
    public FeatureInfo[] features;

    public FeatureGroupInfo() {}

    public FeatureGroupInfo(FeatureGroupInfo other) {
        features = other != null ? other.features : null;
    }

    @Override public int describeContents() { return 0; }

    /** AOSP FeatureGroupInfo.java:48-50. This was `dest.writeValue(features)`
     *  on the premise that FeatureInfo was not Parcelable in this tree. It is
     *  now, and writeValue was never actually equivalent: Parcel.writeValue
     *  tags a reference array as VAL_PARCELABLEARRAY and readValue answers it
     *  with a `Parcelable[]`, so the read side's `(FeatureInfo[])` cast threw
     *  ClassCastException -- and once Parcel started really marshalling, the
     *  write side threw first for any non-Parcelable element type. */
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeTypedArray(features, flags);
    }

    public static final android.os.Parcelable.Creator<FeatureGroupInfo> CREATOR =
        new android.os.Parcelable.Creator<FeatureGroupInfo>() {
            @Override public FeatureGroupInfo createFromParcel(android.os.Parcel source) {
                FeatureGroupInfo out = new FeatureGroupInfo();
                // AOSP FeatureGroupInfo.java:55.
                out.features = source.createTypedArray(FeatureInfo.CREATOR);
                return out;
            }
            @Override public FeatureGroupInfo[] newArray(int size) {
                return new FeatureGroupInfo[size];
            }
        };

    @Override public String toString() {
        return "FeatureGroupInfo{" + Integer.toHexString(System.identityHashCode(this))
            + " " + (features == null ? "null" : String.valueOf(features.length))
            + " features}";
    }
}
