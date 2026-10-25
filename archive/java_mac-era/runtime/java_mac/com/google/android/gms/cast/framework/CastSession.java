package com.google.android.gms.cast.framework;

// com.google.android.gms.cast.framework.CastSession -- a connected Cast session.
// On a de-Googled device no Cast device is ever discovered or connected, so the
// SessionManager never produces a real CastSession; this type exists so apps that
// reference it (e.g. SessionManagerListener<CastSession>) link. isConnected is
// always false. Ref: developers.google.com/android/reference/.../framework/CastSession
public class CastSession extends Session {

    @Override public boolean isConnected() { return false; }
    @Override public boolean isConnecting() { return false; }
    public com.google.android.gms.cast.framework.media.RemoteMediaClient getRemoteMediaClient() { return null; }
    public double getVolume() { return 0d; }
    public void setVolume(double volume) {}
}
