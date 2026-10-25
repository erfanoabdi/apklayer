package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** The provider-side clear-state request. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/ClearCredentialStateRequest.java */
public final class ClearCredentialStateRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final Bundle mData;

    public ClearCredentialStateRequest(CallingAppInfo callingAppInfo, Bundle data) {
        mCallingAppInfo = callingAppInfo; mData = data;
    }

    public CallingAppInfo getCallingAppInfo() { return mCallingAppInfo; }
    public Bundle getData() { return mData; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeBundle(mData);
    }

    public static final Parcelable.Creator<ClearCredentialStateRequest> CREATOR =
            new Parcelable.Creator<ClearCredentialStateRequest>() {
                @Override public ClearCredentialStateRequest createFromParcel(Parcel in) {
                    return new ClearCredentialStateRequest(
                            in.readTypedObject(CallingAppInfo.CREATOR), in.readBundle());
                }
                @Override public ClearCredentialStateRequest[] newArray(int size) {
                    return new ClearCredentialStateRequest[size];
                }
            };
}
