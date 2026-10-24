package android.service.controls.templates;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * The checked state + accessibility description of the button inside a
 * ToggleTemplate / ToggleRangeTemplate.
 *
 * Pure value object. See the package note on android.service.controls.DeviceTypes.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/ControlButton.java
 */
public final class ControlButton implements Parcelable {

    private final boolean mChecked;
    private final CharSequence mActionDescription;

    public ControlButton(boolean checked, CharSequence actionDescription) {
        if (actionDescription == null) throw new NullPointerException("actionDescription");
        mChecked = checked;
        mActionDescription = actionDescription;
    }

    public boolean isChecked() { return mChecked; }

    public CharSequence getActionDescription() { return mActionDescription; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (mChecked ? 1 : 0));
        TextUtils.writeToParcel(mActionDescription, dest, flags);
    }

    public static final Creator<ControlButton> CREATOR = new Creator<ControlButton>() {
        @Override public ControlButton createFromParcel(Parcel source) {
            boolean checked = source.readByte() != 0;
            CharSequence desc = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            return new ControlButton(checked, desc == null ? "" : desc);
        }
        @Override public ControlButton[] newArray(int size) { return new ControlButton[size]; }
    };
}
