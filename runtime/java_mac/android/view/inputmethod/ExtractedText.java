// android.view.inputmethod.ExtractedText -- the block of editor text an IME
// asked for with InputConnection.getExtractedText, plus where the selection
// sits inside it.
//
// AOSP: core/java/android/view/inputmethod/ExtractedText.java (android16-release).
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/view/inputmethod/ExtractedText.java
//
// This was a plain field bag with no `implements Parcelable`. That is not a
// harmless omission in this runtime: Bundle.getParcelable ends with
// `return (v instanceof Parcelable) ? (T) v : null;` while the matching PUT is
// unchecked under VM-wide -Xverify:none, so an app that put one of these in a
// Bundle got it back as NULL with no exception anywhere. The parcel format
// below is AOSP's field order verbatim so a value round-trips identically.
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public class ExtractedText implements Parcelable {
    public CharSequence text;
    public int startOffset;
    public int partialStartOffset;
    public int partialEndOffset;
    public int selectionStart;
    public int selectionEnd;
    public int flags;
    /** AOSP API 33+: the editor's hint, so an IME extract-mode UI can show it
     *  when the field is empty. Part of the parcel format, hence present. */
    public CharSequence hint;

    public static final int FLAG_SINGLE_LINE = 0x0001;
    public static final int FLAG_SELECTING   = 0x0002;

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        TextUtils.writeToParcel(text, dest, flags);
        dest.writeInt(startOffset);
        dest.writeInt(partialStartOffset);
        dest.writeInt(partialEndOffset);
        dest.writeInt(selectionStart);
        dest.writeInt(selectionEnd);
        dest.writeInt(this.flags);
        TextUtils.writeToParcel(hint, dest, flags);
    }

    public static final Parcelable.Creator<ExtractedText> CREATOR =
            new Parcelable.Creator<ExtractedText>() {
        @Override public ExtractedText createFromParcel(Parcel source) {
            ExtractedText res = new ExtractedText();
            res.text = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.startOffset = source.readInt();
            res.partialStartOffset = source.readInt();
            res.partialEndOffset = source.readInt();
            res.selectionStart = source.readInt();
            res.selectionEnd = source.readInt();
            res.flags = source.readInt();
            res.hint = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            return res;
        }
        @Override public ExtractedText[] newArray(int size) {
            return new ExtractedText[size];
        }
    };
}
