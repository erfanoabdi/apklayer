// android.view.inputmethod.TextAttribute -- extra information an IME attaches
// to text it commits or composes (conversion suggestions plus an opaque extras
// bundle). Source: frameworks/base
// core/java/android/view/inputmethod/TextAttribute.java (android16-release).
//
// Mac.apk routes keystrokes from the Cocoa bridge straight into the focused
// view and runs no IME, so nothing produces a TextAttribute -- but the type is
// named by five InputConnection methods, so it must exist for those signatures.
package android.view.inputmethod;

public final class TextAttribute implements android.os.Parcelable {
    private final java.util.List<String> mTextConversionSuggestions;
    private final android.os.PersistableBundle mExtras;

    private TextAttribute(Builder b) {
        mTextConversionSuggestions = b.mTextConversionSuggestions;
        mExtras = b.mExtras;
    }

    /** AOSP: @NonNull, empty when the IME supplied none. */
    public java.util.List<String> getTextConversionSuggestions() {
        return mTextConversionSuggestions;
    }
    /** AOSP: @NonNull. */
    public android.os.PersistableBundle getExtras() { return mExtras; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeStringList(mTextConversionSuggestions);
        dest.writePersistableBundle(mExtras);
    }

    public static final android.os.Parcelable.Creator<TextAttribute> CREATOR =
            new android.os.Parcelable.Creator<TextAttribute>() {
        @Override public TextAttribute createFromParcel(android.os.Parcel source) {
            Builder b = new Builder();
            java.util.List<String> suggestions = source.createStringArrayList();
            if (suggestions != null) b.setTextConversionSuggestions(suggestions);
            android.os.PersistableBundle extras = source.readPersistableBundle();
            if (extras != null) b.setExtras(extras);
            return b.build();
        }
        @Override public TextAttribute[] newArray(int size) { return new TextAttribute[size]; }
    };

    public static final class Builder {
        private java.util.List<String> mTextConversionSuggestions =
                java.util.Collections.emptyList();
        private android.os.PersistableBundle mExtras = new android.os.PersistableBundle();

        public Builder() {}
        public Builder setTextConversionSuggestions(java.util.List<String> suggestions) {
            mTextConversionSuggestions =
                    java.util.Collections.unmodifiableList(
                            new java.util.ArrayList<String>(suggestions));
            return this;
        }
        public Builder setExtras(android.os.PersistableBundle extras) {
            mExtras = extras;
            return this;
        }
        public TextAttribute build() { return new TextAttribute(this); }
    }
}
