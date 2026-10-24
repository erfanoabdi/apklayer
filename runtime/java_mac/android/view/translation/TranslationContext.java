// android.view.translation.TranslationContext -- source/target spec pair plus
// the per-request flags, built by TranslationContext.Builder.
// Source: frameworks/base core/java/android/view/translation/TranslationContext.java
package android.view.translation;

public final class TranslationContext implements android.os.Parcelable {
    public static final int FLAG_LOW_LATENCY = 1;
    public static final int FLAG_TRANSLITERATION = 2;
    public static final int FLAG_DEFINITIONS = 4;

    private final TranslationSpec mSourceSpec;
    private final TranslationSpec mTargetSpec;
    private final int mTranslationFlags;

    private TranslationContext(TranslationSpec sourceSpec, TranslationSpec targetSpec,
            int translationFlags) {
        mSourceSpec = sourceSpec;
        mTargetSpec = targetSpec;
        mTranslationFlags = translationFlags;
    }

    public TranslationSpec getSourceSpec() { return mSourceSpec; }
    public TranslationSpec getTargetSpec() { return mTargetSpec; }
    public int getTranslationFlags() { return mTranslationFlags; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeParcelable(mSourceSpec, flags);
        dest.writeParcelable(mTargetSpec, flags);
        dest.writeInt(mTranslationFlags);
    }

    public static final android.os.Parcelable.Creator<TranslationContext> CREATOR =
            new android.os.Parcelable.Creator<TranslationContext>() {
        @Override public TranslationContext createFromParcel(android.os.Parcel source) {
            ClassLoader cl = TranslationSpec.class.getClassLoader();
            return new TranslationContext(
                    (TranslationSpec) source.readParcelable(cl),
                    (TranslationSpec) source.readParcelable(cl),
                    source.readInt());
        }
        @Override public TranslationContext[] newArray(int size) {
            return new TranslationContext[size];
        }
    };

    public static final class Builder {
        private final TranslationSpec mSourceSpec;
        private final TranslationSpec mTargetSpec;
        private int mTranslationFlags;

        public Builder(TranslationSpec sourceSpec, TranslationSpec targetSpec) {
            mSourceSpec = sourceSpec;
            mTargetSpec = targetSpec;
        }
        public Builder setTranslationFlags(int value) { mTranslationFlags = value; return this; }
        public TranslationContext build() {
            return new TranslationContext(mSourceSpec, mTargetSpec, mTranslationFlags);
        }
    }
}
