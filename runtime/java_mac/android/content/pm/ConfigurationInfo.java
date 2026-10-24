// android.content.pm.ConfigurationInfo -- the bag returned by
// ActivityManager.getDeviceConfigurationInfo(). The engines we target
// only inspect reqGlEsVersion to decide which renderer to spin up.
package android.content.pm;

public class ConfigurationInfo implements android.os.Parcelable {
    public static final int GL_ES_VERSION_UNDEFINED = 0;
    public int reqGlEsVersion = GL_ES_VERSION_UNDEFINED;
    public int reqInputFeatures;
    public int reqKeyboardType;
    public int reqNavigation;
    public int reqTouchScreen;

    /** AOSP ConfigurationInfo (API 1): INPUT_FEATURE_* bits for reqInputFeatures.
     *  Values verbatim from frameworks/base .../ConfigurationInfo.java. */
    public static final int INPUT_FEATURE_HARD_KEYBOARD = 0x00000001;
    public static final int INPUT_FEATURE_FIVE_WAY_NAV  = 0x00000002;

    public ConfigurationInfo() {}

    /** AOSP ConfigurationInfo(ConfigurationInfo orig) -- the copy constructor.
     *  Copies every field; no null-check, matching upstream (it NPEs, and
     *  swallowing that would hand the caller a silently blank object). */
    public ConfigurationInfo(ConfigurationInfo orig) {
        reqTouchScreen   = orig.reqTouchScreen;
        reqKeyboardType  = orig.reqKeyboardType;
        reqNavigation    = orig.reqNavigation;
        reqInputFeatures = orig.reqInputFeatures;
        reqGlEsVersion   = orig.reqGlEsVersion;
    }
    public String getGlEsVersion() {
        return ((reqGlEsVersion & 0xffff0000) >> 16) + "." + (reqGlEsVersion & 0xffff);
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `ConfigurationInfo implements Parcelable` (android11-release
    // ConfigurationInfo.java:29) -- it extends Object, so there is no super
    // chain here. Order is AOSP ConfigurationInfo.java:108-114, which is NOT
    // the declaration order: reqGlEsVersion is written LAST.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        dest.writeInt(reqTouchScreen);
        dest.writeInt(reqKeyboardType);
        dest.writeInt(reqNavigation);
        dest.writeInt(reqInputFeatures);
        dest.writeInt(reqGlEsVersion);
    }

    /** AOSP ConfigurationInfo.java:126-132. */
    private ConfigurationInfo(android.os.Parcel source) {
        reqTouchScreen = source.readInt();
        reqKeyboardType = source.readInt();
        reqNavigation = source.readInt();
        reqInputFeatures = source.readInt();
        reqGlEsVersion = source.readInt();
    }

    public static final android.os.Parcelable.Creator<ConfigurationInfo> CREATOR =
        new android.os.Parcelable.Creator<ConfigurationInfo>() {
            @Override public ConfigurationInfo createFromParcel(android.os.Parcel source) {
                return new ConfigurationInfo(source);
            }
            @Override public ConfigurationInfo[] newArray(int size) {
                return new ConfigurationInfo[size];
            }
        };
}
