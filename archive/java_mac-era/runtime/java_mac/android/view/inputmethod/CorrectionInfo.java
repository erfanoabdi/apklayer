package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Describes an autocorrection the IME applied: where it happened, what the user
 * typed, and what it was replaced with. Editors use it to show the
 * "reverted to X" affordance.
 *
 * Source: frameworks/base/core/java/android/view/inputmethod/CorrectionInfo.java
 */
public final class CorrectionInfo implements Parcelable {

    private final int mOffset;
    private final CharSequence mOldText;
    private final CharSequence mNewText;

    public CorrectionInfo(int offset, CharSequence oldText, CharSequence newText) {
        mOffset = offset;
        mOldText = oldText;
        mNewText = newText;
    }

    public int getOffset() { return mOffset; }

    public CharSequence getOldText() { return mOldText; }

    public CharSequence getNewText() { return mNewText; }

    @Override public String toString() {
        return "CorrectionInfo{#" + mOffset + " \"" + mOldText + "\" -> \"" + mNewText + "\"}";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOffset);
        android.text.TextUtils.writeToParcel(mOldText, dest, flags);
        android.text.TextUtils.writeToParcel(mNewText, dest, flags);
    }

    public static final Creator<CorrectionInfo> CREATOR = new Creator<CorrectionInfo>() {
        @Override public CorrectionInfo createFromParcel(Parcel source) {
            return new CorrectionInfo(source.readInt(),
                    android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source),
                    android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source));
        }
        @Override public CorrectionInfo[] newArray(int size) { return new CorrectionInfo[size]; }
    };
}
