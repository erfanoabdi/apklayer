package hello.nativeaudio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class TrackCheck {
    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }
    static void run(android.content.Context context) throws Exception {
        AudioManager manager = context.getSystemService(AudioManager.class);
        // what an app finds before it touches the volume: audible (a phone starts at a mid index, not 0 or muted)
        int initial = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
        System.out.println("[hello-native-audio] default music volume " + initial + "/" + manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                + " muted=" + manager.isStreamMute(AudioManager.STREAM_MUSIC));
        require(initial > 0 && !manager.isStreamMute(AudioManager.STREAM_MUSIC), "default music volume is audible");
        manager.setStreamVolume(AudioManager.STREAM_MUSIC,
                manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        require(manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                == manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), "real volume policy");
        int maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
        require(manager.getStreamVolume(AudioManager.STREAM_MUSIC) == maximum - 1, "native volume lower");
        manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
        require(manager.getStreamVolume(AudioManager.STREAM_MUSIC) == maximum, "native volume raise");
        int session = manager.generateAudioSessionId();
        require(session > 0, "real global audio session");
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setSessionId(session).setBufferSizeInBytes(16384).setTransferMode(AudioTrack.MODE_STREAM).build();
        try {
            require(track.getAudioSessionId() == session, "native session preserved");
            require(track.getState() == AudioTrack.STATE_INITIALIZED, "native initialized");
            CountDownLatch marker = new CountDownLatch(1);
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                public void onMarkerReached(AudioTrack ignored) { System.out.println("[hello-native-audio] marker delivered thread=" + Thread.currentThread().getName()); marker.countDown(); }
                public void onPeriodicNotification(AudioTrack ignored) {}
            }, new Handler(Looper.getMainLooper()));
            require(track.setNotificationMarkerPosition(52800) == AudioTrack.SUCCESS, "marker setup");
            track.play();
            long previous = 0;
            boolean queued = false;
            for (int offset = 0; offset < 57600; offset += 960) {
                short[] pcm = new short[1920];
                if (offset < 48000) for (int n = 0; n < 960; n++) {
                    pcm[n * 2] = (short)(Math.sin(2 * Math.PI * 440 * (offset + n) / 48000) * 6000);
                    pcm[n * 2 + 1] = (short)(Math.sin(2 * Math.PI * 880 * (offset + n) / 48000) * 4000);
                }
                int done = 0;
                while (done < pcm.length) {
                    int count = track.write(pcm, done, pcm.length - done, AudioTrack.WRITE_BLOCKING);
                    require(count > 0, "native write " + count); done += count;
                }
                long position = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
                require(position >= previous && position <= offset + 960, "native head " + position);
                queued |= position < offset + 960;
                previous = position;
                if ((offset + 960) % 9600 == 0)
                    System.out.println("[hello-native-audio] AudioTrack submitted=" + (offset + 960) + " head=" + position);
            }
            require(queued, "native playback head accounts for queued frames");
            if (!marker.await(3, TimeUnit.SECONDS)) {
                Looper.getMainLooper().dump(new android.util.PrintWriterPrinter(new java.io.PrintWriter(System.out, true)), "audio marker ");
                throw new AssertionError("actual marker callback");
            }
            AudioTimestamp timestamp = new AudioTimestamp();
            require(track.getTimestamp(timestamp) && timestamp.framePosition > 0
                    && timestamp.framePosition <= 57600 && timestamp.nanoTime > 0, "actual presentation timestamp");
            track.stop(); track.flush();
            System.out.println("[hello-native-audio] AudioTrack PASS PCM/marker/timestamp=" + timestamp.framePosition + "/stop/flush/release");
        } finally { track.release(); }
        require(track.getState() == AudioTrack.STATE_UNINITIALIZED, "native release");
    }
}
