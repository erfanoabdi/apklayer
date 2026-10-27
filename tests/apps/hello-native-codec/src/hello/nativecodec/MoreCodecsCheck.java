package hello.nativecodec;

import android.content.Context;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/** The rest of the tree's software decoders (HEVC, MPEG-4, AV1, G.711) through the real extractors: real frames and PCM. */
final class MoreCodecsCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }

    // asset, MIME, expected decoder
    private static final String[][] VIDEO = {
        {"red-hevc.mp4", "video/hevc", "c2.android.hevc.decoder"},
        {"red-mpeg4.mp4", "video/mp4v-es", "c2.android.mpeg4.decoder"},
        {"red-av1.webm", "video/av01", "c2.android.av1-dav1d.decoder"},
        {"red-h263.3gp", "video/3gpp", "c2.android.h263.decoder"},
        {"red-mpeg2.ts", "video/mpeg2", "c2.android.mpeg2.decoder"},
    };
    private static final String[][] AUDIO = {
        {"tone-mulaw.wav", "audio/g711-mlaw", "c2.android.g711.mlaw.decoder"},
        {"tone-alaw.wav", "audio/g711-alaw", "c2.android.g711.alaw.decoder"},
        {"tone-amrwb.awb", "audio/amr-wb", "c2.android.amrwb.decoder"},  // 16 kHz
        {"zeros-amrnb.amr", "audio/3gpp", "c2.android.amrnb.decoder"},  // no AMR-NB encoder here: 50 all-zero 12.2 kbit/s frames, only the sample count is checked
    };

    static void run(Context context) throws Exception {
        for (String[] c : VIDEO) video(context, c[0], c[1], c[2]);
        for (String[] c : AUDIO) {
            try { audio(context, c[0], c[1], c[2]); } catch (RuntimeException e) { e.printStackTrace(System.out); throw e; }
        }
    }

    private static File copy(Context context, String asset) throws Exception {
        File path = new File(context.getCacheDir(), asset);
        try (InputStream in = context.getAssets().open(asset)) { Files.write(path.toPath(), in.readAllBytes()); }
        return path;
    }

    private static MediaFormat track(MediaExtractor extractor, String prefix, String mime) {
        for (int n = 0; n < extractor.getTrackCount(); n++) {
            MediaFormat f = extractor.getTrackFormat(n);
            if (f.getString(MediaFormat.KEY_MIME).startsWith(prefix)) { extractor.selectTrack(n); require(mime.equals(f.getString(MediaFormat.KEY_MIME)), "MIME " + f.getString(MediaFormat.KEY_MIME)); return f; }
        }
        throw new AssertionError("no " + prefix + " track");
    }

    private static void video(Context context, String asset, String mime, String codecName) throws Exception {
        File path = copy(context, asset);
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try (FileInputStream input = new FileInputStream(path)) {
            extractor.setDataSource(input.getFD());
            MediaFormat format = track(extractor, "video/", mime);
            codec = MediaCodec.createDecoderByType(mime);
            require(codec.getName().equals(codecName), asset + ": codec " + codec.getName());
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int frames = 0;
            double luma = 0;
            boolean inputDone = false, eos = false;
            long deadline = System.nanoTime() + 20_000_000_000L;
            while (!eos) {
                require(System.nanoTime() < deadline, asset + ": decode exceeded 20 s");
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        int size = extractor.readSampleData(codec.getInputBuffer(index), 0);
                        if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else { codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0); extractor.advance(); }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 10_000);
                if (out >= 0) {
                    if (info.size > 0) {
                        Image image = codec.getOutputImage(out);
                        require(image != null, asset + ": no image");
                        Image.Plane y = image.getPlanes()[0];
                        ByteBuffer b = y.getBuffer();
                        int w = image.getWidth(), h = image.getHeight(), sum = 0;
                        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) sum += b.get(row * y.getRowStride() + col * y.getPixelStride()) & 255;
                        luma += (double) sum / (w * h);
                        frames++;
                        image.close();
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(out, false);
                }
            }
            luma /= Math.max(frames, 1);
            // solid red (255,0,0): Y = 81 limited range
            require(frames >= (asset.endsWith(".ts") ? 5 : 8) && frames <= 12, asset + ": " + frames + " frames"); // the TS extractor holds back the last access units of a tiny stream
            require(luma > 70 && luma < 95, asset + ": mean luma " + luma);
            System.out.println("[hello-native-codec] VIDEO PASS " + asset + " via " + codecName + ": " + frames + " frames, mean luma " + (int) luma + ", EOS");
        } finally {
            if (codec != null) { try { codec.stop(); } catch (RuntimeException ignored) {} codec.release(); }
            extractor.release();
            Files.deleteIfExists(path.toPath());
        }
    }

    private static void audio(Context context, String asset, String mime, String codecName) throws Exception {
        File path = copy(context, asset);
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try (FileInputStream input = new FileInputStream(path)) {
            extractor.setDataSource(input.getFD());
            MediaFormat format = track(extractor, "audio/", mime);
            codec = MediaCodec.createDecoderByType(mime);
            require(codec.getName().equals(codecName), asset + ": codec " + codec.getName());
            if (!format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536); // the WAV extractor hands out big chunks
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 8000;
            short[] pcm = new short[rate * 3];
            int samples = 0;
            boolean inputDone = false, eos = false;
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (!eos) {
                require(System.nanoTime() < deadline, asset + ": decode exceeded 15 s");
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        int size = extractor.readSampleData(codec.getInputBuffer(index), 0);
                        if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else { codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0); extractor.advance(); }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 10_000);
                if (out >= 0) {
                    if (info.size > 0) {
                        ByteBuffer b = codec.getOutputBuffer(out).order(ByteOrder.LITTLE_ENDIAN);
                        b.position(info.offset).limit(info.offset + info.size);
                        while (b.remaining() >= 2 && samples < pcm.length) pcm[samples++] = b.getShort();
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(out, false);
                }
            }
            if (asset.startsWith("zeros-")) {
                require(samples == 50 * 160, asset + ": " + samples + " samples");
                System.out.println("[hello-native-codec] AUDIO PASS " + asset + " via " + codecName + ": " + samples + " samples, EOS");
                return;
            }
            require(samples >= 2 * rate - 1000 && samples <= 2 * rate + 1000, asset + ": " + samples + " samples at " + rate + " Hz");
            int from = samples / 2 - 2000, to = from + 4000, crossings = 0;
            double sum = 0;
            for (int n = from; n < to; n++) { sum += (double) pcm[n] * pcm[n]; if (n > from && (pcm[n - 1] < 0) != (pcm[n] < 0)) crossings++; }
            double rms = Math.sqrt(sum / (to - from)), hertz = crossings / 2.0 / (4000.0 / rate);
            require(rms > 6000 && rms < 15000, asset + ": rms " + rms); // AMR-WB loses some level
            require(hertz > 425 && hertz < 455, asset + ": " + hertz + " Hz");
            System.out.println("[hello-native-codec] AUDIO PASS " + asset + " via " + codecName + ": " + samples + " samples, rms=" + (int) rms + ", " + (int) hertz + " Hz, EOS");
        } finally {
            if (codec != null) { try { codec.stop(); } catch (RuntimeException ignored) {} codec.release(); }
            extractor.release();
            Files.deleteIfExists(path.toPath());
        }
    }

    private MoreCodecsCheck() {}
}
