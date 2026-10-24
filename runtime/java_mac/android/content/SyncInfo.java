// android.content.SyncInfo -- an ACTIVE sync, as returned by
// ContentResolver.getCurrentSyncs().
//
// AOSP ref: frameworks/base/core/java/android/content/SyncInfo.java
// (android16-release). Mac.apk runs no SyncManager, so getCurrentSyncs()
// returns an empty list and nothing constructs one of these -- the type exists
// because the API names it.
package android.content;

import android.accounts.Account;
import android.os.Parcel;
import android.os.Parcelable;

public class SyncInfo implements Parcelable {
    /** @hide upstream; the sync-queue id. */
    public final int authorityId;
    public final Account account;
    public final String authority;
    /** elapsedRealtime() at which the sync started. */
    public final long startTime;

    /** @hide upstream. */
    public SyncInfo(int authorityId, Account account, String authority, long startTime) {
        this.authorityId = authorityId;
        this.account = account;
        this.authority = authority;
        this.startTime = startTime;
    }

    /** @hide upstream. */
    public SyncInfo(SyncInfo other) {
        this.authorityId = other.authorityId;
        this.account = new Account(other.account.name, other.account.type);
        this.authority = other.authority;
        this.startTime = other.startTime;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(authorityId);
        account.writeToParcel(parcel, 0);
        parcel.writeString(authority);
        parcel.writeLong(startTime);
    }

    SyncInfo(Parcel parcel) {
        authorityId = parcel.readInt();
        account = new Account(parcel);
        authority = parcel.readString();
        startTime = parcel.readLong();
    }

    public static final Creator<SyncInfo> CREATOR = new Creator<SyncInfo>() {
        @Override public SyncInfo createFromParcel(Parcel in) { return new SyncInfo(in); }
        @Override public SyncInfo[] newArray(int size) { return new SyncInfo[size]; }
    };
}
