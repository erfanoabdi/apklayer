// android.view.textservice.SentenceSuggestionsInfo -- the per-word results for
// one sentence, each with its offset and length inside the sentence.
// Source: frameworks/base
// core/java/android/view/textservice/SentenceSuggestionsInfo.java (android16-release).
package android.view.textservice;

public final class SentenceSuggestionsInfo implements android.os.Parcelable {
    private final int mSuggestionsCount;
    private final SuggestionsInfo[] mSuggestionsInfos;
    private final int[] mOffsets;
    private final int[] mLengths;

    public SentenceSuggestionsInfo(SuggestionsInfo[] suggestionsInfos, int[] offsets,
            int[] lengths) {
        if (suggestionsInfos == null || offsets == null || lengths == null) {
            throw new NullPointerException();
        }
        if (suggestionsInfos.length != offsets.length
                || suggestionsInfos.length != lengths.length) {
            throw new IllegalArgumentException();
        }
        mSuggestionsCount = suggestionsInfos.length;
        mSuggestionsInfos = new SuggestionsInfo[mSuggestionsCount];
        System.arraycopy(suggestionsInfos, 0, mSuggestionsInfos, 0, mSuggestionsCount);
        mOffsets = new int[mSuggestionsCount];
        System.arraycopy(offsets, 0, mOffsets, 0, mSuggestionsCount);
        mLengths = new int[mSuggestionsCount];
        System.arraycopy(lengths, 0, mLengths, 0, mSuggestionsCount);
    }

    public SentenceSuggestionsInfo(android.os.Parcel source) {
        mSuggestionsCount = source.readInt();
        mSuggestionsInfos = new SuggestionsInfo[mSuggestionsCount];
        source.readTypedArray(mSuggestionsInfos, SuggestionsInfo.CREATOR);
        mOffsets = new int[mSuggestionsCount];
        source.readIntArray(mOffsets);
        mLengths = new int[mSuggestionsCount];
        source.readIntArray(mLengths);
    }

    public int getSuggestionsCount() { return mSuggestionsCount; }
    /** AOSP: null when the index is out of range, rather than throwing. */
    public SuggestionsInfo getSuggestionsInfoAt(int i) {
        return (i >= 0 && i < mSuggestionsCount) ? mSuggestionsInfos[i] : null;
    }
    public int getOffsetAt(int i) { return (i >= 0 && i < mSuggestionsCount) ? mOffsets[i] : -1; }
    public int getLengthAt(int i) { return (i >= 0 && i < mSuggestionsCount) ? mLengths[i] : -1; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mSuggestionsCount);
        dest.writeTypedArray(mSuggestionsInfos, 0);
        dest.writeIntArray(mOffsets);
        dest.writeIntArray(mLengths);
    }

    public static final android.os.Parcelable.Creator<SentenceSuggestionsInfo> CREATOR =
            new android.os.Parcelable.Creator<SentenceSuggestionsInfo>() {
        @Override public SentenceSuggestionsInfo createFromParcel(android.os.Parcel source) {
            return new SentenceSuggestionsInfo(source);
        }
        @Override public SentenceSuggestionsInfo[] newArray(int size) {
            return new SentenceSuggestionsInfo[size];
        }
    };
}
