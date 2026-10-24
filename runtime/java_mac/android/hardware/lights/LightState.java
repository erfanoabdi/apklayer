// android.hardware.lights.LightState -- an immutable requested color +
// intensity (or player-id selection) for one Light. Pure data holder; see
// LightsManager.java for why Mac.apk never actually drives a light with one
// of these -- there is no peripheral-light hardware for a LightsRequest to
// reach.
//
// Shape verified against AOSP (fetched 2026-08-12, android13-release):
// frameworks/base/core/java/android/hardware/lights/LightState.java
package android.hardware.lights;

import android.os.Parcel;
import android.os.Parcelable;

public final class LightState implements Parcelable {
    private final int mColor;
    private final int mPlayerId;

    /** @deprecated superseded by {@link Builder}; kept so bytecode compiled
     *  against older AOSP that calls this constructor directly still links. */
    @Deprecated
    public LightState(int color) {
        this(color, 0);
    }

    /** playerId only applies to a Light of type LIGHT_TYPE_PLAYER_ID. */
    public LightState(int color, int playerId) {
        mColor = color;
        mPlayerId = playerId;
    }

    private LightState(Parcel in) {
        mColor = in.readInt();
        mPlayerId = in.readInt();
    }

    public static final class Builder {
        private int mValue;
        private boolean mIsForPlayerId;

        public Builder() {
            mValue = 0;
            mIsForPlayerId = false;
        }

        public Builder setColor(int color) {
            mIsForPlayerId = false;
            mValue = color;
            return this;
        }

        public Builder setPlayerId(int playerId) {
            mIsForPlayerId = true;
            mValue = playerId;
            return this;
        }

        public LightState build() {
            return mIsForPlayerId ? new LightState(0, mValue) : new LightState(mValue, 0);
        }
    }

    /** ARGB; the A channel is ignored. 0 for a LIGHT_TYPE_PLAYER_ID state. */
    public int getColor() { return mColor; }
    /** 0 for any type other than LIGHT_TYPE_PLAYER_ID. */
    public int getPlayerId() { return mPlayerId; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mColor);
        dest.writeInt(mPlayerId);
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public String toString() {
        return "LightState{Color=0x" + Integer.toHexString(mColor) + ", PlayerId=" + mPlayerId + "}";
    }

    public static final Parcelable.Creator<LightState> CREATOR = new Parcelable.Creator<LightState>() {
        public LightState createFromParcel(Parcel in) { return new LightState(in); }
        public LightState[] newArray(int size) { return new LightState[size]; }
    };
}
