package android.credentials;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One credential type an app is willing to accept in a get request. API 34.
 *  Source: frameworks/base/core/java/android/credentials/CredentialOption.java */
public final class CredentialOption implements Parcelable {
    /** Bundle key under which a provider's supported element keys are passed. */
    public static final String SUPPORTED_ELEMENT_KEYS =
            "android.credentials.GetCredentialOption.SUPPORTED_ELEMENT_KEYS";

    private final String mType;
    private final Bundle mCredentialRetrievalData;
    private final Bundle mCandidateQueryData;
    private final boolean mIsSystemProviderRequired;
    private final Set<ComponentName> mAllowedProviders;

    private CredentialOption(Builder b) {
        mType = b.mType; mCredentialRetrievalData = b.mRetrieval;
        mCandidateQueryData = b.mQuery; mIsSystemProviderRequired = b.mSystem;
        mAllowedProviders = Collections.unmodifiableSet(new LinkedHashSet<>(b.mProviders));
    }

    private CredentialOption(String type, Bundle retrieval, Bundle query, boolean system,
            Set<ComponentName> providers) {
        mType = type; mCredentialRetrievalData = retrieval; mCandidateQueryData = query;
        mIsSystemProviderRequired = system;
        mAllowedProviders = Collections.unmodifiableSet(new LinkedHashSet<>(providers));
    }

    public String getType() { return mType; }
    public Bundle getCredentialRetrievalData() { return mCredentialRetrievalData; }
    public Bundle getCandidateQueryData() { return mCandidateQueryData; }
    public boolean isSystemProviderRequired() { return mIsSystemProviderRequired; }
    public Set<ComponentName> getAllowedProviders() { return mAllowedProviders; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mCredentialRetrievalData);
        dest.writeBundle(mCandidateQueryData);
        dest.writeBoolean(mIsSystemProviderRequired);
        dest.writeTypedList(new ArrayList<>(mAllowedProviders), flags);
    }

    public static final Parcelable.Creator<CredentialOption> CREATOR =
            new Parcelable.Creator<CredentialOption>() {
                @Override public CredentialOption createFromParcel(Parcel in) {
                    String type = in.readString();
                    Bundle retrieval = in.readBundle();
                    Bundle query = in.readBundle();
                    boolean system = in.readBoolean();
                    List<ComponentName> providers = in.createTypedArrayList(ComponentName.CREATOR);
                    return new CredentialOption(type, retrieval, query, system,
                            providers != null ? new LinkedHashSet<>(providers)
                                              : Collections.<ComponentName>emptySet());
                }
                @Override public CredentialOption[] newArray(int size) {
                    return new CredentialOption[size];
                }
            };

    public static final class Builder {
        private final String mType;
        private final Bundle mRetrieval;
        private final Bundle mQuery;
        private boolean mSystem;
        private Set<ComponentName> mProviders = new LinkedHashSet<>();
        public Builder(String type, Bundle credentialRetrievalData, Bundle candidateQueryData) {
            mType = type; mRetrieval = credentialRetrievalData; mQuery = candidateQueryData;
        }
        public Builder setIsSystemProviderRequired(boolean v) { mSystem = v; return this; }
        /** AOSP: adds one provider to the allow-list, keeping any already added. */
        public Builder addAllowedProvider(ComponentName allowedProvider) {
            if (allowedProvider != null) mProviders.add(allowedProvider);
            return this;
        }
        /** AOSP: REPLACES the allow-list. */
        public Builder setAllowedProviders(Set<ComponentName> providers) {
            mProviders = providers != null ? new LinkedHashSet<>(providers)
                                           : new LinkedHashSet<ComponentName>();
            return this;
        }
        public CredentialOption build() { return new CredentialOption(this); }
    }
}
