package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * AOSP android.hardware.SyncFence (API 33), core/java/android/hardware/SyncFence.java.
 *
 * A handle to a Linux sync_file: a GPU/display fence you can wait on. Mac.apk has no
 * sync_file -- the host synchronisation primitive is a Metal shared event, reached
 * through ANGLE, and nothing in this tree hands a fence fd out to guest Java.
 *
 * So every instance we can honestly produce is an INVALID fence, and AOSP defines that
 * state precisely: `isValid()` false, `getSignalTime()` == SIGNAL_TIME_INVALID, and both
 * await forms return immediately. Quoting AOSP's own javadoc for await: "If the fence
 * is invalid ... returns immediately". An invalid fence is therefore not a fabrication;
 * it is the documented representation of "there is no fence here", and callers already
 * branch on isValid() because a real device returns invalid fences too (a query on a
 * frame that never produced one).
 *
 * ⚠ Do NOT "improve" this into a fence that reports signalled-at-time-T. A caller that
 * believes it waited for GPU work that never happened reads a half-drawn buffer, and
 * that failure surfaces far from here. Absent-and-honest beats plausible.
 *
 * Added 2026-08-24 to unblock android.media Image.getFence()/setFence() and
 * android.opengl EGLExt.eglDupNativeFenceFDANDROID, which name this type.
 */
public final class SyncFence implements AutoCloseable, Parcelable {

    /** AOSP: "the fence is invalid", returned by getSignalTime(). */
    public static final long SIGNAL_TIME_INVALID = -1L;
    /** AOSP: the fence has not signalled yet. */
    public static final long SIGNAL_TIME_PENDING = Long.MAX_VALUE;

    private boolean mClosed;

    /** AOSP's copy constructor (API 36). Copies validity; ours is always invalid. */
    public SyncFence(SyncFence other) {
        this.mClosed = (other == null) || other.mClosed;
    }

    private SyncFence() {}

    /** @hide -- the only way this tree produces one. */
    public static SyncFence createEmpty() { return new SyncFence(); }

    /**
     * AOSP: waits up to `timeout` for the fence to signal, returning whether it did.
     * An invalid fence returns immediately -- and false, because nothing signalled.
     */
    public boolean await(java.time.Duration timeout) {
        if (timeout == null) throw new NullPointerException("timeout must not be null");
        return false;
    }

    /** AOSP: await with no timeout. Same reasoning as await(Duration). */
    public boolean awaitForever() { return false; }

    /** AOSP: true only for a fence backed by a real sync_file fd. Never true here. */
    public boolean isValid() { return false; }

    /** AOSP: SIGNAL_TIME_INVALID for an invalid fence. */
    public long getSignalTime() { return SIGNAL_TIME_INVALID; }

    @Override public void close() { mClosed = true; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // AOSP marshals the fd via a native ParcelFileDescriptor write. With no fd,
        // the honest wire form is "no fence", which unparcels back to an invalid one.
        dest.writeInt(0);
    }

    public static final Parcelable.Creator<SyncFence> CREATOR =
            new Parcelable.Creator<SyncFence>() {
        @Override public SyncFence createFromParcel(Parcel in) {
            in.readInt();
            return new SyncFence();
        }
        @Override public SyncFence[] newArray(int size) { return new SyncFence[size]; }
    };
}
