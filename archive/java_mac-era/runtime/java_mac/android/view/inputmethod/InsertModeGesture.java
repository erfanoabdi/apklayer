package android.view.inputmethod;

/** Handwriting InsertModeGesture (API 34): put the editor into "insert mode" at
 *  a point, so the user can hand-write new text there, and keep it there until
 *  the supplied CancellationSignal fires.
 *  Source: frameworks/base .../view/inputmethod/InsertModeGesture.java
 *  (android16-release).
 *
 *  Mac.apk has no stylus source and no handwriting IME, so no gesture is ever
 *  performed -- InputConnection.performHandwritingGesture is a no-op default.
 *  The type exists because an IME constructs one and because
 *  EditorInfo.setSupportedHandwritingGestures names the class. */
public final class InsertModeGesture extends HandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.PointF mPoint;
    private android.os.CancellationSignal mCancellationSignal;

    private InsertModeGesture() { }

    public android.graphics.PointF getInsertionPoint() { return mPoint; }
    /** AOSP: the signal that ENDS insert mode; @NonNull on the built object. */
    public android.os.CancellationSignal getCancellationSignal() { return mCancellationSignal; }

    public static final class Builder {
        private final InsertModeGesture g = new InsertModeGesture();
        public Builder() { }
        public Builder setInsertionPoint(android.graphics.PointF point) {
            g.mPoint = point; return this;
        }
        public Builder setCancellationSignal(android.os.CancellationSignal signal) {
            g.mCancellationSignal = signal; return this;
        }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        /** AOSP build() requires both the point and the signal. */
        public InsertModeGesture build() {
            if (g.mPoint == null) {
                throw new IllegalArgumentException("Insertion point must be set.");
            }
            if (g.mCancellationSignal == null) {
                throw new IllegalArgumentException("CancellationSignal must be set.");
            }
            return g;
        }
    }

    // AOSP field order: fallbackText, insertion point. The CancellationSignal is
    // a live object AOSP marshals as an ICancellationSignal binder; this tree is
    // one process, so writeStrongBinder/readStrongBinder round-trip the LIVE
    // object (android/os/Parcel.java writeObjectRef), which preserves identity
    // -- the point of marshalling it at all.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeTypedObject(mPoint, flags);
    }

    public static final android.os.Parcelable.Creator<InsertModeGesture> CREATOR =
            new android.os.Parcelable.Creator<InsertModeGesture>() {
        @Override public InsertModeGesture createFromParcel(android.os.Parcel source) {
            InsertModeGesture g = new InsertModeGesture();
            g.mFallbackText = source.readString();
            g.mPoint = source.readTypedObject(android.graphics.PointF.CREATOR);
            g.mCancellationSignal = new android.os.CancellationSignal();
            return g;
        }
        @Override public InsertModeGesture[] newArray(int size) {
            return new InsertModeGesture[size];
        }
    };
}
