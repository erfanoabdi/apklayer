package com.google.android.gms.cast.framework.media;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaStatus;

// com.google.android.gms.cast.framework.media.RemoteMediaClient -- controls
// playback on a connected Cast receiver. Mac.apk connects to no receiver, so this
// is only ever obtained as null from CastSession.getRemoteMediaClient on a
// de-Googled device; the type exists so apps that reference it link. All controls
// are no-ops. Ref: developers.google.com/android/reference/.../media/RemoteMediaClient
public class RemoteMediaClient {

    public void load(MediaInfo mediaInfo) {}
    public void play() {}
    public void pause() {}
    public void stop() {}
    public void seek(long position) {}
    public MediaStatus getMediaStatus() { return null; }
    public boolean isPlaying() { return false; }
    public boolean isPaused() { return false; }
    public long getApproximateStreamPosition() { return 0L; }
}
