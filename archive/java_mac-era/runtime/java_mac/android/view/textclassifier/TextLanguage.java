// android.view.textclassifier.TextLanguage -- the language-detection result:
// an ordered list of ULocale hypotheses with confidence scores.
// Source: frameworks/base
// core/java/android/view/textclassifier/TextLanguage.java (android16-release).
//
// Mac.apk has no on-device language model, so TextClassifier.detectLanguage
// returns EMPTY (zero hypotheses) -- AOSP's own answer for a classifier with no
// model, which is what NO_OP is.
package android.view.textclassifier;

public final class TextLanguage implements android.os.Parcelable {
    /** AOSP TextLanguage.EMPTY: a result with no hypotheses at all. */
    public static final TextLanguage EMPTY = new Builder().build();

    private final String mId;
    private final java.util.List<android.icu.util.ULocale> mLocales;
    private final java.util.Map<android.icu.util.ULocale, Float> mScores;
    private final android.os.Bundle mBundle;

    private TextLanguage(Builder b) {
        mId = b.mId;
        mLocales = new java.util.ArrayList<android.icu.util.ULocale>(b.mScores.keySet());
        mScores = b.mScores;
        mBundle = b.mBundle;
    }

    public String getId() { return mId; }
    public int getLocaleHypothesisCount() { return mLocales.size(); }
    public android.icu.util.ULocale getLocale(int index) { return mLocales.get(index); }
    public float getConfidenceScore(android.icu.util.ULocale locale) {
        Float f = mScores.get(locale);
        return f == null ? 0f : f.floatValue();
    }
    public android.os.Bundle getExtras() { return mBundle; }

    @Override public String toString() {
        return "TextLanguage{id=" + mId + ", locales=" + mLocales + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeInt(mLocales.size());
        for (android.icu.util.ULocale l : mLocales) {
            dest.writeString(l.getName());
            dest.writeFloat(getConfidenceScore(l));
        }
        dest.writeBundle(mBundle);
    }
    public static final android.os.Parcelable.Creator<TextLanguage> CREATOR =
            new android.os.Parcelable.Creator<TextLanguage>() {
        @Override public TextLanguage createFromParcel(android.os.Parcel in) {
            Builder b = new Builder().setId(in.readString());
            final int n = in.readInt();
            for (int i = 0; i < n; i++) {
                b.putLocale(new android.icu.util.ULocale(in.readString()), in.readFloat());
            }
            android.os.Bundle extras = in.readBundle();
            if (extras != null) b.setExtras(extras);
            return b.build();
        }
        @Override public TextLanguage[] newArray(int size) { return new TextLanguage[size]; }
    };

    public static final class Builder {
        private String mId;
        // LinkedHashMap, because AOSP's getLocale(index) is documented to return
        // hypotheses in DESCENDING confidence order, i.e. insertion order for a
        // caller that adds them that way; a HashMap would scramble it.
        private final java.util.LinkedHashMap<android.icu.util.ULocale, Float> mScores =
                new java.util.LinkedHashMap<android.icu.util.ULocale, Float>();
        private android.os.Bundle mBundle = android.os.Bundle.EMPTY;

        public Builder() {}
        public Builder putLocale(android.icu.util.ULocale locale, float confidenceScore) {
            java.util.Objects.requireNonNull(locale);
            mScores.put(locale, Float.valueOf(confidenceScore));
            return this;
        }
        public Builder setId(String id) { mId = id; return this; }
        public Builder setExtras(android.os.Bundle bundle) {
            mBundle = java.util.Objects.requireNonNull(bundle); return this;
        }
        public TextLanguage build() { return new TextLanguage(this); }
    }

    /** AOSP TextLanguage.Request: the text to detect the language of. */
    public static final class Request implements android.os.Parcelable {
        private final CharSequence mText;
        private final android.os.Bundle mExtra;
        private String mCallingPackageName;

        private Request(CharSequence text, android.os.Bundle bundle) {
            mText = text;
            mExtra = bundle;
        }

        public CharSequence getText() { return mText; }
        public android.os.Bundle getExtras() { return mExtra; }
        /** AOSP: set by the SYSTEM when the request crosses to the classifier
         *  service; null for a request an app built and never sent, which is
         *  every request here. */
        public String getCallingPackageName() { return mCallingPackageName; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeCharSequence(mText);
            dest.writeBundle(mExtra);
            dest.writeString(mCallingPackageName);
        }
        public static final android.os.Parcelable.Creator<Request> CREATOR =
                new android.os.Parcelable.Creator<Request>() {
            @Override public Request createFromParcel(android.os.Parcel in) {
                Request r = new Request(in.readCharSequence(), in.readBundle());
                r.mCallingPackageName = in.readString();
                return r;
            }
            @Override public Request[] newArray(int size) { return new Request[size]; }
        };

        public static final class Builder {
            private final CharSequence mText;
            private android.os.Bundle mBundle = android.os.Bundle.EMPTY;
            public Builder(CharSequence text) {
                mText = java.util.Objects.requireNonNull(text);
            }
            public Builder setExtras(android.os.Bundle bundle) {
                mBundle = java.util.Objects.requireNonNull(bundle); return this;
            }
            public Request build() { return new Request(mText, mBundle); }
        }
    }
}
