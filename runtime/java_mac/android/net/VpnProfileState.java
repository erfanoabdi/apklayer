// android.net.VpnProfileState -- the state of a platform VPN profile, returned
// by VpnManager.getProvisionedVpnProfileState().
//
// A REAL implementation: a plain immutable Parcelable value with no device
// dependency. Mac.apk never hands one out (getProvisionedVpnProfileState()
// returns null because nothing is ever provisioned -- see VpnManager), but the
// type must resolve for an app that names it, and an app that constructs one
// itself gets correct behaviour.
//
// Mirrors frameworks/base/core/java/android/net/VpnProfileState.java; public
// surface confirmed against frameworks/base/core/api/current.txt.
package android.net;

import android.os.Parcel;
import android.os.Parcelable;

public final class VpnProfileState implements Parcelable {

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_FAILED = 3;

    private final int mState;
    private final String mSessionKey;
    private final boolean mAlwaysOn;
    private final boolean mLockdown;

    public VpnProfileState(int state, String sessionKey, boolean alwaysOn, boolean lockdown) {
        mState = state;
        mSessionKey = sessionKey;
        mAlwaysOn = alwaysOn;
        mLockdown = lockdown;
    }

    public int getState() { return mState; }

    public String getSessionId() { return mSessionKey; }

    public boolean isAlwaysOn() { return mAlwaysOn; }

    public boolean isLockdownEnabled() { return mLockdown; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mState);
        out.writeString(mSessionKey);
        out.writeBoolean(mAlwaysOn);
        out.writeBoolean(mLockdown);
    }

    @Override public String toString() {
        return "VpnProfileState{state=" + mState + ", sessionKey=" + mSessionKey
                + ", alwaysOn=" + mAlwaysOn + ", lockdown=" + mLockdown + "}";
    }

    public static final Creator<VpnProfileState> CREATOR = new Creator<VpnProfileState>() {
        @Override public VpnProfileState createFromParcel(Parcel in) {
            return new VpnProfileState(in.readInt(), in.readString(),
                    in.readBoolean(), in.readBoolean());
        }

        @Override public VpnProfileState[] newArray(int size) {
            return new VpnProfileState[size];
        }
    };
}
