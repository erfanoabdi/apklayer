package hello.nativeaudio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

/** The audio clock runs at real time: 8 s of PCM written to a stream track play out in 8 s, the presentation position advances at 48000 frames per wall second, no underruns. */
final class RateCheck {
    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }

    static void run() throws Exception {
        final int rate = 48000, seconds = 8, total = rate * seconds, chunk = 960;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(rate * 4 / 4).setTransferMode(AudioTrack.MODE_STREAM).build();
        try {
            require(track.getState() == AudioTrack.STATE_INITIALIZED, "rate track initialized");
            track.play();
            long start = System.nanoTime();
            long t1 = 0, f1 = 0, t2 = 0, f2 = 0;
            AudioTimestamp ts = new AudioTimestamp();
            for (int offset = 0; offset < total; offset += chunk) {
                short[] pcm = new short[chunk * 2];
                for (int n = 0; n < chunk; n++) { // the same stereo content as TrackCheck (440 Hz left, 880 Hz right): the capture check runs over the whole output
                    pcm[n * 2] = (short) (Math.sin(2 * Math.PI * 440 * (offset + n) / rate) * 6000);
                    pcm[n * 2 + 1] = (short) (Math.sin(2 * Math.PI * 880 * (offset + n) / rate) * 4000);
                }
                for (int done = 0; done < pcm.length; ) {
                    int count = track.write(pcm, done, pcm.length - done, AudioTrack.WRITE_BLOCKING);
                    require(count > 0, "rate write " + count);
                    done += count;
                }
                long elapsed = System.nanoTime() - start;
                if (t1 == 0 && elapsed > 2_000_000_000L && track.getTimestamp(ts)) { t1 = ts.nanoTime; f1 = ts.framePosition; }
                if (t2 == 0 && elapsed > 6_000_000_000L && track.getTimestamp(ts)) { t2 = ts.nanoTime; f2 = ts.framePosition; }
            }
            // drain: the head reaches the end
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (Integer.toUnsignedLong(track.getPlaybackHeadPosition()) < total && System.nanoTime() < deadline) Thread.sleep(10);
            double wall = (System.nanoTime() - start) / 1e9;
            require(t1 != 0 && t2 != 0, "rate: no timestamps");
            double framesPerSecond = (f2 - f1) / ((t2 - t1) / 1e9);
            int underruns = track.getUnderrunCount();
            System.out.println("[hello-native-audio] RATE wall=" + String.format("%.2f", wall) + " s for " + seconds + " s of PCM, clock "
                    + String.format("%.0f", framesPerSecond) + " frames/s, underruns=" + underruns);
            require(wall > seconds - 0.7 && wall < seconds + 1.3, "playing 8 s of PCM took " + wall + " s");
            require(framesPerSecond > rate * 0.97 && framesPerSecond < rate * 1.03, "audio clock advances at " + framesPerSecond + " frames/s");
            require(underruns <= 2, "underruns " + underruns);
            System.out.println("[hello-native-audio] RATE PASS");
        } finally { track.release(); }
    }

    private RateCheck() {}
}
