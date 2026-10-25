package android.util;
/** Development leak detector: an object arms a CloseGuard in its ctor via
 *  open("close"), disarms it in close(), and calls warnIfOpen() from finalize().
 *  If still armed at finalize, the allocation stack is logged. Real behavior,
 *  matching AOSP's public CloseGuard (API 30). Source:
 *  frameworks/base/core/java/android/util/CloseGuard.java */
public final class CloseGuard {
    private Throwable mAllocationSite;
    public CloseGuard() { }
    public void open(String closeMethodName) {
        if (closeMethodName == null) closeMethodName = "close";
        mAllocationSite = new Throwable("Explicit termination method '"
                + closeMethodName + "' not called");
    }
    public void close() { mAllocationSite = null; }
    public void warnIfOpen() {
        if (mAllocationSite != null) {
            System.err.println("A resource failed to call " + mAllocationSite.getMessage());
            mAllocationSite.printStackTrace();
        }
    }
}
