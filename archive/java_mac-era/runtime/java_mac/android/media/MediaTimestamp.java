package android.media;
/** A media/system-clock anchor pair. Mac.apk fills it from the media player when
 *  available; TIMESTAMP_UNKNOWN otherwise. Mirrors AOSP getters. */
public final class MediaTimestamp {
    public static final MediaTimestamp TIMESTAMP_UNKNOWN = new MediaTimestamp(-1L, -1L, 0.0f);
    private final long mediaTimeUs;
    private final long nanoTime;
    private final float clockRate;
    public MediaTimestamp(long mediaUs, long systemNano, float rate) {
        mediaTimeUs = mediaUs; nanoTime = systemNano; clockRate = rate;
    }
    MediaTimestamp() { this(0L, 0L, 1.0f); }
    public long getAnchorMediaTimeUs() { return mediaTimeUs; }
    public long getAnchorSystemNanoTime() { return nanoTime; }
    /** @deprecated AOSP shipped this with the typo ("Sytem") and kept it for
     *  source compatibility, deprecating it in favour of the spelled-correctly
     *  getAnchorSystemNanoTime() above. Apps compiled against older SDKs still
     *  call it, so it has to exist. */
    @Deprecated public long getAnchorSytemNanoTime() { return nanoTime; }
    public float getMediaClockRate() { return clockRate; }
}
