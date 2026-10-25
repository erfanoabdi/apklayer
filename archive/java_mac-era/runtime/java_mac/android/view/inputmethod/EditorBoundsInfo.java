package android.view.inputmethod;

import android.graphics.RectF;

/**
 * The editor's bounds and handwriting area, reported to the IME (API 33) for
 * stylus handwriting placement. Value object built via its Builder.
 *
 * Source: frameworks/base/core/java/android/view/inputmethod/EditorBoundsInfo.java
 */
public final class EditorBoundsInfo implements android.os.Parcelable {

    private final RectF mEditorBounds;
    private final RectF mHandwritingBounds;
    // AOSP caches a combined hash at construction time and writes it as the
    // FIRST parcel field (EditorBoundsInfo.java L46/108) rather than
    // recomputing hashCode() on demand. Mirrored exactly so a round-tripped
    // instance's hashCode() matches the original bit for bit.
    private final int mHashCode;

    private EditorBoundsInfo(Builder b) {
        mEditorBounds = b.mEditorBounds;
        mHandwritingBounds = b.mHandwritingBounds;
        int hash = java.util.Objects.hashCode(mEditorBounds);
        hash *= 31;
        hash += java.util.Objects.hashCode(mHandwritingBounds);
        mHashCode = hash;
    }

    private EditorBoundsInfo(android.os.Parcel source) {
        mHashCode = source.readInt();
        mEditorBounds = source.readTypedObject(RectF.CREATOR);
        mHandwritingBounds = source.readTypedObject(RectF.CREATOR);
    }

    public RectF getEditorBounds() { return mEditorBounds; }
    public RectF getHandwritingBounds() { return mHandwritingBounds; }

    @Override public int hashCode() { return mHashCode; }

    @Override public boolean equals(Object obj) {
        if (!(obj instanceof EditorBoundsInfo)) return false;
        EditorBoundsInfo b = (EditorBoundsInfo) obj;
        return java.util.Objects.equals(b.mEditorBounds, mEditorBounds)
                && java.util.Objects.equals(b.mHandwritingBounds, mHandwritingBounds);
    }

    @Override public String toString() {
        return "EditorBoundsInfo{editorBounds=" + mEditorBounds
                + ", handwritingBounds=" + mHandwritingBounds + "}";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mHashCode);
        dest.writeTypedObject(mEditorBounds, flags);
        dest.writeTypedObject(mHandwritingBounds, flags);
    }

    public static final android.os.Parcelable.Creator<EditorBoundsInfo> CREATOR =
            new android.os.Parcelable.Creator<EditorBoundsInfo>() {
        @Override public EditorBoundsInfo createFromParcel(android.os.Parcel source) {
            return new EditorBoundsInfo(source);
        }
        @Override public EditorBoundsInfo[] newArray(int size) { return new EditorBoundsInfo[size]; }
    };

    public static final class Builder {
        private RectF mEditorBounds;
        private RectF mHandwritingBounds;

        public Builder() { }

        public Builder setEditorBounds(RectF bounds) { mEditorBounds = bounds; return this; }
        public Builder setHandwritingBounds(RectF bounds) { mHandwritingBounds = bounds; return this; }

        public EditorBoundsInfo build() { return new EditorBoundsInfo(this); }
    }
}
