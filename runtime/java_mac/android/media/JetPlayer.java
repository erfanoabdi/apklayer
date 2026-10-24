package android.media;

import android.content.res.AssetFileDescriptor;
import android.os.Handler;

/**
 * The JET interactive-music engine (API 1): plays a .jet file -- a container of
 * MIDI segments an app queues and mutes in real time -- through the platform's
 * built-in synthesiser and SONiVOX DLS bank.
 *
 * Mac.apk has no MIDI synthesiser: nothing in this tree renders a MIDI stream to
 * PCM (the android.media.midi package here exposes no devices either, and there
 * is no soft-synth in the native audio bridge), and JET files carry no PCM of
 * their own to fall back on. So every operation reports FAILURE the way AOSP's
 * own JetPlayer does when its native engine is unavailable: loadJetFile()
 * returns false, and everything downstream of a successful load returns false
 * too.
 *
 * That is deliberately a refusal rather than a silent success. A JET-driven
 * game reads these booleans to decide whether to fall back to its own audio; an
 * implementation that answered true would leave it waiting for music that never
 * plays.
 *
 * Source: frameworks/base/media/java/android/media/JetPlayer.java
 * (android16-release).
 */
public class JetPlayer {

    /** AOSP's fixed track count for the JET engine. */
    private static final int MAXTRACKS = 32;

    private static JetPlayer sSingletonRef;

    private OnJetEventListener mListener;

    private JetPlayer() { }

    /** AOSP holds ONE JetPlayer per process; a second getJetPlayer() returns
     *  the same instance (JetPlayer.java, getJetPlayer). */
    public static synchronized JetPlayer getJetPlayer() {
        if (sSingletonRef == null) sSingletonRef = new JetPlayer();
        return sSingletonRef;
    }

    /** AOSP's JetPlayer is a singleton and refuses to be cloned. */
    @Override public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public static int getMaxTracks() { return MAXTRACKS; }

    public boolean loadJetFile(String path) { return false; }
    public boolean loadJetFile(AssetFileDescriptor afd) { return false; }
    public boolean closeJetFile() { return false; }

    public boolean play() { return false; }
    public boolean pause() { return false; }

    public boolean queueJetSegment(int segmentNum, int libIndex, int repeatCount,
            int transpose, int muteFlags, byte userID) {
        return false;
    }

    public boolean queueJetSegmentMuteArray(int segmentNum, int libIndex, int repeatCount,
            int transpose, boolean[] muteArray, byte userID) {
        return false;
    }

    public boolean setMuteFlags(int muteFlags, boolean sync) { return false; }
    public boolean setMuteArray(boolean[] muteArray, boolean sync) { return false; }
    public boolean setMuteFlag(int trackId, boolean muteFlag, boolean sync) { return false; }
    public boolean triggerClip(int clipId) { return false; }
    public boolean clearQueue() { return false; }

    /** Registration is real -- the listener is stored and can be cleared -- but
     *  no event can ever be raised, because no file can ever be loaded. */
    public void setEventListener(OnJetEventListener listener) { mListener = listener; }
    public void setEventListener(OnJetEventListener listener, Handler handler) {
        mListener = listener;
    }

    public void release() { mListener = null; }

    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }

    /** JET engine events. Never delivered here; see the class comment. */
    public interface OnJetEventListener {
        void onJetEvent(JetPlayer player, short segment, byte track, byte channel,
                byte controller, byte value);
        void onJetUserIdUpdate(JetPlayer player, int userId, int repeatCount);
        void onJetNumQueuedSegmentUpdate(JetPlayer player, int nbSegments);
        void onJetPauseUpdate(JetPlayer player, int paused);
    }
}
