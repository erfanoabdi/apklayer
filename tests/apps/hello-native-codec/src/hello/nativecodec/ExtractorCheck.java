package hello.nativecodec;

import android.content.Context;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Native MP4 extraction over binder, then actual MediaCodec decode of its samples/CSD. */
final class ExtractorCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    private static final class Source extends MediaDataSource {
        private final byte[] data;
        final AtomicInteger reads = new AtomicInteger();
        private volatile boolean closed;
        Source(byte[] data) { this.data = data; }
        public int readAt(long position, byte[] buffer, int offset, int size) {
            reads.incrementAndGet();
            if (size == 0) return 0;
            if (closed || position < 0 || position >= data.length) return -1;
            int length = (int)Math.min(size, data.length - position);
            System.arraycopy(data, (int)position, buffer, offset, length);
            return length;
        }
        public long getSize() { return data.length; }
        public void close() { closed = true; }
    }
    static void run(Context context) throws Exception {
        byte[] mp4;
        try (InputStream input = context.getAssets().open("red-h264.mp4")) { mp4 = input.readAllBytes(); }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        Source source = new Source(mp4);
        try {
            extractor.setDataSource(source);
            require(source.reads.get() > 0 && extractor.getTrackCount() == 1, "native MP4 track/callback reads");
            MediaFormat format = extractor.getTrackFormat(0);
            require("video/avc".equals(format.getString(MediaFormat.KEY_MIME)), "native MP4 MIME");
            require(format.getInteger(MediaFormat.KEY_WIDTH) == 64 && format.getInteger(MediaFormat.KEY_HEIGHT) == 48,
                    "native MP4 dimensions");
            require(format.containsKey("csd-0"), "extractor AVC initialization data");
            System.out.println("[hello-native-codec] extractor format=" + format);
            extractor.selectTrack(0);
            long first = extractor.getSampleTime();
            ByteBuffer scan = ByteBuffer.allocateDirect(1024 * 1024);
            int count = 0;
            while (extractor.getSampleTrackIndex() >= 0) {
                require(extractor.getSampleTrackIndex() == 0 && extractor.readSampleData(scan, 0) > 0, "native MP4 sample");
                count++;
                if (!extractor.advance()) break;
            }
            require(count == 12 && extractor.getSampleTime() == -1, "native MP4 sample count/EOS");
            extractor.seekTo(first, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            require(extractor.getSampleTime() == first && (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0,
                    "native MP4 seek to keyframe");
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            codec = MediaCodec.createDecoderByType("video/avc");
            codec.configure(format, null, null, 0);
            codec.start();
            long[] pts = new long[count];
            for (int n = 0; n < count; n++) {
                int index = codec.dequeueInputBuffer(2000000);
                require(index >= 0, "extracted decoder input");
                int bytes = extractor.readSampleData(codec.getInputBuffer(index), 0);
                pts[n] = extractor.getSampleTime();
                require(bytes > 0 && pts[n] >= 0, "extracted sample bytes/PTS");
                codec.queueInputBuffer(index, 0, bytes, pts[n], n + 1 == count ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                extractor.advance();
            }
            Arrays.sort(pts);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int pictures = 0;
            boolean eos = false;
            for (int event = 0; event < count + 5 && !eos; event++) {
                int output = codec.dequeueOutputBuffer(info, 2000000);
                if (output < 0) {
                    require(output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || output == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                            "extracted decoder output " + output);
                    continue;
                }
                if (info.size > 0) {
                    require(pictures < count && info.presentationTimeUs == pts[pictures], "extracted output PTS");
                    try (Image image = codec.getOutputImage(output)) {
                        require(image != null && image.getWidth() == 64 && image.getHeight() == 48, "extracted output image");
                        Image.Plane[] planes = image.getPlanes();
                        int y = planes[0].getBuffer().get(0) & 255, u = planes[1].getBuffer().get(0) & 255, v = planes[2].getBuffer().get(0) & 255;
                        require(y > 50 && y < 110 && u < 130 && v > 180, "extracted red YUV");
                    }
                    System.out.println("[hello-native-codec] extracted picture=" + pictures++ + " pts=" + info.presentationTimeUs);
                }
                eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, false);
            }
            require(eos && pictures == count, "extracted pictures/EOS");
            codec.stop(); codec.release(); codec = null;
            System.out.println("[hello-native-codec] extractor PASS MP4 tracks, seek, 12 decoded YUV pictures/PTS/EOS; callback reads=" + source.reads.get());
        } finally {
            try { if (codec != null) codec.release(); }
            finally { try { extractor.release(); } finally { source.close(); } }
        }
    }
    private ExtractorCheck() {}
}
