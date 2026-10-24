// android.app.admin.SystemUpdateInfo -- "A class that represents information
// about a pending system update", returned by
// DevicePolicyManager.getPendingSystemUpdate. API 26.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/SystemUpdateInfo.java
// (android16-release).
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

public final class SystemUpdateInfo implements Parcelable {

    /** AOSP SECURITY_PATCH_STATE_*: whether the pending update is a security
     *  patch. UNKNOWN is what a device reports when the OTA metadata does not
     *  say. */
    public static final int SECURITY_PATCH_STATE_UNKNOWN = 0;
    public static final int SECURITY_PATCH_STATE_FALSE = 1;
    public static final int SECURITY_PATCH_STATE_TRUE = 2;

    private final long mReceivedTime;
    private final int mSecurityPatchState;

    /** @hide -- AOSP's factory is @hide; only the system produces one. */
    SystemUpdateInfo(long receivedTime, int securityPatchState) {
        mReceivedTime = receivedTime;
        mSecurityPatchState = securityPatchState;
    }

    private SystemUpdateInfo(Parcel in) {
        mReceivedTime = in.readLong();
        mSecurityPatchState = in.readInt();
    }

    /** AOSP getReceivedTime(): "the time when the update was first available,
     *  in milliseconds since the epoch." */
    public long getReceivedTime() { return mReceivedTime; }

    /** AOSP getSecurityPatchState(). */
    public int getSecurityPatchState() { return mSecurityPatchState; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mReceivedTime);
        dest.writeInt(mSecurityPatchState);
    }

    public static final Parcelable.Creator<SystemUpdateInfo> CREATOR =
            new Parcelable.Creator<SystemUpdateInfo>() {
        @Override public SystemUpdateInfo createFromParcel(Parcel in) {
            return new SystemUpdateInfo(in);
        }
        @Override public SystemUpdateInfo[] newArray(int size) {
            return new SystemUpdateInfo[size];
        }
    };
}
