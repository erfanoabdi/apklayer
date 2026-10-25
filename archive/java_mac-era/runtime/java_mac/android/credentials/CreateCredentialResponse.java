package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** The successful result of a create-credential call. API 34.
 *  Source: frameworks/base/core/java/android/credentials/CreateCredentialResponse.java */
public final class CreateCredentialResponse implements Parcelable {
    private final Bundle mData;

    public CreateCredentialResponse(Bundle data) { mData = data; }

    public Bundle getData() { return mData; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(mData);
    }

    public static final Parcelable.Creator<CreateCredentialResponse> CREATOR =
            new Parcelable.Creator<CreateCredentialResponse>() {
                @Override public CreateCredentialResponse createFromParcel(Parcel in) {
                    return new CreateCredentialResponse(in.readBundle());
                }
                @Override public CreateCredentialResponse[] newArray(int size) {
                    return new CreateCredentialResponse[size];
                }
            };
}
