package android.service.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A provider's answer to the query-phase get-credential request. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/BeginGetCredentialResponse.java
 *
 *  The public no-arg constructor is AOSP's empty response -- no credential
 *  entries, no actions, no remote entry -- which is what a provider with
 *  nothing matching returns. */
public final class BeginGetCredentialResponse implements Parcelable {
    private final List<CredentialEntry> mCredentialEntries;
    private final List<Action> mActions;
    private final List<Action> mAuthActions;
    private final RemoteEntry mRemote;

    public BeginGetCredentialResponse() {
        this(Collections.<CredentialEntry>emptyList(), Collections.<Action>emptyList(),
                Collections.<Action>emptyList(), null);
    }

    private BeginGetCredentialResponse(Builder b) {
        this(b.mCredentialEntries, b.mActions, b.mAuthActions, b.mRemote);
    }

    private BeginGetCredentialResponse(List<CredentialEntry> credentialEntries,
            List<Action> actions, List<Action> authActions, RemoteEntry remote) {
        mCredentialEntries = Collections.unmodifiableList(credentialEntries != null
                ? new ArrayList<>(credentialEntries) : new ArrayList<CredentialEntry>());
        mActions = Collections.unmodifiableList(actions != null
                ? new ArrayList<>(actions) : new ArrayList<Action>());
        mAuthActions = Collections.unmodifiableList(authActions != null
                ? new ArrayList<>(authActions) : new ArrayList<Action>());
        mRemote = remote;
    }

    public List<CredentialEntry> getCredentialEntries() { return mCredentialEntries; }
    public List<Action> getActions() { return mActions; }
    public List<Action> getAuthenticationActions() { return mAuthActions; }
    public RemoteEntry getRemoteCredentialEntry() { return mRemote; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mCredentialEntries, flags);
        dest.writeTypedList(mActions, flags);
        dest.writeTypedList(mAuthActions, flags);
        dest.writeTypedObject(mRemote, flags);
    }

    public static final Parcelable.Creator<BeginGetCredentialResponse> CREATOR =
            new Parcelable.Creator<BeginGetCredentialResponse>() {
                @Override public BeginGetCredentialResponse createFromParcel(Parcel in) {
                    List<CredentialEntry> entries =
                            in.createTypedArrayList(CredentialEntry.CREATOR);
                    List<Action> actions = in.createTypedArrayList(Action.CREATOR);
                    List<Action> authActions = in.createTypedArrayList(Action.CREATOR);
                    RemoteEntry remote = in.readTypedObject(RemoteEntry.CREATOR);
                    return new BeginGetCredentialResponse(entries, actions, authActions, remote);
                }
                @Override public BeginGetCredentialResponse[] newArray(int size) {
                    return new BeginGetCredentialResponse[size];
                }
            };

    public static final class Builder {
        private List<CredentialEntry> mCredentialEntries = new ArrayList<>();
        private List<Action> mActions = new ArrayList<>();
        private List<Action> mAuthActions = new ArrayList<>();
        private RemoteEntry mRemote;
        public Builder() {}
        public Builder addCredentialEntry(CredentialEntry credentialEntry) {
            if (credentialEntry != null) mCredentialEntries.add(credentialEntry); return this;
        }
        public Builder addAction(Action action) {
            if (action != null) mActions.add(action); return this;
        }
        public Builder addAuthenticationAction(Action authenticationAction) {
            if (authenticationAction != null) mAuthActions.add(authenticationAction); return this;
        }
        /** AOSP: REPLACES the credential-entry list. */
        public Builder setCredentialEntries(List<CredentialEntry> credentialEntries) {
            mCredentialEntries = credentialEntries != null ? new ArrayList<>(credentialEntries)
                                                           : new ArrayList<CredentialEntry>();
            return this;
        }
        /** AOSP: REPLACES the action list. */
        public Builder setActions(List<Action> actions) {
            mActions = actions != null ? new ArrayList<>(actions) : new ArrayList<Action>();
            return this;
        }
        /** AOSP: REPLACES the authentication-action list. */
        public Builder setAuthenticationActions(List<Action> authenticationActions) {
            mAuthActions = authenticationActions != null ? new ArrayList<>(authenticationActions)
                                                         : new ArrayList<Action>();
            return this;
        }
        public Builder setRemoteCredentialEntry(RemoteEntry remoteCredentialEntry) {
            mRemote = remoteCredentialEntry; return this;
        }
        public BeginGetCredentialResponse build() { return new BeginGetCredentialResponse(this); }
    }
}
