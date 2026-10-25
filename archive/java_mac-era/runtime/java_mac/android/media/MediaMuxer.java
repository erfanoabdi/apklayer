// android.media.MediaMuxer -- multiplexes encoded elementary streams into a
// container file. AOSP frameworks/base media/java/android/media/MediaMuxer.java.
//
// Mac.apk has no muxer backend, so this implements the full state machine
// (addTrack before start, writeSampleData while started, stop) but writes no
// container data: the output file is created empty. Apps that drive a muxer
// (screen recorders, transcoders) run their loop without a NoSuchMethodError or
// IllegalState; the produced file is simply empty -- honest for a no-mux device.
package android.media;
import java.nio.ByteBuffer;

public final class MediaMuxer {
    public static final class OutputFormat {
        public static final int MUXER_OUTPUT_MPEG_4 = 0;
        public static final int MUXER_OUTPUT_WEBM = 1;
        public static final int MUXER_OUTPUT_3GPP = 2;
        public static final int MUXER_OUTPUT_HEIF = 3;
        public static final int MUXER_OUTPUT_OGG = 4;
        private OutputFormat() {}
    }

    private int mNextTrack = 0;
    private boolean mStarted = false;
    private final String mPath;

    public MediaMuxer(String path, int format) throws java.io.IOException {
        mPath = path;
        if (path != null) {
            try { new java.io.FileOutputStream(path).close(); }
            catch (Throwable ignore) {}
        }
    }
    public MediaMuxer(java.io.FileDescriptor fd, int format) throws java.io.IOException {
        mPath = null;
    }

    public int addTrack(MediaFormat format) {
        if (mStarted) throw new IllegalStateException("Muxer is already started");
        return mNextTrack++;
    }
    public void setOrientationHint(int degrees) {}
    public void setLocation(float latitude, float longitude) {}
    public void start() {
        if (mStarted) throw new IllegalStateException("Can't start. Muxer already started");
        mStarted = true;
    }
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        if (!mStarted) throw new IllegalStateException("Can't write. Muxer not started");
        // No container backend: drain the buffer position the way a real muxer
        // would consume it, but discard the bytes.
        if (byteBuf != null && bufferInfo != null) {
            byteBuf.position(bufferInfo.offset + bufferInfo.size);
        }
    }
    public void stop() {
        if (!mStarted) throw new IllegalStateException("Can't stop. Muxer is not started");
        mStarted = false;
    }
    public void release() { mStarted = false; }
}
