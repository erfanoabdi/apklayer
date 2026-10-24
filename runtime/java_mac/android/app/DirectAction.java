package android.app;
/** A voice-assistant direct action an activity exposes (API 29). Mac.apk has no
 *  assistant, so one is built but never invoked.
 *  Source: frameworks/base/core/java/android/app/DirectAction.java
 *
 *  2026-08-23 conformance sweep: added mTaskId/mActivityId (AOSP's
 *  setSource(int, IBinder), @hide) and Parcelable. AOSP's own writeToParcel
 *  (L151-156) calls `mLocusId.getId()` UNCONDITIONALLY -- it NPEs if the
 *  Builder never called setLocusId(). That is upstream's own contract (a
 *  DirectAction handed to a system caller is expected to always carry a
 *  locus id), so it is mirrored rather than silently patched; do not "fix"
 *  it here without checking whether AOSP has since guarded it. */
public final class DirectAction implements android.os.Parcelable {
    private int mTaskId;
    private android.os.IBinder mActivityId;
    private final String mId; private final android.os.Bundle mExtras; private final android.content.LocusId mLocusId;
    private DirectAction(Builder b) { mId = b.mId; mExtras = b.mExtras; mLocusId = b.mLocusId; }

    /** @hide -- matches AOSP's setSource(int, IBinder). */
    public void setSource(int taskId, android.os.IBinder activityId) {
        mTaskId = taskId; mActivityId = activityId;
    }
    /** @hide */
    public int getTaskId() { return mTaskId; }
    /** @hide */
    public android.os.IBinder getActivityId() { return mActivityId; }

    public String getId() { return mId; }
    public android.os.Bundle getExtras() { return mExtras; }
    public android.content.LocusId getLocusId() { return mLocusId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mTaskId);
        dest.writeStrongBinder(mActivityId);
        dest.writeString(mId);
        dest.writeBundle(mExtras);
        dest.writeString(mLocusId.getId());
    }

    public static final android.os.Parcelable.Creator<DirectAction> CREATOR =
            new android.os.Parcelable.Creator<DirectAction>() {
        @Override public DirectAction createFromParcel(android.os.Parcel in) {
            int taskId = in.readInt();
            android.os.IBinder activityId = in.readStrongBinder();
            String id = in.readString();
            android.os.Bundle extras = in.readBundle();
            String locusIdString = in.readString();
            android.content.LocusId locusId =
                    (locusIdString != null) ? new android.content.LocusId(locusIdString) : null;
            return new DirectAction(id, extras, locusId, taskId, activityId);
        }
        @Override public DirectAction[] newArray(int size) { return new DirectAction[size]; }
    };

    // Package-private full constructor used only by CREATOR, so the real
    // (final) fields can be set from parcel data without a placeholder build.
    private DirectAction(String id, android.os.Bundle extras, android.content.LocusId locusId,
            int taskId, android.os.IBinder activityId) {
        mId = id; mExtras = extras; mLocusId = locusId; mTaskId = taskId; mActivityId = activityId;
    }

    public static final class Builder {
        private final String mId; private android.os.Bundle mExtras; private android.content.LocusId mLocusId;
        public Builder(String id) { mId = id; }
        public Builder setExtras(android.os.Bundle extras) { mExtras = extras; return this; }
        public Builder setLocusId(android.content.LocusId locusId) { mLocusId = locusId; return this; }
        public DirectAction build() { return new DirectAction(this); }
    }
}
