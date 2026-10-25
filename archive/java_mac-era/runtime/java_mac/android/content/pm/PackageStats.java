package android.content.pm;
import android.os.Parcel;
import android.os.Parcelable;
/** On-disk size accounting for a package. Mac.apk reports zeros. */
public class PackageStats implements Parcelable {
    public String packageName;
    public int userHandle;
    public long codeSize, dataSize, cacheSize;
    public long externalCodeSize, externalDataSize, externalCacheSize, externalMediaSize, externalObbSize;
    public PackageStats(String pkgName) { packageName = pkgName; }
    public PackageStats(String pkgName, int userHandle) { this.packageName = pkgName; this.userHandle = userHandle; }
    public PackageStats(PackageStats pStats) { packageName = pStats.packageName; userHandle = pStats.userHandle; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(packageName); }
    public static final Parcelable.Creator<PackageStats> CREATOR = new Parcelable.Creator<PackageStats>() {
        @Override public PackageStats createFromParcel(Parcel in) { return new PackageStats(in.readString()); }
        @Override public PackageStats[] newArray(int size) { return new PackageStats[size]; }
    };
}
