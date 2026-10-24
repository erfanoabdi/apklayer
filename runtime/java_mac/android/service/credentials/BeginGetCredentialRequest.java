package android.service.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The query-phase get-credential request handed to a provider. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/BeginGetCredentialRequest.java */
public final class BeginGetCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final List<BeginGetCredentialOption> mOptions;

    private BeginGetCredentialRequest(Builder b) {
        mCallingAppInfo = b.mCallingAppInfo;
        mOptions = Collections.unmodifiableList(new ArrayList<>(b.mOptions));
    }

    private BeginGetCredentialRequest(CallingAppInfo callingAppInfo,
            List<BeginGetCredentialOption> options) {
        mCallingAppInfo = callingAppInfo;
        mOptions = Collections.unmodifiableList(options != null ? new ArrayList<>(options)
                : new ArrayList<BeginGetCredentialOption>());
    }

    public CallingAppInfo getCallingAppInfo() { return mCallingAppInfo; }
    public List<BeginGetCredentialOption> getBeginGetCredentialOptions() { return mOptions; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeTypedList(mOptions, flags);
    }

    public static final Parcelable.Creator<BeginGetCredentialRequest> CREATOR =
            new Parcelable.Creator<BeginGetCredentialRequest>() {
                @Override public BeginGetCredentialRequest createFromParcel(Parcel in) {
                    return new BeginGetCredentialRequest(
                            in.readTypedObject(CallingAppInfo.CREATOR),
                            in.createTypedArrayList(BeginGetCredentialOption.CREATOR));
                }
                @Override public BeginGetCredentialRequest[] newArray(int size) {
                    return new BeginGetCredentialRequest[size];
                }
            };

    public static final class Builder {
        private CallingAppInfo mCallingAppInfo;
        private List<BeginGetCredentialOption> mOptions = new ArrayList<>();
        public Builder() {}
        public Builder setCallingAppInfo(CallingAppInfo callingAppInfo) {
            mCallingAppInfo = callingAppInfo; return this;
        }
        /** AOSP: appends one option, keeping any already added. */
        public Builder addBeginGetCredentialOption(BeginGetCredentialOption option) {
            if (option != null) mOptions.add(option); return this;
        }
        /** AOSP: REPLACES the option list. */
        public Builder setBeginGetCredentialOptions(List<BeginGetCredentialOption> options) {
            mOptions = options != null ? new ArrayList<>(options)
                                       : new ArrayList<BeginGetCredentialOption>();
            return this;
        }
        public BeginGetCredentialRequest build() { return new BeginGetCredentialRequest(this); }
    }
}
