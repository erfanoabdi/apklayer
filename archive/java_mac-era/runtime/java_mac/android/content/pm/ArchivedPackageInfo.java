package android.content.pm;

/** AOSP android.content.pm.ArchivedPackageInfo (API 35 / Android 15,
 *  {@code @FlaggedApi(FLAG_ARCHIVING)}): "Contains fields required for archived
 *  package installation, i.e. installation without an APK."
 *
 *  Source: frameworks/base core/java/android/content/pm/ArchivedPackageInfo.java,
 *  android15-release. A @DataClass with genSetters=true, so every setter is
 *  fluent and returns `this`; the field set and their nullability are copied
 *  from that file verbatim. The ArchivedPackageParcel constructor and
 *  getParcel() are @hide system-server plumbing and are not reproduced.
 *
 *  Note the three String-typed tri-state fields
 *  (mDefaultToDeviceProtectedStorage / mRequestLegacyExternalStorage /
 *  mUserDataFragile): they really are @Nullable String and not boolean in AOSP
 *  -- they carry the manifest attribute's raw value, where null means "the
 *  manifest did not say".
 *
 *  Mac.apk does not implement app archiving, so the framework never constructs
 *  one of these; see PackageManager.getArchivedPackage. The class ships so the
 *  method descriptor resolves. */
public final class ArchivedPackageInfo {
    private String mPackageName;
    private SigningInfo mSigningInfo;
    private int mVersionCode = 0;
    private int mVersionCodeMajor = 0;
    private int mTargetSdkVersion = 0;
    private String mDefaultToDeviceProtectedStorage;
    private String mRequestLegacyExternalStorage;
    private String mUserDataFragile;
    private java.util.List<ArchivedActivityInfo> mLauncherActivities;

    public ArchivedPackageInfo(String packageName, SigningInfo signingInfo,
                               java.util.List<ArchivedActivityInfo> launcherActivities) {
        mPackageName = packageName;
        mSigningInfo = signingInfo;
        mLauncherActivities = launcherActivities;
    }

    public String getPackageName() { return mPackageName; }
    public SigningInfo getSigningInfo() { return mSigningInfo; }
    public int getVersionCode() { return mVersionCode; }
    public int getVersionCodeMajor() { return mVersionCodeMajor; }
    public int getTargetSdkVersion() { return mTargetSdkVersion; }
    public String getDefaultToDeviceProtectedStorage() { return mDefaultToDeviceProtectedStorage; }
    public String getRequestLegacyExternalStorage() { return mRequestLegacyExternalStorage; }
    public String getUserDataFragile() { return mUserDataFragile; }
    public java.util.List<ArchivedActivityInfo> getLauncherActivities() {
        return mLauncherActivities;
    }

    public ArchivedPackageInfo setPackageName(String value) { mPackageName = value; return this; }
    public ArchivedPackageInfo setSigningInfo(SigningInfo value) { mSigningInfo = value; return this; }
    public ArchivedPackageInfo setVersionCode(int value) { mVersionCode = value; return this; }
    public ArchivedPackageInfo setVersionCodeMajor(int value) { mVersionCodeMajor = value; return this; }
    public ArchivedPackageInfo setTargetSdkVersion(int value) { mTargetSdkVersion = value; return this; }
    public ArchivedPackageInfo setDefaultToDeviceProtectedStorage(String value) {
        mDefaultToDeviceProtectedStorage = value; return this;
    }
    public ArchivedPackageInfo setRequestLegacyExternalStorage(String value) {
        mRequestLegacyExternalStorage = value; return this;
    }
    public ArchivedPackageInfo setUserDataFragile(String value) {
        mUserDataFragile = value; return this;
    }
    public ArchivedPackageInfo setLauncherActivities(java.util.List<ArchivedActivityInfo> value) {
        mLauncherActivities = value; return this;
    }
}
