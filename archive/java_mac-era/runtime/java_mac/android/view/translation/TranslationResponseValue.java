package android.view.translation;
/** One field's translation result. Mac.apk performs no on-device translation
 *  (TranslationManager reports no capabilities), so a value is only ever
 *  constructed empty (STATUS_ERROR) by this framework -- but an app can build
 *  one with the Builder and everything it sets comes back.
 *  Source: frameworks/base .../view/translation/TranslationResponseValue.java
 *  (android16-release). */
public final class TranslationResponseValue implements android.os.Parcelable {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_ERROR = 1;
    /** AOSP: the extras key under which a dictionary definition is returned. */
    public static final String EXTRA_DEFINITIONS = "android.view.translation.extra.DEFINITIONS";

    private final int mStatusCode;
    private final CharSequence mText;
    private final CharSequence mTransliteration;
    private final android.os.Bundle mExtras;

    private TranslationResponseValue(int statusCode, CharSequence text,
            CharSequence transliteration, android.os.Bundle extras) {
        mStatusCode = statusCode;
        mText = text;
        mTransliteration = transliteration;
        mExtras = extras;
    }
    /** @hide -- kept because this tree already shipped these two entry points. */
    TranslationResponseValue(int status, CharSequence text) {
        this(status, text, null, android.os.Bundle.EMPTY);
    }
    public TranslationResponseValue() { this(STATUS_ERROR, null); }

    public int getStatusCode() { return mStatusCode; }
    public CharSequence getText() { return mText; }
    /** AOSP getTransliteration() (API 31): the phonetic rendering, when the
     *  request asked for FLAG_TRANSLITERATION. */
    public CharSequence getTransliteration() { return mTransliteration; }
    public android.os.Bundle getExtras() {
        return mExtras != null ? mExtras : android.os.Bundle.EMPTY;
    }
    public static TranslationResponseValue forError() {
        return new TranslationResponseValue(STATUS_ERROR, null);
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mStatusCode);
        dest.writeCharSequence(mText);
        dest.writeCharSequence(mTransliteration);
        dest.writeBundle(mExtras);
    }
    public static final android.os.Parcelable.Creator<TranslationResponseValue> CREATOR =
            new android.os.Parcelable.Creator<TranslationResponseValue>() {
        @Override public TranslationResponseValue createFromParcel(android.os.Parcel source) {
            return new TranslationResponseValue(source.readInt(), source.readCharSequence(),
                    source.readCharSequence(), source.readBundle());
        }
        @Override public TranslationResponseValue[] newArray(int size) {
            return new TranslationResponseValue[size];
        }
    };

    /** AOSP TranslationResponseValue.Builder(int statusCode). */
    public static final class Builder {
        private final int mStatusCode;
        private CharSequence mText;
        private CharSequence mTransliteration;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

        public Builder(int statusCode) { mStatusCode = statusCode; }
        public Builder setText(CharSequence value) {
            mText = java.util.Objects.requireNonNull(value); return this;
        }
        public Builder setTransliteration(CharSequence value) {
            mTransliteration = java.util.Objects.requireNonNull(value); return this;
        }
        public Builder setExtras(android.os.Bundle value) {
            mExtras = java.util.Objects.requireNonNull(value); return this;
        }
        public TranslationResponseValue build() {
            return new TranslationResponseValue(mStatusCode, mText, mTransliteration, mExtras);
        }
    }
}
