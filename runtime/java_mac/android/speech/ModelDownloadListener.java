// android.speech.ModelDownloadListener (API 33) -- progress callbacks for
// SpeechRecognizer.triggerModelDownload. AOSP:
// frameworks/base/core/java/android/speech/ModelDownloadListener.java
package android.speech;

public interface ModelDownloadListener {
    /**
     * Called when the download has started; {@code completedPercent} is
     * 0..100. AOSP notes the callback may be invoked several times.
     */
    void onProgress(int completedPercent);

    /**
     * Called when the model download completed successfully. Terminal: no
     * further callbacks arrive after this one.
     */
    void onSuccess();

    /**
     * Called when the download was scheduled rather than started immediately
     * (the recognition service decides). Terminal.
     */
    void onScheduled();

    /**
     * A {@code SpeechRecognizer.ERROR_*} code. Terminal.
     */
    void onError(int error);
}
