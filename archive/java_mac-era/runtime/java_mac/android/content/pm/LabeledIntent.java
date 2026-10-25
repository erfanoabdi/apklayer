package android.content.pm;

import android.content.Intent;

/** android.content.pm.LabeledIntent -- an Intent that carries its own label and
 *  icon, drawn from a named source package's resources. The chooser/share-sheet
 *  primitive: Intent.createChooser + EXTRA_INITIAL_INTENTS hands these to the
 *  resolver so an app can offer extra rows with their own text and artwork.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/LabeledIntent.java
 *  (android16-release) field for field and method for method. */
public class LabeledIntent extends Intent {
    // AOSP keeps these MUTABLE because readFromParcel assigns them.
    private String mSourcePackage;
    private int mLabelRes;
    private CharSequence mNonLocalizedLabel;
    private int mIcon;

    public LabeledIntent(Intent origin, String sourcePackage, int labelRes, int icon) {
        super(origin); mSourcePackage = sourcePackage; mLabelRes = labelRes; mIcon = icon;
    }
    public LabeledIntent(Intent origin, String sourcePackage, CharSequence nonLocalizedLabel, int icon) {
        super(origin); mSourcePackage = sourcePackage; mNonLocalizedLabel = nonLocalizedLabel; mLabelRes = 0; mIcon = icon;
    }
    public LabeledIntent(String sourcePackage, int labelRes, int icon) {
        mSourcePackage = sourcePackage; mLabelRes = labelRes; mIcon = icon;
    }
    /** AOSP LabeledIntent(String, CharSequence, int): "Create a labeled intent
     *  from scratch, given an explicit label." Missing until v1.1.0 -- the
     *  (String,int,int) form has the same ARITY, so a caller that passed a
     *  CharSequence label linked against a method that does not exist. */
    public LabeledIntent(String sourcePackage, CharSequence nonLocalizedLabel, int icon) {
        mSourcePackage = sourcePackage; mNonLocalizedLabel = nonLocalizedLabel;
        mLabelRes = 0; mIcon = icon;
    }

    public String getSourcePackage() { return mSourcePackage; }
    public int getLabelResource() { return mLabelRes; }
    public CharSequence getNonLocalizedLabel() { return mNonLocalizedLabel; }
    public int getIconResource() { return mIcon; }

    /** AOSP LabeledIntent.loadLabel:132-143 verbatim, including the NULL
     *  return: "If the object does not have a label, null will be returned, in
     *  which case you will probably want to load the label from the underlying
     *  resolved info for the Intent." So null is the documented answer a caller
     *  branches on, not a failure. */
    public CharSequence loadLabel(PackageManager pm) {
        if (mNonLocalizedLabel != null) return mNonLocalizedLabel;
        if (mLabelRes != 0 && mSourcePackage != null && pm != null) {
            CharSequence label = pm.getText(mSourcePackage, mLabelRes, null);
            if (label != null) return label;
        }
        return null;
    }

    /** AOSP LabeledIntent.loadIcon:149-157 verbatim -- same documented null. */
    public android.graphics.drawable.Drawable loadIcon(PackageManager pm) {
        if (mIcon != 0 && mSourcePackage != null && pm != null) {
            android.graphics.drawable.Drawable icon = pm.getDrawable(mSourcePackage, mIcon, null);
            if (icon != null) return icon;
        }
        return null;
    }

    /** AOSP LabeledIntent.writeToParcel:159-165: the Intent state first, then
     *  the four label/icon fields. */
    @Override public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeString(mSourcePackage);
        dest.writeInt(mLabelRes);
        android.text.TextUtils.writeToParcel(mNonLocalizedLabel, dest, parcelableFlags);
        dest.writeInt(mIcon);
    }

    protected LabeledIntent(android.os.Parcel in) { readFromParcel(in); }

    @Override public void readFromParcel(android.os.Parcel in) {
        super.readFromParcel(in);
        mSourcePackage = in.readString();
        mLabelRes = in.readInt();
        mNonLocalizedLabel =
            android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mIcon = in.readInt();
    }

    /** AOSP declares its OWN Creator here. Without it `LabeledIntent.CREATOR`
     *  resolves to the inherited Intent.CREATOR, which rebuilds a plain Intent
     *  and silently drops the label/icon -- a chooser row would lose exactly
     *  the thing this class exists to carry. */
    public static final android.os.Parcelable.Creator<LabeledIntent> CREATOR =
            new android.os.Parcelable.Creator<LabeledIntent>() {
        @Override public LabeledIntent createFromParcel(android.os.Parcel source) {
            return new LabeledIntent(source);
        }
        @Override public LabeledIntent[] newArray(int size) {
            return new LabeledIntent[size];
        }
    };
}
