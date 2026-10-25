// android.view.contentcapture.DataShareRequest -- an app offering a blob of data
// to the content-capture service. Source: frameworks/base
// core/java/android/view/contentcapture/DataShareRequest.java (android16-release).
package android.view.contentcapture;

public final class DataShareRequest implements android.os.Parcelable {
    private final String mPackageName;
    private final android.content.LocusId mLocusId;
    private final String mMimeType;

    public DataShareRequest(android.content.LocusId locusId, String mimeType) {
        mLocusId = locusId;
        mMimeType = java.util.Objects.requireNonNull(mimeType);
        // AOSP stamps ActivityThread.currentPackageName(); one app per process
        // here, so the app's own package is it (same source as
        // DataRemovalRequest.Builder.build).
        String pkg = "";
        try {
            android.content.res.Resources res = android.content.res.Resources.getSystem();
            if (res != null) pkg = res.getResourcePackageName(0x7f000000);
        } catch (Throwable ignored) {}
        mPackageName = pkg == null ? "" : pkg;
    }

    /** @hide -- CREATOR path, which carries the package explicitly. */
    private DataShareRequest(String packageName, android.content.LocusId locusId,
            String mimeType) {
        mPackageName = packageName;
        mLocusId = locusId;
        mMimeType = mimeType;
    }

    public String getPackageName() { return mPackageName; }
    public android.content.LocusId getLocusId() { return mLocusId; }
    public String getMimeType() { return mMimeType; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeParcelable(mLocusId, flags);
        dest.writeString(mMimeType);
    }
    public static final android.os.Parcelable.Creator<DataShareRequest> CREATOR =
            new android.os.Parcelable.Creator<DataShareRequest>() {
        @Override public DataShareRequest createFromParcel(android.os.Parcel in) {
            return new DataShareRequest(in.readString(),
                    (android.content.LocusId) in.readParcelable(
                            android.content.LocusId.class.getClassLoader()),
                    in.readString());
        }
        @Override public DataShareRequest[] newArray(int size) {
            return new DataShareRequest[size];
        }
    };
}
