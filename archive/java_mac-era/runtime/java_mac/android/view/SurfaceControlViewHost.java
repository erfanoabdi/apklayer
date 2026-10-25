// android.view.SurfaceControlViewHost -- hosts a View hierarchy inside a
// SurfaceControl that ANOTHER process can embed in its own window
// (SurfaceView.setChildSurfacePackage). Source: frameworks/base
// core/java/android/view/SurfaceControlViewHost.java (android16-release).
//
// Mac.apk is a SINGLE process with a single Cocoa window and no SurfaceFlinger
// (see SurfaceControl's header), so there is no second process to embed a
// hierarchy from and nothing composites an embedded layer. The host therefore
// holds the view it is given and reports it back -- every getter answers what
// the caller set -- but the hierarchy is never presented. That is the same
// observable state as a SurfacePackage whose remote end has gone away, which
// AOSP's own API is specified to tolerate.
package android.view;

public class SurfaceControlViewHost {
    private final android.content.Context mContext;
    private final SurfaceControl mSurfaceControl;
    private View mView;
    private int mWidth, mHeight;
    private SurfacePackage mSurfacePackage;
    private boolean mReleased;

    public SurfaceControlViewHost(android.content.Context context, Display display,
            android.os.IBinder hostToken) {
        mContext = context;
        mSurfaceControl = new SurfaceControl.Builder()
                .setName("SurfaceControlViewHost").build();
    }

    /** AOSP SurfaceControlViewHost.setView(View, int, int). */
    public void setView(View view, int width, int height) {
        mView = view;
        mWidth = width;
        mHeight = height;
    }

    /** AOSP SurfaceControlViewHost.getView(). */
    public View getView() { return mView; }

    /** AOSP SurfaceControlViewHost.relayout(int, int). */
    public void relayout(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /** AOSP SurfaceControlViewHost.getSurfacePackage(): null once released. */
    public SurfacePackage getSurfacePackage() {
        if (mReleased) return null;
        if (mSurfacePackage == null) mSurfacePackage = new SurfacePackage(mSurfaceControl);
        return mSurfacePackage;
    }

    /** AOSP SurfaceControlViewHost.release(). */
    public void release() {
        mReleased = true;
        mView = null;
        mSurfaceControl.release();
    }

    /** AOSP SurfaceControlViewHost.transferTouchGestureToHost(), deprecated at
     *  API 35 in favour of WindowManager.transferTouchGesture. There is no
     *  separate embedded input channel here, so there is no gesture to hand
     *  back and the transfer does not happen -- AOSP returns false in exactly
     *  that situation (no active gesture on the embedded window). */
    @Deprecated
    public boolean transferTouchGestureToHost() { return false; }

    /** AOSP SurfaceControlViewHost.SurfacePackage -- the parcelable handle a
     *  host passes to SurfaceView.setChildSurfacePackage. */
    public static final class SurfacePackage implements android.os.Parcelable {
        private final SurfaceControl mSurfaceControl;

        /** @hide -- built by SurfaceControlViewHost.getSurfacePackage(). */
        SurfacePackage(SurfaceControl sc) { mSurfaceControl = sc; }

        /** AOSP SurfacePackage(SurfacePackage): the copy constructor, which
         *  duplicates the handle so the copy can be released independently. */
        public SurfacePackage(SurfacePackage other) {
            mSurfaceControl = other != null ? other.mSurfaceControl : null;
        }

        public SurfaceControl getSurfaceControl() { return mSurfaceControl; }

        /** AOSP SurfacePackage.notifyConfigurationChanged(Configuration):
         *  forwards a configuration change to the embedded hierarchy. Nothing is
         *  embedded here (see the class header), so there is nobody to tell. */
        public void notifyConfigurationChanged(android.content.res.Configuration c) {}
        /** AOSP SurfacePackage.notifyDetachedFromWindow(). */
        public void notifyDetachedFromWindow() {}
        /** AOSP SurfacePackage.release(). */
        public void release() { if (mSurfaceControl != null) mSurfaceControl.release(); }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeParcelable(mSurfaceControl, flags);
        }

        public static final android.os.Parcelable.Creator<SurfacePackage> CREATOR =
                new android.os.Parcelable.Creator<SurfacePackage>() {
            @Override public SurfacePackage createFromParcel(android.os.Parcel in) {
                return new SurfacePackage((SurfaceControl) in.readParcelable(
                        SurfaceControl.class.getClassLoader()));
            }
            @Override public SurfacePackage[] newArray(int size) {
                return new SurfacePackage[size];
            }
        };
    }
}
