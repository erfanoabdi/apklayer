// android.media.MediaSyncEvent -- a synchronization point used to start a
// capture (AudioRecord.startRecording(MediaSyncEvent)) relative to another
// session. AOSP frameworks/base media/java/android/media/MediaSyncEvent.java.
// Mac.apk has no cross-session sync, so this is a typed holder of the
// (type, audioSession) pair the app configures.
package android.media;

public class MediaSyncEvent implements android.os.Parcelable {
    public static final int SYNC_EVENT_NONE = 0;
    public static final int SYNC_EVENT_PRESENTATION_COMPLETE = 1;

    private final int mType;
    private int mAudioSession;

    private MediaSyncEvent(int eventType) { mType = eventType; }
    public static MediaSyncEvent createEvent(int eventType) {
        return new MediaSyncEvent(eventType);
    }
    public MediaSyncEvent setAudioSessionId(int audioSessionId) {
        mAudioSession = audioSessionId; return this;
    }
    public int getType() { return mType; }
    public int getAudioSessionId() { return mAudioSession; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mAudioSession);
    }

    public static final android.os.Parcelable.Creator<MediaSyncEvent> CREATOR =
            new android.os.Parcelable.Creator<MediaSyncEvent>() {
        @Override public MediaSyncEvent createFromParcel(android.os.Parcel in) {
            MediaSyncEvent e = new MediaSyncEvent(in.readInt());
            e.mAudioSession = in.readInt();
            return e;
        }
        @Override public MediaSyncEvent[] newArray(int size) { return new MediaSyncEvent[size]; }
    };
}
