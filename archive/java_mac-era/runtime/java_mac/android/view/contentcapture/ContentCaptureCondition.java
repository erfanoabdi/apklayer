// android.view.contentcapture.ContentCaptureCondition -- one URL/locus pattern
// the content-capture service says it is interested in.
// Source: frameworks/base
// core/java/android/view/contentcapture/ContentCaptureCondition.java.
package android.view.contentcapture;

public final class ContentCaptureCondition implements android.os.Parcelable {
    /** The LocusId is a regular expression rather than a literal. */
    public static final int FLAG_IS_REGEX = 0x2;

    private final android.content.LocusId mLocusId;
    private final int mFlags;

    public ContentCaptureCondition(android.content.LocusId locusId, int flags) {
        mLocusId = java.util.Objects.requireNonNull(locusId);
        mFlags = flags;
    }

    public android.content.LocusId getLocusId() { return mLocusId; }
    public int getFlags() { return mFlags; }

    @Override public int hashCode() { return java.util.Objects.hash(mLocusId, mFlags); }
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ContentCaptureCondition)) return false;
        ContentCaptureCondition other = (ContentCaptureCondition) obj;
        return mFlags == other.mFlags && java.util.Objects.equals(mLocusId, other.mLocusId);
    }
    @Override public String toString() { return mLocusId + (mFlags == 0 ? "" : ":" + mFlags); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeParcelable(mLocusId, flags);
        dest.writeInt(mFlags);
    }
    public static final android.os.Parcelable.Creator<ContentCaptureCondition> CREATOR =
            new android.os.Parcelable.Creator<ContentCaptureCondition>() {
        @Override public ContentCaptureCondition createFromParcel(android.os.Parcel in) {
            return new ContentCaptureCondition(
                    (android.content.LocusId) in.readParcelable(
                            android.content.LocusId.class.getClassLoader()),
                    in.readInt());
        }
        @Override public ContentCaptureCondition[] newArray(int size) {
            return new ContentCaptureCondition[size];
        }
    };
}
