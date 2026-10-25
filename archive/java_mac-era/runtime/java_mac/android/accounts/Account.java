// android.accounts.Account -- identifies a single account (name + type) in the
// AccountManager. Apps reference it for sync/backup/sign-in flows. Mac.apk has
// no account system, so AccountManager hands back none, but the class itself
// must exist and round-trip (Parcelable) because app code (e.g. Google
// Calculator's CalculatorApplication.onCreate) loads it during init. Mirrors
// AOSP frameworks/base core/java/android/accounts/Account.java.
package android.accounts;

import android.os.Parcel;
import android.os.Parcelable;

public class Account implements Parcelable {
    public final String name;
    public final String type;

    public Account(String name, String type) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("the name must not be empty: " + name);
        if (type == null || type.isEmpty()) throw new IllegalArgumentException("the type must not be empty: " + type);
        this.name = name;
        this.type = type;
    }

    public Account(Parcel in) {
        this.name = in.readString();
        this.type = in.readString();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account other = (Account) o;
        return name.equals(other.name) && type.equals(other.type);
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override public String toString() { return "Account {name=" + name + ", type=" + type + "}"; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(type);
    }

    public static final Creator<Account> CREATOR = new Creator<Account>() {
        @Override public Account createFromParcel(Parcel source) { return new Account(source); }
        @Override public Account[] newArray(int size) { return new Account[size]; }
    };
}
