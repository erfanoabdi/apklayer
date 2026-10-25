package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** A retrieved credential (type + opaque data Bundle). API 34.
 *  Source: frameworks/base/core/java/android/credentials/Credential.java */
public final class Credential implements Parcelable {
    /** The type string for a username/password credential. */
    public static final String TYPE_PASSWORD_CREDENTIAL =
            "android.credentials.TYPE_PASSWORD_CREDENTIAL";

    private final String mType;
    private final Bundle mData;

    public Credential(String type, Bundle data) { mType = type; mData = data; }

    public String getType() { return mType; }
    public Bundle getData() { return mData; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mData);
    }

    public static final Parcelable.Creator<Credential> CREATOR =
            new Parcelable.Creator<Credential>() {
                @Override public Credential createFromParcel(Parcel in) {
                    return new Credential(in.readString(), in.readBundle());
                }
                @Override public Credential[] newArray(int size) {
                    return new Credential[size];
                }
            };
}
