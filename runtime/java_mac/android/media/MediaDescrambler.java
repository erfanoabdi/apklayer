package android.media;

import java.nio.ByteBuffer;

/**
 * Descrambles a conditional-access-protected transport stream using a MediaCas
 * session.
 *
 * Mac.apk has no CAS plugin and would not add one -- descrambling protected
 * broadcast content is out of scope for this project by the same rule that
 * keeps DRM out (CLAUDE.md, "Explicitly OUT of scope"). AOSP's own contract for
 * a CA system the device cannot service is
 * MediaCasException.UnsupportedCasException from the constructor, which is what
 * MediaCas itself already reports, so this class refuses identically and every
 * downstream method is unreachable.
 *
 * Source: frameworks/base/media/java/android/media/MediaDescrambler.java
 */
public final class MediaDescrambler implements AutoCloseable {

    public static final byte SCRAMBLE_CONTROL_UNSCRAMBLED = 0;
    public static final byte SCRAMBLE_CONTROL_RESERVED = 1;
    public static final byte SCRAMBLE_CONTROL_EVEN_KEY = 2;
    public static final byte SCRAMBLE_CONTROL_ODD_KEY = 3;

    public static final byte SCRAMBLE_FLAG_PES_HEADER = 1;

    public MediaDescrambler(int casSystemId)
            throws MediaCasException.UnsupportedCasException {
        throw new MediaCasException.UnsupportedCasException(
                "No CAS plugin is available for system id " + casSystemId);
    }

    private static IllegalStateException noPlugin() {
        return new IllegalStateException("No CAS plugin available");
    }

    /** AOSP: whether the scrambled stream needs a secure decoder. Unreachable
     *  past the constructor. */
    public boolean requiresSecureDecoderComponent(String mime) { throw noPlugin(); }

    public void setMediaCasSession(MediaCas.Session session) { throw noPlugin(); }

    public int descramble(ByteBuffer srcBuf, ByteBuffer dstBuf,
            MediaCodec.CryptoInfo cryptoInfo) {
        throw noPlugin();
    }

    @Override public void close() { }

    @Override protected void finalize() throws Throwable {
        try { close(); } finally { super.finalize(); }
    }
}
