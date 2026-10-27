package hello.nativecodec;

import android.content.Context;
import android.view.Surface;
import android.os.Handler;
import android.os.Looper;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import java.io.InputStream;
import java.util.Arrays;

/** Real JNI decoder with B-frames, coded-order input and presentation-order output. */
public final class AvcCheck {
    private static long le(byte[] b, int start, int size) {
        long result = 0;
        for (int n = 0; n < size; n++) result |= (long)(b[start + n] & 255) << (8 * n);
        return result;
    }
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    public static MediaCodec run(Context context, Surface surface) throws Exception {
        String tag = surface == null ? "[hello-native-codec]" : "[hello-native-surface]";
        MediaCodec codec = null;
        try (InputStream input = context.getAssets().open("red-h264.au")) {
            byte[] header = input.readNBytes(12);
            require(header.length == 12 && header[0] == 'A', "AVC fixture");
            int width = (int)le(header, 4, 2), height = (int)le(header, 6, 2), count = (int)le(header, 8, 4);
            long[] pts = new long[count];
            codec = MediaCodec.createDecoderByType("video/avc");
            require(codec.getName().equals("c2.apklayer.avc.decoder"), "AVC discovery");
            require(!codec.getCodecInfo().isHardwareAccelerated(), "software AVC must not claim hardware");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            if (surface == null) format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            else codec.setOnFrameRenderedListener((c, ptsValue, time) ->
                System.out.println(tag + " AVC rendered pts=" + ptsValue), new Handler(Looper.getMainLooper()));
            codec.configure(format, surface, null, 0);
            codec.start();
            byte[][] packets = new byte[count][];
            for (int frame = 0; frame < count; frame++) {
                byte[] packet = input.readNBytes(12);
                require(packet.length == 12, "AVC packet header");
                int bytes = (int)le(packet, 0, 4);
                pts[frame] = le(packet, 4, 8);
                packets[frame] = input.readNBytes(bytes);
                require(packets[frame].length == bytes, "AVC packet payload");
            }
            // Flush with delayed B-frames still pending, then replay the stream.
            int passes = surface == null ? 2 : 1;
            for (int pass = 0; pass < passes; pass++) {
                for (int frame = 0; frame < count; frame++) {
                    int index = codec.dequeueInputBuffer(2000000);
                    require(index >= 0, "AVC input " + index);
                    codec.getInputBuffer(index).put(packets[frame]);
                    boolean last = pass + 1 == passes && frame + 1 == count;
                    codec.queueInputBuffer(index, 0, packets[frame].length, pts[frame], last ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    System.out.println(tag + " AVC input=" + frame + " pts=" + pts[frame]);
                }
                if (pass + 1 < passes) {
                    codec.flush();
                    System.out.println(tag + " AVC flush PASS");
                }
            }
            long[] expected = pts.clone();
            Arrays.sort(expected);
            require(!Arrays.equals(expected, pts), "fixture must contain reordered input");
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int pictures = 0;
            boolean eos = false;
            for (int event = 0; event < count + 5 && !eos; event++) {
                int output = codec.dequeueOutputBuffer(info, 2000000);
                if (output < 0) {
                    require(output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || output == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                            "AVC output " + output);
                    continue;
                }
                boolean picture = info.size > 0 || (surface != null && pictures < count);
                if (picture) {
                    require(pictures < count && info.presentationTimeUs == expected[pictures], "AVC presentation PTS");
                    if (surface == null) try (Image image = codec.getOutputImage(output)) {
                        require(image != null && image.getWidth() == width && image.getHeight() == height, "AVC image");
                        Image.Plane[] planes = image.getPlanes();
                        int y = planes[0].getBuffer().get(0) & 255, u = planes[1].getBuffer().get(0) & 255, v = planes[2].getBuffer().get(0) & 255;
                        require(y > 50 && y < 110 && u < 130 && v > 180, "AVC red YUV");
                    }
                    System.out.println(tag + " AVC picture=" + pictures++ + " pts=" + info.presentationTimeUs);
                }
                eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, surface != null && picture);
            }
            require(eos && pictures == count, "AVC pictures/EOS");
            if (surface != null) {
                MediaCodec live = codec; codec = null;
                System.out.println(tag + " AVC PASS reordered " + count + " Surface pictures, exact PTS/EOS");
                return live;
            }
            codec.stop(); codec.release(); codec = null;
            System.out.println(tag + " AVC PASS reordered " + count + " YUV pictures, exact PTS/EOS/release");
            return null;
        } finally {
            if (codec != null) codec.release();
        }
    }
    private AvcCheck() {}
}
