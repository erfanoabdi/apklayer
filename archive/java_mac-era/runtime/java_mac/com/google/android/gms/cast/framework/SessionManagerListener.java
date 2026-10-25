package com.google.android.gms.cast.framework;

// com.google.android.gms.cast.framework.SessionManagerListener -- the callback
// interface apps register with SessionManager. On Mac.apk no session ever starts,
// so none of these fire, but the interface must exist for apps to implement it.
// Mirrors the published session-lifecycle callbacks.
// Ref: developers.google.com/android/reference/.../framework/SessionManagerListener
public interface SessionManagerListener<T extends Session> {
    void onSessionStarting(T session);
    void onSessionStarted(T session, String sessionId);
    void onSessionStartFailed(T session, int error);
    void onSessionEnding(T session);
    void onSessionEnded(T session, int error);
    void onSessionResuming(T session, String sessionId);
    void onSessionResumed(T session, boolean wasSuspended);
    void onSessionResumeFailed(T session, int error);
    void onSessionSuspended(T session, int reason);
}
