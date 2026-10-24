// android.view.textservice.TextInfo -- a chunk of text handed to a spell
// checker, plus the cookie/sequence pair that lets the caller correlate the
// asynchronous result with the request.
// Source: frameworks/base core/java/android/view/textservice/TextInfo.java
// (android16-release).
package android.view.textservice;

public final class TextInfo implements android.os.Parcelable {
    private final CharSequence mCharSequence;
    private final int mCookie;
    private final int mSequence;

    public TextInfo(String text) { this(text, 0, getStringLengthOrZero(text), 0, 0); }

    public TextInfo(String text, int cookie, int sequence) {
        this(text, 0, getStringLengthOrZero(text), cookie, sequence);
    }

    /** AOSP TextInfo(CharSequence, int start, int end, int cookie, int sequence):
     *  the sub-sequence form, with AOSP's own argument validation. */
    public TextInfo(CharSequence charSequence, int start, int end, int cookie, int sequence) {
        if (TextUtilsIsEmpty(charSequence)) {
            throw new IllegalArgumentException("charSequence is empty");
        }
        // AOSP copies the spans it cares about via SpannableString; a plain
        // subSequence keeps a CharSequence's own spans when it is Spanned and is
        // otherwise identical.
        mCharSequence = charSequence.subSequence(start, end);
        mCookie = cookie;
        mSequence = sequence;
    }

    public TextInfo(android.os.Parcel source) {
        mCharSequence = source.readCharSequence();
        mCookie = source.readInt();
        mSequence = source.readInt();
    }

    /** AOSP getText(): the text as a String, null when it was a non-String
     *  CharSequence carrying spans (getCharSequence() is the modern accessor). */
    public String getText() {
        return mCharSequence == null ? null : mCharSequence.toString();
    }
    public CharSequence getCharSequence() { return mCharSequence; }
    public int getCookie() { return mCookie; }
    public int getSequence() { return mSequence; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeCharSequence(mCharSequence);
        dest.writeInt(mCookie);
        dest.writeInt(mSequence);
    }

    public static final android.os.Parcelable.Creator<TextInfo> CREATOR =
            new android.os.Parcelable.Creator<TextInfo>() {
        @Override public TextInfo createFromParcel(android.os.Parcel source) {
            return new TextInfo(source);
        }
        @Override public TextInfo[] newArray(int size) { return new TextInfo[size]; }
    };

    private static int getStringLengthOrZero(String text) { return text == null ? 0 : text.length(); }
    private static boolean TextUtilsIsEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }
}
