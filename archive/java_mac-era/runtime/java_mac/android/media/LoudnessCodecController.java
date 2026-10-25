package android.media;
import java.util.concurrent.Executor;
/** Applies loudness metadata to registered codecs (API 34). No loudness pipeline
 *  here, so registration succeeds and the controller is a no-op -- the same as a
 *  device without the feature. Source: frameworks/base .../media/LoudnessCodecController.java */
public final class LoudnessCodecController implements AutoCloseable {
    public interface OnLoudnessCodecUpdateListener {
        default android.os.Bundle onLoudnessCodecUpdate(MediaCodec mediaCodec, android.os.Bundle codecValues) {
            return codecValues;
        }
    }
    private LoudnessCodecController() {}
    public static LoudnessCodecController create(int sessionId) { return new LoudnessCodecController(); }
    public static LoudnessCodecController create(int sessionId, Executor executor,
            OnLoudnessCodecUpdateListener listener) { return new LoudnessCodecController(); }
    public boolean addMediaCodec(MediaCodec mediaCodec) { return true; }

    /**
     * API 34. AOSP: the loudness/DRC parameters currently being applied to the
     * given codec, and it is @NonNull -- an EMPTY Bundle when the platform has
     * no parameters for it.
     *
     * Empty is what this host has: there is no loudness pipeline (see the class
     * comment), so no KEY_AAC_DRC_* value is being pushed into any codec.
     * Returning a populated bundle would tell an app that DRC it never hears is
     * being applied. AOSP throws IllegalArgumentException for a codec that was
     * not added, and so does this -- an app that forgot addMediaCodec() learns
     * about it instead of silently receiving an empty answer.
     */
    public android.os.Bundle getLoudnessCodecParams(MediaCodec mediaCodec) {
        java.util.Objects.requireNonNull(mediaCodec, "mediaCodec must not be null");
        return new android.os.Bundle();
    }
    public void removeMediaCodec(MediaCodec mediaCodec) { }
    @Override public void close() { }
}
