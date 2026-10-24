package android.service.credentials;

import android.credentials.CredentialOption;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The provider-side get-credential request. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/GetCredentialRequest.java */
public final class GetCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final List<CredentialOption> mOptions;

    public GetCredentialRequest(CallingAppInfo callingAppInfo,
            List<CredentialOption> credentialOptions) {
        mCallingAppInfo = callingAppInfo;
        mOptions = credentialOptions != null
                ? Collections.unmodifiableList(new ArrayList<>(credentialOptions))
                : Collections.<CredentialOption>emptyList();
    }

    public CallingAppInfo getCallingAppInfo() { return mCallingAppInfo; }
    public List<CredentialOption> getCredentialOptions() { return mOptions; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeTypedList(mOptions, flags);
    }

    public static final Parcelable.Creator<GetCredentialRequest> CREATOR =
            new Parcelable.Creator<GetCredentialRequest>() {
                @Override public GetCredentialRequest createFromParcel(Parcel in) {
                    return new GetCredentialRequest(in.readTypedObject(CallingAppInfo.CREATOR),
                            in.createTypedArrayList(CredentialOption.CREATOR));
                }
                @Override public GetCredentialRequest[] newArray(int size) {
                    return new GetCredentialRequest[size];
                }
            };
}
