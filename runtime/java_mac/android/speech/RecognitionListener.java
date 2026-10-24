// android.speech.RecognitionListener -- the callbacks a client registers with
// SpeechRecognizer. AOSP:
// frameworks/base/core/java/android/speech/RecognitionListener.java
package android.speech;

public interface RecognitionListener {
    void onReadyForSpeech(android.os.Bundle params);
    void onBeginningOfSpeech();
    void onRmsChanged(float rmsdB);
    void onBufferReceived(byte[] buffer);
    void onEndOfSpeech();
    void onError(int error);
    void onResults(android.os.Bundle results);
    void onPartialResults(android.os.Bundle partialResults);
    void onEvent(int eventType, android.os.Bundle params);

    // The three below are DEFAULT methods in AOSP (API 33) -- core/api/current.txt
    // says "method public default ...". That is not a convenience: this
    // interface has been implemented by apps since API 8, and making any of
    // these abstract would break every one of them. Mirrored as defaults for
    // the same reason.

    /**
     * Called when a segmented recognition session (RecognizerIntent
     * #EXTRA_SEGMENTED_SESSION) delivers a batch of results.
     */
    default void onSegmentResults(android.os.Bundle segmentResults) {}

    /** Called when a segmented recognition session has finished. */
    default void onEndOfSegmentedSession() {}

    /**
     * Called when the service detected the language being spoken.
     * The bundle carries SpeechRecognizer#DETECTED_LANGUAGE,
     * #LANGUAGE_DETECTION_CONFIDENCE_LEVEL, #TOP_LOCALE_ALTERNATIVES and
     * #LANGUAGE_SWITCH_RESULT.
     */
    default void onLanguageDetection(android.os.Bundle results) {}
}
