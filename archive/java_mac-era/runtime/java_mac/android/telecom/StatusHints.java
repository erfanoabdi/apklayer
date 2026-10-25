// android.telecom.StatusHints -- the label/icon/extras a Connection shows in
// the in-call UI (for example "Calling over Wi-Fi").
//
// A REAL implementation: a plain immutable value object with no device
// dependency. A self-managed calling app sets it on its own Connection and can
// read it straight back, which works here exactly as on Android. Only the
// DISPLAY of it needs a system in-call UI, and there is none to notify.
//
// Mirrors telecomm/java/android/telecom/StatusHints.java; public surface
// confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class StatusHints implements Parcelable {

    private final CharSequence mLabel;
    private final Icon mIcon;
    private final Bundle mExtras;

    public StatusHints(CharSequence label, Icon icon, Bundle extras) {
        mLabel = label;
        mIcon = icon;
        mExtras = extras;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    @Override public int describeContents() {
        return 0;
    }

    // AOSP marshals the Icon here via writeParcelable. android.graphics.
    // drawable.Icon in THIS tree is a plain class that does not implement
    // Parcelable, so it cannot be written that way and the icon does not
    // survive a parcel round trip -- readers get null back for it.
    //
    // The absent-marker below keeps the stream SHAPE identical (one int slot
    // where the icon would be) so the read side stays in sync with the write
    // side; the alternative, omitting the field, would silently desync any
    // future writer that does marshal it.
    //
    // REMOVAL CONDITION: when android/graphics/drawable/Icon.java implements
    // Parcelable, replace the marker with writeParcelable(mIcon, flags) /
    // readParcelable on the read side. That file is owned by another lane, so
    // this is reported rather than fixed here.
    //
    // Cost of the gap is nil in practice: StatusHints is only reachable from
    // telecom, and it is only ever PARCELLED when it crosses to the Telecom
    // service -- which does not exist here, so nothing round-trips it.
    private static final int ICON_ABSENT = 0;

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeCharSequence(mLabel);
        out.writeInt(ICON_ABSENT);
        out.writeBundle(mExtras);
    }

    public static final Creator<StatusHints> CREATOR = new Creator<StatusHints>() {
        @Override public StatusHints createFromParcel(Parcel in) {
            CharSequence label = in.readCharSequence();
            in.readInt();
            Icon icon = null;
            Bundle extras = in.readBundle();
            return new StatusHints(label, icon, extras);
        }

        @Override public StatusHints[] newArray(int size) {
            return new StatusHints[size];
        }
    };

    @Override public boolean equals(Object other) {
        if (other != null && other instanceof StatusHints) {
            StatusHints otherHints = (StatusHints) other;
            return Objects.equals(otherHints.getLabel(), getLabel())
                    && Objects.equals(otherHints.getIcon(), getIcon())
                    && Objects.equals(otherHints.getExtras(), getExtras());
        }
        return false;
    }

    @Override public int hashCode() {
        return Objects.hash(mLabel, mIcon, mExtras);
    }
}
