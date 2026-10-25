// android.view.VerifiedMotionEvent -- see VerifiedInputEvent's header.
// Source: frameworks/base core/java/android/view/VerifiedMotionEvent.java.
package android.view;

public final class VerifiedMotionEvent extends VerifiedInputEvent {
    private final float mRawX;
    private final float mRawY;
    private final int mActionMasked;
    private final long mDownTimeNanos;
    private final int mFlags;
    private final int mMetaState;
    private final int mButtonState;

    /** @hide */
    VerifiedMotionEvent(int deviceId, long eventTimeNanos, int source, int displayId,
            float rawX, float rawY, int actionMasked, long downTimeNanos, int flags,
            int metaState, int buttonState) {
        super(VERIFIED_MOTION, deviceId, eventTimeNanos, source, displayId);
        mRawX = rawX;
        mRawY = rawY;
        mActionMasked = actionMasked;
        mDownTimeNanos = downTimeNanos;
        mFlags = flags;
        mMetaState = metaState;
        mButtonState = buttonState;
    }

    public float getRawX() { return mRawX; }
    public float getRawY() { return mRawY; }
    public int getActionMasked() { return mActionMasked; }
    public long getDownTimeNanos() { return mDownTimeNanos; }
    public int getMetaState() { return mMetaState; }
    public int getButtonState() { return mButtonState; }
    /** AOSP getFlag(int): Boolean, null for a flag the system did not verify --
     *  see VerifiedKeyEvent.getFlag for why the third state matters. */
    public Boolean getFlag(int flag) {
        if (flag == MotionEvent.FLAG_WINDOW_IS_OBSCURED) {
            return Boolean.valueOf((mFlags & flag) != 0);
        }
        return null;
    }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloat(mRawX);
        dest.writeFloat(mRawY);
        dest.writeInt(mActionMasked);
        dest.writeLong(mDownTimeNanos);
        dest.writeInt(mFlags);
        dest.writeInt(mMetaState);
        dest.writeInt(mButtonState);
    }

    public static final android.os.Parcelable.Creator<VerifiedMotionEvent> CREATOR =
            new android.os.Parcelable.Creator<VerifiedMotionEvent>() {
        @Override public VerifiedMotionEvent createFromParcel(android.os.Parcel in) {
            in.readInt();   // type token
            final int deviceId = in.readInt();
            final long eventTimeNanos = in.readLong();
            final int source = in.readInt();
            final int displayId = in.readInt();
            return new VerifiedMotionEvent(deviceId, eventTimeNanos, source, displayId,
                    in.readFloat(), in.readFloat(), in.readInt(), in.readLong(), in.readInt(),
                    in.readInt(), in.readInt());
        }
        @Override public VerifiedMotionEvent[] newArray(int size) {
            return new VerifiedMotionEvent[size];
        }
    };
}
