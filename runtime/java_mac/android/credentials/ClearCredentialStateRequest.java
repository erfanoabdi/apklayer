package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** A request to clear the app's credential-selection state. API 34.
 *  Source: frameworks/base/core/java/android/credentials/ClearCredentialStateRequest.java
 *
 *  Parcelable on AOSP because the request crosses the binder to the credential
 *  manager service. Mac.apk has no such service, but the interface and CREATOR
 *  must exist: apps put these objects in Bundles / Intents and a missing
 *  CREATOR is an unrecoverable BadParcelableException at read time. */
public final class ClearCredentialStateRequest implements Parcelable {
    private final Bundle mData;

    public ClearCredentialStateRequest(Bundle data) { mData = data; }

    public Bundle getData() { return mData; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(mData);
    }

    public static final Parcelable.Creator<ClearCredentialStateRequest> CREATOR =
            new Parcelable.Creator<ClearCredentialStateRequest>() {
                @Override public ClearCredentialStateRequest createFromParcel(Parcel in) {
                    return new ClearCredentialStateRequest(in.readBundle());
                }
                @Override public ClearCredentialStateRequest[] newArray(int size) {
                    return new ClearCredentialStateRequest[size];
                }
            };
}
