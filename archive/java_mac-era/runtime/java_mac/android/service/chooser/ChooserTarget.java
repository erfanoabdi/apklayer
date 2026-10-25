// android.service.chooser.ChooserTarget -- one deep-link target an app offers
// to the system share sheet (a specific conversation, say, rather than just
// "open the app").
//
// A REAL value object with no device dependency: an app builds these and
// returns them from a ChooserTargetService. The system share sheet is what
// would consume them, and Mac.apk has none -- but nothing here pretends
// otherwise, because a ChooserTarget makes no claim about being displayed. It
// is a plain description that the app hands back and forgets.
//
// The constructor's RANGE CHECK is copied deliberately: AOSP throws
// IllegalArgumentException for a score outside [0, 1], and an app relying on
// that to catch its own bad input must still get it.
//
// @Deprecated on AOSP (superseded by the ShortcutManager sharing shortcuts
// API), but still referenced by apps in the corpus.
//
// Mirrors core/java/android/service/chooser/ChooserTarget.java
// (android11-release); public surface confirmed against
// frameworks/base/core/api/current.txt.
package android.service.chooser;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

@Deprecated
public final class ChooserTarget implements Parcelable {

    private CharSequence mTitle;
    private Icon mIcon;
    private ComponentName mComponentName;
    private Bundle mIntentExtras;
    private float mScore;

    public ChooserTarget(CharSequence title, Icon icon, float score,
            ComponentName componentName, Bundle intentExtras) {
        mTitle = title;
        mIcon = icon;
        if (score > 1.f || score < 0.f) {
            throw new IllegalArgumentException("Score " + score + " out of range; "
                    + "must be between 0.0f and 1.0f");
        }
        mScore = score;
        mComponentName = componentName;
        mIntentExtras = intentExtras;
    }

    public CharSequence getTitle() { return mTitle; }

    public Icon getIcon() { return mIcon; }

    public float getScore() { return mScore; }

    public ComponentName getComponentName() { return mComponentName; }

    public Bundle getIntentExtras() { return mIntentExtras; }

    @Override public String toString() {
        return "ChooserTarget{" + mComponentName + ", " + mIntentExtras + ", '" + mTitle
                + "', " + mScore + "}";
    }

    @Override public int describeContents() { return 0; }

    // The Icon is not marshalled: android.graphics.drawable.Icon in this tree
    // does not implement Parcelable. A fixed marker slot keeps the read and
    // write sides in step, exactly as in android.telecom.StatusHints.
    // REMOVAL CONDITION: when Icon implements Parcelable, marshal it here.
    // Nothing round-trips a ChooserTarget today -- the only consumer would be
    // the system share sheet, which does not exist here.
    private static final int ICON_ABSENT = 0;

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeCharSequence(mTitle);
        dest.writeInt(ICON_ABSENT);
        dest.writeFloat(mScore);
        dest.writeString(mComponentName != null ? mComponentName.flattenToString() : null);
        dest.writeBundle(mIntentExtras);
    }

    public static final Creator<ChooserTarget> CREATOR = new Creator<ChooserTarget>() {
        @Override public ChooserTarget createFromParcel(Parcel source) {
            CharSequence title = source.readCharSequence();
            source.readInt();
            Icon icon = null;
            float score = source.readFloat();
            String cn = source.readString();
            Bundle extras = source.readBundle();
            return new ChooserTarget(title, icon, score,
                    cn != null ? ComponentName.unflattenFromString(cn) : null, extras);
        }

        @Override public ChooserTarget[] newArray(int size) {
            return new ChooserTarget[size];
        }
    };
}
