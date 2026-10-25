package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** The provider-side create-credential request. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/CreateCredentialRequest.java */
public final class CreateCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final String mType;
    private final Bundle mData;

    public CreateCredentialRequest(CallingAppInfo callingAppInfo, String type, Bundle data) {
        mCallingAppInfo = callingAppInfo; mType = type; mData = data;
    }

    public CallingAppInfo getCallingAppInfo() { return mCallingAppInfo; }
    public String getType() { return mType; }
    public Bundle getData() { return mData; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeString(mType);
        dest.writeBundle(mData);
    }

    public static final Parcelable.Creator<CreateCredentialRequest> CREATOR =
            new Parcelable.Creator<CreateCredentialRequest>() {
                @Override public CreateCredentialRequest createFromParcel(Parcel in) {
                    return new CreateCredentialRequest(in.readTypedObject(CallingAppInfo.CREATOR),
                            in.readString(), in.readBundle());
                }
                @Override public CreateCredentialRequest[] newArray(int size) {
                    return new CreateCredentialRequest[size];
                }
            };
}
