package android.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import android.service.credentials.CredentialEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A description of a credential a provider holds, registered with the system so
 *  that a matching get-request can be routed to it without waking the provider.
 *  API 34.
 *  Source: frameworks/base/core/java/android/credentials/CredentialDescription.java
 *
 *  The constructor's three preconditions are AOSP's and are reproduced exactly:
 *  a non-empty type, at most 16 entries, and every entry's type equal to the
 *  description's type. They are cheap and they are the difference between an
 *  app seeing IllegalArgumentException here (as on a device) and seeing a
 *  confusing failure later. */
public final class CredentialDescription implements Parcelable {
    private static final int MAX_ALLOWED_ENTRIES_PER_DESCRIPTION = 16;

    private final String mType;
    private final Set<String> mSupportedElementKeys;
    private final List<CredentialEntry> mCredentialEntries;

    public CredentialDescription(String type, Set<String> supportedElementKeys,
            List<CredentialEntry> credentialEntries) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        mType = type;
        mSupportedElementKeys = Objects.requireNonNull(supportedElementKeys);
        mCredentialEntries = Objects.requireNonNull(credentialEntries);
        if (credentialEntries.size() > MAX_ALLOWED_ENTRIES_PER_DESCRIPTION) {
            throw new IllegalArgumentException(
                    "The number of Credential Entries exceed 16.");
        }
        for (CredentialEntry entry : credentialEntries) {
            if (entry == null || !type.equals(entry.getType())) {
                throw new IllegalArgumentException(
                        "Credential Entry type(s) do not match the request type.");
            }
        }
    }

    private CredentialDescription(Parcel in) {
        mType = in.readString();
        List<String> keys = in.createStringArrayList();
        mSupportedElementKeys = keys != null ? new HashSet<>(keys) : new HashSet<String>();
        List<CredentialEntry> entries = in.createTypedArrayList(CredentialEntry.CREATOR);
        mCredentialEntries = entries != null ? entries : new ArrayList<CredentialEntry>();
    }

    public String getType() { return mType; }

    public Set<String> getSupportedElementKeys() { return new HashSet<>(mSupportedElementKeys); }

    public List<CredentialEntry> getCredentialEntries() { return mCredentialEntries; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeStringList(new ArrayList<>(mSupportedElementKeys));
        dest.writeTypedList(mCredentialEntries, flags);
    }

    /** AOSP hashes on type + supported keys only: the constructor already forces
     *  every entry to share the type, and an entry's Slice cannot be hashed. */
    @Override public int hashCode() { return Objects.hash(mType, mSupportedElementKeys); }

    @Override public boolean equals(Object obj) {
        if (!(obj instanceof CredentialDescription)) return false;
        CredentialDescription other = (CredentialDescription) obj;
        return mType.equals(other.mType)
                && mSupportedElementKeys.equals(other.mSupportedElementKeys);
    }

    public static final Parcelable.Creator<CredentialDescription> CREATOR =
            new Parcelable.Creator<CredentialDescription>() {
                @Override public CredentialDescription createFromParcel(Parcel in) {
                    return new CredentialDescription(in);
                }
                @Override public CredentialDescription[] newArray(int size) {
                    return new CredentialDescription[size];
                }
            };
}
