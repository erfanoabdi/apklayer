package android.media;

import android.app.PendingIntent;
import android.graphics.Bitmap;

/**
 * Deprecated (API 21) lock-screen / remote transport-control metadata client,
 * superseded by MediaSession. Still referenced by 9 of 37 corpus apps whose
 * media code retains the old path.
 *
 * Mac.apk has no lock screen or bluetooth AVRCP transport to publish to, so the
 * metadata and playback state are ACCEPTED and stored (a MetadataEditor that
 * round-trips) but not surfaced anywhere -- which is the same observable result
 * as a device with no remote-control display, and lets the app's media code run
 * unchanged.
 *
 * Source: frameworks/base/media/java/android/media/RemoteControlClient.java
 */
public class RemoteControlClient {

    public static final int PLAYSTATE_STOPPED = 1;
    public static final int PLAYSTATE_PAUSED = 2;
    public static final int PLAYSTATE_PLAYING = 3;
    public static final int PLAYSTATE_FAST_FORWARDING = 4;
    public static final int PLAYSTATE_REWINDING = 5;
    public static final int PLAYSTATE_SKIPPING_FORWARDS = 6;
    public static final int PLAYSTATE_SKIPPING_BACKWARDS = 7;
    public static final int PLAYSTATE_BUFFERING = 8;
    public static final int PLAYSTATE_ERROR = 9;

    public static final int FLAG_KEY_MEDIA_PREVIOUS = 1 << 0;
    public static final int FLAG_KEY_MEDIA_REWIND = 1 << 1;
    public static final int FLAG_KEY_MEDIA_PLAY = 1 << 2;
    public static final int FLAG_KEY_MEDIA_PLAY_PAUSE = 1 << 3;
    public static final int FLAG_KEY_MEDIA_PAUSE = 1 << 4;
    public static final int FLAG_KEY_MEDIA_STOP = 1 << 5;
    public static final int FLAG_KEY_MEDIA_FAST_FORWARD = 1 << 6;
    public static final int FLAG_KEY_MEDIA_NEXT = 1 << 7;
    public static final int FLAG_KEY_MEDIA_POSITION_UPDATE = 1 << 8;
    public static final int FLAG_KEY_MEDIA_RATING = 1 << 9;

    private int mPlaybackState = PLAYSTATE_STOPPED;
    private int mTransportControlFlags;
    private final MetadataEditor mEditor = new MetadataEditor();

    public RemoteControlClient(PendingIntent mediaButtonIntent) { }

    public RemoteControlClient(PendingIntent mediaButtonIntent, android.os.Looper looper) { }

    public void setPlaybackState(int state) { mPlaybackState = state; }

    public void setPlaybackState(int state, long timeInMs, float playbackSpeed) {
        mPlaybackState = state;
    }

    public void setTransportControlFlags(int transportControlFlags) {
        mTransportControlFlags = transportControlFlags;
    }

    public MetadataEditor editMetadata(boolean startEmpty) { return mEditor; }

    public void setMetadataUpdateListener(OnMetadataUpdateListener l) { }

    public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener l) { }

    public void setOnGetPlaybackPositionListener(OnGetPlaybackPositionListener l) { }

    public interface OnMetadataUpdateListener {
        void onMetadataUpdate(int key, Object newValue);
    }

    public interface OnPlaybackPositionUpdateListener {
        void onPlaybackPositionUpdate(long newPositionMs);
    }

    public interface OnGetPlaybackPositionListener {
        long onGetPlaybackPosition();
    }

    /**
     * API 21. AOSP: "Get a MediaSession associated with this RCC ... @return
     * token for the session, guaranteed to be non-null." AOSP's RCC internally
     * OWNS a MediaSession and forwards state to it, which is how the deprecated
     * RCC surface keeps working after the platform moved to MediaSession.
     *
     * The same relationship is modelled here: this client lazily creates its own
     * session and hands back that session's token, so an app migrating from RCC
     * to MediaSession gets a real, usable token rather than the null its
     * @NonNull contract forbids. The session is a local holder (see
     * MediaSession's class comment) -- as is everything else this class
     * publishes to.
     */
    public android.media.session.MediaSession.Token getMediaSession() {
        synchronized (this) {
            if (mSession == null) {
                mSession = new android.media.session.MediaSession(null, "RemoteControlClient");
            }
            return mSession.getSessionToken();
        }
    }

    private android.media.session.MediaSession mSession;

    /**
     * AOSP's MetadataEditor stores key/value pairs and applies them; the put*
     * methods return `this` for chaining. We keep them in a Bundle-like map so
     * a caller that reads back what it wrote (some do) sees its own values.
     */
    public class MetadataEditor extends MediaMetadataEditor {
        // AOSP: `public class MetadataEditor extends MediaMetadataEditor`. The
        // put*/get*/clear storage lives on the BASE there, and the base's put*
        // return MediaMetadataEditor -- so the covariant MetadataEditor-returning
        // overrides that used to be declared here had no base to override and any
        // MediaMetadataEditor-typed use of the editor failed.
        // frameworks/base/media/java/android/media/RemoteControlClient.java
        MetadataEditor() { }

        @Override public void apply() { /* nothing to publish to */ }

        // putString/putLong: MediaMetadataEditor already declares both
        // (returning MediaMetadataEditor), so these are ordinary covariant-
        // return overrides -- same shape as AOSP's real
        // MetadataEditor.putString/putLong minus the MediaSession
        // Bundle-mirroring AOSP does through its mMetadataBuilder, which
        // this simplified single-tier editor has no equivalent of (there is
        // no second listener anywhere that reads a mirrored copy).
        // frameworks/base/media/java/android/media/RemoteControlClient.java
        @Override
        public MetadataEditor putString(int key, String value) throws IllegalArgumentException {
            super.putString(key, value);
            return this;
        }

        @Override
        public MetadataEditor putLong(int key, long value) throws IllegalArgumentException {
            super.putLong(key, value);
            return this;
        }

        // putBitmap: AOSP declares this on the MediaMetadataEditor BASE
        // (validated against BITMAP_KEY_ARTWORK, stored via putObject) and
        // RemoteControlClient.MetadataEditor overrides it with @Override.
        // Our MediaMetadataEditor has no putBitmap at all (out of this
        // file's ownership to add), so this is a new method rather than an
        // override -- it reaches the same storage (putObject/mEditorMetadata)
        // through the base's public putObject, so a caller that reads back
        // BITMAP_KEY_ARTWORK still sees what it wrote, and the key
        // validation matches AOSP's own IllegalArgumentException contract.
        /**
         * AOSP declares clone() on MetadataEditor and makes it throw
         * CloneNotSupportedException unconditionally -- "@throws
         * CloneNotSupportedException" with a body of exactly
         * `throw new CloneNotSupportedException();`
         * (frameworks/base/media/java/android/media/RemoteControlClient.java).
         * It exists to REFUSE cloning, so mirroring it means throwing, not
         * inventing a copy.
         */
        @Override
        public Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException();
        }

        public MetadataEditor putBitmap(int key, Bitmap bitmap) throws IllegalArgumentException {
            if (key != BITMAP_KEY_ARTWORK) {
                throw new IllegalArgumentException("Invalid type 'Bitmap' for key " + key);
            }
            putObject(key, bitmap);
            return this;
        }
    }
}
