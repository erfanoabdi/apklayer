package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** One entry of a query-phase get-credential request. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/BeginGetCredentialOption.java */
public final class BeginGetCredentialOption implements Parcelable {
    private final String mId;
    private final String mType;
    private final Bundle mCandidateQueryData;

    public BeginGetCredentialOption(String id, String type, Bundle candidateQueryData) {
        mId = id; mType = type; mCandidateQueryData = candidateQueryData;
    }

    public String getId() { return mId; }
    public String getType() { return mType; }
    public Bundle getCandidateQueryData() { return mCandidateQueryData; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mType);
        dest.writeBundle(mCandidateQueryData);
    }

    public static final Parcelable.Creator<BeginGetCredentialOption> CREATOR =
            new Parcelable.Creator<BeginGetCredentialOption>() {
                @Override public BeginGetCredentialOption createFromParcel(Parcel in) {
                    return new BeginGetCredentialOption(in.readString(), in.readString(),
                            in.readBundle());
                }
                @Override public BeginGetCredentialOption[] newArray(int size) {
                    return new BeginGetCredentialOption[size];
                }
            };
}
