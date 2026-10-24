package android.view.translation;
/** A value to translate. Source: frameworks/base .../view/translation/TranslationRequestValue.java
 *  (android16-release). AOSP declares `implements Parcelable`; the missing
 *  `implements` is invisible to a member-level API diff but fails every type
 *  check the value passes through (it travels inside a TranslationRequest). */
public final class TranslationRequestValue implements android.os.Parcelable {
    private final CharSequence mText;
    private TranslationRequestValue(CharSequence text) { mText = text; }
    public static TranslationRequestValue forText(CharSequence text) {
        return new TranslationRequestValue(text);
    }
    public CharSequence getText() { return mText; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeCharSequence(mText);
    }
    public static final android.os.Parcelable.Creator<TranslationRequestValue> CREATOR =
            new android.os.Parcelable.Creator<TranslationRequestValue>() {
        @Override public TranslationRequestValue createFromParcel(android.os.Parcel source) {
            return new TranslationRequestValue(source.readCharSequence());
        }
        @Override public TranslationRequestValue[] newArray(int size) {
            return new TranslationRequestValue[size];
        }
    };

    @Override public boolean equals(Object o) {
        return o instanceof TranslationRequestValue
                && java.util.Objects.equals(((TranslationRequestValue) o).mText, mText);
    }
    @Override public int hashCode() { return java.util.Objects.hashCode(mText); }
    @Override public String toString() { return "TranslationRequestValue { text= " + mText + " }"; }
}
