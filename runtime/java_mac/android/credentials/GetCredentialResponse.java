package android.credentials;

import android.os.Parcel;
import android.os.Parcelable;

/** The successful result of a get-credential call. API 34.
 *  Source: frameworks/base/core/java/android/credentials/GetCredentialResponse.java */
public final class GetCredentialResponse implements Parcelable {
    private final Credential mCredential;

    public GetCredentialResponse(Credential credential) { mCredential = credential; }

    public Credential getCredential() { return mCredential; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCredential, flags);
    }

    public static final Parcelable.Creator<GetCredentialResponse> CREATOR =
            new Parcelable.Creator<GetCredentialResponse>() {
                @Override public GetCredentialResponse createFromParcel(Parcel in) {
                    return new GetCredentialResponse(in.readTypedObject(Credential.CREATOR));
                }
                @Override public GetCredentialResponse[] newArray(int size) {
                    return new GetCredentialResponse[size];
                }
            };
}
