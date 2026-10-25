// android.media.AudioTimestamp -- a (framePosition, nanoTime) pair sampled from
// an AudioTrack/AudioRecord clock. AOSP frameworks/base
// media/java/android/media/AudioTimestamp.java. Public mutable fields, filled by
// getTimestamp(); Mac.apk's getTimestamp approximations write into these.
package android.media;

public final class AudioTimestamp implements android.os.Parcelable {
    public static final int TIMEBASE_MONOTONIC = 0;
    public static final int TIMEBASE_BOOTTIME = 1;
    public long framePosition = 0;
    public long nanoTime = 0;
    public AudioTimestamp() {}

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(framePosition);
        dest.writeLong(nanoTime);
    }

    public static final android.os.Parcelable.Creator<AudioTimestamp> CREATOR =
            new android.os.Parcelable.Creator<AudioTimestamp>() {
        @Override public AudioTimestamp createFromParcel(android.os.Parcel in) {
            AudioTimestamp t = new AudioTimestamp();
            t.framePosition = in.readLong();
            t.nanoTime = in.readLong();
            return t;
        }
        @Override public AudioTimestamp[] newArray(int size) { return new AudioTimestamp[size]; }
    };
}
