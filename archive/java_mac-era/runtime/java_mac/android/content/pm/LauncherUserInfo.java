package android.content.pm;

/** android.content.pm.LauncherUserInfo (API 35) -- the launcher-facing
 *  description of a user profile: its UserManager type string, its serial
 *  number, and a config Bundle of per-profile launcher preferences.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/LauncherUserInfo.java
 *  (android16-release). Built by the system (its constructor is @hide); public
 *  here so this tree can construct the one profile it has. */
public final class LauncherUserInfo implements android.os.Parcelable {
    /** AOSP LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN: the boolean key
     *  in getUserConfig() saying whether the private-space entry point should
     *  be hidden. Value verbatim. */
    public static final String PRIVATE_SPACE_ENTRYPOINT_HIDDEN =
            "private_space_entrypoint_hidden";

    private final String mUserType;
    private final int mUserSerialNumber;
    private final android.os.Bundle mUserConfig;

    public LauncherUserInfo(String userType, int userSerialNumber,
            android.os.Bundle userConfig) {
        mUserType = userType;
        mUserSerialNumber = userSerialNumber;
        mUserConfig = userConfig;
    }

    /** AOSP: "the type of the user as defined in UserManager" -- e.g.
     *  android.os.usertype.full.SYSTEM. */
    public String getUserType() { return mUserType; }

    /** AOSP: "the serial number of the user." */
    public int getUserSerialNumber() { return mUserSerialNumber; }

    /** AOSP: "the configuration for the user", @NonNull. */
    public android.os.Bundle getUserConfig() {
        return mUserConfig != null ? mUserConfig : new android.os.Bundle();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mUserType);
        dest.writeInt(mUserSerialNumber);
        dest.writeBundle(mUserConfig);
    }

    public static final android.os.Parcelable.Creator<LauncherUserInfo> CREATOR =
            new android.os.Parcelable.Creator<LauncherUserInfo>() {
        @Override public LauncherUserInfo createFromParcel(android.os.Parcel in) {
            return new LauncherUserInfo(in.readString(), in.readInt(), in.readBundle());
        }
        @Override public LauncherUserInfo[] newArray(int size) {
            return new LauncherUserInfo[size];
        }
    };
}
