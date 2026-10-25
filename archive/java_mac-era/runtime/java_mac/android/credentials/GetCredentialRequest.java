package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A request to get a credential: one or more CredentialOptions. API 34.
 *  Source: frameworks/base/core/java/android/credentials/GetCredentialRequest.java */
public final class GetCredentialRequest implements Parcelable {
    private final Bundle mData;
    private final List<CredentialOption> mOptions;
    private final boolean mAlwaysSendAppInfoToProvider;
    private final String mOrigin;

    private GetCredentialRequest(Builder b) {
        mData = b.mData;
        mOptions = Collections.unmodifiableList(new ArrayList<>(b.mOptions));
        mAlwaysSendAppInfoToProvider = b.mSendAppInfo;
        mOrigin = b.mOrigin;
    }

    private GetCredentialRequest(Bundle data, List<CredentialOption> options,
            boolean alwaysSendAppInfoToProvider, String origin) {
        mData = data;
        mOptions = Collections.unmodifiableList(
                options != null ? new ArrayList<>(options) : new ArrayList<CredentialOption>());
        mAlwaysSendAppInfoToProvider = alwaysSendAppInfoToProvider;
        mOrigin = origin;
    }

    public Bundle getData() { return mData; }
    public List<CredentialOption> getCredentialOptions() { return mOptions; }
    public boolean alwaysSendAppInfoToProvider() { return mAlwaysSendAppInfoToProvider; }
    public String getOrigin() { return mOrigin; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(mData);
        dest.writeTypedList(mOptions, flags);
        dest.writeBoolean(mAlwaysSendAppInfoToProvider);
        dest.writeString(mOrigin);
    }

    public static final Parcelable.Creator<GetCredentialRequest> CREATOR =
            new Parcelable.Creator<GetCredentialRequest>() {
                @Override public GetCredentialRequest createFromParcel(Parcel in) {
                    Bundle data = in.readBundle();
                    List<CredentialOption> options =
                            in.createTypedArrayList(CredentialOption.CREATOR);
                    boolean sendAppInfo = in.readBoolean();
                    String origin = in.readString();
                    return new GetCredentialRequest(data, options, sendAppInfo, origin);
                }
                @Override public GetCredentialRequest[] newArray(int size) {
                    return new GetCredentialRequest[size];
                }
            };

    public static final class Builder {
        private final Bundle mData;
        private List<CredentialOption> mOptions = new ArrayList<>();
        private boolean mSendAppInfo = true;
        private String mOrigin;
        public Builder(Bundle data) { mData = data; }
        public Builder addCredentialOption(CredentialOption option) {
            if (option != null) mOptions.add(option); return this;
        }
        /** AOSP: REPLACES the option list. */
        public Builder setCredentialOptions(List<CredentialOption> credentialOptions) {
            mOptions = credentialOptions != null ? new ArrayList<>(credentialOptions)
                                                 : new ArrayList<CredentialOption>();
            return this;
        }
        public Builder setAlwaysSendAppInfoToProvider(boolean v) { mSendAppInfo = v; return this; }
        public Builder setOrigin(String origin) { mOrigin = origin; return this; }
        public GetCredentialRequest build() { return new GetCredentialRequest(this); }
    }
}
