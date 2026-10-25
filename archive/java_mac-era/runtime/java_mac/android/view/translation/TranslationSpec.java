// android.view.translation.TranslationSpec -- the (locale, data format) pair
// that names one side of a translation. Source: frameworks/base
// core/java/android/view/translation/TranslationSpec.java (android16-release).
package android.view.translation;

public final class TranslationSpec implements android.os.Parcelable {
    public static final int DATA_FORMAT_TEXT = 1;

    private final android.icu.util.ULocale mLocale;
    private final int mDataFormat;

    public TranslationSpec(android.icu.util.ULocale locale, int dataFormat) {
        mLocale = locale;
        mDataFormat = dataFormat;
    }

    public android.icu.util.ULocale getLocale() { return mLocale; }
    public int getDataFormat() { return mDataFormat; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mLocale == null ? null : mLocale.getName());
        dest.writeInt(mDataFormat);
    }

    public static final android.os.Parcelable.Creator<TranslationSpec> CREATOR =
            new android.os.Parcelable.Creator<TranslationSpec>() {
        @Override public TranslationSpec createFromParcel(android.os.Parcel source) {
            String id = source.readString();
            return new TranslationSpec(
                    id == null ? null : new android.icu.util.ULocale(id),
                    source.readInt());
        }
        @Override public TranslationSpec[] newArray(int size) { return new TranslationSpec[size]; }
    };

    @Override public String toString() {
        return "TranslationSpec{locale=" + mLocale + ", dataFormat=" + mDataFormat + "}";
    }
}
