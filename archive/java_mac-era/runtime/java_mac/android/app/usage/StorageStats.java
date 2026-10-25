package android.app.usage;
/** Storage usage totals for a package/user (app / data / cache bytes). API 26.
 *  Mac.apk reports the on-disk size of the app's private data tree; unmeasured
 *  fields report 0.
 *  Source: frameworks/base/core/java/android/app/usage/StorageStats.java */
public final class StorageStats implements android.os.Parcelable {

    /** AOSP APP_DATA_TYPE_* (API 36): the per-data-type breakdown
     *  getAppBytesByDataType splits getAppBytes() into. */
    public static final int APP_DATA_TYPE_FILE_TYPE_DEXOPT_ARTIFACT = 0;
    public static final int APP_DATA_TYPE_FILE_TYPE_REFERENCE_PROFILE = 1;
    public static final int APP_DATA_TYPE_FILE_TYPE_CURRENT_PROFILE = 2;
    public static final int APP_DATA_TYPE_FILE_TYPE_APK = 3;
    public static final int APP_DATA_TYPE_FILE_TYPE_DM = 4;
    public static final int APP_DATA_TYPE_LIB = 5;

    long appBytes;
    long dataBytes;
    long cacheBytes;
    long externalCacheBytes;
    public StorageStats() { }
    private StorageStats(android.os.Parcel in) {
        appBytes = in.readLong();
        dataBytes = in.readLong();
        cacheBytes = in.readLong();
        externalCacheBytes = in.readLong();
    }
    public long getAppBytes() { return appBytes; }
    public long getDataBytes() { return dataBytes; }
    public long getCacheBytes() { return cacheBytes; }
    public long getExternalCacheBytes() { return externalCacheBytes; }

    /** AOSP getAppBytesByDataType(int dataType) (API 36): "the size of the app
     *  data of the given type", one slice of getAppBytes(). AOSP validates the
     *  type and returns 0 for a type the measurement did not produce.
     *
     *  Mac.apk's queryStatsForPackage measures the app's on-disk tree as a
     *  whole, without separating dexopt artifacts, profiles, the APK, .dm files
     *  and native libs -- those categories describe how the ART/installd
     *  pipeline lays an app out on a device, which is not how Mac.apk stores
     *  one. So every type reports 0, which is AOSP's own answer for a data type
     *  the platform did not measure. Deliberately NOT getAppBytes() for one
     *  arbitrary type: an app summing the types would then double-count. */
    public long getAppBytesByDataType(int dataType) {
        if (dataType < APP_DATA_TYPE_FILE_TYPE_DEXOPT_ARTIFACT || dataType > APP_DATA_TYPE_LIB) {
            throw new IllegalArgumentException("Unknown data type: " + dataType);
        }
        return 0L;
    }

    /** AOSP StorageStats is Parcelable: the stats cross the Binder from the
     *  storage-stats service. */
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(appBytes);
        dest.writeLong(dataBytes);
        dest.writeLong(cacheBytes);
        dest.writeLong(externalCacheBytes);
    }

    public static final android.os.Parcelable.Creator<StorageStats> CREATOR =
            new android.os.Parcelable.Creator<StorageStats>() {
        @Override public StorageStats createFromParcel(android.os.Parcel in) {
            return new StorageStats(in);
        }
        @Override public StorageStats[] newArray(int size) { return new StorageStats[size]; }
    };
}
