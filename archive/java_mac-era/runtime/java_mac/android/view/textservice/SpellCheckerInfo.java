// android.view.textservice.SpellCheckerInfo -- descriptor for one installed
// spell-checker service. Source: frameworks/base
// core/java/android/view/textservice/SpellCheckerInfo.java (android16-release).
//
// Mac.apk installs no spell checkers, so TextServicesManager's lists are empty
// and the framework never produces an instance; the type exists because those
// list element types and getCurrentSpellCheckerInfo() name it.
package android.view.textservice;

public final class SpellCheckerInfo implements android.os.Parcelable {
    private final android.content.pm.ServiceInfo mService;
    private final String mId;
    private final String mSettingsActivityName;

    /** @hide -- AOSP's constructors are @hide too; only the system parses one
     *  out of a package's meta-data. */
    SpellCheckerInfo(android.content.pm.ServiceInfo service, String settingsActivityName) {
        mService = service;
        mId = service == null ? "" : new android.content.ComponentName(
                service.packageName, service.name).flattenToShortString();
        mSettingsActivityName = settingsActivityName;
    }

    public String getId() { return mId; }
    public android.content.ComponentName getComponent() {
        return mService == null ? null
                : new android.content.ComponentName(mService.packageName, mService.name);
    }
    public String getPackageName() { return mService == null ? null : mService.packageName; }
    public android.content.pm.ServiceInfo getServiceInfo() { return mService; }
    public String getSettingsActivity() { return mSettingsActivityName; }

    /** AOSP getSubtypeCount() / getSubtypeAt(int): the locales this checker
     *  declared. A checker with no &lt;subtype&gt; meta-data has none, and AOSP
     *  indexes an empty array -- so every index is out of range. */
    public int getSubtypeCount() { return 0; }
    public SpellCheckerSubtype getSubtypeAt(int index) {
        throw new IndexOutOfBoundsException("index=" + index + ", size=0");
    }

    public CharSequence loadLabel(android.content.pm.PackageManager pm) {
        return (mService == null || pm == null) ? "" : mService.loadLabel(pm);
    }
    public android.graphics.drawable.Drawable loadIcon(android.content.pm.PackageManager pm) {
        return (mService == null || pm == null) ? null : mService.loadIcon(pm);
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mSettingsActivityName);
        dest.writeParcelable(mService, flags);
    }

    public static final android.os.Parcelable.Creator<SpellCheckerInfo> CREATOR =
            new android.os.Parcelable.Creator<SpellCheckerInfo>() {
        @Override public SpellCheckerInfo createFromParcel(android.os.Parcel source) {
            String settings = source.readString();
            android.content.pm.ServiceInfo si = source.readParcelable(
                    android.content.pm.ServiceInfo.class.getClassLoader());
            return new SpellCheckerInfo(si, settings);
        }
        @Override public SpellCheckerInfo[] newArray(int size) {
            return new SpellCheckerInfo[size];
        }
    };
}
