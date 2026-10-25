// android.media.session.MediaSession -- the app side of the platform media
// session (transport controls, metadata, playback state). AOSP frameworks/base
// media/java/android/media/session/MediaSession.java.
//
// Mac.apk has no system MediaSessionService, so a session is a local holder:
// setMetadata/setPlaybackState store the latest values (readable via the paired
// MediaController) and setActive/setCallback record state. Since v1.0.2243 the
// media-button session is also mirrored into macOS Now Playing, and its
// transport commands (Control Center, media keys, headsets) reach the Callback
// on its Handler: see MacApkNowPlaying.
package android.media.session;

public final class MediaSession {
    public static final int FLAG_HANDLES_MEDIA_BUTTONS = 1 << 0;
    public static final int FLAG_HANDLES_TRANSPORT_CONTROLS = 1 << 1;

    private final String mTag;
    private final Token mToken;
    private boolean mActive = false;
    private Callback mCallback;
    private android.media.MediaMetadata mMetadata;
    private PlaybackState mPlaybackState;
    private java.util.List<QueueItem> mQueue;
    private CharSequence mQueueTitle;
    private android.app.PendingIntent mMediaButtonIntent;
    private android.content.ComponentName mMediaButtonBroadcastReceiver;
    private android.os.Bundle mExtras;
    private android.app.PendingIntent mSessionActivity;
    private int mRatingType = android.media.Rating.RATING_NONE;
    private int mFlags;
    // Controllers built from this session's Token (getController(), or a
    // MediaController(context, token) the app constructs itself elsewhere in
    // the same process -- see Token below) that have registerCallback()'d.
    // AOSP delivers these through a Binder round-trip to a system session
    // record; Mac.apk has no such record, so this session IS the source of
    // truth and notifies its controllers' callbacks directly.
    private final java.util.List<MediaController.Callback> mControllerCallbacks =
        new java.util.concurrent.CopyOnWriteArrayList<MediaController.Callback>();

    // The process-wide list of live sessions, and which of them most recently
    // became active. AOSP keeps this in MediaSessionService and hands it to
    // MediaSessionManager over binder; Mac.apk has no such service, so the
    // sessions themselves ARE the registry. This is what lets
    // MediaSessionManager answer getMediaKeyEventSession() and lets
    // AudioManager.dispatchMediaKeyEvent() actually reach the app's own session
    // instead of vanishing.
    private static final java.util.List<MediaSession> sLiveSessions =
        new java.util.concurrent.CopyOnWriteArrayList<MediaSession>();
    private static volatile MediaSession sMediaButtonSession;
    private final String mPackageName;
    private final android.os.Bundle mSessionInfo;

    public MediaSession(android.content.Context context, String tag) {
        this(context, tag, null);
    }

    /** API 29. AOSP's third argument is opaque app-supplied session info,
     *  readable back through MediaController.getSessionInfo(). It rejects a
     *  Bundle containing a non-framework Parcelable (MediaSession.java L200-210);
     *  that check needs Bundle.hasFileDescriptors-style introspection we do not
     *  model, so the Bundle is simply copied and stored. */
    public MediaSession(android.content.Context context, String tag, android.os.Bundle sessionInfo) {
        if (tag == null) {
            throw new IllegalArgumentException("tag cannot be null");
        }
        mTag = tag;
        String pkg = "";
        try {
            if (context != null && context.getPackageName() != null) {
                pkg = context.getPackageName();
            }
        } catch (Throwable ignore) {
            // A Context that cannot name its package still yields a usable session.
        }
        mPackageName = pkg;
        mSessionInfo = (sessionInfo == null) ? null : new android.os.Bundle(sessionInfo);
        mToken = new Token(this);
        sLiveSessions.add(this);
    }

    /** Package-private registry accessors for MediaSessionManager (the
     *  in-process stand-in for AOSP's MediaSessionService queries). */
    static java.util.List<MediaSession> getLiveSessions() {
        return new java.util.ArrayList<MediaSession>(sLiveSessions);
    }
    static MediaSession getMediaButtonSession() {
        MediaSession session = sMediaButtonSession;
        return (session != null && session.mActive) ? session : null;
    }
    String getPackageName() { return mPackageName; }
    /** Package-private: backs MediaController.getTag(), which is public API. */
    String getTag() { return mTag; }
    android.os.Bundle getSessionInfo() { return mSessionInfo; }
    Callback getCallback() { return mCallback; }

    /** AOSP: callbacks run on `handler`, or, without one, on the calling
     *  thread's Looper (MediaSession.setCallback: "If null the callback will
     *  be invoked on the thread of the current Looper"), falling back to the
     *  main thread. Kept so a transport command from macOS Now Playing reaches
     *  the app on the thread it chose (MacApkNowPlaying.onRemoteCommand). */
    private android.os.Handler mCallbackHandler;
    public void setCallback(Callback callback) { setCallback(callback, null); }
    public void setCallback(Callback callback, android.os.Handler handler) {
        mCallback = callback;
        if (callback == null) { mCallbackHandler = null; return; }
        if (handler == null) {
            android.os.Looper l = android.os.Looper.myLooper();
            handler = new android.os.Handler(l != null ? l : android.os.Looper.getMainLooper());
        }
        mCallbackHandler = handler;
    }
    android.os.Handler getCallbackHandler() { return mCallbackHandler; }
    /** AOSP: an active session is one the system may route media buttons to,
     *  and the most recently activated session wins that focus. */
    public void setActive(boolean active) {
        mActive = active;
        if (active) sMediaButtonSession = this;
        else if (sMediaButtonSession == this) sMediaButtonSession = null;
        // The media-button session is what a device's system media controls
        // show; on the Mac that is Now Playing.
        if (active) MacApkNowPlaying.sessionChanged(this);
        else MacApkNowPlaying.sessionGone(this);
    }
    public boolean isActive() { return mActive; }
    public void setPlaybackToLocal(android.media.AudioAttributes attributes) {}
    // Published descriptor is (Landroid/media/VolumeProvider;)V; the prior Object
    // param compiled but would not resolve a guest's setPlaybackToRemote(vp) call
    // (descriptor mismatch). No remote routing, so accept and ignore.
    public void setPlaybackToRemote(android.media.VolumeProvider volumeProvider) {}
    /** @deprecated in AOSP since API 31, replaced by
     *  setMediaButtonBroadcastReceiver(ComponentName). */
    @Deprecated
    public void setMediaButtonReceiver(android.app.PendingIntent mbr) { mMediaButtonIntent = mbr; }

    /** API 31. The BroadcastReceiver that should get ACTION_MEDIA_BUTTON when
     *  the app is not running. AOSP rejects a receiver outside the session's own
     *  package (MediaSession.java L390-400); the same check is applied here
     *  because accepting one would silently misroute the app's buttons.
     *  Mac.apk never delivers a media button to a stopped app -- there is no
     *  system button router and no way for the process to be resumed by one --
     *  so the value is stored (and read back) rather than acted on. */
    public void setMediaButtonBroadcastReceiver(android.content.ComponentName broadcastReceiver) {
        if (broadcastReceiver != null && mPackageName != null && !mPackageName.isEmpty()
                && !mPackageName.equals(broadcastReceiver.getPackageName())) {
            throw new IllegalArgumentException("broadcastReceiver has wrong package name");
        }
        mMediaButtonBroadcastReceiver = broadcastReceiver;
    }
    public android.content.ComponentName getMediaButtonBroadcastReceiver() {
        return mMediaButtonBroadcastReceiver;
    }
    // AOSP forwards to a system session record with no local getter (the
    // record IS the source of truth there). This session plays that role on
    // Mac.apk, so the value is actually stored -- otherwise a now-playing
    // notification/activity PendingIntent an app configures is silently
    // dropped and getController().getSessionActivity() always answers null.
    public void setSessionActivity(android.app.PendingIntent pi) { mSessionActivity = pi; }
    public android.app.PendingIntent getSessionActivity() { return mSessionActivity; }
    public void setMetadata(android.media.MediaMetadata metadata) {
        mMetadata = metadata;
        MacApkNowPlaying.sessionChanged(this);
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onMetadataChanged(metadata); } catch (Throwable ignore) {}
        }
    }
    public android.media.MediaMetadata getMetadata() { return mMetadata; }
    public void setPlaybackState(PlaybackState state) {
        mPlaybackState = state;
        MacApkNowPlaying.sessionChanged(this);
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onPlaybackStateChanged(state); } catch (Throwable ignore) {}
        }
    }
    public PlaybackState getPlaybackState() { return mPlaybackState; }
    public void setQueue(java.util.List<QueueItem> queue) {
        mQueue = queue;
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onQueueChanged(queue); } catch (Throwable ignore) {}
        }
    }
    public java.util.List<QueueItem> getQueue() { return mQueue; }
    public void setQueueTitle(CharSequence title) {
        mQueueTitle = title;
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onQueueTitleChanged(title); } catch (Throwable ignore) {}
        }
    }
    public CharSequence getQueueTitle() { return mQueueTitle; }
    public void setExtras(android.os.Bundle extras) {
        mExtras = extras;
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onExtrasChanged(extras); } catch (Throwable ignore) {}
        }
    }
    public android.os.Bundle getExtras() { return mExtras; }
    public void setRatingType(int type) { mRatingType = type; }
    public int getRatingType() { return mRatingType; }
    public void setFlags(int flags) { mFlags = flags; }
    public int getFlags() { return mFlags; }
    public void sendSessionEvent(String event, android.os.Bundle extras) {
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onSessionEvent(event, extras); } catch (Throwable ignore) {}
        }
    }
    public Token getSessionToken() { return mToken; }
    // AOSP returns a MediaController bound to this session. Mac.apk's
    // MediaController is a minimal handle (no in-process transport routing), so we
    // hand back a fresh one; an in-app controller-to-session loop is not wired,
    // matching the "no active sessions" model the controller/manager use.
    public android.media.session.MediaController getController() {
        return new android.media.session.MediaController(null, mToken);
    }
    public void release() {
        mActive = false;
        sLiveSessions.remove(this);
        if (sMediaButtonSession == this) sMediaButtonSession = null;
        MacApkNowPlaying.sessionGone(this);
        for (MediaController.Callback cb : mControllerCallbacks) {
            try { cb.onSessionDestroyed(); } catch (Throwable ignore) {}
        }
        mControllerCallbacks.clear();
    }

    // Package-private: MediaController.register/unregisterCallback delegate
    // here when the controller was built from THIS session's Token (the only
    // case Mac.apk can route -- see Token below). Object identity is the
    // correct de-dup key (Callback has no equals()/hashCode() override),
    // matching AOSP's own register/unregister-by-reference contract.
    void addControllerCallback(MediaController.Callback cb) {
        if (cb != null) mControllerCallbacks.add(cb);
    }
    void removeControllerCallback(MediaController.Callback cb) {
        if (cb != null) mControllerCallbacks.remove(cb);
    }

    public static final class Token implements android.os.Parcelable {
        // AOSP's real Token carries (uid, ISessionController) -- a Binder
        // reference the system session service hands out, resolved by a
        // MediaController through IPC. Mac.apk has no cross-process
        // transport (see CLAUDE.md: no emulation/virtualization/IPC layer to
        // a separate system process), so a Token that stays in this same JVM
        // can just keep a direct reference to its owning session instead.
        // Package-private: only MediaController (same package) reads it, and
        // only to resolve state that already lives in this in-process
        // session -- see MediaController's getPlaybackState()/getMetadata()/
        // etc. A Token rebuilt from a Parcel (CREATOR below) has no owner,
        // which behaves the same as AOSP's "session no longer reachable"
        // case: the controller falls back to null/empty answers.
        final MediaSession owner;
        Token(MediaSession owner) { this.owner = owner; }
        @Override public int describeContents() { return 0; }

        // AOSP android11-release media/java/android/media/session/MediaSession
        // .java writes `dest.writeInt(mUid)` then
        // `dest.writeStrongBinder(mBinder)`.
        //
        // owner ABSENT-MARKER. Neither AOSP field exists here: there is no uid
        // (single process, no session service) and `owner` is a live
        // MediaSession standing in for AOSP's ISessionController Binder. It is
        // not an IBinder, so writeStrongBinder cannot take it, and it is not
        // Parcelable, so writeValue would throw. The marker records that a
        // session WAS attached, keeping the body non-empty -- an empty body
        // would silently route this class back through Parcel's liveref
        // fallback, which is the aliasing behaviour we are removing.
        //
        // The read side therefore yields an owner-less Token, which is exactly
        // the state this class already documents above: it behaves like AOSP's
        // "session no longer reachable" case, and MediaController falls back to
        // null/empty answers. REMOVAL CONDITION: if a session ever becomes
        // reachable across a parcel (it cannot today, and would need a real
        // session registry to look one up by id), replace the marker with that
        // id and resolve it here.
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(owner != null ? 1 : 0);
        }
        public static final android.os.Parcelable.Creator<Token> CREATOR =
            new android.os.Parcelable.Creator<Token>() {
                @Override public Token createFromParcel(android.os.Parcel in) {
                    in.readInt();       // consume the owner ABSENT-MARKER
                    return new Token(null);
                }
                @Override public Token[] newArray(int size) { return new Token[size]; }
            };
    }

    public static final class QueueItem implements android.os.Parcelable {
        public static final int UNKNOWN_ID = -1;
        private final android.media.MediaDescription mDescription;
        private final long mId;
        public QueueItem(android.media.MediaDescription description, long id) {
            mDescription = description; mId = id;
        }
        public android.media.MediaDescription getDescription() { return mDescription; }
        public long getQueueId() { return mId; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            if (mDescription != null) mDescription.writeToParcel(dest, flags);
            dest.writeLong(mId);
        }
        public static final android.os.Parcelable.Creator<QueueItem> CREATOR =
            new android.os.Parcelable.Creator<QueueItem>() {
                @Override public QueueItem createFromParcel(android.os.Parcel in) {
                    return new QueueItem(android.media.MediaDescription.CREATOR.createFromParcel(in), in.readLong());
                }
                @Override public QueueItem[] newArray(int size) { return new QueueItem[size]; }
            };
    }

    public abstract static class Callback {
        public Callback() {}
        public void onCommand(String command, android.os.Bundle args, android.os.ResultReceiver cb) {}
        public boolean onMediaButtonEvent(android.content.Intent mediaButtonIntent) { return false; }
        public void onPrepare() {}
        public void onPrepareFromMediaId(String mediaId, android.os.Bundle extras) {}
        public void onPrepareFromSearch(String query, android.os.Bundle extras) {}
        public void onPrepareFromUri(android.net.Uri uri, android.os.Bundle extras) {}
        public void onPlay() {}
        public void onPlayFromMediaId(String mediaId, android.os.Bundle extras) {}
        public void onPlayFromSearch(String query, android.os.Bundle extras) {}
        public void onPlayFromUri(android.net.Uri uri, android.os.Bundle extras) {}
        public void onSkipToQueueItem(long id) {}
        public void onPause() {}
        public void onSkipToNext() {}
        public void onSkipToPrevious() {}
        public void onFastForward() {}
        public void onRewind() {}
        public void onStop() {}
        public void onSeekTo(long pos) {}
        public void onSetRating(android.media.Rating rating) {}
        public void onSetPlaybackSpeed(float speed) {}
        public void onCustomAction(String action, android.os.Bundle extras) {}
    }

    /** AOSP: who sent the command currently being handled. It is @NonNull, and
     *  AOSP throws IllegalStateException when called outside a Callback. Mac.apk
     *  runs exactly one app per process, so the only party that can ever be
     *  controlling this session is that app itself -- which is a real, correct,
     *  non-null answer, and a better one than the null this used to return (an
     *  app dereferencing an @NonNull result got a confusing NPE instead). */
    public android.media.session.MediaSessionManager.RemoteUserInfo getCurrentControllerInfo() {
        return new android.media.session.MediaSessionManager.RemoteUserInfo(
                mPackageName, android.os.Process.myPid(), android.os.Process.myUid());
    }
}


