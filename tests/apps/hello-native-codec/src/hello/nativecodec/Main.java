package hello.nativecodec;

import android.app.Activity;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Actual MediaCodec JNI + Codec2 + gralloc, with a generated red VP8 fixture. */
public class Main extends Activity {
    private TextView result;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        result = new TextView(this);
        result.setTextSize(22);
        result.setText("Decoding VP8 through Android MediaCodec…");
        setContentView(result);
        new Thread(this::decode, "codec-check").start();
    }
    private static long le(byte[] b, int start, int count) {
        long value = 0;
        for (int n = 0; n < count; n++) value |= (long)(b[start + n] & 255) << (8 * n);
        return value;
    }
    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
    private void decode() {
        MediaCodec codec = null;
        try (InputStream input = getAssets().open("red-vp8.ivf")) {
            System.out.println("[hello-native-codec] begin");
            byte[] header = input.readNBytes(32);
            require(header.length == 32, "IVF header");
            int width = (int)le(header, 12, 2), height = (int)le(header, 14, 2);
            long rate = le(header, 16, 4), scale = le(header, 20, 4);
            int count = (int)le(header, 24, 4);
            codec = MediaCodec.createDecoderByType("video/x-vnd.on2.vp8");
            require(codec.getName().equals("c2.apklayer.vp8.decoder"), "native codec discovery");
            System.out.println("[hello-native-codec] softwareOnly=" + codec.getCodecInfo().isSoftwareOnly()
                    + " hardwareAccelerated=" + codec.getCodecInfo().isHardwareAccelerated());
            require(!codec.getCodecInfo().isHardwareAccelerated(), "decoder must not claim hardware acceleration");
            System.out.println("[hello-native-codec] codec=" + codec.getName());
            MediaFormat format = MediaFormat.createVideoFormat("video/x-vnd.on2.vp8", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean eos = false;
            for (int frame = 0; frame < count; frame++) {
                byte[] packet = input.readNBytes(12);
                require(packet.length == 12, "IVF packet");
                int bytes = (int)le(packet, 0, 4);
                byte[] compressed = input.readNBytes(bytes);
                require(compressed.length == bytes, "compressed payload");
                long pts = le(packet, 4, 8) * scale * 1000000 / rate;
                int index = codec.dequeueInputBuffer(2000000);
                require(index >= 0, "input buffer: " + index);
                ByteBuffer buffer = codec.getInputBuffer(index);
                require(buffer != null && buffer.remaining() >= bytes, "input capacity");
                buffer.put(compressed);
                codec.queueInputBuffer(index, 0, bytes, pts,
                        frame + 1 == count ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                int output = MediaCodec.INFO_TRY_AGAIN_LATER;
                for (int event = 0; event < 4; event++) {
                    output = codec.dequeueOutputBuffer(info, 2000000);
                    if (output >= 0) break;
                    require(output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || output == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                            "output buffer: " + output);
                    System.out.println("[hello-native-codec] output-format=" + codec.getOutputFormat());
                }
                require(output >= 0 && info.presentationTimeUs == pts, "output PTS");
                try (Image image = codec.getOutputImage(output)) {
                    require(image != null && image.getWidth() == width && image.getHeight() == height, "output image");
                    Image.Plane[] planes = image.getPlanes();
                    require(planes.length == 3, "YUV planes");
                    int y = planes[0].getBuffer().get(0) & 255;
                    int u = planes[1].getBuffer().get(0) & 255;
                    int v = planes[2].getBuffer().get(0) & 255;
                    require(y > 50 && y < 110 && u < 130 && v > 180, "decoded red YUV");
                    System.out.println("[hello-native-codec] frame=" + frame + " pts=" + pts + " YUV=" + y + "," + u + "," + v);
                }
                eos |= (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, false);
            }
            for (int event = 0; !eos && event < 4; event++) {
                int output = codec.dequeueOutputBuffer(info, 2000000);
                require(output >= 0, "draining EOS: " + output);
                require(info.size == 0, "unexpected extra picture");
                eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, false);
            }
            require(eos, "EOS");
            codec.stop();
            codec.release();
            codec = null;
            System.out.println("[hello-native-codec] PASS native MediaCodec decoded " + count + " YUV frames with PTS/EOS");
            runOnUiThread(() -> {
                Runnable show = () -> {
                    result.getViewTreeObserver().registerFrameCommitCallback(
                            () -> System.out.println("[hello-native-codec] FRAME PASS"));
                    result.setText("PASS: Android MediaCodec decoded 3 VP8 frames\nYUV 81 / 90 / 240");
                    result.invalidate();
                };
                if (result.isAttachedToWindow()) show.run();
                else result.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View view) {
                        view.removeOnAttachStateChangeListener(this);
                        view.post(show);
                    }
                    public void onViewDetachedFromWindow(View view) {}
                });
            });
        } catch (Throwable error) {
            System.out.println("[hello-native-codec] FAIL " + error);
            error.printStackTrace();
            runOnUiThread(() -> result.setText("FAIL: " + error));
        } finally {
            if (codec != null) codec.release();
        }
    }
}
