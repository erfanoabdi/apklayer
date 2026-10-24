package com.google.android.gms.cast.framework;

// com.google.android.gms.cast.framework.Session -- base type for a Cast
// framework session (CastSession extends it). Abstract in the real SDK but kept
// concrete-enough here so SessionManager's generics resolve. Mac.apk never
// produces a live session. Ref: developers.google.com/android/reference
public class Session {

    public boolean isConnected() { return false; }
    public boolean isConnecting() { return false; }
    public String getSessionId() { return null; }
}
