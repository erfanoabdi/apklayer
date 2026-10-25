package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;

/** A credential-provider entry backed by a Slice. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/Action.java */
public final class Action implements Parcelable {
    private final Slice mSlice;

    public Action(Slice slice) { mSlice = slice; }

    public Slice getSlice() { return mSlice; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSlice, flags);
    }

    public static final Parcelable.Creator<Action> CREATOR =
            new Parcelable.Creator<Action>() {
                @Override public Action createFromParcel(Parcel in) {
                    return new Action(in.readTypedObject(Slice.CREATOR));
                }
                @Override public Action[] newArray(int size) { return new Action[size]; }
            };
}
