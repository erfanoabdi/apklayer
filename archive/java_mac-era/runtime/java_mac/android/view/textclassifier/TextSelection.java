package android.view.textclassifier;

/**
 * A suggested text selection (smart-select). No on-device classifier, so
 * TextClassifier.NO_OP returns the caller's own indices with no attached
 * classification -- AOSP's no-classifier result. Everything an app SETS on the
 * builder still comes back out.
 * Source: frameworks/base/core/java/android/view/textclassifier/TextSelection.java
 * (android16-release). AOSP declares `implements Parcelable`.
 */
public final class TextSelection implements android.os.Parcelable {

    private final int mStart, mEnd;
    private final java.util.List<String> mEntities;
    private final java.util.Map<String, Float> mEntityScores;
    private final String mId;
    private final TextClassification mClassification;
    private final android.os.Bundle mExtras;

    private TextSelection(Builder b) {
        mStart = b.mStart;
        mEnd = b.mEnd;
        mEntityScores = b.mEntityScores;
        mEntities = new java.util.ArrayList<String>(b.mEntityScores.keySet());
        // Descending confidence, matching AOSP: getEntity(0) is the best guess.
        java.util.Collections.sort(mEntities, (x, y) ->
                Float.compare(mEntityScores.get(y), mEntityScores.get(x)));
        mId = b.mId;
        mClassification = b.mClassification;
        mExtras = b.mExtras;
    }

    public int getSelectionStartIndex() { return mStart; }
    public int getSelectionEndIndex() { return mEnd; }
    public int getEntityCount() { return mEntities.size(); }
    public String getEntity(int index) { return mEntities.get(index); }
    public float getConfidenceScore(String entity) {
        Float f = mEntityScores.get(entity);
        return f == null ? 0f : f.floatValue();
    }
    /** AOSP getTextClassification(): the classification of the selected text
     *  when the request asked for it, null otherwise. */
    public TextClassification getTextClassification() { return mClassification; }
    public String getId() { return mId; }
    public android.os.Bundle getExtras() { return mExtras; }

    @Override public String toString() {
        return "TextSelection{start=" + mStart + ", end=" + mEnd
                + ", entities=" + mEntities + ", id=" + mId + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mStart);
        dest.writeInt(mEnd);
        dest.writeInt(mEntities.size());
        for (String entity : mEntities) {
            dest.writeString(entity);
            dest.writeFloat(getConfidenceScore(entity));
        }
        dest.writeString(mId);
        dest.writeParcelable(mClassification, flags);
        dest.writeBundle(mExtras);
    }
    public static final android.os.Parcelable.Creator<TextSelection> CREATOR =
            new android.os.Parcelable.Creator<TextSelection>() {
        @Override public TextSelection createFromParcel(android.os.Parcel in) {
            Builder b = new Builder(in.readInt(), in.readInt());
            final int n = in.readInt();
            for (int i = 0; i < n; i++) b.setEntityType(in.readString(), in.readFloat());
            b.setId(in.readString());
            b.setTextClassification((TextClassification) in.readParcelable(
                    TextClassification.class.getClassLoader()));
            android.os.Bundle extras = in.readBundle();
            if (extras != null) b.setExtras(extras);
            return b.build();
        }
        @Override public TextSelection[] newArray(int size) { return new TextSelection[size]; }
    };

    public static final class Builder {
        private final int mStart, mEnd;
        private final java.util.Map<String, Float> mEntityScores =
                new java.util.HashMap<String, Float>();
        private String mId;
        private TextClassification mClassification;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

        public Builder(int startIndex, int endIndex) { mStart = startIndex; mEnd = endIndex; }

        public Builder setEntityType(String type, float confidenceScore) {
            java.util.Objects.requireNonNull(type);
            if (confidenceScore < 0 || confidenceScore > 1) {
                throw new IllegalArgumentException("confidenceScore must be in [0, 1]");
            }
            mEntityScores.put(type, Float.valueOf(confidenceScore));
            return this;
        }
        public Builder setId(String id) { mId = id; return this; }
        /** AOSP Builder.setTextClassification(TextClassification) (API 31). */
        public Builder setTextClassification(TextClassification classification) {
            mClassification = classification; return this;
        }
        public Builder setExtras(android.os.Bundle extras) {
            mExtras = extras != null ? extras : android.os.Bundle.EMPTY; return this;
        }
        public TextSelection build() { return new TextSelection(this); }
    }

    /** AOSP TextSelection.Request: the text and the initial selection range. */
    public static final class Request implements android.os.Parcelable {
        private final CharSequence mText;
        private final int mStart, mEnd;
        private android.os.LocaleList mLocales;
        private boolean mIncludeTextClassification;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;
        private String mCallingPackageName;

        private Request(CharSequence text, int start, int end) {
            mText = text; mStart = start; mEnd = end;
        }
        public CharSequence getText() { return mText; }
        public int getStartIndex() { return mStart; }
        public int getEndIndex() { return mEnd; }
        public android.os.LocaleList getDefaultLocales() { return mLocales; }
        /** AOSP shouldIncludeTextClassification() (API 31). */
        public boolean shouldIncludeTextClassification() { return mIncludeTextClassification; }
        public android.os.Bundle getExtras() { return mExtras; }
        /** AOSP: stamped by the system on the way to the classifier service;
         *  null for a request that never left the app. */
        public String getCallingPackageName() { return mCallingPackageName; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeCharSequence(mText);
            dest.writeInt(mStart);
            dest.writeInt(mEnd);
            dest.writeString(mLocales == null ? null : mLocales.toLanguageTags());
            dest.writeBoolean(mIncludeTextClassification);
            dest.writeBundle(mExtras);
            dest.writeString(mCallingPackageName);
        }
        public static final android.os.Parcelable.Creator<Request> CREATOR =
                new android.os.Parcelable.Creator<Request>() {
            @Override public Request createFromParcel(android.os.Parcel in) {
                Request r = new Request(in.readCharSequence(), in.readInt(), in.readInt());
                String tags = in.readString();
                r.mLocales = tags == null ? null : android.os.LocaleList.forLanguageTags(tags);
                r.mIncludeTextClassification = in.readBoolean();
                android.os.Bundle extras = in.readBundle();
                if (extras != null) r.mExtras = extras;
                r.mCallingPackageName = in.readString();
                return r;
            }
            @Override public Request[] newArray(int size) { return new Request[size]; }
        };

        public static final class Builder {
            private final CharSequence mText;
            private final int mStart, mEnd;
            private android.os.LocaleList mLocales;
            private boolean mIncludeTextClassification;
            private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

            public Builder(CharSequence text, int startIndex, int endIndex) {
                mText = text; mStart = startIndex; mEnd = endIndex;
            }
            public Builder setDefaultLocales(android.os.LocaleList locales) {
                mLocales = locales; return this;
            }
            public Builder setIncludeTextClassification(boolean includeTextClassification) {
                mIncludeTextClassification = includeTextClassification; return this;
            }
            public Builder setExtras(android.os.Bundle bundle) {
                mExtras = bundle != null ? bundle : android.os.Bundle.EMPTY; return this;
            }
            public Request build() {
                Request r = new Request(mText, mStart, mEnd);
                r.mLocales = mLocales;
                r.mIncludeTextClassification = mIncludeTextClassification;
                r.mExtras = mExtras;
                return r;
            }
        }
    }
}
