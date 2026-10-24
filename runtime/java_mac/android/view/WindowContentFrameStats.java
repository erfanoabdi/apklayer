// android.view.WindowContentFrameStats -- FrameStats plus the posted/ready
// timestamps for a window's CONTENT frames. Source: frameworks/base
// core/java/android/view/WindowContentFrameStats.java (android16-release).
// Only UiAutomation produces one, and Mac.apk hosts none -- see FrameStats.
package android.view;

public final class WindowContentFrameStats extends FrameStats
        implements android.os.Parcelable {
    private long[] mFramesPostedTimeNano;
    private long[] mFramesReadyTimeNano;

    /** @hide -- only the framework builds one. */
    WindowContentFrameStats() {}

    public long getFramePostedTimeNano(int index) {
        if (mFramesPostedTimeNano == null) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=0");
        }
        return mFramesPostedTimeNano[index];
    }
    public long getFrameReadyTimeNano(int index) {
        if (mFramesReadyTimeNano == null) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=0");
        }
        return mFramesReadyTimeNano[index];
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(mRefreshPeriodNano);
        dest.writeLongArray(mFramesPostedTimeNano != null ? mFramesPostedTimeNano : new long[0]);
        dest.writeLongArray(mFramesPresentedTimeNano != null
                ? mFramesPresentedTimeNano : new long[0]);
        dest.writeLongArray(mFramesReadyTimeNano != null ? mFramesReadyTimeNano : new long[0]);
    }
    public static final android.os.Parcelable.Creator<WindowContentFrameStats> CREATOR =
            new android.os.Parcelable.Creator<WindowContentFrameStats>() {
        @Override public WindowContentFrameStats createFromParcel(android.os.Parcel in) {
            WindowContentFrameStats s = new WindowContentFrameStats();
            s.mRefreshPeriodNano = in.readLong();
            s.mFramesPostedTimeNano = in.createLongArray();
            s.mFramesPresentedTimeNano = in.createLongArray();
            s.mFramesReadyTimeNano = in.createLongArray();
            return s;
        }
        @Override public WindowContentFrameStats[] newArray(int size) {
            return new WindowContentFrameStats[size];
        }
    };
}
