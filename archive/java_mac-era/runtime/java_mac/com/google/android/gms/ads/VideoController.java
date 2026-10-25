// com.google.android.gms.ads.VideoController
//
// GMS Ads reference: controls video-ad playback. No video ad ever plays
// on a no-backend device, so this is an inert controller. The type
// exists so app code that queries it compiles.
package com.google.android.gms.ads;

public class VideoController {
    // Playback-state constants from the reference.
    public static final int PLAYBACK_STATE_UNKNOWN = 0;
    public static final int PLAYBACK_STATE_READY = 1;
    public static final int PLAYBACK_STATE_PLAYING = 2;
    public static final int PLAYBACK_STATE_PAUSED = 3;
    public static final int PLAYBACK_STATE_ENDED = 4;

    private VideoLifecycleCallbacks callbacks;

    public boolean hasVideoContent() { return false; }

    public float getAspectRatio() { return 0f; }

    public int getPlaybackState() { return PLAYBACK_STATE_UNKNOWN; }

    public boolean isClickToExpandEnabled() { return false; }

    public boolean isCustomControlsEnabled() { return false; }

    public boolean isMuted() { return true; }

    public void mute(boolean mute) { }

    public void play() { }

    public void pause() { }

    public void stop() { }

    public void setVideoLifecycleCallbacks(VideoLifecycleCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public VideoLifecycleCallbacks getVideoLifecycleCallbacks() { return callbacks; }

    // Nested lifecycle callbacks from the reference; never invoked on a
    // no-backend device.
    public abstract static class VideoLifecycleCallbacks {
        public void onVideoStart() { }
        public void onVideoPlay() { }
        public void onVideoPause() { }
        public void onVideoEnd() { }
        public void onVideoMute(boolean isMuted) { }
    }
}
