package apklayer.host;

import android.media.AudioTrack;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowAudioTrack;

/**
 * Robolectric reports the playback head as the bytes written so far, which runs ahead of what is audible (the pipe
 * and the audio server buffer it). Players pace video by this clock (ExoPlayer, MediaPlayer-based apps), so frames
 * look late. Here the head follows real time while the track plays, and never passes what was written.
 */
@Implements(AudioTrack.class)
public class ShadowHostAudioTrack extends ShadowAudioTrack {
    @RealObject private AudioTrack real;
    private double playedFrames;
    private long lastNanos;

    @Implementation
    @Override
    protected int getPlaybackHeadPosition() {
        long now = System.nanoTime();
        if (real.getPlayState() == AudioTrack.PLAYSTATE_PLAYING && lastNanos != 0)
            playedFrames += (now - lastNanos) * 1e-9 * real.getSampleRate();
        lastNanos = now;
        int written = super.getPlaybackHeadPosition();
        if (playedFrames > written) playedFrames = written;
        return (int) playedFrames;
    }

    @Implementation
    @Override
    protected void flush() {
        super.flush();
        playedFrames = 0;
    }
}
