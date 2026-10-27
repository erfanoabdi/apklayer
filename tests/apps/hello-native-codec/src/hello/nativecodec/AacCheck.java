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

/** Real MediaExtractor (MP4/AAC) into the real MediaCodec AAC decoder (Codec2 component over the host's AAC decoder): actual PCM. */
final class AacCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    static void run(Context context) throws Exception {
        File path = new File(context.getCacheDir(), "tone-aac.m4a");
        try (InputStream asset = context.getAssets().open("tone-aac.m4a")) { Files.write(path.toPath(), asset.readAllBytes()); }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try (FileInputStream input = new FileInputStream(path)) {
            extractor.setDataSource(input.getFD());
            int track = -1;
            for (int n = 0; n < extractor.getTrackCount() && track < 0; n++)
                if (extractor.getTrackFormat(n).getString(MediaFormat.KEY_MIME).startsWith("audio/")) track = n;
            require(track >= 0, "AAC track");
            MediaFormat format = extractor.getTrackFormat(track);
            require("audio/mp4a-latm".equals(format.getString(MediaFormat.KEY_MIME)), "AAC MIME");
            require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == 48000 && format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2, "AAC rate/channels");
            extractor.selectTrack(track);
            codec = MediaCodec.createDecoderByType("audio/mp4a-latm");
            require(codec.getName().equals("c2.apklayer.aac.decoder"), "native AAC codec discovery: " + codec.getName());
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            short[] left = new short[8 * 48000];
            int frames = 0, channels = 2;
            long lastPts = -1;
            boolean inputDone = false, eos = false;
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (!eos) {
                require(System.nanoTime() < deadline, "AAC decode exceeded 15 s");
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        int size = extractor.readSampleData(buffer, 0);
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
                    require(changed.getInteger(MediaFormat.KEY_SAMPLE_RATE) == 48000 && channels == 2, "AAC output format");
                } else if (out >= 0) {
                    if (info.size > 0) {
                        require(info.presentationTimeUs >= lastPts, "AAC PTS must not go backwards");
                        lastPts = info.presentationTimeUs;
                        ByteBuffer pcm = codec.getOutputBuffer(out).order(ByteOrder.LITTLE_ENDIAN);
                        pcm.position(info.offset).limit(info.offset + info.size);
                        while (pcm.remaining() >= 2 * channels && frames < left.length) {
                            left[frames++] = pcm.getShort();
                            for (int c = 1; c < channels; c++) pcm.getShort();
                        }
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(out, false);
                }
            }
            require(frames >= 100 * 1024 - 2048 && frames <= 100 * 1024 + 2048, "AAC decoded sample count " + frames);
            // the fixture is a 440 Hz sine at half scale: amplitude and pitch of the middle second
            int from = frames / 2 - 24000, to = from + 48000, crossings = 0;
            double sum = 0;
            for (int n = from; n < to; n++) {
                sum += (double)left[n] * left[n];
                if (n > from && (left[n - 1] < 0) != (left[n] < 0)) crossings++;
            }
            double rms = Math.sqrt(sum / (to - from));
            double hertz = crossings / 2.0;
            require(rms > 8000 && rms < 15000, "AAC amplitude rms=" + rms);
            require(hertz > 430 && hertz < 450, "AAC pitch " + hertz + " Hz");
            System.out.println("[hello-native-codec] AAC PASS real extractor to c2.apklayer.aac.decoder: " + frames
                    + " samples, rms=" + (int)rms + ", " + (int)hertz + " Hz, PTS monotonic, EOS");
        } finally {
            if (codec != null) { try { codec.stop(); } catch (RuntimeException ignored) {} codec.release(); }
            extractor.release();
            Files.deleteIfExists(path.toPath());
        }
    }
    private AacCheck() {}
}
