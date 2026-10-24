// android.view.FrameStats -- the per-frame timing stats a window can report to
// a UiAutomation test. Source: frameworks/base
// core/java/android/view/FrameStats.java (android16-release).
//
// Mac.apk keeps no per-frame timeline for a window (there is no SurfaceFlinger
// and no UiAutomation host), so no instance is ever produced by the framework;
// AOSP's own answer for a window that has posted no frames is a zero-length
// stats object, which is what a subclass built here reports.
package android.view;

public abstract class FrameStats {
    /** AOSP: the sentinel for a timestamp the platform could not determine. */
    public static final long UNDEFINED_TIME_NANO = -1;

    /** @hide -- populated by the concrete subclasses in this package. */
    protected long mRefreshPeriodNano;
    /** @hide */
    protected long[] mFramesPresentedTimeNano;

    public FrameStats() {}

    public final long getRefreshPeriodNano() { return mRefreshPeriodNano; }
    public final int getFrameCount() {
        return mFramesPresentedTimeNano != null ? mFramesPresentedTimeNano.length : 0;
    }
    public final long getStartTimeNano() {
        return getFrameCount() == 0 ? UNDEFINED_TIME_NANO : mFramesPresentedTimeNano[0];
    }
    public final long getEndTimeNano() {
        final int n = getFrameCount();
        return n == 0 ? UNDEFINED_TIME_NANO : mFramesPresentedTimeNano[n - 1];
    }
    public final long getFramePresentedTimeNano(int index) {
        if (mFramesPresentedTimeNano == null) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=0");
        }
        return mFramesPresentedTimeNano[index];
    }
}
