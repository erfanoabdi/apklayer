package android.view.inputmethod;

/** Handwriting DeleteGesture (API 34). Value object built via Builder.
 *  Source: frameworks/base .../DeleteGesture.java */
public final class DeleteGesture extends PreviewableHandwritingGesture
        implements android.os.Parcelable {

    private android.graphics.RectF mArea;
    private int mGranularity;
    private DeleteGesture() { }

    public android.graphics.RectF getDeletionArea() { return mArea; }
    public int getGranularity() { return mGranularity; }

    public static final class Builder {
        private final DeleteGesture g = new DeleteGesture();
        public Builder() { }
        public Builder setDeletionArea(android.graphics.RectF v) { g.mArea = v; return this; }
        public Builder setGranularity(int v) { g.mGranularity = v; return this; }
        public Builder setFallbackText(String v) { g.mFallbackText = v; return this; }
        public DeleteGesture build() { return g; }
    }

    // AOSP DeleteGesture.java: field order is fallbackText, granularity, area
    // (frameworks/base/core/java/android/view/inputmethod/DeleteGesture.java,
    // writeToParcel around L501-505). AOSP uses writeString8/readString8; our
    // Parcel has one string encoding, so writeString/readString is the same
    // operation (per debug/parcel_roundtrip.sh's E3 note on VersionedPackage).
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeInt(mGranularity);
        dest.writeTypedObject(mArea, flags);
    }

    public static final android.os.Parcelable.Creator<DeleteGesture> CREATOR =
            new android.os.Parcelable.Creator<DeleteGesture>() {
        @Override public DeleteGesture createFromParcel(android.os.Parcel source) {
            DeleteGesture g = new DeleteGesture();
            g.mFallbackText = source.readString();
            g.mGranularity = source.readInt();
            g.mArea = source.readTypedObject(android.graphics.RectF.CREATOR);
            return g;
        }
        @Override public DeleteGesture[] newArray(int size) { return new DeleteGesture[size]; }
    };
}
