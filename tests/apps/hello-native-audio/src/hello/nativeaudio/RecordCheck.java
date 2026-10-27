package hello.nativeaudio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

/** AudioRecord through the real AudioFlinger input path and the audio HAL's capture: the machine plays 440 Hz (left) / 880 Hz (right) into a sink,
 *  the HAL captures that sink's monitor (APKLAYER_AUDIO_TARGET_IN), the app reads it back at real time. */
final class RecordCheck {
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

    static void run() throws Exception {
        final int rate = 48000, seconds = 4;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(rate).setTransferMode(AudioTrack.MODE_STREAM).build();
        AudioRecord record = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(rate * 4).build();
        Thread player = new Thread(() -> {
            short[] pcm = new short[960 * 2];
            for (int offset = 0; offset < rate * seconds; offset += 960) {
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
        }, "record-check-player");
        try {
            require(record.getState() == AudioRecord.STATE_INITIALIZED, "AudioRecord initialized");
            track.play();
            player.start();
            record.startRecording();
            require(record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING, "recording state");
            short[] pcm = new short[rate * 2 * 2];
            int frames = 0;
            long start = System.nanoTime();
            // read 2.5 s: the first half second is dropped (start-up), the rest is analysed
            short[] chunk = new short[960 * 2];
            short[] left = new short[rate * 3], right = new short[rate * 3];
            int total = 0;
            while (total < rate * 5 / 2) {
                int n = record.read(chunk, 0, chunk.length, AudioRecord.READ_BLOCKING);
                require(n > 0, "AudioRecord.read returned " + n);
                for (int i = 0; i + 1 < n && total < left.length; i += 2, total++) { left[total] = chunk[i]; right[total] = chunk[i + 1]; }
                require(System.nanoTime() - start < 15_000_000_000L, "AudioRecord read exceeded 15 s");
            }
            double wall = (System.nanoTime() - start) / 1e9;
            int skip = rate / 2, count = total - skip;
            short[] l = java.util.Arrays.copyOfRange(left, skip, total), r = java.util.Arrays.copyOfRange(right, skip, total);
            double l440 = power(l, count, 440, rate), l880 = power(l, count, 880, rate), r880 = power(r, count, 880, rate), r440 = power(r, count, 440, rate);
            System.out.println("[hello-native-audio] RECORD read " + total + " frames in " + String.format("%.2f", wall) + " s, left 440/880 = "
                    + (long) (l440 / Math.max(l880, 1)) + ", right 880/440 = " + (long) (r880 / Math.max(r440, 1)));
            require(wall > 2.0 && wall < 3.6, "recording 2.5 s took " + wall + " s");
            require(l440 > l880 * 50, "left tone lost/swapped");
            require(r880 > r440 * 50, "right tone lost/swapped");
            record.stop();
            System.out.println("[hello-native-audio] RECORD PASS");
        } finally {
            record.release();
            track.stop();
            track.release();
            player.join(5000);
        }
    }

    private RecordCheck() {}
}
