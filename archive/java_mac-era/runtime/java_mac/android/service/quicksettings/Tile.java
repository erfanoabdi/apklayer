package android.service.quicksettings;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * A quick-settings tile's mutable state.
 *
 * A TileService owns one of these and mutates it, then calls updateTile() to
 * push the new state to the panel. Mac.apk draws no quick-settings panel, so
 * updateTile() has nowhere to push -- but the object itself is pure state, and
 * an app's tile logic (compute the label, flip the state, set the icon) reads
 * its own writes back. That round-trip is real here, so a tile's own unit-test-
 * shaped code and its "refresh" paths behave as on a device.
 *
 * AOSP declares it `implements Parcelable`; that was missing, so a tile handed
 * to anything typed Parcelable (a Bundle, an Intent extra) threw
 * ClassCastException.
 *
 * Constant values transcribed verbatim from /tmp/aosp_api/android16.txt
 * (signature format 2.0): STATE_UNAVAILABLE=0, STATE_INACTIVE=1, STATE_ACTIVE=2.
 *
 * Source: frameworks/base/core/java/android/service/quicksettings/Tile.java
 */
public final class Tile implements Parcelable {

    public static final int STATE_UNAVAILABLE = 0;
    public static final int STATE_INACTIVE = 1;
    public static final int STATE_ACTIVE = 2;

    private int mState = STATE_INACTIVE;
    private Icon mIcon;
    private CharSequence mLabel;
    private CharSequence mSubtitle;
    private CharSequence mContentDescription;
    private CharSequence mStateDescription;
    private PendingIntent mActivityLaunchForClick;

    public Tile() { }

    public int getState() { return mState; }

    public void setState(int state) { mState = state; }

    /** API 24. AOSP holds the Icon the service set; null until it sets one. */
    public Icon getIcon() { return mIcon; }

    public void setIcon(Icon icon) { mIcon = icon; }

    public CharSequence getLabel() { return mLabel; }

    public void setLabel(CharSequence label) { mLabel = label; }

    public CharSequence getSubtitle() { return mSubtitle; }

    public void setSubtitle(CharSequence subtitle) { mSubtitle = subtitle; }

    public CharSequence getContentDescription() { return mContentDescription; }

    public void setContentDescription(CharSequence contentDescription) {
        mContentDescription = contentDescription;
    }

    /** API 30. The spoken state ("On"/"Off") read out separately from the
     *  label; distinct field from the content description in AOSP too. */
    public CharSequence getStateDescription() { return mStateDescription; }

    public void setStateDescription(CharSequence stateDescription) {
        mStateDescription = stateDescription;
    }

    /** API 33. When set, a click launches this PendingIntent instead of
     *  calling TileService.onClick(). */
    public PendingIntent getActivityLaunchForClick() { return mActivityLaunchForClick; }

    public void setActivityLaunchForClick(PendingIntent pendingIntent) {
        mActivityLaunchForClick = pendingIntent;
    }

    /**
     * AOSP pushes the state to the quick-settings panel over the tile binder.
     * There is no panel here, so there is nothing to push -- and nothing is
     * lost either, because every field the panel would have read is already
     * stored above and readable by the service itself.
     */
    public void updateTile() { }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mState);
        writeNullable(dest, mIcon, flags);
        TextUtils.writeToParcel(mLabel, dest, flags);
        TextUtils.writeToParcel(mSubtitle, dest, flags);
        TextUtils.writeToParcel(mContentDescription, dest, flags);
        TextUtils.writeToParcel(mStateDescription, dest, flags);
        writeNullable(dest, mActivityLaunchForClick, flags);
    }

    private static void writeNullable(Parcel dest, Parcelable value, int flags) {
        if (value == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            value.writeToParcel(dest, flags);
        }
    }

    public static final Creator<Tile> CREATOR = new Creator<Tile>() {
        @Override public Tile createFromParcel(Parcel source) {
            Tile tile = new Tile();
            tile.mState = source.readInt();
            tile.mIcon = source.readInt() != 0 ? Icon.CREATOR.createFromParcel(source) : null;
            tile.mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            tile.mSubtitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            tile.mContentDescription =
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            tile.mStateDescription =
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            tile.mActivityLaunchForClick =
                    source.readInt() != 0 ? PendingIntent.CREATOR.createFromParcel(source) : null;
            return tile;
        }
        @Override public Tile[] newArray(int size) { return new Tile[size]; }
    };
}
