package android.view.inputmethod;

/** Handwriting JoinOrSplitGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../JoinOrSplitGesture.java */
public final class JoinOrSplitGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.PointF mPoint;
    private JoinOrSplitGesture() { }

    public android.graphics.PointF getJoinOrSplitPoint() { return mPoint; }

    public static final class Builder {
        private final JoinOrSplitGesture g = new JoinOrSplitGesture();
        public Builder() { }
        public Builder setJoinOrSplitPoint(android.graphics.PointF v) { g.mPoint = v; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public JoinOrSplitGesture build() { return g; }
    }

    // AOSP field order here is REVERSED from its siblings: point, then
    // fallbackText (frameworks/base .../JoinOrSplitGesture.java writeToParcel).
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeTypedObject(mPoint, flags);
        dest.writeString(mFallbackText);
    }

    public static final android.os.Parcelable.Creator<JoinOrSplitGesture> CREATOR =
            new android.os.Parcelable.Creator<JoinOrSplitGesture>() {
        @Override public JoinOrSplitGesture createFromParcel(android.os.Parcel source) {
            JoinOrSplitGesture g = new JoinOrSplitGesture();
            g.mPoint = source.readTypedObject(android.graphics.PointF.CREATOR);
            g.mFallbackText = source.readString();
            return g;
        }
        @Override public JoinOrSplitGesture[] newArray(int size) { return new JoinOrSplitGesture[size]; }
    };
}
