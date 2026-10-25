// android.speech.tts.SynthesisCallback -- the channel a TextToSpeechService
// implementation pushes synthesized PCM back through. AOSP:
// frameworks/base/core/java/android/speech/tts/SynthesisCallback.java
//
// Direction note (same as RecognitionService): this is implemented by the
// FRAMEWORK and consumed by a guest app's TextToSpeechService subclass. What
// the app needs from us is the exact interface shape.
package android.speech.tts;

public interface SynthesisCallback {
    /**
     * The largest buffer {@link #audioAvailable} will accept, in bytes.
     * A service must chunk its output to this size.
     */
    int getMaxBufferSize();

    /**
     * Called once, before any audio, to declare the format of what follows.
     *
     * @param sampleRateInHz sample rate of the generated audio
     * @param audioFormat one of {@link android.media.AudioFormat#ENCODING_PCM_8BIT} or
     *        {@link android.media.AudioFormat#ENCODING_PCM_16BIT}
     * @param channelCount 1 (mono) or 2 (stereo); the API range is 1..2
     * @return {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}
     */
    int start(int sampleRateInHz, int audioFormat, int channelCount);

    /**
     * Deliver one chunk of PCM. Must be preceded by {@link #start} and
     * followed eventually by {@link #done}.
     *
     * @return {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}
     */
    int audioAvailable(byte[] buffer, int offset, int length);

    /**
     * Called after the last {@link #audioAvailable}. Terminal for a
     * successful synthesis.
     *
     * @return {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}
     */
    int done();

    /** @deprecated Use {@link #error(int)}. */
    @Deprecated
    void error();

    /**
     * Terminal failure.
     * @param errorCode one of the {@code TextToSpeech.ERROR_*} constants
     */
    void error(int errorCode);

    /** True once {@link #start} has been called for this request. */
    boolean hasStarted();

    /** True once {@link #done} or {@link #error} has been called. */
    boolean hasFinished();

    /**
     * Optional progress report: the service tells the framework which range of
     * the input text it is about to speak, so the client's
     * UtteranceProgressListener#onRangeStart fires.
     *
     * DEFAULT in AOSP (core/api/current.txt: "method public default
     * rangeStart(int,int,int)") because the interface predates it and existing
     * implementations must keep compiling. Mirrored as a default.
     */
    default void rangeStart(int markerInFrames, int start, int end) {}
}
