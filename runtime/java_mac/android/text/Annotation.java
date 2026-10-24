package android.text;
/** A key/value text markup span (parsed from &lt;annotation&gt; in string
 *  resources). A real value class (a stub existed before). Source:
 *  frameworks/base/core/java/android/text/Annotation.java
 *
 *  2026-08-23 conformance sweep: implements ParcelableSpan, field order
 *  (key, value) and type id 18 verified against Annotation.java
 *  (android16-release) verbatim -- AOSP's own shape is this exact 2-field
 *  class, so no divergence to note here. */
public class Annotation implements ParcelableSpan {
    private final String mKey;
    private final String mValue;
    public Annotation() { mKey = null; mValue = null; }
    public Annotation(String key, String value) { mKey = key; mValue = value; }
    /** AOSP Annotation(Parcel src): `mKey = src.readString(); mValue =
     *  src.readString();` -- the PUBLIC un-parcelling constructor. It is public
     *  API in its own right, not only the CREATOR's implementation detail (the
     *  CREATOR below now routes through it, as AOSP's does), so an app that
     *  reconstructs a span from a Parcel by hand needs it to exist. */
    public Annotation(android.os.Parcel src) {
        mKey = src.readString();
        mValue = src.readString();
    }
    public String getKey() { return mKey; }
    public String getValue() { return mValue; }
    @Override public String toString() { return "Annotation{key=" + mKey + " value=" + mValue + "}"; }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return TextUtils.ANNOTATION; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeString(mKey);
        dest.writeString(mValue);
    }
    public static final android.os.Parcelable.Creator<Annotation> CREATOR =
            new android.os.Parcelable.Creator<Annotation>() {
        @Override public Annotation createFromParcel(android.os.Parcel in) {
            return new Annotation(in);
        }
        @Override public Annotation[] newArray(int size) { return new Annotation[size]; }
    };
}
