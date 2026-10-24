package android.os;

/**
 * AOSP-faithful android.os.CpuUsageInfo (API 24). A per-core (active, total)
 * tick pair returned by HardwarePropertiesManager.getCpuUsages(). Mirrors
 * frameworks/base/core/java/android/os/CpuUsageInfo.java.
 */
public final class CpuUsageInfo implements Parcelable {
    private final long mActive;
    private final long mTotal;

    public CpuUsageInfo(long activeTime, long totalTime) {
        mActive = activeTime;
        mTotal = totalTime;
    }

    private CpuUsageInfo(Parcel in) {
        mActive = in.readLong();
        mTotal = in.readLong();
    }

    public long getActive() { return mActive; }
    public long getTotal() { return mTotal; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mActive);
        dest.writeLong(mTotal);
    }

    public static final Parcelable.Creator<CpuUsageInfo> CREATOR =
            new Parcelable.Creator<CpuUsageInfo>() {
        @Override
        public CpuUsageInfo createFromParcel(Parcel in) { return new CpuUsageInfo(in); }
        @Override
        public CpuUsageInfo[] newArray(int size) { return new CpuUsageInfo[size]; }
    };
}
