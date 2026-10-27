package hello.nativecodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/** Every audio container/codec the tree ships (MP3, Vorbis, Opus, FLAC, WAV/raw) through the real MediaExtractor and the tree's own software codec: real PCM. */
final class AudioFormatsCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }

    /** asset, expected MIME, expected codec name; every fixture is 2 s of a half-scale 440 Hz stereo sine at 48 kHz. */
    private static final String[][] CASES = {
        {"tone.mp3", "audio/mpeg", "c2.android.mp3.decoder"},
        {"tone-vorbis.ogg", "audio/vorbis", "c2.android.vorbis.decoder"},
        {"tone-opus.ogg", "audio/opus", "c2.android.opus.decoder"},
        {"tone.flac", "audio/raw", "c2.android.raw.decoder"},  // the FLAC extractor decodes (libFLAC) and hands out raw PCM
        {"tone.wav", "audio/raw", "c2.android.raw.decoder"},
    };

    static void run(Context context) throws Exception {
        for (String[] c : CASES) decode(context, c[0], c[1], c[2]);
    }

    private static void decode(Context context, String asset, String mime, String codecName) throws Exception {
        File path = new File(context.getCacheDir(), asset);
        try (InputStream in = context.getAssets().open(asset)) { Files.write(path.toPath(), in.readAllBytes()); }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try (FileInputStream input = new FileInputStream(path)) {
            extractor.setDataSource(input.getFD());
            int track = -1;
            for (int n = 0; n < extractor.getTrackCount() && track < 0; n++)
                if (extractor.getTrackFormat(n).getString(MediaFormat.KEY_MIME).startsWith("audio/")) track = n;
            require(track >= 0, asset + ": no audio track");
            MediaFormat format = extractor.getTrackFormat(track);
            require(mime.equals(format.getString(MediaFormat.KEY_MIME)), asset + ": MIME " + format.getString(MediaFormat.KEY_MIME));
            extractor.selectTrack(track);
            codec = MediaCodec.createDecoderByType(mime);
            require(codec.getName().equals(codecName), asset + ": codec " + codec.getName());
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            short[] left = new short[4 * 48000];
            int frames = 0, channels = 2, rate = 48000;
            long lastPts = -1;
            boolean inputDone = false, eos = false;
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (!eos) {
                require(System.nanoTime() < deadline, asset + ": decode exceeded 15 s");
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        int size = extractor.readSampleData(codec.getInputBuffer(index), 0);
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat changed = codec.getOutputFormat();
                    channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                } else if (out >= 0) {
                    if (info.size > 0) {
                        require(info.presentationTimeUs >= lastPts, asset + ": PTS must not go backwards");
                        lastPts = info.presentationTimeUs;
                        ByteBuffer pcm = codec.getOutputBuffer(out).order(ByteOrder.LITTLE_ENDIAN);
                        pcm.position(info.offset).limit(info.offset + info.size);
                        while (pcm.remaining() >= 2 * channels && frames < left.length) {
                            left[frames++] = pcm.getShort();
                            for (int ch = 1; ch < channels; ch++) pcm.getShort();
                        }
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(out, false);
                }
            }
            require(rate == 48000 && channels == 2, asset + ": output " + rate + " Hz x " + channels);
            // 2 s; codecs add or trim priming samples, so a few percent
            require(frames >= 2 * 48000 - 4800 && frames <= 2 * 48000 + 4800, asset + ": " + frames + " frames");
            int from = frames / 2 - 24000, to = from + 48000, crossings = 0;
            double sum = 0;
            for (int n = from; n < to; n++) {
                sum += (double) left[n] * left[n];
                if (n > from && (left[n - 1] < 0) != (left[n] < 0)) crossings++;
            }
            double rms = Math.sqrt(sum / (to - from)), hertz = crossings / 2.0;
            require(rms > 8000 && rms < 15000, asset + ": rms=" + rms);
            require(hertz > 430 && hertz < 450, asset + ": " + hertz + " Hz");
            System.out.println("[hello-native-codec] AUDIO PASS " + asset + " via " + codecName + ": " + frames + " samples, rms=" + (int) rms
                    + ", " + (int) hertz + " Hz, PTS monotonic, EOS");
        } finally {
            if (codec != null) { try { codec.stop(); } catch (RuntimeException ignored) {} codec.release(); }
            extractor.release();
            Files.deleteIfExists(path.toPath());
        }
    }

    private AudioFormatsCheck() {}
}
