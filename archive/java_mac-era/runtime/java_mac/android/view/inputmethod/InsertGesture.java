package android.view.inputmethod;

/** Handwriting InsertGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../InsertGesture.java */
public final class InsertGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.PointF mPoint;
    private String mText;
    private InsertGesture() { }

    public android.graphics.PointF getInsertionPoint() { return mPoint; }
    public String getTextToInsert() { return mText; }

    public static final class Builder {
        private final InsertGesture g = new InsertGesture();
        public Builder() { }
        public Builder setInsertionPoint(android.graphics.PointF v) { g.mPoint = v; return this; }
        public Builder setTextToInsert(String v) { g.mText = v; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public InsertGesture build() { return g; }
    }

    // AOSP field order: fallbackText, textToInsert, point.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeString(mText);
        dest.writeTypedObject(mPoint, flags);
    }

    public static final android.os.Parcelable.Creator<InsertGesture> CREATOR =
            new android.os.Parcelable.Creator<InsertGesture>() {
        @Override public InsertGesture createFromParcel(android.os.Parcel source) {
            InsertGesture g = new InsertGesture();
            g.mFallbackText = source.readString();
            g.mText = source.readString();
            g.mPoint = source.readTypedObject(android.graphics.PointF.CREATOR);
            return g;
        }
        @Override public InsertGesture[] newArray(int size) { return new InsertGesture[size]; }
    };
}
