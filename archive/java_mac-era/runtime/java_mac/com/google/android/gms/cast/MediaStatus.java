package com.google.android.gms.cast;

// com.google.android.gms.cast.MediaStatus -- the receiver's current playback
// state. Mac.apk has no Cast receiver, so an app never obtains a populated
// MediaStatus; the type exists with its published player-state constants so code
// that switches on getPlayerState() links. Reports IDLE.
// Ref: developers.google.com/android/reference/.../cast/MediaStatus
public class MediaStatus {

    public static final int PLAYER_STATE_UNKNOWN = 0;
    public static final int PLAYER_STATE_IDLE = 1;
    public static final int PLAYER_STATE_PLAYING = 2;
    public static final int PLAYER_STATE_PAUSED = 3;
    public static final int PLAYER_STATE_BUFFERING = 4;
    public static final int PLAYER_STATE_LOADING = 5;

    public int getPlayerState() { return PLAYER_STATE_IDLE; }
    public MediaInfo getMediaInfo() { return null; }
    public long getStreamPosition() { return 0L; }
}
