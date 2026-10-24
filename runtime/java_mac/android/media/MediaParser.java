package android.media;

import android.util.Pair;

/**
 * Low-level container parser (API 30), used by ExoPlayer/Media3 as an optional
 * platform extractor. Mac.apk provides no platform parser, so create() returns
 * an instance whose advance() reports "no more data" (false) -- which is the
 * documented signal for the caller to fall back to its OWN extractors, which
 * ExoPlayer/Media3 always ship. So media playback proceeds on the app's path
 * rather than depending on a platform parser we do not have.
 *
 * Source: frameworks/base/media/java/android/media/MediaParser.java
 */
public final class MediaParser {

    public static final String PARAMETER_ADTS_ENABLE_CBR_SEEKING = "android.media.mediaparser.adts.enableCbrSeeking";
    public static final String PARAMETER_IN_BAND_CRYPTO_INFO = "android.media.mediaparser.inBandCryptoInfo";

    private final OutputConsumer mOutputConsumer;
    private final String[] mParserNames;
    private String mSelectedParserName = "";

    private MediaParser(OutputConsumer outputConsumer, String... parserNames) {
        mOutputConsumer = outputConsumer;
        mParserNames = parserNames;
    }

    public static MediaParser create(OutputConsumer outputConsumer, String... parserNames) {
        return new MediaParser(outputConsumer, parserNames);
    }

    public static MediaParser createByName(String parserName, OutputConsumer outputConsumer) {
        MediaParser p = new MediaParser(outputConsumer, parserName);
        p.mSelectedParserName = parserName != null ? parserName : "";
        return p;
    }

    public String getParserName() { return mSelectedParserName; }

    public MediaParser setParameter(String parameterName, Object value) { return this; }

    public boolean supportsParameter(String parameterName) { return false; }

    public void setLogSessionId(android.media.metrics.LogSessionId logSessionId) { }

    public android.media.metrics.LogSessionId getLogSessionId() {
        return android.media.metrics.LogSessionId.LOG_SESSION_ID_NONE;
    }

    /** No platform parse; report end-of-input so the caller uses its own path. */
    public boolean advance(SeekableInputReader seekableInputReader) { return false; }

    public void seek(SeekPoint seekPoint) { }

    public void release() { }

    // ---- interfaces / value types apps reference -----------------------------
    public interface OutputConsumer { }

    public interface InputReader {
        int read(byte[] buffer, int offset, int readLength) throws java.io.IOException;
        long getPosition();
        long getLength();
    }

    public interface SeekableInputReader extends InputReader {
        void seekToPosition(long position);
    }

    // KIND: a FINAL CLASS with a PRIVATE constructor, not an interface. Confirmed
    // against the platform jar (`javap -p -constants 'android.media.MediaParser$SeekMap'`):
    //     public final class android.media.MediaParser$SeekMap {
    //       public static final int UNKNOWN_DURATION = -2147483648;
    //       private android.media.MediaParser$SeekMap();
    //       public boolean isSeekable();
    //       public long getDurationMicros();
    //       public android.util.Pair<...SeekPoint, ...SeekPoint> getSeekPoints(long);
    //     }
    // Apps never implement or construct one -- they only RECEIVE it from
    // MediaParser (AOSP's wraps an internal ExoPlayer SeekMap). Declaring it as
    // an interface meant a guest that wrote `extends MediaParser.SeekMap` failed
    // to link with IncompatibleClassChangeError. Nothing in this tree references
    // it, so nothing needed adapting.
    public static final class SeekMap {
        public static final int UNKNOWN_DURATION = Integer.MIN_VALUE;
        private SeekMap() {}
        public long getDurationMicros() { return UNKNOWN_DURATION; }
        public boolean isSeekable() { return false; }
        public Pair<SeekPoint, SeekPoint> getSeekPoints(long timeMicros) {
            return new Pair<SeekPoint, SeekPoint>(SeekPoint.START, SeekPoint.START);
        }
    }

    public static final class SeekPoint {
        public static final SeekPoint START = new SeekPoint(0, 0);
        public final long timeMicros;
        public final long position;
        public SeekPoint(long timeMicros, long position) {
            this.timeMicros = timeMicros;
            this.position = position;
        }
        @Override public String toString() {
            return "[timeMicros=" + timeMicros + ", position=" + position + "]";
        }
    }

    public static final class TrackData {
        public final MediaFormat mediaFormat;
        public TrackData(MediaFormat mediaFormat) { this.mediaFormat = mediaFormat; }
        public android.media.DrmInitData drmInitData;
    }

    public static final class UnrecognizedInputFormatException extends java.io.IOException {
        public UnrecognizedInputFormatException(String message) { super(message); }
    }

    public static final class ParsingException extends java.io.IOException {
        public ParsingException(Throwable cause) { super(cause); }
    }
}
