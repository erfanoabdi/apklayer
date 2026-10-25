// android.view.VerifiedInputEvent -- the subset of an InputEvent's fields that
// the SYSTEM can vouch for, returned by
// InputManager.verifyInputEvent(InputEvent). Source: frameworks/base
// core/java/android/view/VerifiedInputEvent.java (android16-release).
//
// Verification requires the system's own input pipeline to have signed the
// event. Mac.apk synthesises its events from Cocoa (cocoa_bridge.mm) with no
// such pipeline, so InputManager.verifyInputEvent returns null -- which is
// exactly what a device returns for an event it cannot verify (an injected one).
// The types must exist because that method's return type names them.
package android.view;

public abstract class VerifiedInputEvent implements android.os.Parcelable {
    /** @hide */ protected static final int VERIFIED_KEY = 1;
    /** @hide */ protected static final int VERIFIED_MOTION = 2;

    private final int mType;
    private final int mDeviceId;
    private final long mEventTimeNanos;
    private final int mSource;
    private final int mDisplayId;

    /** @hide -- only the framework constructs one. */
    VerifiedInputEvent(int type, int deviceId, long eventTimeNanos, int source, int displayId) {
        mType = type;
        mDeviceId = deviceId;
        mEventTimeNanos = eventTimeNanos;
        mSource = source;
        mDisplayId = displayId;
    }

    public int getDeviceId() { return mDeviceId; }
    public long getEventTimeNanos() { return mEventTimeNanos; }
    public int getSource() { return mSource; }
    public int getDisplayId() { return mDisplayId; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mDeviceId);
        dest.writeLong(mEventTimeNanos);
        dest.writeInt(mSource);
        dest.writeInt(mDisplayId);
    }

    /** AOSP's CREATOR dispatches on the leading type token that writeToParcel
     *  emits, exactly as InputEvent.CREATOR does for the unverified family. */
    public static final android.os.Parcelable.Creator<VerifiedInputEvent> CREATOR =
            new android.os.Parcelable.Creator<VerifiedInputEvent>() {
        @Override public VerifiedInputEvent createFromParcel(android.os.Parcel in) {
            final int start = in.dataPosition();
            final int type = in.readInt();
            in.setDataPosition(start);
            if (type == VERIFIED_KEY) return VerifiedKeyEvent.CREATOR.createFromParcel(in);
            if (type == VERIFIED_MOTION) return VerifiedMotionEvent.CREATOR.createFromParcel(in);
            throw new IllegalStateException("Unexpected verified event type in parcel.");
        }
        @Override public VerifiedInputEvent[] newArray(int size) {
            return new VerifiedInputEvent[size];
        }
    };
}
