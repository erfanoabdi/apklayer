package android.view.inputmethod;

/** Handwriting RemoveSpaceGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../RemoveSpaceGesture.java */
public final class RemoveSpaceGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.PointF mStart;
    private android.graphics.PointF mEnd;
    private RemoveSpaceGesture() { }

    public android.graphics.PointF getStartPoint() { return mStart; }
    public android.graphics.PointF getEndPoint() { return mEnd; }

    public static final class Builder {
        private final RemoveSpaceGesture g = new RemoveSpaceGesture();
        public Builder() { }
        public Builder setPoints(android.graphics.PointF startPoint, android.graphics.PointF endPoint) { g.mStart = startPoint; g.mEnd = endPoint; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public RemoveSpaceGesture build() { return g; }
    }

    // AOSP field order: startPoint, endPoint, fallbackText (note: fallbackText
    // LAST here, unlike Delete/Select which write it first).
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeTypedObject(mStart, flags);
        dest.writeTypedObject(mEnd, flags);
        dest.writeString(mFallbackText);
    }

    public static final android.os.Parcelable.Creator<RemoveSpaceGesture> CREATOR =
            new android.os.Parcelable.Creator<RemoveSpaceGesture>() {
        @Override public RemoveSpaceGesture createFromParcel(android.os.Parcel source) {
            RemoveSpaceGesture g = new RemoveSpaceGesture();
            g.mStart = source.readTypedObject(android.graphics.PointF.CREATOR);
            g.mEnd = source.readTypedObject(android.graphics.PointF.CREATOR);
            g.mFallbackText = source.readString();
            return g;
        }
        @Override public RemoveSpaceGesture[] newArray(int size) { return new RemoveSpaceGesture[size]; }
    };
}
