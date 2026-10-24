package android.media;

/**
 * One timed-metadata sample delivered while a MediaPlayer plays a stream that
 * carries a metadata track (ID3 in HLS, EMSG in DASH). API 23.
 *
 * A plain (timestamp, bytes) value type with a public constructor, so it is
 * fully implementable and fully implemented. What Mac.apk does not have is a
 * PRODUCER: nothing in the extractor path parses a metadata track, so
 * MediaPlayer.setOnTimedMetaDataAvailableListener registers a listener the
 * platform never calls -- which is also what happens on Android for content
 * with no metadata track.
 *
 * Source: frameworks/base/media/java/android/media/TimedMetaData.java
 */
public final class TimedMetaData {

    private final long mTimestampUs;
    private final byte[] mMetaData;

    /** AOSP throws IllegalArgumentException on null metadata
     *  (TimedMetaData.java, TimedMetaData(long, byte[])). */
    public TimedMetaData(long timestampUs, byte[] metaData) {
        if (metaData == null) {
            throw new IllegalArgumentException("null metaData");
        }
        mTimestampUs = timestampUs;
        mMetaData = metaData;
    }

    /** AOSP: the presentation time in MICROSECONDS. */
    public long getTimestamp() { return mTimestampUs; }

    public byte[] getMetaData() { return mMetaData; }
}
