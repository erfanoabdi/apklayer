// android.media.session.MediaSessionManager
//
// AOSP's service for discovering active media sessions and routing media keys.
// Apps fetch it via getSystemService(MEDIA_SESSION_SERVICE). Mac.apk has no
// MediaSessionService backend, so getActiveSessions() is empty and listener
// registration is a no-op (no sessions ever appear) -- the honest "no active
// media sessions" result. Mirrors
// frameworks/base/media/java/android/media/session/MediaSessionManager.java.
package android.media.session;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;

public final class MediaSessionManager {

    public interface OnActiveSessionsChangedListener {
        void onActiveSessionsChanged(java.util.List<MediaController> controllers);
    }

    /** API 29. Notified when the session holding media-button focus changes. */
    public interface OnMediaKeyEventSessionChangedListener {
        void onMediaKeyEventSessionChanged(String packageName,
                android.media.session.MediaSession.Token sessionToken);
    }

    /** API 29, @deprecated in AOSP since API 30 along with the whole
     *  MediaSession2 experiment. */
    @Deprecated
    public interface OnSession2TokensChangedListener {
        void onSession2TokensChanged(java.util.List<android.media.Session2Token> tokens);
    }

    private final Context mContext;

    public MediaSessionManager() { this(null); }
    public MediaSessionManager(Context context) { mContext = context; }

    /**
     * AOSP: the active sessions the caller is allowed to see. The caller must be
     * an enabled NotificationListenerService (the ComponentName argument) or
     * hold MEDIA_CONTENT_CONTROL; anyone else gets a SecurityException.
     *
     * Mac.apk hosts exactly one app per process and has no notification-listener
     * enrolment, so no caller here can qualify -- and rather than throwing a
     * SecurityException an app has no way to satisfy, the empty list ("nothing
     * you may see") is returned, which is the same shape AOSP gives a qualified
     * caller on an idle device. The app's OWN sessions are reachable directly
     * through MediaSession.getController(), which is how an app is meant to
     * control its own playback; this API exists for observing OTHER apps, and
     * there are none.
     */
    public java.util.List<MediaController> getActiveSessions(ComponentName notificationListener) {
        return new java.util.ArrayList<>();
    }

    public void addOnActiveSessionsChangedListener(
            OnActiveSessionsChangedListener listener, ComponentName notificationListener) {}
    public void addOnActiveSessionsChangedListener(
            OnActiveSessionsChangedListener listener, ComponentName notificationListener,
            Handler handler) {}
    public void removeOnActiveSessionsChangedListener(
            OnActiveSessionsChangedListener listener) {}

    // ---- media-button focus (API 29) -------------------------------------
    // Unlike getActiveSessions above, these describe the CALLING process's own
    // session, which is information the app already has -- so answering them
    // truthfully leaks nothing and lets an app that checks "does my session hold
    // media-button focus" get the right answer.

    private final java.util.concurrent.CopyOnWriteArrayList<Object[]> mKeyEventSessionListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<Object[]> mSession2TokenListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** @Nullable in AOSP: null when no session currently holds media-button
     *  focus, i.e. when the app has not activated one. */
    public android.media.session.MediaSession.Token getMediaKeyEventSession() {
        MediaSession session = MediaSession.getMediaButtonSession();
        return (session == null) ? null : session.getSessionToken();
    }

    /** @NonNull in AOSP: the empty string when there is no such session
     *  (MediaSessionManager.java, getMediaKeyEventSessionPackageName). */
    public String getMediaKeyEventSessionPackageName() {
        MediaSession session = MediaSession.getMediaButtonSession();
        if (session != null) {
            String pkg = session.getPackageName();
            if (pkg != null) return pkg;
        }
        return "";
    }

    public void addOnMediaKeyEventSessionChangedListener(
            java.util.concurrent.Executor executor,
            OnMediaKeyEventSessionChangedListener listener) {
        java.util.Objects.requireNonNull(executor, "executor must not be null");
        java.util.Objects.requireNonNull(listener, "listener must not be null");
        for (Object[] entry : mKeyEventSessionListeners) {
            if (entry[1] == listener) return;
        }
        mKeyEventSessionListeners.add(new Object[] { executor, listener });
        // AOSP delivers the CURRENT focus holder to a newly added listener so the
        // app does not have to wait for a change that may never come.
        final String pkg = getMediaKeyEventSessionPackageName();
        final android.media.session.MediaSession.Token token = getMediaKeyEventSession();
        executor.execute(new Runnable() {
            @Override public void run() {
                listener.onMediaKeyEventSessionChanged(pkg, token);
            }
        });
    }

    public void removeOnMediaKeyEventSessionChangedListener(
            OnMediaKeyEventSessionChangedListener listener) {
        java.util.Objects.requireNonNull(listener, "listener must not be null");
        for (Object[] entry : mKeyEventSessionListeners) {
            if (entry[1] == listener) { mKeyEventSessionListeners.remove(entry); return; }
        }
    }

    // ---- MediaSession2 (API 29, deprecated API 30) ------------------------
    // MediaSession2 was never completed in the platform: AOSP deprecated the
    // whole surface one release after adding it, and the recommended replacement
    // is androidx.media2/media3, which an app ships itself. Nothing in this tree
    // creates a Session2, so the token list is genuinely empty and the listeners
    // genuinely never fire -- the same as on a modern device where no app uses it.

    @Deprecated
    public java.util.List<android.media.Session2Token> getSession2Tokens() {
        return java.util.Collections.emptyList();
    }

    @Deprecated
    public void notifySession2Created(android.media.Session2Token token) {
        java.util.Objects.requireNonNull(token, "token must not be null");
    }

    @Deprecated
    public void addOnSession2TokensChangedListener(OnSession2TokensChangedListener listener) {
        addOnSession2TokensChangedListener(listener, null);
    }

    @Deprecated
    public void addOnSession2TokensChangedListener(OnSession2TokensChangedListener listener,
            Handler handler) {
        java.util.Objects.requireNonNull(listener, "listener must not be null");
        for (Object[] entry : mSession2TokenListeners) {
            if (entry[0] == listener) return;
        }
        mSession2TokenListeners.add(new Object[] { listener, handler });
    }

    @Deprecated
    public void removeOnSession2TokensChangedListener(OnSession2TokensChangedListener listener) {
        java.util.Objects.requireNonNull(listener, "listener must not be null");
        for (Object[] entry : mSession2TokenListeners) {
            if (entry[0] == listener) { mSession2TokenListeners.remove(entry); return; }
        }
    }

    /**
     * Deliver a media key to the session that holds media-button focus. Not
     * public API -- AudioManager.dispatchMediaKeyEvent() routes here, standing in
     * for AOSP's MediaSessionService, which is what performs this dispatch on a
     * real device. AOSP wraps the key in an ACTION_MEDIA_BUTTON Intent and calls
     * MediaSession.Callback.onMediaButtonEvent, which is exactly what happens
     * below, so an app's existing callback sees the shape it expects.
     */
    public static void dispatchMediaKeyEventToSessions(android.view.KeyEvent keyEvent) {
        MediaSession session = MediaSession.getMediaButtonSession();
        if (session == null) return;
        MediaSession.Callback callback = session.getCallback();
        if (callback == null) return;
        // Intent.ACTION_MEDIA_BUTTON / EXTRA_KEY_EVENT written as their literal
        // AOSP values (core/java/android/content/Intent.java) rather than by
        // constant reference: android.content is owned by another package here,
        // and these two strings are frozen public API an app matches on.
        android.content.Intent intent =
                new android.content.Intent("android.intent.action.MEDIA_BUTTON");
        intent.putExtra("android.intent.extra.KEY_EVENT", keyEvent);
        callback.onMediaButtonEvent(intent);
    }

    /** Identifies a remote media-browser/controller caller (package + pid/uid).
     *  Source: frameworks/base .../media/session/MediaSessionManager.java */
    public static final class RemoteUserInfo {
        private final String mPackageName;
        private final int mPid;
        private final int mUid;
        public RemoteUserInfo(String packageName, int pid, int uid) {
            mPackageName = packageName; mPid = pid; mUid = uid;
        }
        public String getPackageName() { return mPackageName; }
        public int getPid() { return mPid; }
        public int getUid() { return mUid; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RemoteUserInfo)) return false;
            RemoteUserInfo r = (RemoteUserInfo) o;
            return mPid == r.mPid && mUid == r.mUid
                    && java.util.Objects.equals(mPackageName, r.mPackageName);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(mPackageName, mPid, mUid);
        }
    }

    public boolean isTrustedForMediaControl(android.media.session.MediaSessionManager.RemoteUserInfo userInfo) { return false; }
}

