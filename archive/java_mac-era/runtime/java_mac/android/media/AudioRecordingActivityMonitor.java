package android.media;

// The device-wide registry of ACTIVE audio-capture clients, and the fan-out to
// AudioManager.AudioRecordingCallback listeners.
//
// WHERE THIS SITS IN AOSP. There is no such class in android.media, because on
// Android the registry lives in the system server
// (com.android.server.audio.RecordingActivityMonitor, reached from
// AudioManager over IAudioService: registerRecordingCallback /
// getActiveRecordingConfigurations). Mac.apk has no system server and no
// binder, so the registry has to live in the process -- and it has to be
// STATIC, because the AudioManager instance that registers a callback is
// usually not the one that would have seen the recorder start. Static here is
// the analogue of "one per device" there, not a shortcut.
//
// This class is package-private on purpose: it is not AOSP public API, so no
// app may see it. The two sides that use it are both in android.media --
// AudioManager (the listener side) and the recorder classes (the producer
// side).
//
// ⚠ CORRECTED (this header previously said "NOTHING CALLS recorderStarted/
// recorderStopped YET" and listed the wiring as an unfinished TODO -- that has
// been FALSE since v1.0.940). AudioRecord now announces itself at all four
// points: recorderCreated() from both constructors, recorderStarted() inside
// startRecording()'s state lock, recorderStopped() from stop(), and
// recorderReleased() from release(). So getActiveRecordingConfigurations()
// reports live recorders, and a registered AudioManager.AudioRecordingCallback
// really fires. A stale "not wired yet" note is the expensive kind of wrong: it
// tells the next session not to look.
final class AudioRecordingActivityMonitor {

    private AudioRecordingActivityMonitor() {}

    /** One live capture client. Keyed by the recorder object's IDENTITY, so two
     *  AudioRecords with identical parameters stay distinct entries. */
    private static final class Client {
        final Object recorder;
        final AudioRecordingConfiguration config;
        boolean active;

        Client(Object recorder, AudioRecordingConfiguration config) {
            this.recorder = recorder;
            this.config = config;
        }
    }

    private static final Object sLock = new Object();

    // ArrayList, not a Map: the list is tiny (a device has one or two active
    // recorders) and iteration order has to be stable so two consecutive
    // getActiveRecordingConfigurations() calls agree.
    private static final java.util.ArrayList<Client> sClients =
            new java.util.ArrayList<Client>();

    // The AudioManagers that currently hold at least one registered callback.
    // AOSP registers ONE binder per AudioManager for the same reason.
    private static final java.util.ArrayList<AudioManager> sDispatchers =
            new java.util.ArrayList<AudioManager>();

    /** A recorder exists and is initialized, but is not capturing yet. AOSP
     *  only reports a client once it is RECORDING, so this just remembers the
     *  configuration to report later. */
    static void recorderCreated(Object recorder, int source, AudioFormat format) {
        if (recorder == null) return;
        synchronized (sLock) {
            if (indexOfLocked(recorder) >= 0) return;
            sClients.add(new Client(recorder, new AudioRecordingConfiguration(source, format)));
        }
        // No dispatch: nothing became active, so the active set did not change.
    }

    /** The recorder started capturing. */
    static void recorderStarted(Object recorder) {
        if (setActive(recorder, true)) dispatchConfigChange();
    }

    /** The recorder stopped capturing (stop(), or release() while recording). */
    static void recorderStopped(Object recorder) {
        if (setActive(recorder, false)) dispatchConfigChange();
    }

    /** The recorder is gone for good; drop it entirely. */
    static void recorderReleased(Object recorder) {
        boolean wasActive = false;
        synchronized (sLock) {
            int i = indexOfLocked(recorder);
            if (i < 0) return;
            wasActive = sClients.get(i).active;
            sClients.remove(i);
        }
        if (wasActive) dispatchConfigChange();
    }

    private static boolean setActive(Object recorder, boolean active) {
        if (recorder == null) return false;
        synchronized (sLock) {
            int i = indexOfLocked(recorder);
            if (i < 0) return false;
            Client c = sClients.get(i);
            if (c.active == active) return false;
            c.active = active;
            return true;
        }
    }

    private static int indexOfLocked(Object recorder) {
        for (int i = 0; i < sClients.size(); i++) {
            if (sClients.get(i).recorder == recorder) return i;
        }
        return -1;
    }

    /** The configuration for ONE recorder, or null when it is not active.
     *  Backs AudioRecord.getActiveRecordingConfiguration(), whose javadoc uses
     *  the singular; reading it from this same registry is what keeps it
     *  consistent with the device-wide list below. */
    static AudioRecordingConfiguration getConfigurationFor(Object recorder) {
        synchronized (sLock) {
            int i = indexOfLocked(recorder);
            if (i < 0) return null;
            Client c = sClients.get(i);
            return c.active ? c.config : null;
        }
    }

    /**
     * The current active recording configurations. AOSP's contract: never null,
     * and an EMPTY list means nothing is recording right now.
     */
    static java.util.List<AudioRecordingConfiguration> getActiveRecordingConfigurations() {
        final java.util.ArrayList<AudioRecordingConfiguration> out =
                new java.util.ArrayList<AudioRecordingConfiguration>();
        synchronized (sLock) {
            for (int i = 0; i < sClients.size(); i++) {
                Client c = sClients.get(i);
                if (c.active) out.add(c.config);
            }
        }
        return out;
    }

    /** AudioManager's callback list went 0 -> 1. Mirrors AOSP's
     *  service.registerRecordingCallback(mRecCb). */
    static void registerDispatcher(AudioManager am) {
        synchronized (sLock) {
            if (!sDispatchers.contains(am)) sDispatchers.add(am);
        }
    }

    /** AudioManager's callback list went 1 -> 0. */
    static void unregisterDispatcher(AudioManager am) {
        synchronized (sLock) {
            sDispatchers.remove(am);
        }
    }

    private static void dispatchConfigChange() {
        final java.util.List<AudioRecordingConfiguration> configs =
                getActiveRecordingConfigurations();
        final AudioManager[] targets;
        synchronized (sLock) {
            targets = sDispatchers.toArray(new AudioManager[0]);
        }
        // Outside the lock: each AudioManager posts to its listeners' Handlers,
        // and a listener that re-enters (register/unregister from inside its own
        // callback, which apps do) must not deadlock against sLock.
        for (AudioManager am : targets) {
            am.dispatchRecordingConfigChange(configs);
        }
    }
}
