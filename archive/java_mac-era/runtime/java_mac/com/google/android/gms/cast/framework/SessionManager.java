package com.google.android.gms.cast.framework;

// com.google.android.gms.cast.framework.SessionManager -- manages the lifecycle
// of Cast sessions. On a de-Googled device no session is ever started, so
// getCurrentCastSession returns null and listener registration is inert (the
// listener is never called). The type and method surface exist so apps that
// register a SessionManagerListener link. Ref: developers.google.com/android/reference
public class SessionManager {

    public CastSession getCurrentCastSession() { return null; }
    public Session getCurrentSession() { return null; }

    // Generic listener registration; never fires because no session starts.
    public <T extends Session> void addSessionManagerListener(
            SessionManagerListener<T> listener, Class<T> sessionClass) {}
    public <T extends Session> void removeSessionManagerListener(
            SessionManagerListener<T> listener, Class<T> sessionClass) {}
    public void addSessionManagerListener(SessionManagerListener<Session> listener) {}
    public void removeSessionManagerListener(SessionManagerListener<Session> listener) {}

    public void endCurrentSession(boolean stopCasting) {}
}
