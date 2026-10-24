package android.accounts;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Metadata for one account authenticator, as declared by an app's
 * account-authenticator XML.
 *
 * Mac.apk has no AccountManagerService scanning packages for authenticators, so
 * AccountManager.getAuthenticatorTypes() reports none -- but the type itself is
 * pure data and is faithful here, including the deliberately narrow
 * equals/hashCode (AOSP keys authenticators by TYPE alone, which is what makes
 * newKey() usable as a lookup key).
 *
 * Source: frameworks/base/core/java/android/accounts/AuthenticatorDescription.java
 */
public class AuthenticatorDescription implements Parcelable {

    public final String type;
    public final int labelId;
    public final int iconId;
    public final int smallIconId;
    public final int accountPreferencesId;
    public final String packageName;
    public final boolean customTokens;

    public AuthenticatorDescription(String type, String packageName, int labelId, int iconId,
            int smallIconId, int prefId, boolean customTokens) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (packageName == null) throw new IllegalArgumentException("packageName cannot be null");
        this.type = type;
        this.packageName = packageName;
        this.labelId = labelId;
        this.iconId = iconId;
        this.smallIconId = smallIconId;
        this.accountPreferencesId = prefId;
        this.customTokens = customTokens;
    }

    public AuthenticatorDescription(String type, String packageName, int labelId, int iconId,
            int smallIconId, int prefId) {
        this(type, packageName, labelId, iconId, smallIconId, prefId, false);
    }

    /** A description carrying only the type, for use as a map key. */
    public static AuthenticatorDescription newKey(String type) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        return new AuthenticatorDescription(type);
    }

    private AuthenticatorDescription(String type) {
        this.type = type;
        this.packageName = null;
        this.labelId = 0;
        this.iconId = 0;
        this.smallIconId = 0;
        this.accountPreferencesId = 0;
        this.customTokens = false;
    }

    private AuthenticatorDescription(Parcel source) {
        this.type = source.readString();
        this.packageName = source.readString();
        this.labelId = source.readInt();
        this.iconId = source.readInt();
        this.smallIconId = source.readInt();
        this.accountPreferencesId = source.readInt();
        this.customTokens = source.readByte() == 1;
    }

    @Override public int describeContents() { return 0; }

    /** AOSP hashes the TYPE only; do not "improve" this. */
    @Override public int hashCode() { return type.hashCode(); }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AuthenticatorDescription)) return false;
        return type.equals(((AuthenticatorDescription) o).type);
    }

    @Override public String toString() {
        return "AuthenticatorDescription {type=" + type + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeString(packageName);
        dest.writeInt(labelId);
        dest.writeInt(iconId);
        dest.writeInt(smallIconId);
        dest.writeInt(accountPreferencesId);
        dest.writeByte((byte) (customTokens ? 1 : 0));
    }

    public static final Creator<AuthenticatorDescription> CREATOR =
            new Creator<AuthenticatorDescription>() {
        @Override public AuthenticatorDescription createFromParcel(Parcel source) {
            return new AuthenticatorDescription(source);
        }
        @Override public AuthenticatorDescription[] newArray(int size) {
            return new AuthenticatorDescription[size];
        }
    };
}
