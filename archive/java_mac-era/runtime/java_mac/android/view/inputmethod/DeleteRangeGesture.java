package android.view.inputmethod;

/** Handwriting DeleteRangeGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../DeleteRangeGesture.java */
public final class DeleteRangeGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.RectF mStartArea;
    private android.graphics.RectF mEndArea;
    private int mGranularity;
    private DeleteRangeGesture() { }

    public android.graphics.RectF getDeletionStartArea() { return mStartArea; }
    public android.graphics.RectF getDeletionEndArea() { return mEndArea; }
    public int getGranularity() { return mGranularity; }

    public static final class Builder {
        private final DeleteRangeGesture g = new DeleteRangeGesture();
        public Builder() { }
        public Builder setDeletionStartArea(android.graphics.RectF v) { g.mStartArea = v; return this; }
        public Builder setDeletionEndArea(android.graphics.RectF v) { g.mEndArea = v; return this; }
        public Builder setGranularity(int v) { g.mGranularity = v; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public DeleteRangeGesture build() { return g; }
    }

    // AOSP field order: fallbackText, granularity, startArea, endArea.
    // writeString8/readString8 in AOSP map to our single writeString/readString.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeInt(mGranularity);
        dest.writeTypedObject(mStartArea, flags);
        dest.writeTypedObject(mEndArea, flags);
    }

    public static final android.os.Parcelable.Creator<DeleteRangeGesture> CREATOR =
            new android.os.Parcelable.Creator<DeleteRangeGesture>() {
        @Override public DeleteRangeGesture createFromParcel(android.os.Parcel source) {
            DeleteRangeGesture g = new DeleteRangeGesture();
            g.mFallbackText = source.readString();
            g.mGranularity = source.readInt();
            g.mStartArea = source.readTypedObject(android.graphics.RectF.CREATOR);
            g.mEndArea = source.readTypedObject(android.graphics.RectF.CREATOR);
            return g;
        }
        @Override public DeleteRangeGesture[] newArray(int size) { return new DeleteRangeGesture[size]; }
    };
}
