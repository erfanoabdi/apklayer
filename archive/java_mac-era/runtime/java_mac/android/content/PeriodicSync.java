// android.content.PeriodicSync -- one periodic sync registered for an
// (Account, authority) pair, as returned by
// ContentResolver.getPeriodicSyncs().
//
// AOSP ref: frameworks/base/core/java/android/content/PeriodicSync.java
// (android16-release). Ported verbatim, including the defensive Bundle copy
// in each constructor and the @hide `flexTime` field (public in bytecode).
package android.content;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class PeriodicSync implements Parcelable {
    public final Account account;
    public final String authority;
    public final Bundle extras;
    /** Period in SECONDS. */
    public final long period;
    /** @hide upstream, public in bytecode. */
    public final long flexTime;

    public PeriodicSync(Account account, String authority, Bundle extras, long periodInSeconds) {
        this.account = account;
        this.authority = authority;
        this.extras = (extras == null) ? new Bundle() : new Bundle(extras);
        this.period = periodInSeconds;
        this.flexTime = 0L;
    }

    public PeriodicSync(PeriodicSync other) {
        this.account = other.account;
        this.authority = other.authority;
        this.extras = new Bundle(other.extras);
        this.period = other.period;
        this.flexTime = other.flexTime;
    }

    public PeriodicSync(Account account, String authority, Bundle extras,
                        long period, long flexTime) {
        this.account = account;
        this.authority = authority;
        this.extras = new Bundle(extras);
        this.period = period;
        this.flexTime = flexTime;
    }

    private PeriodicSync(Parcel in) {
        this.account = in.readParcelable(null, Account.class);
        this.authority = in.readString();
        this.extras = in.readBundle();
        this.period = in.readLong();
        this.flexTime = in.readLong();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(account, flags);
        dest.writeString(authority);
        dest.writeBundle(extras);
        dest.writeLong(period);
        dest.writeLong(flexTime);
    }

    public static final Creator<PeriodicSync> CREATOR = new Creator<PeriodicSync>() {
        @Override public PeriodicSync createFromParcel(Parcel source) {
            return new PeriodicSync(source);
        }
        @Override public PeriodicSync[] newArray(int size) { return new PeriodicSync[size]; }
    };

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PeriodicSync)) return false;
        final PeriodicSync other = (PeriodicSync) o;
        return account.equals(other.account)
                && authority.equals(other.authority)
                && period == other.period
                && syncExtrasEquals(extras, other.extras);
    }

    /** AOSP's own comparison: same key count and, for every key, equal values
     *  by Object.equals. */
    private static boolean syncExtrasEquals(Bundle b1, Bundle b2) {
        if (b1.size() != b2.size()) return false;
        if (b1.isEmpty()) return true;
        for (String key : b1.keySet()) {
            if (!b2.containsKey(key)) return false;
            Object v1 = b1.get(key);
            Object v2 = b2.get(key);
            if (v1 == null ? v2 != null : !v1.equals(v2)) return false;
        }
        return true;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + account.hashCode();
        result = 31 * result + authority.hashCode();
        result = 31 * result + (int) period;
        return result;
    }

    @Override public String toString() {
        return "account: " + account
                + ", authority: " + authority
                + ". period: " + period + "s "
                + ", flex: " + flexTime;
    }
}
