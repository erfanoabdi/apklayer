// android.view.inputmethod.CompletionInfo -- one completion candidate an editor
// offers an IME through InputMethodManager.displayCompletions.
//
// AOSP: core/java/android/view/inputmethod/CompletionInfo.java (android16-release).
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/view/inputmethod/CompletionInfo.java
//
// Was a stub whose constructor DISCARDED its arguments and whose getters
// returned constants, and which did not implement Parcelable. Both halves
// mattered: displayCompletions ships an array of these across a Parcel, and
// Bundle.getParcelable in this runtime returns null for a non-Parcelable value
// that -Xverify:none happily let an app put. Now it stores what it is given and
// round-trips in AOSP's field order.
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public final class CompletionInfo implements Parcelable {
    private final long mId;
    private final int mPosition;
    private final CharSequence mText;
    private final CharSequence mLabel;

    public CompletionInfo(long id, int index, CharSequence text) {
        this(id, index, text, null);
    }

    public CompletionInfo(long id, int index, CharSequence text, CharSequence label) {
        mId = id;
        mPosition = index;
        mText = text;
        mLabel = label;
    }

    private CompletionInfo(Parcel source) {
        mId = source.readLong();
        mPosition = source.readInt();
        mText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
    }

    public long getId() { return mId; }
    public int getPosition() { return mPosition; }
    public CharSequence getText() { return mText; }
    public CharSequence getLabel() { return mLabel; }

    @Override public String toString() {
        return "CompletionInfo{#" + mPosition + " \"" + mText
                + "\" id=" + mId + " label=" + mLabel + "}";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeInt(mPosition);
        TextUtils.writeToParcel(mText, dest, flags);
        TextUtils.writeToParcel(mLabel, dest, flags);
    }

    public static final Parcelable.Creator<CompletionInfo> CREATOR =
            new Parcelable.Creator<CompletionInfo>() {
        @Override public CompletionInfo createFromParcel(Parcel source) {
            return new CompletionInfo(source);
        }
        @Override public CompletionInfo[] newArray(int size) {
            return new CompletionInfo[size];
        }
    };
}
