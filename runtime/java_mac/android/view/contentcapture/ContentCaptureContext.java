// android.view.contentcapture.ContentCaptureContext -- identifies WHAT a
// content-capture session is capturing (a LocusId plus optional extras).
// Source: frameworks/base
// core/java/android/view/contentcapture/ContentCaptureContext.java (android16-release).
//
// Mac.apk runs no content-capture service, so no session is ever created by the
// framework -- but this is a plain immutable value object and an app that builds
// one and reads it back gets exactly what it set.
package android.view.contentcapture;

public final class ContentCaptureContext implements android.os.Parcelable {
    private final android.content.LocusId mLocusId;
    private final android.os.Bundle mExtras;

    private ContentCaptureContext(android.content.LocusId locusId, android.os.Bundle extras) {
        mLocusId = locusId;
        mExtras = extras;
    }

    public android.content.LocusId getLocusId() { return mLocusId; }
    public android.os.Bundle getExtras() { return mExtras; }

    /** AOSP ContentCaptureContext.forLocusId(String id): the shorthand for
     *  `new Builder(new LocusId(id)).build()`. */
    public static ContentCaptureContext forLocusId(String id) {
        return new Builder(new android.content.LocusId(id)).build();
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeParcelable(mLocusId, flags);
        dest.writeBundle(mExtras);
    }

    public static final android.os.Parcelable.Creator<ContentCaptureContext> CREATOR =
            new android.os.Parcelable.Creator<ContentCaptureContext>() {
        @Override public ContentCaptureContext createFromParcel(android.os.Parcel in) {
            android.content.LocusId locusId = in.readParcelable(
                    android.content.LocusId.class.getClassLoader());
            return new ContentCaptureContext(locusId, in.readBundle());
        }
        @Override public ContentCaptureContext[] newArray(int size) {
            return new ContentCaptureContext[size];
        }
    };

    @Override public String toString() {
        return "ContentCaptureContext[id=" + mLocusId + "]";
    }

    public static final class Builder {
        private final android.content.LocusId mLocusId;
        private android.os.Bundle mExtras;
        public Builder(android.content.LocusId locusId) {
            mLocusId = java.util.Objects.requireNonNull(locusId);
        }
        public Builder setExtras(android.os.Bundle extras) {
            mExtras = java.util.Objects.requireNonNull(extras);
            return this;
        }
        public ContentCaptureContext build() {
            return new ContentCaptureContext(mLocusId, mExtras);
        }
    }
}
