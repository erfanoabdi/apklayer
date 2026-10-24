// android.view.inputmethod.SurroundingText -- AOSP value object returned by
// InputConnection.getSurroundingText (API 31): the text around the cursor plus
// the selection range and the offset of this sub-text within the full editor.
// AOSP:
//   frameworks/base/core/java/android/view/inputmethod/SurroundingText.java
//   public final class SurroundingText implements Parcelable
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

public final class SurroundingText implements Parcelable {
    private final CharSequence mText;
    private final int mSelectionStart;
    private final int mSelectionEnd;
    private final int mOffset;

    public SurroundingText(CharSequence text, int selectionStart, int selectionEnd,
                           int offset) {
        mText = text;
        mSelectionStart = selectionStart;
        mSelectionEnd = selectionEnd;
        mOffset = offset;
    }

    public CharSequence getText() { return mText; }
    public int getSelectionStart() { return mSelectionStart; }
    public int getSelectionEnd() { return mSelectionEnd; }
    public int getOffset() { return mOffset; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        // No IME IPC in Mac.apk; the InputConnection lives in-process. No-op.
    }

    public static final Parcelable.Creator<SurroundingText> CREATOR =
        new Parcelable.Creator<SurroundingText>() {
            @Override public SurroundingText createFromParcel(Parcel in) {
                return new SurroundingText("", 0, 0, -1);
            }
            @Override public SurroundingText[] newArray(int size) {
                return new SurroundingText[size];
            }
        };
}
