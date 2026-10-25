// android.view.translation.TranslationRequest -- a batch of values (or whole
// views) to translate, handed to Translator.translate.
// Source: frameworks/base
// core/java/android/view/translation/TranslationRequest.java (android16-release).
package android.view.translation;

public final class TranslationRequest implements android.os.Parcelable {
    public static final int FLAG_TRANSLATION_RESULT = 0x1;
    public static final int FLAG_DICTIONARY_RESULT = 0x2;
    public static final int FLAG_TRANSLITERATION_RESULT = 0x4;
    public static final int FLAG_PARTIAL_RESPONSES = 0x8;

    private final int mFlags;
    private final java.util.List<TranslationRequestValue> mTranslationRequestValues;
    private final java.util.List<ViewTranslationRequest> mViewTranslationRequests;

    private TranslationRequest(Builder b) {
        mFlags = b.mFlags;
        mTranslationRequestValues = java.util.Collections.unmodifiableList(
                new java.util.ArrayList<TranslationRequestValue>(b.mTranslationRequestValues));
        mViewTranslationRequests = java.util.Collections.unmodifiableList(
                new java.util.ArrayList<ViewTranslationRequest>(b.mViewTranslationRequests));
    }

    public int getFlags() { return mFlags; }
    public java.util.List<TranslationRequestValue> getTranslationRequestValues() {
        return mTranslationRequestValues;
    }
    public java.util.List<ViewTranslationRequest> getViewTranslationRequests() {
        return mViewTranslationRequests;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mFlags);
        dest.writeTypedList(mTranslationRequestValues);
        dest.writeTypedList(mViewTranslationRequests);
    }
    public static final android.os.Parcelable.Creator<TranslationRequest> CREATOR =
            new android.os.Parcelable.Creator<TranslationRequest>() {
        @Override public TranslationRequest createFromParcel(android.os.Parcel in) {
            Builder b = new Builder().setFlags(in.readInt());
            java.util.List<TranslationRequestValue> values =
                    in.createTypedArrayList(TranslationRequestValue.CREATOR);
            if (values != null) b.setTranslationRequestValues(values);
            java.util.List<ViewTranslationRequest> views =
                    in.createTypedArrayList(ViewTranslationRequest.CREATOR);
            if (views != null) b.setViewTranslationRequests(views);
            return b.build();
        }
        @Override public TranslationRequest[] newArray(int size) {
            return new TranslationRequest[size];
        }
    };

    public static final class Builder {
        private int mFlags;
        private java.util.List<TranslationRequestValue> mTranslationRequestValues =
                java.util.Collections.emptyList();
        private java.util.List<ViewTranslationRequest> mViewTranslationRequests =
                java.util.Collections.emptyList();

        public Builder() {}
        public Builder setFlags(int value) { mFlags = value; return this; }
        public Builder setTranslationRequestValues(java.util.List<TranslationRequestValue> v) {
            mTranslationRequestValues = java.util.Objects.requireNonNull(v); return this;
        }
        public Builder setViewTranslationRequests(java.util.List<ViewTranslationRequest> v) {
            mViewTranslationRequests = java.util.Objects.requireNonNull(v); return this;
        }
        public TranslationRequest build() { return new TranslationRequest(this); }
    }
}
