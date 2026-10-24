package android.view.inputmethod;

/** Handwriting SelectGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../SelectGesture.java */
public final class SelectGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.RectF mArea;
    private int mGranularity;
    private SelectGesture() { }

    public android.graphics.RectF getSelectionArea() { return mArea; }
    public int getGranularity() { return mGranularity; }

    public static final class Builder {
        private final SelectGesture g = new SelectGesture();
        public Builder() { }
        public Builder setSelectionArea(android.graphics.RectF v) { g.mArea = v; return this; }
        public Builder setGranularity(int v) { g.mGranularity = v; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public SelectGesture build() { return g; }
    }

    // AOSP field order: fallbackText, granularity, area.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeInt(mGranularity);
        dest.writeTypedObject(mArea, flags);
    }

    public static final android.os.Parcelable.Creator<SelectGesture> CREATOR =
            new android.os.Parcelable.Creator<SelectGesture>() {
        @Override public SelectGesture createFromParcel(android.os.Parcel source) {
            SelectGesture g = new SelectGesture();
            g.mFallbackText = source.readString();
            g.mGranularity = source.readInt();
            g.mArea = source.readTypedObject(android.graphics.RectF.CREATOR);
            return g;
        }
        @Override public SelectGesture[] newArray(int size) { return new SelectGesture[size]; }
    };
}
