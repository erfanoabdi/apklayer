// android.content.pm.VersionedPackage -- a (package name, version) pair.
// AOSP API-26+, core/java/android/content/pm/VersionedPackage.java.
//
// Added 2026-08-19 alongside SharedLibraryInfo, which references it from
// getDeclaringPackage() / getDependentPackages(). Small enough that stubbing
// it out of SharedLibraryInfo's surface would have been the bigger lie: an app
// that calls getDeclaringPackage() and gets a class-not-found is worse off
// than one that gets AOSP's own null-for-a-builtin answer.
package android.content.pm;

public final class VersionedPackage implements android.os.Parcelable {

    /** AOSP: PackageManager.VERSION_CODE_HIGHEST, used when a caller means
     *  "any version". Mirrored here because AOSP's own javadoc for the
     *  (String,int) constructor names it. */
    public static final int VERSION_CODE_HIGHEST = -1;

    private final String packageName;
    private final long versionCode;

    public VersionedPackage(String packageName, int versionCode) {
        this(packageName, (long) versionCode);
    }

    public VersionedPackage(String packageName, long versionCode) {
        this.packageName = packageName;
        this.versionCode = versionCode;
    }

    public String getPackageName() { return packageName; }

    /** AOSP deprecates this in favour of getLongVersionCode(). */
    @Deprecated
    public int getVersionCode() {
        return versionCode <= Integer.MAX_VALUE ? (int) versionCode : -1;
    }

    public long getLongVersionCode() { return versionCode; }

    @Override public String toString() {
        return "VersionedPackage[" + packageName + "/" + versionCode + "]";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionedPackage)) return false;
        VersionedPackage other = (VersionedPackage) o;
        return versionCode == other.versionCode
            && (packageName == null ? other.packageName == null
                                    : packageName.equals(other.packageName));
    }

    @Override public int hashCode() {
        int h = packageName == null ? 0 : packageName.hashCode();
        return 31 * h + (int) (versionCode ^ (versionCode >>> 32));
    }

    // ---- Parcelable ----------------------------------------------------
    // AOSP: core/java/android/content/pm/VersionedPackage.java
    // (android16-release), which writes packageName then versionCode. It uses
    // writeString8; our Parcel has one string encoding, so writeString is the
    // same operation here. SharedLibraryInfo hands these back from
    // getDeclaringPackage()/getDependentPackages(), and either can land in a
    // Bundle, where a non-Parcelable value reads back as null in this runtime.
    private VersionedPackage(android.os.Parcel source) {
        packageName = source.readString();
        versionCode = source.readLong();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel parcel, int flags) {
        parcel.writeString(packageName);
        parcel.writeLong(versionCode);
    }

    public static final android.os.Parcelable.Creator<VersionedPackage> CREATOR =
            new android.os.Parcelable.Creator<VersionedPackage>() {
        @Override public VersionedPackage createFromParcel(android.os.Parcel source) {
            return new VersionedPackage(source);
        }
        @Override public VersionedPackage[] newArray(int size) {
            return new VersionedPackage[size];
        }
    };
}
