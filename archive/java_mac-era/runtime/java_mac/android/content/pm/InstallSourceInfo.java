package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Who installed this app: the initiating installer, the originating installer,
 * and the current installing package.
 *
 * Apps use this for two things -- attribution ("installed from Play?") and
 * tamper checks ("refuse to run if sideloaded"). Mac.apk has no package
 * installer at all, which is the same state AOSP reports for an app installed
 * by adb or by a caller that has since been uninstalled: every name null. That
 * is a legitimate value AOSP itself returns and which apps must already handle,
 * rather than a fabricated installer identity.
 *
 * Source: frameworks/base/core/java/android/content/pm/InstallSourceInfo.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/pm/InstallSourceInfo.java
 */
public final class InstallSourceInfo implements Parcelable {

    private final String mInitiatingPackageName;
    private final SigningInfo mInitiatingPackageSigningInfo;
    private final String mOriginatingPackageName;
    private final String mInstallingPackageName;
    private final String mUpdateOwnerPackageName;
    private final int mPackageSource;

    // ⚠ These are NOT AOSP API on this class -- upstream's InstallSourceInfo
    // declares no constants at all and its @hide constructor takes
    // PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED. They are kept as local
    // aliases for readability, and their VALUES were wrong until v1.1.0
    // (STORE read 1 and OTHER read 4, the reverse of the real numbering), so
    // any comparison against getPackageSource() -- which is filled from a
    // SessionParams that uses the REAL numbering -- mismatched. Now defined by
    // reference to the single source of truth rather than re-typed.
    public static final int PACKAGE_SOURCE_UNSPECIFIED =
            PackageInstaller.SessionParams.PACKAGE_SOURCE_UNSPECIFIED;
    public static final int PACKAGE_SOURCE_OTHER =
            PackageInstaller.SessionParams.PACKAGE_SOURCE_OTHER;
    public static final int PACKAGE_SOURCE_STORE =
            PackageInstaller.SessionParams.PACKAGE_SOURCE_STORE;
    public static final int PACKAGE_SOURCE_LOCAL_FILE =
            PackageInstaller.SessionParams.PACKAGE_SOURCE_LOCAL_FILE;
    public static final int PACKAGE_SOURCE_DOWNLOADED_FILE =
            PackageInstaller.SessionParams.PACKAGE_SOURCE_DOWNLOADED_FILE;

    public InstallSourceInfo(String initiatingPackageName,
                             SigningInfo initiatingPackageSigningInfo,
                             String originatingPackageName,
                             String installingPackageName) {
        this(initiatingPackageName, initiatingPackageSigningInfo,
                originatingPackageName, installingPackageName,
                null, PACKAGE_SOURCE_UNSPECIFIED);
    }

    public InstallSourceInfo(String initiatingPackageName,
                             SigningInfo initiatingPackageSigningInfo,
                             String originatingPackageName,
                             String installingPackageName,
                             int packageSource) {
        this(initiatingPackageName, initiatingPackageSigningInfo,
                originatingPackageName, installingPackageName, null, packageSource);
    }

    /** Mirrors AOSP's @hide 6-arg constructor
     *  (frameworks/base .../InstallSourceInfo.java:51-62). */
    public InstallSourceInfo(String initiatingPackageName,
                             SigningInfo initiatingPackageSigningInfo,
                             String originatingPackageName,
                             String installingPackageName,
                             String updateOwnerPackageName,
                             int packageSource) {
        mInitiatingPackageName = initiatingPackageName;
        mInitiatingPackageSigningInfo = initiatingPackageSigningInfo;
        mOriginatingPackageName = originatingPackageName;
        mInstallingPackageName = installingPackageName;
        mUpdateOwnerPackageName = updateOwnerPackageName;
        mPackageSource = packageSource;
    }

    public String getInitiatingPackageName() { return mInitiatingPackageName; }

    public SigningInfo getInitiatingPackageSigningInfo() {
        return mInitiatingPackageSigningInfo;
    }

    public String getOriginatingPackageName() { return mOriginatingPackageName; }

    public String getInstallingPackageName() { return mInstallingPackageName; }

    public int getPackageSource() { return mPackageSource; }

    /** AOSP InstallSourceInfo.getUpdateOwnerPackageName() (API 34): "the package
     *  name of the app that requested update ownership", null when no app owns
     *  updates for this package.
     *
     *  UPDATE OWNERSHIP is Android 14's "the store that installed this app gets
     *  first refusal on updating it" mechanism, requested per-session via
     *  SessionParams.setRequestUpdateOwnership. Mac.apk grants it to nobody --
     *  our PackageInstaller does not record an update owner, and the macOS
     *  install helper enforces no such policy -- so null is the honest answer,
     *  and it is exactly what AOSP returns for any package whose installer never
     *  requested ownership (the overwhelming majority on a real device too).
     *
     *  Aurora Store 4.8.4 reads it in its app-detail path. */
    public String getUpdateOwnerPackageName() { return mUpdateOwnerPackageName; }

    /** AOSP InstallSourceInfo.describeContents:64-68 delegates to the nested
     *  SigningInfo, because that is the only field that could carry a file
     *  descriptor. Was a hardcoded 0. */
    @Override public int describeContents() {
        return mInitiatingPackageSigningInfo == null
                ? 0 : mInitiatingPackageSigningInfo.describeContents();
    }

    /** AOSP field order (InstallSourceInfo.java:70-78): initiating name,
     *  initiating SigningInfo, originating, installing, update owner, source.
     *  The SigningInfo was previously dropped on the floor, so a parcel
     *  round-trip silently erased the initiator's certificate -- which is the
     *  one field an anti-tamper caller actually wants out of this object. */
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mInitiatingPackageName);
        dest.writeTypedObject(mInitiatingPackageSigningInfo, flags);
        dest.writeString(mOriginatingPackageName);
        dest.writeString(mInstallingPackageName);
        dest.writeString(mUpdateOwnerPackageName);
        dest.writeInt(mPackageSource);
    }

    public static final Creator<InstallSourceInfo> CREATOR = new Creator<InstallSourceInfo>() {
        @Override public InstallSourceInfo createFromParcel(Parcel in) {
            String initiating = in.readString();
            SigningInfo signing = in.readTypedObject(SigningInfo.CREATOR);
            String originating = in.readString();
            String installing = in.readString();
            String updateOwner = in.readString();
            int source = in.readInt();
            return new InstallSourceInfo(initiating, signing, originating, installing,
                    updateOwner, source);
        }
        @Override public InstallSourceInfo[] newArray(int size) {
            return new InstallSourceInfo[size];
        }
    };
}
