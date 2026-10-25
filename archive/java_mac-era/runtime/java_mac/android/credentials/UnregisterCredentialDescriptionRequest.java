package android.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A request to unregister one or more CredentialDescriptions with the system
 *  credential manager. API 34.
 *  Source: frameworks/base/core/java/android/credentials/UnregisterCredentialDescriptionRequest.java */
public final class UnregisterCredentialDescriptionRequest implements Parcelable {
    private final Set<CredentialDescription> mCredentialDescriptions;

    public UnregisterCredentialDescriptionRequest(CredentialDescription credentialDescription) {
        Objects.requireNonNull(credentialDescription);
        Set<CredentialDescription> set = new HashSet<>();
        set.add(credentialDescription);
        mCredentialDescriptions = set;
    }

    public UnregisterCredentialDescriptionRequest(Set<CredentialDescription> credentialDescriptions) {
        mCredentialDescriptions = new HashSet<>(Objects.requireNonNull(credentialDescriptions));
    }

    public Set<CredentialDescription> getCredentialDescriptions() {
        return mCredentialDescriptions;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(new ArrayList<>(mCredentialDescriptions), flags);
    }

    public static final Parcelable.Creator<UnregisterCredentialDescriptionRequest> CREATOR =
            new Parcelable.Creator<UnregisterCredentialDescriptionRequest>() {
                @Override public UnregisterCredentialDescriptionRequest createFromParcel(Parcel in) {
                    List<CredentialDescription> list =
                            in.createTypedArrayList(CredentialDescription.CREATOR);
                    return new UnregisterCredentialDescriptionRequest(
                            list != null ? new HashSet<>(list)
                                         : new HashSet<CredentialDescription>());
                }
                @Override public UnregisterCredentialDescriptionRequest[] newArray(int size) {
                    return new UnregisterCredentialDescriptionRequest[size];
                }
            };
}
