// android.view.textclassifier.TextClassificationSessionId -- the opaque identity
// of one text-classification session. AOSP's public surface is Parcelable plus
// getValue(). Source: frameworks/base
// core/java/android/view/textclassifier/TextClassificationSessionId.java.
package android.view.textclassifier;

public final class TextClassificationSessionId implements android.os.Parcelable {
    private final String mValue;

    /** @hide -- only the framework mints a session id. */
    TextClassificationSessionId(String value) { mValue = value; }

    public String getValue() { return mValue; }

    @Override public int hashCode() { return mValue.hashCode(); }
    @Override public boolean equals(Object o) {
        return o instanceof TextClassificationSessionId
                && mValue.equals(((TextClassificationSessionId) o).mValue);
    }
    @Override public String toString() { return mValue; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mValue);
    }
    public static final android.os.Parcelable.Creator<TextClassificationSessionId> CREATOR =
            new android.os.Parcelable.Creator<TextClassificationSessionId>() {
        @Override public TextClassificationSessionId createFromParcel(android.os.Parcel in) {
            return new TextClassificationSessionId(in.readString());
        }
        @Override public TextClassificationSessionId[] newArray(int size) {
            return new TextClassificationSessionId[size];
        }
    };
}
