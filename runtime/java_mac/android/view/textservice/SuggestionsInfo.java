// android.view.textservice.SuggestionsInfo -- one spell-check result: the
// attribute flags plus the candidate corrections.
// Source: frameworks/base core/java/android/view/textservice/SuggestionsInfo.java
// (android16-release).
package android.view.textservice;

public final class SuggestionsInfo implements android.os.Parcelable {
    /** The word is in the dictionary, i.e. correctly spelled. */
    public static final int RESULT_ATTR_IN_THE_DICTIONARY = 0x0001;
    public static final int RESULT_ATTR_LOOKS_LIKE_TYPO = 0x0002;
    public static final int RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS = 0x0004;
    public static final int RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR = 0x0008;
    public static final int RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS = 0x0010;

    private static final String[] EMPTY = new String[0];

    private final int mSuggestionsAttributes;
    private final String[] mSuggestions;
    private final boolean mSuggestionsAvailable;
    private int mCookie;
    private int mSequence;

    public SuggestionsInfo(int suggestionsAttributes, String[] suggestions) {
        this(suggestionsAttributes, suggestions, 0, 0);
    }

    public SuggestionsInfo(int suggestionsAttributes, String[] suggestions, int cookie,
            int sequence) {
        if (suggestions == null) {
            mSuggestions = EMPTY;
            mSuggestionsAvailable = false;
        } else {
            mSuggestions = suggestions;
            mSuggestionsAvailable = true;
        }
        mSuggestionsAttributes = suggestionsAttributes;
        mCookie = cookie;
        mSequence = sequence;
    }

    public SuggestionsInfo(android.os.Parcel source) {
        mSuggestionsAttributes = source.readInt();
        mSuggestions = source.createStringArray();
        mCookie = source.readInt();
        mSequence = source.readInt();
        mSuggestionsAvailable = source.readInt() == 1;
    }

    /** AOSP setCookieAndSequence(int, int): re-stamp the correlation pair. */
    public void setCookieAndSequence(int cookie, int sequence) {
        mCookie = cookie;
        mSequence = sequence;
    }

    public int getCookie() { return mCookie; }
    public int getSequence() { return mSequence; }
    public int getSuggestionsAttributes() { return mSuggestionsAttributes; }
    /** AOSP: -1 (not 0) when the checker reported no suggestion LIST at all,
     *  which is a different state from an empty list. */
    public int getSuggestionsCount() { return mSuggestionsAvailable ? mSuggestions.length : -1; }
    public String getSuggestionAt(int i) { return mSuggestions[i]; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mSuggestionsAttributes);
        dest.writeStringArray(mSuggestions);
        dest.writeInt(mCookie);
        dest.writeInt(mSequence);
        dest.writeInt(mSuggestionsAvailable ? 1 : 0);
    }

    public static final android.os.Parcelable.Creator<SuggestionsInfo> CREATOR =
            new android.os.Parcelable.Creator<SuggestionsInfo>() {
        @Override public SuggestionsInfo createFromParcel(android.os.Parcel source) {
            return new SuggestionsInfo(source);
        }
        @Override public SuggestionsInfo[] newArray(int size) { return new SuggestionsInfo[size]; }
    };
}
