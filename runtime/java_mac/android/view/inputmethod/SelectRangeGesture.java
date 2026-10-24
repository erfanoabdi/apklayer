package android.view.inputmethod;

/** Handwriting SelectRangeGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../SelectRangeGesture.java */
public final class SelectRangeGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.RectF mStartArea;
    private android.graphics.RectF mEndArea;
    private int mGranularity;
    private SelectRangeGesture() { }

    public android.graphics.RectF getSelectionStartArea() { return mStartArea; }
    public android.graphics.RectF getSelectionEndArea() { return mEndArea; }
    public int getGranularity() { return mGranularity; }

    public static final class Builder {
        private final SelectRangeGesture g = new SelectRangeGesture();
        public Builder() { }
        public Builder setSelectionStartArea(android.graphics.RectF v) { g.mStartArea = v; return this; }
        public Builder setSelectionEndArea(android.graphics.RectF v) { g.mEndArea = v; return this; }
        public Builder setGranularity(int v) { g.mGranularity = v; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public SelectRangeGesture build() { return g; }
    }

    // AOSP field order: fallbackText, granularity, startArea, endArea.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeInt(mGranularity);
        dest.writeTypedObject(mStartArea, flags);
        dest.writeTypedObject(mEndArea, flags);
    }

    public static final android.os.Parcelable.Creator<SelectRangeGesture> CREATOR =
            new android.os.Parcelable.Creator<SelectRangeGesture>() {
        @Override public SelectRangeGesture createFromParcel(android.os.Parcel source) {
            SelectRangeGesture g = new SelectRangeGesture();
            g.mFallbackText = source.readString();
            g.mGranularity = source.readInt();
            g.mStartArea = source.readTypedObject(android.graphics.RectF.CREATOR);
            g.mEndArea = source.readTypedObject(android.graphics.RectF.CREATOR);
            return g;
        }
        @Override public SelectRangeGesture[] newArray(int size) { return new SelectRangeGesture[size]; }
    };
}
