package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** The query-phase create-credential request handed to a provider. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/BeginCreateCredentialRequest.java */
public final class BeginCreateCredentialRequest implements Parcelable {
    private final String mType;
    private final Bundle mData;
    private final CallingAppInfo mCallingAppInfo;

    public BeginCreateCredentialRequest(String type, Bundle data) { this(type, data, null); }

    public BeginCreateCredentialRequest(String type, Bundle data, CallingAppInfo callingAppInfo) {
        mType = type; mData = data; mCallingAppInfo = callingAppInfo;
    }

    public String getType() { return mType; }
    public Bundle getData() { return mData; }
    public CallingAppInfo getCallingAppInfo() { return mCallingAppInfo; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mData);
        dest.writeTypedObject(mCallingAppInfo, flags);
    }

    public static final Parcelable.Creator<BeginCreateCredentialRequest> CREATOR =
            new Parcelable.Creator<BeginCreateCredentialRequest>() {
                @Override public BeginCreateCredentialRequest createFromParcel(Parcel in) {
                    return new BeginCreateCredentialRequest(in.readString(), in.readBundle(),
                            in.readTypedObject(CallingAppInfo.CREATOR));
                }
                @Override public BeginCreateCredentialRequest[] newArray(int size) {
                    return new BeginCreateCredentialRequest[size];
                }
            };
}
