package android.service.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A provider's answer to the query-phase create-credential request. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/BeginCreateCredentialResponse.java
 *
 *  The public no-arg constructor is AOSP's "I have nothing to offer" response
 *  (empty entry list, no remote entry) and is exactly what a provider returns
 *  when it cannot create the requested credential type. */
public final class BeginCreateCredentialResponse implements Parcelable {
    private final List<CreateEntry> mCreateEntries;
    private final RemoteEntry mRemote;

    public BeginCreateCredentialResponse() {
        this(Collections.<CreateEntry>emptyList(), null);
    }

    private BeginCreateCredentialResponse(Builder b) { this(b.mCreateEntries, b.mRemote); }

    private BeginCreateCredentialResponse(List<CreateEntry> createEntries, RemoteEntry remote) {
        mCreateEntries = Collections.unmodifiableList(createEntries != null
                ? new ArrayList<>(createEntries) : new ArrayList<CreateEntry>());
        mRemote = remote;
    }

    public List<CreateEntry> getCreateEntries() { return mCreateEntries; }
    public RemoteEntry getRemoteCreateEntry() { return mRemote; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mCreateEntries, flags);
        dest.writeTypedObject(mRemote, flags);
    }

    public static final Parcelable.Creator<BeginCreateCredentialResponse> CREATOR =
            new Parcelable.Creator<BeginCreateCredentialResponse>() {
                @Override public BeginCreateCredentialResponse createFromParcel(Parcel in) {
                    return new BeginCreateCredentialResponse(
                            in.createTypedArrayList(CreateEntry.CREATOR),
                            in.readTypedObject(RemoteEntry.CREATOR));
                }
                @Override public BeginCreateCredentialResponse[] newArray(int size) {
                    return new BeginCreateCredentialResponse[size];
                }
            };

    public static final class Builder {
        private List<CreateEntry> mCreateEntries = new ArrayList<>();
        private RemoteEntry mRemote;
        public Builder() {}
        /** AOSP: appends one entry. */
        public Builder addCreateEntry(CreateEntry createEntry) {
            if (createEntry != null) mCreateEntries.add(createEntry); return this;
        }
        /** AOSP: REPLACES the entry list. */
        public Builder setCreateEntries(List<CreateEntry> createEntries) {
            mCreateEntries = createEntries != null ? new ArrayList<>(createEntries)
                                                   : new ArrayList<CreateEntry>();
            return this;
        }
        public Builder setRemoteCreateEntry(RemoteEntry remoteCreateEntry) {
            mRemote = remoteCreateEntry; return this;
        }
        public BeginCreateCredentialResponse build() {
            return new BeginCreateCredentialResponse(this);
        }
    }
}
