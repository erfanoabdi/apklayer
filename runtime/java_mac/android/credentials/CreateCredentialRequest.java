package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** A request to create/register a credential. API 34.
 *  Source: frameworks/base/core/java/android/credentials/CreateCredentialRequest.java */
public final class CreateCredentialRequest implements Parcelable {
    private final String mType;
    private final Bundle mCredentialData;
    private final Bundle mCandidateQueryData;
    private final boolean mSystemProviderRequired;
    private final boolean mAlwaysSendAppInfoToProvider;
    private final String mOrigin;

    private CreateCredentialRequest(Builder b) {
        mType = b.mType; mCredentialData = b.mCredentialData;
        mCandidateQueryData = b.mCandidateQueryData;
        mSystemProviderRequired = b.mSystem;
        mAlwaysSendAppInfoToProvider = b.mSendAppInfo; mOrigin = b.mOrigin;
    }

    private CreateCredentialRequest(String type, Bundle credentialData, Bundle candidateQueryData,
            boolean systemProviderRequired, boolean alwaysSendAppInfoToProvider, String origin) {
        mType = type; mCredentialData = credentialData; mCandidateQueryData = candidateQueryData;
        mSystemProviderRequired = systemProviderRequired;
        mAlwaysSendAppInfoToProvider = alwaysSendAppInfoToProvider; mOrigin = origin;
    }

    public String getType() { return mType; }
    public Bundle getCredentialData() { return mCredentialData; }
    public Bundle getCandidateQueryData() { return mCandidateQueryData; }
    public boolean isSystemProviderRequired() { return mSystemProviderRequired; }
    public boolean alwaysSendAppInfoToProvider() { return mAlwaysSendAppInfoToProvider; }
    public String getOrigin() { return mOrigin; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mCredentialData);
        dest.writeBundle(mCandidateQueryData);
        dest.writeBoolean(mSystemProviderRequired);
        dest.writeBoolean(mAlwaysSendAppInfoToProvider);
        dest.writeString(mOrigin);
    }

    public static final Parcelable.Creator<CreateCredentialRequest> CREATOR =
            new Parcelable.Creator<CreateCredentialRequest>() {
                @Override public CreateCredentialRequest createFromParcel(Parcel in) {
                    return new CreateCredentialRequest(in.readString(), in.readBundle(),
                            in.readBundle(), in.readBoolean(), in.readBoolean(), in.readString());
                }
                @Override public CreateCredentialRequest[] newArray(int size) {
                    return new CreateCredentialRequest[size];
                }
            };

    public static final class Builder {
        private final String mType;
        private final Bundle mCredentialData;
        private final Bundle mCandidateQueryData;
        private boolean mSystem;
        private boolean mSendAppInfo = true;
        private String mOrigin;
        public Builder(String type, Bundle credentialData, Bundle candidateQueryData) {
            mType = type; mCredentialData = credentialData; mCandidateQueryData = candidateQueryData;
        }
        public Builder setIsSystemProviderRequired(boolean v) { mSystem = v; return this; }
        public Builder setAlwaysSendAppInfoToProvider(boolean v) { mSendAppInfo = v; return this; }
        public Builder setOrigin(String origin) { mOrigin = origin; return this; }
        public CreateCredentialRequest build() { return new CreateCredentialRequest(this); }
    }
}
