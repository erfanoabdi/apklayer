// android.media.SubtitleData -- a buffer of subtitle data for a selected track
// (API 28+). AOSP frameworks/base media/java/android/media/SubtitleData.java.
// Mac.apk produces none; this exists so OnSubtitleDataListener's parameter type
// resolves.
package android.media;

public final class SubtitleData {
    private final int mTrackIndex;
    private final long mStartTimeUs;
    private final long mDurationUs;
    private final byte[] mData;
    public SubtitleData(int trackIndex, long startTimeUs, long durationUs, byte[] data) {
        mTrackIndex = trackIndex;
        mStartTimeUs = startTimeUs;
        mDurationUs = durationUs;
        mData = data != null ? data : new byte[0];
    }
    public int getTrackIndex() { return mTrackIndex; }
    public long getStartTimeUs() { return mStartTimeUs; }
    public long getDurationUs() { return mDurationUs; }
    public byte[] getData() { return mData; }
}
