// android.view.VerifiedKeyEvent -- see VerifiedInputEvent's header.
// Source: frameworks/base core/java/android/view/VerifiedKeyEvent.java.
package android.view;

public final class VerifiedKeyEvent extends VerifiedInputEvent {
    private final int mAction;
    private final long mDownTimeNanos;
    private final int mFlags;
    private final int mKeyCode;
    private final int mScanCode;
    private final int mMetaState;
    private final int mRepeatCount;

    /** @hide */
    VerifiedKeyEvent(int deviceId, long eventTimeNanos, int source, int displayId, int action,
            long downTimeNanos, int flags, int keyCode, int scanCode, int metaState,
            int repeatCount) {
        super(VERIFIED_KEY, deviceId, eventTimeNanos, source, displayId);
        mAction = action;
        mDownTimeNanos = downTimeNanos;
        mFlags = flags;
        mKeyCode = keyCode;
        mScanCode = scanCode;
        mMetaState = metaState;
        mRepeatCount = repeatCount;
    }

    public int getAction() { return mAction; }
    public long getDownTimeNanos() { return mDownTimeNanos; }
    public int getKeyCode() { return mKeyCode; }
    public int getScanCode() { return mScanCode; }
    public int getMetaState() { return mMetaState; }
    public int getRepeatCount() { return mRepeatCount; }
    /** AOSP getFlag(int): Boolean rather than boolean, because AOSP returns NULL
     *  for a flag the system did not verify -- "unknown" is a third state and
     *  collapsing it to false would tell the caller the flag was verified off. */
    public Boolean getFlag(int flag) {
        switch (flag) {
            case KeyEvent.FLAG_CANCELED:
            case KeyEvent.FLAG_IS_ACCESSIBILITY_EVENT:
                return Boolean.valueOf((mFlags & flag) != 0);
            default:
                return null;
        }
    }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mAction);
        dest.writeLong(mDownTimeNanos);
        dest.writeInt(mFlags);
        dest.writeInt(mKeyCode);
        dest.writeInt(mScanCode);
        dest.writeInt(mMetaState);
        dest.writeInt(mRepeatCount);
    }

    public static final android.os.Parcelable.Creator<VerifiedKeyEvent> CREATOR =
            new android.os.Parcelable.Creator<VerifiedKeyEvent>() {
        @Override public VerifiedKeyEvent createFromParcel(android.os.Parcel in) {
            in.readInt();   // type token
            final int deviceId = in.readInt();
            final long eventTimeNanos = in.readLong();
            final int source = in.readInt();
            final int displayId = in.readInt();
            return new VerifiedKeyEvent(deviceId, eventTimeNanos, source, displayId,
                    in.readInt(), in.readLong(), in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt());
        }
        @Override public VerifiedKeyEvent[] newArray(int size) {
            return new VerifiedKeyEvent[size];
        }
    };
}
