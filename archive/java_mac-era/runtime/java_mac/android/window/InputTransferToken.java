// android.window.InputTransferToken (API 35) -- an opaque handle identifying an
// input channel, used by WindowManager#transferTouchGesture to hand a gesture
// between an embedded SurfaceControl and its host. AOSP:
// frameworks/base/core/java/android/window/InputTransferToken.java
//
// The token is DELIBERATELY opaque: AOSP's public API exposes no accessor at
// all, only Parcelable plumbing and equals/hashCode (core/api/current.txt
// lists exactly describeContents, writeToParcel and CREATOR). Its whole job is
// identity -- the system matches the token it handed out against the one
// handed back.
//
// Mac.apk has no cross-process input channels, so a token here identifies
// nothing beyond itself. That is enough for the only observable contract an
// app can exercise: a token round-trips through a Parcel and compares equal to
// itself and unequal to any other. The identity is a UUID rather than a
// reference, so it survives parcelling, which a reference would not.
package android.window;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

public final class InputTransferToken implements Parcelable {
    private final long mMostSigBits;
    private final long mLeastSigBits;

    /** @hide AOSP's public API declares no constructor; the system makes these. */
    public InputTransferToken() {
        UUID id = UUID.randomUUID();
        mMostSigBits = id.getMostSignificantBits();
        mLeastSigBits = id.getLeastSignificantBits();
    }

    private InputTransferToken(long most, long least) {
        mMostSigBits = most;
        mLeastSigBits = least;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mMostSigBits);
        dest.writeLong(mLeastSigBits);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof InputTransferToken)) return false;
        InputTransferToken other = (InputTransferToken) obj;
        return mMostSigBits == other.mMostSigBits && mLeastSigBits == other.mLeastSigBits;
    }

    @Override public int hashCode() {
        long x = mMostSigBits ^ mLeastSigBits;
        return (int) (x ^ (x >>> 32));
    }

    @Override public String toString() {
        return "InputTransferToken{" + new UUID(mMostSigBits, mLeastSigBits) + "}";
    }

    public static final Parcelable.Creator<InputTransferToken> CREATOR =
            new Parcelable.Creator<InputTransferToken>() {
        @Override public InputTransferToken createFromParcel(Parcel in) {
            return new InputTransferToken(in.readLong(), in.readLong());
        }
        @Override public InputTransferToken[] newArray(int size) {
            return new InputTransferToken[size];
        }
    };
}
