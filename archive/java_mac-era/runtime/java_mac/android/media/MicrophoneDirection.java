package android.media;

/**
 * Implemented by {@link AudioRecord} and {@link MediaRecorder} to express a
 * preferred capture direction / zoom on devices with a steerable microphone
 * array. API 29.
 *
 * Source: frameworks/base/media/java/android/media/MicrophoneDirection.java
 */
public interface MicrophoneDirection {
    int MIC_DIRECTION_UNSPECIFIED = 0;
    int MIC_DIRECTION_TOWARDS_USER = 1;
    int MIC_DIRECTION_AWAY_FROM_USER = 2;
    int MIC_DIRECTION_EXTERNAL = 3;

    /** @return true if the preference was applied. */
    boolean setPreferredMicrophoneDirection(int direction);

    /** @param zoom -1 (widest) .. 1 (narrowest). @return true if applied. */
    boolean setPreferredMicrophoneFieldDimension(float zoom);
}
