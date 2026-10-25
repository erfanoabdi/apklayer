// android.content.pm.FeatureInfo -- a single system feature, either a
// named hardware/software feature ("android.hardware.camera") or, when
// name is null, an OpenGL ES version requirement carried in
// reqGlEsVersion. PackageManager.getSystemAvailableFeatures() returns
// these; apps reading <uses-feature> from their own PackageInfo also get
// them.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/pm/FeatureInfo.java
// Previously a bare stub -> field/method access NoSuchFieldError /
// NoSuchMethodError.
package android.content.pm;

public class FeatureInfo implements android.os.Parcelable {
    /** Feature name, e.g. "android.hardware.camera"; null encodes a GL ES
     *  version feature (reqGlEsVersion is then meaningful). */
    public String name;
    /** Maximum version of the named feature available on the device, or the
     *  minimum required by an app's <uses-feature>. */
    public int version;
    /** OpenGL ES version: upper 16 bits = major, lower 16 = minor. */
    public int reqGlEsVersion;
    /** Additional feature flags (FLAG_REQUIRED). */
    public int flags;

    /** Default value of reqGlEsVersion when the feature is not a GL feature. */
    public static final int GL_ES_VERSION_UNDEFINED = 0;
    /** Set in flags when the feature is required by the application. */
    public static final int FLAG_REQUIRED = 0x0001;

    public FeatureInfo() {}

    public FeatureInfo(FeatureInfo orig) {
        name = orig.name;
        version = orig.version;
        reqGlEsVersion = orig.reqGlEsVersion;
        flags = orig.flags;
    }

    /** AOSP FeatureInfo.getGlEsVersion(): formats reqGlEsVersion as
     *  "major.minor" (e.g. 0x00020000 -> "2.0"). */
    public String getGlEsVersion() {
        int major = (reqGlEsVersion & 0xffff0000) >> 16;
        int minor = reqGlEsVersion & 0x0000ffff;
        return major + "." + minor;
    }

    @Override public String toString() {
        if (name != null) {
            return "FeatureInfo{" + name + " v=" + version + "}";
        }
        return "FeatureInfo{glEsVers=" + getGlEsVersion() + "}";
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `FeatureInfo implements Parcelable` (android11-release
    // FeatureInfo.java:31). Order is AOSP FeatureInfo.java:110-115; this tree
    // declares every field it writes. PackageInfo.reqFeatures and
    // FeatureGroupInfo.features are both FeatureInfo[] flattened with
    // writeTypedArray, so this CREATOR is what makes those arrays marshallable.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        dest.writeString(name);
        dest.writeInt(version);
        dest.writeInt(reqGlEsVersion);
        dest.writeInt(flags);
    }

    /** AOSP FeatureInfo.java:140-145. */
    private FeatureInfo(android.os.Parcel source) {
        name = source.readString();
        version = source.readInt();
        reqGlEsVersion = source.readInt();
        flags = source.readInt();
    }

    public static final android.os.Parcelable.Creator<FeatureInfo> CREATOR =
        new android.os.Parcelable.Creator<FeatureInfo>() {
            @Override public FeatureInfo createFromParcel(android.os.Parcel source) {
                return new FeatureInfo(source);
            }
            @Override public FeatureInfo[] newArray(int size) {
                return new FeatureInfo[size];
            }
        };
}
