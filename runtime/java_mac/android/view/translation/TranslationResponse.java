// android.view.translation.TranslationResponse -- what a Translator hands back:
// a status plus the per-index results.
// Source: frameworks/base
// core/java/android/view/translation/TranslationResponse.java (android16-release).
//
// Mac.apk ships no on-device translator, so no response is ever produced by the
// framework -- but an app (or a test) can build one and read it back.
package android.view.translation;

public final class TranslationResponse implements android.os.Parcelable {
    public static final int TRANSLATION_STATUS_SUCCESS = 0;
    public static final int TRANSLATION_STATUS_UNKNOWN_ERROR = 1;
    public static final int TRANSLATION_STATUS_CONTEXT_UNSUPPORTED = 2;

    private final int mTranslationStatus;
    private final android.util.SparseArray<TranslationResponseValue> mTranslationResponseValues;
    private final android.util.SparseArray<ViewTranslationResponse> mViewTranslationResponses;
    private final boolean mFinalResponse;

    private TranslationResponse(Builder b) {
        mTranslationStatus = b.mTranslationStatus;
        mTranslationResponseValues = b.mTranslationResponseValues;
        mViewTranslationResponses = b.mViewTranslationResponses;
        mFinalResponse = b.mFinalResponse;
    }

    public int getTranslationStatus() { return mTranslationStatus; }
    public android.util.SparseArray<TranslationResponseValue> getTranslationResponseValues() {
        return mTranslationResponseValues;
    }
    public android.util.SparseArray<ViewTranslationResponse> getViewTranslationResponses() {
        return mViewTranslationResponses;
    }
    /** AOSP isFinalResponse(): false while more partial responses are coming
     *  (FLAG_PARTIAL_RESPONSES). */
    public boolean isFinalResponse() { return mFinalResponse; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mTranslationStatus);
        dest.writeBoolean(mFinalResponse);
        writeSparse(dest, mTranslationResponseValues, flags);
        writeSparse(dest, mViewTranslationResponses, flags);
    }
    private static <T extends android.os.Parcelable> void writeSparse(android.os.Parcel dest,
            android.util.SparseArray<T> array, int flags) {
        final int n = array == null ? 0 : array.size();
        dest.writeInt(n);
        for (int i = 0; i < n; i++) {
            dest.writeInt(array.keyAt(i));
            dest.writeParcelable(array.valueAt(i), flags);
        }
    }

    public static final android.os.Parcelable.Creator<TranslationResponse> CREATOR =
            new android.os.Parcelable.Creator<TranslationResponse>() {
        @Override public TranslationResponse createFromParcel(android.os.Parcel in) {
            Builder b = new Builder(in.readInt());
            b.setFinalResponse(in.readBoolean());
            int n = in.readInt();
            ClassLoader vcl = TranslationResponseValue.class.getClassLoader();
            for (int i = 0; i < n; i++) {
                b.setTranslationResponseValue(in.readInt(),
                        (TranslationResponseValue) in.readParcelable(vcl));
            }
            n = in.readInt();
            ClassLoader wcl = ViewTranslationResponse.class.getClassLoader();
            for (int i = 0; i < n; i++) {
                b.setViewTranslationResponse(in.readInt(),
                        (ViewTranslationResponse) in.readParcelable(wcl));
            }
            return b.build();
        }
        @Override public TranslationResponse[] newArray(int size) {
            return new TranslationResponse[size];
        }
    };

    public static final class Builder {
        private final int mTranslationStatus;
        private android.util.SparseArray<TranslationResponseValue> mTranslationResponseValues =
                new android.util.SparseArray<TranslationResponseValue>();
        private android.util.SparseArray<ViewTranslationResponse> mViewTranslationResponses =
                new android.util.SparseArray<ViewTranslationResponse>();
        private boolean mFinalResponse = true;

        public Builder(int translationStatus) { mTranslationStatus = translationStatus; }

        public Builder setTranslationResponseValues(
                android.util.SparseArray<TranslationResponseValue> values) {
            mTranslationResponseValues = java.util.Objects.requireNonNull(values); return this;
        }
        public Builder setViewTranslationResponses(
                android.util.SparseArray<ViewTranslationResponse> responses) {
            mViewTranslationResponses = java.util.Objects.requireNonNull(responses); return this;
        }
        public Builder setTranslationResponseValue(int index, TranslationResponseValue value) {
            mTranslationResponseValues.put(index, java.util.Objects.requireNonNull(value));
            return this;
        }
        public Builder setViewTranslationResponse(int index, ViewTranslationResponse response) {
            mViewTranslationResponses.put(index, java.util.Objects.requireNonNull(response));
            return this;
        }
        public Builder setFinalResponse(boolean finalResponse) {
            mFinalResponse = finalResponse; return this;
        }
        public TranslationResponse build() { return new TranslationResponse(this); }
    }
}
