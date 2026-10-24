package android.service.chooser;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * API 34 custom action an app adds to the system share sheet via
 * Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS.
 *
 * An immutable value object in AOSP too. Mac.apk shows no share sheet, so an
 * action put in the intent is never rendered -- but the object itself is real,
 * the builder validates its arguments exactly as AOSP does (all three are
 * @NonNull and AOSP requires them), and it parcels, so an app that builds
 * actions and stuffs them into an Intent extra behaves identically up to the
 * point the sheet would have appeared.
 *
 * Source: frameworks/base/core/java/android/service/chooser/ChooserAction.java
 */
public final class ChooserAction implements Parcelable {

    private final Icon mIcon;
    private final CharSequence mLabel;
    private final PendingIntent mAction;

    private ChooserAction(Icon icon, CharSequence label, PendingIntent action) {
        mIcon = icon;
        mLabel = label;
        mAction = action;
    }

    public Icon getIcon() { return mIcon; }

    public CharSequence getLabel() { return mLabel; }

    public PendingIntent getAction() { return mAction; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        mIcon.writeToParcel(dest, flags);
        TextUtils.writeToParcel(mLabel, dest, flags);
        mAction.writeToParcel(dest, flags);
    }

    @Override public String toString() {
        return "ChooserAction[label=" + mLabel + ", action=" + mAction + "]";
    }

    public static final Creator<ChooserAction> CREATOR = new Creator<ChooserAction>() {
        @Override public ChooserAction createFromParcel(Parcel in) {
            return new ChooserAction(
                    Icon.CREATOR.createFromParcel(in),
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in),
                    PendingIntent.CREATOR.createFromParcel(in));
        }
        @Override public ChooserAction[] newArray(int size) {
            return new ChooserAction[size];
        }
    };

    /** All three arguments are required in AOSP; the constructor rejects null
     *  rather than deferring the failure to the share sheet. */
    public static final class Builder {
        private final Icon mIcon;
        private final CharSequence mLabel;
        private final PendingIntent mAction;

        public Builder(Icon icon, CharSequence label, PendingIntent action) {
            if (icon == null) throw new NullPointerException("icon");
            if (label == null) throw new NullPointerException("label");
            if (action == null) throw new NullPointerException("action");
            mIcon = icon;
            mLabel = label;
            mAction = action;
        }

        public ChooserAction build() {
            return new ChooserAction(mIcon, mLabel, mAction);
        }
    }
}
