// android.speech.RecognitionSupportCallback (API 33) -- the reply channel for
// SpeechRecognizer.checkRecognitionSupport. AOSP:
// frameworks/base/core/java/android/speech/RecognitionSupportCallback.java
package android.speech;

public interface RecognitionSupportCallback {
    /** The service answered; exactly one of this or onError is invoked. */
    void onSupportResult(RecognitionSupport recognitionSupport);

    /** A {@code SpeechRecognizer.ERROR_*} code. */
    void onError(int error);
}
