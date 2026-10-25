package android.media;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.Objects;

/**
 * The pre-Lollipop "remote control display" client: a lock screen or companion
 * app used this to OBSERVE and control whatever RemoteControlClient currently
 * held media-button focus. Deprecated in AOSP since API 21 (superseded by
 * android.media.session.MediaController) but still present and still callable.
 *
 * Registering one requires being an enabled NotificationListenerService or the
 * current lock screen -- AOSP's AudioManager.registerRemoteController() returns
 * FALSE for any other caller. Mac.apk hosts exactly one app and has no
 * notification-listener enrolment and no lock screen, so no caller here can ever
 * qualify: registerRemoteController returns false, this object is never fed by
 * the system, and every control method returns false ("the command could not be
 * sent"). That is the same answer real Android gives an unqualified app, and it
 * is the reason none of these methods pretends to have succeeded.
 *
 * Source: frameworks/base/media/java/android/media/RemoteController.java
 * (android16-release).
 */
@Deprecated
public final class RemoteController {

    public static final int POSITION_SYNCHRONIZATION_NONE = 0;
    public static final int POSITION_SYNCHRONIZATION_CHECK = 1;

    private final OnClientUpdateListener mListener;
    private int mArtworkWidth = -1;
    private int mArtworkHeight = -1;
    private int mPositionSyncMode = POSITION_SYNCHRONIZATION_NONE;

    /** AOSP throws IllegalArgumentException on a null Context or listener, and
     *  on a caller with no Looper (RemoteController.java L120-140). */
    public RemoteController(Context context, OnClientUpdateListener updateListener)
            throws IllegalArgumentException {
        this(context, updateListener, null);
    }

    public RemoteController(Context context, OnClientUpdateListener updateListener, Looper looper)
            throws IllegalArgumentException {
        if (context == null) {
            throw new IllegalArgumentException("Invalid null Context");
        }
        if (updateListener == null) {
            throw new IllegalArgumentException("Invalid null OnClientUpdateListener");
        }
        if (looper == null && Looper.myLooper() == null && Looper.getMainLooper() == null) {
            throw new IllegalArgumentException("Calling thread not associated with a looper");
        }
        mListener = updateListener;
    }

    /** AOSP: the estimated position of the observed client's media, or a
     *  negative value when there is nothing to report. There is never an
     *  observed client here (see the class comment), so -1. */
    public long getEstimatedMediaPosition() { return -1L; }

    /** AOSP returns false when the command could not be sent -- which is the
     *  case whenever this controller is not registered. It still validates the
     *  argument first, and so does this. */
    public boolean sendMediaKeyEvent(KeyEvent keyEvent) throws IllegalArgumentException {
        if (keyEvent == null) {
            throw new IllegalArgumentException("Invalid null KeyEvent");
        }
        if (!isMediaSessionKey(keyEvent.getKeyCode())) {
            throw new IllegalArgumentException("not a media key event");
        }
        return false;
    }

    /** AOSP rejects a negative position and otherwise returns whether the seek
     *  was delivered (RemoteController.java L300-320). */
    public boolean seekTo(long timeMs) throws IllegalArgumentException {
        if (timeMs < 0) {
            throw new IllegalArgumentException("illegal negative time value");
        }
        return false;
    }

    /** AOSP rejects non-positive dimensions (RemoteController.java L345-360). */
    public boolean setArtworkConfiguration(int width, int height)
            throws IllegalArgumentException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        mArtworkWidth = width;
        mArtworkHeight = height;
        return false;
    }

    public boolean clearArtworkConfiguration() {
        mArtworkWidth = -1;
        mArtworkHeight = -1;
        return false;
    }

    public boolean setSynchronizationMode(int sync) throws IllegalArgumentException {
        if (sync != POSITION_SYNCHRONIZATION_NONE && sync != POSITION_SYNCHRONIZATION_CHECK) {
            throw new IllegalArgumentException("Unknown synchronization mode " + sync);
        }
        mPositionSyncMode = sync;
        return false;
    }

    /** AOSP hands back an editor pre-loaded with the observed client's metadata;
     *  with no observed client it is simply empty, and apply() has nothing to
     *  publish to. */
    public MetadataEditor editMetadata() { return new MetadataEditor(); }

    /** The keycode set AOSP's @hide KeyEvent.isMediaSessionKey() accepts
     *  (frameworks/base/core/java/android/view/KeyEvent.java, isMediaSessionKey).
     *  Inlined because that helper is hidden API and cannot be called. */
    private static boolean isMediaSessionKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return true;
            default:
                return false;
        }
    }

    OnClientUpdateListener getListener() { return mListener; }

    /** AOSP RemoteController.MetadataEditor: edits pushed BACK to the observed
     *  client (a rating, typically). */
    @Deprecated
    public class MetadataEditor extends MediaMetadataEditor {
        MetadataEditor() { }
        @Override public void apply() {
            // Nothing to publish to: see the class comment. AOSP's apply() is
            // also a silent no-op when the editor has no target client.
            mApplied = true;
        }
    }

    /** Callbacks describing the currently observed RemoteControlClient. Never
     *  invoked here -- no client is ever observed. */
    @Deprecated
    public interface OnClientUpdateListener {
        void onClientChange(boolean clearing);
        void onClientPlaybackStateUpdate(int state);
        void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed);
        void onClientTransportControlUpdate(int transportControlFlags);
        void onClientMetadataUpdate(MetadataEditor metadataEditor);
    }
}
