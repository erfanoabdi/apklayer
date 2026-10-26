package hello.codec;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** P5.2 sample: decodes video frames (/tmp/codec-f-NNN.bin, one encoded frame per file, MIME type in /tmp/codec.mime) with MediaCodec onto a Surface. */
public class Main extends Activity implements SurfaceHolder.Callback {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SurfaceView v = new SurfaceView(this);
        v.getHolder().addCallback(this);
        setContentView(v);
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        new Thread(() -> decode(h)).start();
    }

    private void decode(SurfaceHolder h) {
        try {
            String mime = new String(Files.readAllBytes(new File("/tmp/codec.mime").toPath())).trim();
            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(MediaFormat.createVideoFormat(mime, 320, 240), h.getSurface(), null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int rendered = 0;
            long t0 = System.nanoTime();
            for (int i = 0; new File(String.format("/tmp/codec-f-%03d.bin", i)).exists(); i++) {
                byte[] frame = Files.readAllBytes(new File(String.format("/tmp/codec-f-%03d.bin", i)).toPath());
                int in = codec.dequeueInputBuffer(1_000_000);
                ByteBuffer buf = codec.getInputBuffer(in);
                buf.put(frame);
                codec.queueInputBuffer(in, 0, frame.length, i * 33333L, 0);
                int out = codec.dequeueOutputBuffer(info, 1_000_000);
                if (out >= 0) {
                    codec.releaseOutputBuffer(out, info.size > 0);
                    if (info.size > 0) rendered++;
                }
            }
            System.out.println("[hello-codec] rendered " + rendered + " frames in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
            // the last frame stays on the surface
        } catch (Exception e) {
            System.out.println("[hello-codec] failed: " + e);
            e.printStackTrace(System.out);
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) {}
}
