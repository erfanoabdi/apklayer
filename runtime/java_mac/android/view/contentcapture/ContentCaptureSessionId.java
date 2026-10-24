// android.view.contentcapture.ContentCaptureSessionId -- the opaque identity of
// one content-capture session. Source: frameworks/base
// core/java/android/view/contentcapture/ContentCaptureSessionId.java.
// AOSP's public surface is only Parcelable plus equals/hashCode/toString; the
// value itself is @hide.
package android.view.contentcapture;

public final class ContentCaptureSessionId implements android.os.Parcelable {
    private final int mValue;

    /** @hide -- only the framework mints a session id. */
    ContentCaptureSessionId(int value) { mValue = value; }

    @Override public int hashCode() { return mValue; }
    @Override public boolean equals(Object obj) {
        return obj instanceof ContentCaptureSessionId
                && ((ContentCaptureSessionId) obj).mValue == mValue;
    }
    @Override public String toString() { return Integer.toString(mValue); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mValue);
    }
    public static final android.os.Parcelable.Creator<ContentCaptureSessionId> CREATOR =
            new android.os.Parcelable.Creator<ContentCaptureSessionId>() {
        @Override public ContentCaptureSessionId createFromParcel(android.os.Parcel in) {
            return new ContentCaptureSessionId(in.readInt());
        }
        @Override public ContentCaptureSessionId[] newArray(int size) {
            return new ContentCaptureSessionId[size];
        }
    };
}
