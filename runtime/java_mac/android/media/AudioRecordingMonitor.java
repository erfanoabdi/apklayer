package android.media;

import java.util.concurrent.Executor;

/**
 * AudioRecordingMonitor defines the interface implemented by {@link AudioRecord}
 * and {@link MediaRecorder} that lets an app install a callback and be notified
 * of changes in the capture path while recording is active.
 *
 * AOSP ref: frameworks/base/media/java/android/media/AudioRecordingMonitor.java
 * (android16-release). PUBLIC in the API-36 signature file -- it was
 * package-private here, which meant AudioRecord/MediaRecorder could not be
 * declared to implement it and an app doing `(AudioRecordingMonitor) recorder`
 * would fail. android16.txt: "public interface AudioRecordingMonitor".
 *
 * Neither AudioRecord nor MediaRecorder implements it in this tree yet; see the
 * wiring note in AudioRecordingActivityMonitor. The interface lands first
 * because it is what those two files have to implement, and because
 * {@link AudioManager.AudioRecordingCallback} -- its parameter type -- did not
 * exist until now either.
 */
public interface AudioRecordingMonitor {
    /**
     * Register a callback for changes to the capture path (pre-processing,
     * format, sample rate) or for capture being silenced by the system.
     *
     * @param executor the {@link Executor} the callbacks run on.
     * @param cb non-null callback to register.
     */
    void registerAudioRecordingCallback(Executor executor,
            AudioManager.AudioRecordingCallback cb);

    /**
     * Unregister a callback previously registered with
     * {@link #registerAudioRecordingCallback(Executor, AudioManager.AudioRecordingCallback)}.
     */
    void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb);

    /**
     * The active recording configuration for THIS recorder, or null when it is
     * not recording. Note the singular: the AudioManager method of nearly the
     * same name returns the whole device's list.
     */
    AudioRecordingConfiguration getActiveRecordingConfiguration();
}
