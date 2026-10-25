package android.content.pm;

/** android.content.pm.ModuleInfo (API 29) -- metadata for an installed
 *  MAINLINE MODULE (an APEX or an updatable system package the platform ships
 *  and Play updates out-of-band).
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/ModuleInfo.java
 *  (android16-release). AOSP's constructors are @hide (only ModuleInfoProvider
 *  builds one), so the public surface is the three getters plus Parcelable;
 *  the constructor is public here only because nothing else in this tree could
 *  build one otherwise.
 *
 *  Mac.apk has no mainline modules -- there is no APEX, no module metadata
 *  provider, and PackageManager.getInstalledModules() correctly reports an
 *  empty list. The CLASS still has to exist: we present API 36, so a
 *  `List<ModuleInfo>` local or a checkcast on the (empty) result must resolve. */
public final class ModuleInfo implements android.os.Parcelable {
    private CharSequence mName;
    private String mPackageName;
    private boolean mHidden;

    public ModuleInfo() {}

    public ModuleInfo(ModuleInfo orig) {
        mName = orig.mName;
        mPackageName = orig.mPackageName;
        mHidden = orig.mHidden;
    }

    /** AOSP: "the user-visible name of the module." */
    public CharSequence getName() { return mName; }
    public ModuleInfo setName(CharSequence name) { mName = name; return this; }

    /** AOSP: "the package name of the module." */
    public String getPackageName() { return mPackageName; }
    public ModuleInfo setPackageName(String packageName) {
        mPackageName = packageName; return this;
    }

    /** AOSP: "whether or not this module is hidden from the user." */
    public boolean isHidden() { return mHidden; }
    public ModuleInfo setHidden(boolean hidden) { mHidden = hidden; return this; }

    @Override public int describeContents() { return 0; }

    /** AOSP field order: name, packageName, hidden. */
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeCharSequence(mName);
        dest.writeString(mPackageName);
        dest.writeBoolean(mHidden);
    }

    public static final android.os.Parcelable.Creator<ModuleInfo> CREATOR =
            new android.os.Parcelable.Creator<ModuleInfo>() {
        @Override public ModuleInfo createFromParcel(android.os.Parcel in) {
            ModuleInfo m = new ModuleInfo();
            m.mName = in.readCharSequence();
            m.mPackageName = in.readString();
            m.mHidden = in.readBoolean();
            return m;
        }
        @Override public ModuleInfo[] newArray(int size) { return new ModuleInfo[size]; }
    };

    @Override public String toString() {
        return "ModuleInfo{" + Integer.toHexString(System.identityHashCode(this))
                + " " + mName + "}";
    }
}
