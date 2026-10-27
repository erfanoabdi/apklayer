package hello.nativesurface;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Real MediaCodec output Surface, BufferQueue and SurfaceFlinger child layer. */
public class Main extends Activity implements SurfaceHolder.Callback {
    private TextView status;
    private boolean started;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setText("Waiting for real SurfaceView…");
        layout.addView(status, new LinearLayout.LayoutParams(-1, 70));
        SurfaceView video = new SurfaceView(this);
        video.getHolder().addCallback(this);
        layout.addView(video, new LinearLayout.LayoutParams(-1, -1));
        setContentView(layout);
    }
    public void surfaceCreated(SurfaceHolder holder) {
        System.out.println("[hello-native-surface] created valid=" + holder.getSurface().isValid());
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        System.out.println("[hello-native-surface] changed " + width + "x" + height);
        if (started) return;
        started = true;
        new Thread(() -> decode(holder), "surface-codec-check").start();
    }
    public void surfaceDestroyed(SurfaceHolder holder) {
        System.out.println("[hello-native-surface] destroyed");
    }
    private static long le(byte[] b, int start, int count) {
        long value = 0;
        for (int i = 0; i < count; i++) value |= (long)(b[start + i] & 255) << (8 * i);
        return value;
    }
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    private void decode(SurfaceHolder holder) {
        try {
            decodeStream(holder, "red-vp8.ivf", false);
            decodeStream(holder, "red-vp9.ivf", true);
            System.out.println("[hello-native-surface] DECODE PASS");
        } catch (Throwable error) {
            System.out.println("[hello-native-surface] FAIL " + error);
            error.printStackTrace();
        }
    }
    private void decodeStream(SurfaceHolder holder, String asset, boolean keep) throws Exception {
        MediaCodec codec = null;
        try (InputStream input = getAssets().open(asset)) {
            byte[] header = input.readNBytes(32);
            int count = (int)le(header, 24, 4);
            String kind = header[10] == '9' ? "vp9" : "vp8";
            String mime = "video/x-vnd.on2." + kind;
            codec = MediaCodec.createDecoderByType(mime);
            System.out.println("[hello-native-surface] codec=" + codec.getName());
            codec.setOnFrameRenderedListener((c, pts, time) -> {
                System.out.println("[hello-native-surface] rendered pts=" + pts + " codec=" + kind);
                status.setText("PASS: native " + kind + " → SurfaceView");
            }, new Handler(Looper.getMainLooper()));
            codec.configure(MediaFormat.createVideoFormat(mime,
                    (int)le(header, 12, 2), (int)le(header, 14, 2)), holder.getSurface(), null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean eos = false;
            for (int frame = 0; frame < count; frame++) {
                byte[] packet = input.readNBytes(12);
                int size = (int)le(packet, 0, 4);
                byte[] data = input.readNBytes(size);
                require(data.length == size, "packet");
                long pts = le(packet, 4, 8) * le(header, 20, 4) * 1000000 / le(header, 16, 4);
                int index = codec.dequeueInputBuffer(2000000);
                require(index >= 0, "input " + index);
                ByteBuffer buffer = codec.getInputBuffer(index);
                require(buffer != null, "input buffer");
                buffer.put(data);
                codec.queueInputBuffer(index, 0, size, pts,
                        frame + 1 == count ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                int output = -1;
                for (int event = 0; event < 4; event++) {
                    output = codec.dequeueOutputBuffer(info, 2000000);
                    if (output >= 0) break;
                    require(output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || output == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                            "output " + output);
                }
                require(output >= 0 && info.presentationTimeUs == pts, "output PTS");
                eos |= (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, true);
                System.out.println("[hello-native-surface] queued frame=" + frame + " pts=" + pts);
            }
            for (int event = 0; !eos && event < 4; event++) {
                int output = codec.dequeueOutputBuffer(info, 2000000);
                require(output >= 0 && info.size == 0, "EOS buffer");
                eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, false);
            }
            require(eos, "EOS");
            if (keep) { liveCodec = codec; codec = null; }
            else codec.stop();
        } finally {
            if (codec != null) codec.release();
        }
    }
    private volatile MediaCodec liveCodec;
    @Override protected void onDestroy() {
        MediaCodec codec = liveCodec;
        if (codec != null) { codec.stop(); codec.release(); }
        super.onDestroy();
    }
}
