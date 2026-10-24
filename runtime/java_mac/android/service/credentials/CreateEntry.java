package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;

/** A create-credential entry a provider offers, backed by a Slice. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/CreateEntry.java */
public final class CreateEntry implements Parcelable {
    private final Slice mSlice;

    public CreateEntry(Slice slice) { mSlice = slice; }

    public Slice getSlice() { return mSlice; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSlice, flags);
    }

    public static final Parcelable.Creator<CreateEntry> CREATOR =
            new Parcelable.Creator<CreateEntry>() {
                @Override public CreateEntry createFromParcel(Parcel in) {
                    return new CreateEntry(in.readTypedObject(Slice.CREATOR));
                }
                @Override public CreateEntry[] newArray(int size) { return new CreateEntry[size]; }
            };
}
