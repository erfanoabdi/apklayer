// android.content.SyncAdapterType -- describes one registered sync adapter
// (authority + account type) and its capability flags, as returned by
// ContentResolver.getSyncAdapterTypes().
//
// AOSP ref: frameworks/base/core/java/android/content/SyncAdapterType.java
// (android16-release). Ported in full.
//
// Mac.apk registers no sync adapters, so getSyncAdapterTypes() returns an
// empty array and nothing here is ever constructed BY the framework -- but an
// app constructs these itself (that is what the public constructors are for)
// and hands them around, so the value semantics, the "key" mode and the
// Parcelable surface all have to be right.
//
// The "key" mode is the subtle part and is preserved verbatim: newKey() builds
// an instance that carries ONLY (authority, accountType) for use as a map key,
// and every capability getter on it throws IllegalStateException rather than
// returning a default. Answering false there instead would silently turn a
// programming error into a wrong capability.
package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public class SyncAdapterType implements Parcelable {
    public final String authority;
    public final String accountType;
    public final boolean isKey;
    private final boolean userVisible;
    private final boolean supportsUploading;
    private final boolean isAlwaysSyncable;
    private final boolean allowParallelSyncs;
    private final String settingsActivity;
    private final String packageName;

    public SyncAdapterType(String authority, String accountType, boolean userVisible,
                           boolean supportsUploading) {
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("the authority must not be empty: " + authority);
        }
        if (accountType == null || accountType.isEmpty()) {
            throw new IllegalArgumentException("the accountType must not be empty: " + accountType);
        }
        this.authority = authority;
        this.accountType = accountType;
        this.userVisible = userVisible;
        this.supportsUploading = supportsUploading;
        this.isAlwaysSyncable = false;
        this.allowParallelSyncs = false;
        this.settingsActivity = null;
        this.isKey = false;
        this.packageName = null;
    }

    /** @hide upstream, public in bytecode -- the full-capability form. */
    public SyncAdapterType(String authority, String accountType, boolean userVisible,
                           boolean supportsUploading, boolean isAlwaysSyncable,
                           boolean allowParallelSyncs, String settingsActivity,
                           String packageName) {
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("the authority must not be empty: " + authority);
        }
        if (accountType == null || accountType.isEmpty()) {
            throw new IllegalArgumentException("the accountType must not be empty: " + accountType);
        }
        this.authority = authority;
        this.accountType = accountType;
        this.userVisible = userVisible;
        this.supportsUploading = supportsUploading;
        this.isAlwaysSyncable = isAlwaysSyncable;
        this.allowParallelSyncs = allowParallelSyncs;
        this.settingsActivity = settingsActivity;
        this.isKey = false;
        this.packageName = packageName;
    }

    private SyncAdapterType(String authority, String accountType) {
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("the authority must not be empty: " + authority);
        }
        if (accountType == null || accountType.isEmpty()) {
            throw new IllegalArgumentException("the accountType must not be empty: " + accountType);
        }
        this.authority = authority;
        this.accountType = accountType;
        this.userVisible = true;
        this.supportsUploading = true;
        this.isAlwaysSyncable = false;
        this.allowParallelSyncs = false;
        this.settingsActivity = null;
        this.isKey = true;
        this.packageName = null;
    }

    public String getAuthority() { return authority; }
    public String getAccountType() { return accountType; }

    public boolean supportsUploading() {
        checkNotKey();
        return supportsUploading;
    }

    public boolean isUserVisible() {
        checkNotKey();
        return userVisible;
    }

    public boolean allowParallelSyncs() {
        checkNotKey();
        return allowParallelSyncs;
    }

    public boolean isAlwaysSyncable() {
        checkNotKey();
        return isAlwaysSyncable;
    }

    public String getSettingsActivity() {
        checkNotKey();
        return settingsActivity;
    }

    /** @TestApi upstream, public in bytecode. */
    public String getPackageName() { return packageName; }

    private void checkNotKey() {
        if (isKey) {
            throw new IllegalStateException(
                    "this method is not allowed to be called when this is a key");
        }
    }

    public static SyncAdapterType newKey(String authority, String accountType) {
        return new SyncAdapterType(authority, accountType);
    }

    /** AOSP compares ONLY (authority, accountType) -- deliberately, so a key
     *  built by newKey() equals a fully-populated adapter type for the same
     *  pair. Do not "fix" this to include the flags. */
    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SyncAdapterType)) return false;
        final SyncAdapterType other = (SyncAdapterType) o;
        return authority.equals(other.authority) && accountType.equals(other.accountType);
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + authority.hashCode();
        result = 31 * result + accountType.hashCode();
        return result;
    }

    @Override public String toString() {
        if (isKey) {
            return "SyncAdapterType Key {name=" + authority
                    + ", type=" + accountType
                    + "}";
        }
        return "SyncAdapterType {name=" + authority
                + ", type=" + accountType
                + ", userVisible=" + userVisible
                + ", supportsUploading=" + supportsUploading
                + ", isAlwaysSyncable=" + isAlwaysSyncable
                + ", allowParallelSyncs=" + allowParallelSyncs
                + ", settingsActivity=" + settingsActivity
                + ", packageName=" + packageName
                + "}";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        if (isKey) {
            throw new IllegalStateException("keys aren't parcelable");
        }
        dest.writeString(authority);
        dest.writeString(accountType);
        dest.writeInt(userVisible ? 1 : 0);
        dest.writeInt(supportsUploading ? 1 : 0);
        dest.writeInt(isAlwaysSyncable ? 1 : 0);
        dest.writeInt(allowParallelSyncs ? 1 : 0);
        dest.writeString(settingsActivity);
        dest.writeString(packageName);
    }

    public SyncAdapterType(Parcel source) {
        this(source.readString(),
             source.readString(),
             source.readInt() != 0,
             source.readInt() != 0,
             source.readInt() != 0,
             source.readInt() != 0,
             source.readString(),
             source.readString());
    }

    public static final Creator<SyncAdapterType> CREATOR = new Creator<SyncAdapterType>() {
        public SyncAdapterType createFromParcel(Parcel source) {
            return new SyncAdapterType(source);
        }
        public SyncAdapterType[] newArray(int size) { return new SyncAdapterType[size]; }
    };
}
