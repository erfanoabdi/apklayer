package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;

/** A remote-device credential entry, backed by a Slice. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/RemoteEntry.java */
public final class RemoteEntry implements Parcelable {
    private final Slice mSlice;

    public RemoteEntry(Slice slice) { mSlice = slice; }

    public Slice getSlice() { return mSlice; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSlice, flags);
    }

    public static final Parcelable.Creator<RemoteEntry> CREATOR =
            new Parcelable.Creator<RemoteEntry>() {
                @Override public RemoteEntry createFromParcel(Parcel in) {
                    return new RemoteEntry(in.readTypedObject(Slice.CREATOR));
                }
                @Override public RemoteEntry[] newArray(int size) { return new RemoteEntry[size]; }
            };
}
