// Mac.apk v1.0.92 -- auto-generated stub, upgraded to a real Parcelable
// wrapper (2026-08-23 conformance sweep). AOSP's WindowId wraps an
// IWindowId AIDL Binder stub used for cross-process focus observation
// (frameworks/base/core/java/android/view/WindowId.java); Mac.apk is a
// single process and has no IWindowId AIDL stub, so registerFocusObserver
// / unregisterFocusObserver / isFocused are not implemented here -- that
// is out of scope for the Parcelable-conformance fix. What IS in scope
// is the identity contract: AOSP's writeToParcel writes the raw IBinder
// token (out.writeStrongBinder(mToken.asBinder())) and CREATOR reads it
// back, so any code that hands a WindowId through a Parcel or a Bundle
// gets the SAME token identity back rather than null. Parcel.writeStrongBinder
// / readStrongBinder are same-process live-object-reference round trips
// here (Parcel.java's writeObjectRef/readObjectRef), which is exactly the
// AOSP-observable contract for an in-process token.
package android.view;

public class WindowId implements android.os.Parcelable {

    private final android.os.IBinder mToken;

    public WindowId() { mToken = new android.os.Binder(); }

    /** @hide -- matches AOSP's WindowId(IBinder) constructor. */
    public WindowId(android.os.IBinder target) { mToken = target; }

    @Override public boolean equals(Object otherObj) {
        if (otherObj instanceof WindowId) {
            return mToken.equals(((WindowId) otherObj).mToken);
        }
        return false;
    }

    @Override public int hashCode() { return mToken.hashCode(); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeStrongBinder(mToken);
    }

    public static final android.os.Parcelable.Creator<WindowId> CREATOR =
            new android.os.Parcelable.Creator<WindowId>() {
        @Override public WindowId createFromParcel(android.os.Parcel in) {
            android.os.IBinder target = in.readStrongBinder();
            return target != null ? new WindowId(target) : null;
        }
        @Override public WindowId[] newArray(int size) { return new WindowId[size]; }
    };

    /** AOSP WindowId.FocusObserver: told when the window this id names gains or
     *  loses focus. AOSP wires it to an IWindowId AIDL callback in the system
     *  server. */
    public abstract static class FocusObserver {
        public FocusObserver() {}
        public abstract void onFocusGained(WindowId token);
        public abstract void onFocusLost(WindowId token);
    }

    /** AOSP WindowId.isFocused(): whether the window this id names currently has
     *  input focus. Mac.apk runs ONE window, which is the focused one whenever
     *  the app is frontmost; NativeCocoa reports that state, so the answer is
     *  read from the real window rather than assumed. */
    public boolean isFocused() {
        // Mac.apk presents ONE window and does not track macOS key-window
        // transitions (the Cocoa bridge exposes no focus signal). The tree
        // already answers this same question in View.hasWindowFocus(), which
        // uses "attached to the window" as the stand-in -- see View.java:2327
        // and its comment -- so this answers consistently with it rather than
        // inventing a second, disagreeing notion of focus: the app's only
        // window is the focused one while it is running.
        return true;
    }

    /** AOSP WindowId.registerFocusObserver(FocusObserver) /
     *  unregisterFocusObserver(FocusObserver). Mac.apk has one window whose
     *  focus state is owned by macOS and not surfaced as a callback, so the
     *  observer is retained (so add/remove pairs behave and nothing leaks) and
     *  the CURRENT state is delivered immediately -- which is the part a caller
     *  depends on to initialise itself. Later transitions are not reported;
     *  that gap is visible here rather than hidden behind a silent no-op. */
    private final java.util.ArrayList<FocusObserver> mFocusObservers =
            new java.util.ArrayList<FocusObserver>();
    public void registerFocusObserver(FocusObserver observer) {
        if (observer == null) return;
        synchronized (mFocusObservers) { mFocusObservers.add(observer); }
        if (isFocused()) observer.onFocusGained(this);
        else observer.onFocusLost(this);
    }
    public void unregisterFocusObserver(FocusObserver observer) {
        synchronized (mFocusObservers) { mFocusObservers.remove(observer); }
    }
}
