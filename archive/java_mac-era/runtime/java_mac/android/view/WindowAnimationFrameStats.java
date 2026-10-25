// android.view.WindowAnimationFrameStats -- FrameStats for a window ANIMATION.
// Deprecated at API 33 (window animations moved out of the app process).
// Source: frameworks/base core/java/android/view/WindowAnimationFrameStats.java.
// Only UiAutomation produces one, and Mac.apk hosts none -- see FrameStats.
package android.view;

@Deprecated
public final class WindowAnimationFrameStats extends FrameStats
        implements android.os.Parcelable {

    /** @hide -- only the framework builds one. */
    WindowAnimationFrameStats() {}

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(mRefreshPeriodNano);
        dest.writeLongArray(mFramesPresentedTimeNano != null
                ? mFramesPresentedTimeNano : new long[0]);
    }
    public static final android.os.Parcelable.Creator<WindowAnimationFrameStats> CREATOR =
            new android.os.Parcelable.Creator<WindowAnimationFrameStats>() {
        @Override public WindowAnimationFrameStats createFromParcel(android.os.Parcel in) {
            WindowAnimationFrameStats s = new WindowAnimationFrameStats();
            s.mRefreshPeriodNano = in.readLong();
            s.mFramesPresentedTimeNano = in.createLongArray();
            return s;
        }
        @Override public WindowAnimationFrameStats[] newArray(int size) {
            return new WindowAnimationFrameStats[size];
        }
    };
}
