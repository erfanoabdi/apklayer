package hello.nativeaudio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** MediaRecorder (MIC, MPEG-4, AAC) over the real recorder service, AudioRecord and the AAC encoder: the machine plays 440 Hz left / 880 Hz right into the sink the
 *  input captures, the recorder writes an .m4a, and the file is read back through MediaExtractor and decoded: duration, format and tones. */
final class RecorderCheck {
    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }

    private static double power(short[] channel, int count, int hz, int rate) {
        double coefficient = 2 * Math.cos(2 * Math.PI * hz / rate), first = 0, second = 0;
        for (int n = 0; n < count; n++) {
            double current = channel[n] + coefficient * first - second;
            second = first;
            first = current;
        }
        return first * first + second * second - coefficient * first * second;
    }

    static void run(android.content.Context context) throws Exception {
        final int rate = 48000;
        File file = new File(context.getCacheDir(), "recorded.m4a");
        file.delete();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(rate).setTransferMode(AudioTrack.MODE_STREAM).build();
        Thread player = new Thread(() -> {
            short[] pcm = new short[960 * 2];
            for (int offset = 0; offset < rate * 6; offset += 960) {
                for (int n = 0; n < 960; n++) {
                    pcm[n * 2] = (short) (Math.sin(2 * Math.PI * 440 * (offset + n) / rate) * 6000);
                    pcm[n * 2 + 1] = (short) (Math.sin(2 * Math.PI * 880 * (offset + n) / rate) * 4000);
                }
                for (int done = 0; done < pcm.length; ) {
                    int count = track.write(pcm, done, pcm.length - done, AudioTrack.WRITE_BLOCKING);
                    if (count <= 0) return;
                    done += count;
                }
            }
        }, "recorder-check-player");
        MediaRecorder recorder = new MediaRecorder(context);
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(2);
            recorder.setAudioSamplingRate(rate);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            track.play();
            player.start();
            long start = System.nanoTime();
            recorder.start();
            Thread.sleep(3000);
            recorder.stop();
            double wall = (System.nanoTime() - start) / 1e9;
            System.out.println("[hello-native-audio] RECORDER recorded " + String.format("%.2f", wall) + " s into " + file.length() + " bytes");
        } finally {
            recorder.release();
            track.stop();
            track.release();
            player.join(8000);
        }
        require(file.length() > 8000, "recorded file size " + file.length());
        // read it back
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try (FileInputStream in = new FileInputStream(file)) {
            extractor.setDataSource(in.getFD());
            int audio = -1;
            for (int n = 0; n < extractor.getTrackCount() && audio < 0; n++)
                if (extractor.getTrackFormat(n).getString(MediaFormat.KEY_MIME).startsWith("audio/")) audio = n;
            require(audio >= 0, "recorded file has an audio track");
            MediaFormat format = extractor.getTrackFormat(audio);
            require("audio/mp4a-latm".equals(format.getString(MediaFormat.KEY_MIME)), "recorded MIME " + format.getString(MediaFormat.KEY_MIME));
            require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == rate && format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2, "recorded rate/channels");
            long durationUs = format.getLong(MediaFormat.KEY_DURATION);
            require(durationUs > 2_000_000L && durationUs < 4_000_000L, "recorded duration " + durationUs);
            extractor.selectTrack(audio);
            decoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
            decoder.configure(format, null, null, 0);
            decoder.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            short[] left = new short[rate * 5], right = new short[rate * 5];
            int frames = 0;
            boolean inputDone = false, eos = false;
            long deadline = System.nanoTime() + 20_000_000_000L;
            while (!eos) {
                require(System.nanoTime() < deadline, "decoding the recording exceeded 20 s");
                if (!inputDone) {
                    int index = decoder.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        int size = extractor.readSampleData(decoder.getInputBuffer(index), 0);
                        if (size < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else { decoder.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0); extractor.advance(); }
                    }
                }
                int out = decoder.dequeueOutputBuffer(info, 10_000);
                if (out >= 0) {
                    if (info.size > 0) {
                        ByteBuffer b = decoder.getOutputBuffer(out).order(ByteOrder.LITTLE_ENDIAN);
                        b.position(info.offset).limit(info.offset + info.size);
                        while (b.remaining() >= 4 && frames < left.length) { left[frames] = b.getShort(); right[frames] = b.getShort(); frames++; }
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(out, false);
                }
            }
            int skip = rate / 2, count = frames - skip;
            require(count > rate, "decoded recording too short: " + frames + " frames");
            short[] l = java.util.Arrays.copyOfRange(left, skip, frames), r = java.util.Arrays.copyOfRange(right, skip, frames);
            double l440 = power(l, count, 440, rate), l880 = power(l, count, 880, rate), r880 = power(r, count, 880, rate), r440 = power(r, count, 440, rate);
            System.out.println("[hello-native-audio] RECORDER decoded " + frames + " frames, left 440/880 = " + (long) (l440 / Math.max(l880, 1))
                    + ", right 880/440 = " + (long) (r880 / Math.max(r440, 1)));
            require(l440 > l880 * 50, "recorded left tone lost/swapped");
            require(r880 > r440 * 50, "recorded right tone lost/swapped");
            System.out.println("[hello-native-audio] RECORDER PASS");
        } finally {
            if (decoder != null) { try { decoder.stop(); } catch (RuntimeException ignored) {} decoder.release(); }
            extractor.release();
            file.delete();
        }
    }

    private RecorderCheck() {}
}
