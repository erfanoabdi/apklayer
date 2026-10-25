// android.speech.SpeechRecognizer -- the CLIENT side of Android speech
// recognition. AOSP:
// frameworks/base/core/java/android/speech/SpeechRecognizer.java
//
// WHAT THIS PLATFORM CAN HONESTLY ANSWER. Recognition on Android is performed
// by a separate app that publishes a RecognitionService; the framework class
// is only a binder client. Mac.apk has no such service installed, so
// isRecognitionAvailable() / isOnDeviceRecognitionAvailable() return false --
// and that is not a stub, it is the same answer a real device with no voice
// app gives. Every well-behaved caller checks it first.
//
// The important design decision here is what the ACTION methods do given that
// answer. AOSP's SpeechRecognizerImpl, when it cannot bind to a service,
// delivers RecognitionListener.onError(ERROR_CLIENT) rather than throwing or
// staying silent. We do the same. Silence is the one behaviour that must be
// avoided: an app that calls startListening() and never hears back leaves its
// "Listening..." UI up forever, which is a worse and much harder-to-diagnose
// failure than an error code the app already has a branch for.
//
// (AOSP's *base* class throws UnsupportedOperationException from these
// methods, because upstream it is never the object handed to an app --
// createSpeechRecognizer returns the private SpeechRecognizerImpl subclass.
// Ours IS the object handed to the app, so it has to behave like the impl,
// not like the abstract shell.)
package android.speech;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.Objects;
import java.util.concurrent.Executor;

public class SpeechRecognizer {
    // ---- result bundle keys ----
    public static final String RESULTS_RECOGNITION = "results_recognition";
    public static final String CONFIDENCE_SCORES = "confidence_scores";
    public static final String RESULTS_ALTERNATIVES = "results_alternatives";
    public static final String RECOGNITION_PARTS = "recognition_parts";
    public static final String DETECTED_LANGUAGE = "detected_language";
    public static final String LANGUAGE_DETECTION_CONFIDENCE_LEVEL =
            "language_detection_confidence_level";
    public static final String TOP_LOCALE_ALTERNATIVES = "top_locale_alternatives";
    public static final String LANGUAGE_SWITCH_RESULT = "language_switch_result";

    // ---- error codes ----
    public static final int ERROR_NETWORK_TIMEOUT = 1;
    public static final int ERROR_NETWORK = 2;
    public static final int ERROR_AUDIO = 3;
    public static final int ERROR_SERVER = 4;
    public static final int ERROR_CLIENT = 5;
    public static final int ERROR_SPEECH_TIMEOUT = 6;
    public static final int ERROR_NO_MATCH = 7;
    public static final int ERROR_RECOGNIZER_BUSY = 8;
    public static final int ERROR_INSUFFICIENT_PERMISSIONS = 9;
    public static final int ERROR_TOO_MANY_REQUESTS = 10;
    public static final int ERROR_SERVER_DISCONNECTED = 11;
    public static final int ERROR_LANGUAGE_NOT_SUPPORTED = 12;
    public static final int ERROR_LANGUAGE_UNAVAILABLE = 13;
    public static final int ERROR_CANNOT_CHECK_SUPPORT = 14;
    public static final int ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15;

    // ---- language-detection confidence ----
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN = 0;
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_NOT_CONFIDENT = 1;
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT = 2;
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_HIGHLY_CONFIDENT = 3;

    // ---- language-switch results ----
    public static final int LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED = 0;
    public static final int LANGUAGE_SWITCH_RESULT_SUCCEEDED = 1;
    public static final int LANGUAGE_SWITCH_RESULT_FAILED = 2;
    public static final int LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL = 3;

    private RecognitionListener mListener;
    /** The main-thread handler callbacks are posted on. AOSP delivers every
     *  RecognitionListener callback on the main thread. */
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    protected SpeechRecognizer() {}

    public static boolean isRecognitionAvailable(Context context) { return false; }

    public static boolean isOnDeviceRecognitionAvailable(Context context) { return false; }

    public static SpeechRecognizer createSpeechRecognizer(Context context) {
        return new SpeechRecognizer();
    }

    public static SpeechRecognizer createSpeechRecognizer(Context context,
            ComponentName serviceComponent) {
        return new SpeechRecognizer();
    }

    public static SpeechRecognizer createOnDeviceSpeechRecognizer(Context context) {
        return new SpeechRecognizer();
    }

    public void setRecognitionListener(RecognitionListener listener) {
        mListener = listener;
    }

    /**
     * With no recognition service bound, AOSP's impl reports ERROR_CLIENT to
     * the listener. See the class header for why reporting beats silence.
     */
    public void startListening(Intent recognizerIntent) {
        final RecognitionListener l = mListener;
        if (l == null) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override public void run() { l.onError(ERROR_CLIENT); }
        });
    }

    public void stopListening() {}

    public void cancel() {}

    public void destroy() { mListener = null; }

    /**
     * API 33. There is no service to ask, and ERROR_CANNOT_CHECK_SUPPORT is
     * the code the platform defines for exactly that -- it is the answer, not
     * a placeholder. Delivered on the caller's Executor, as AOSP does.
     */
    public void checkRecognitionSupport(Intent recognizerIntent, Executor executor,
            RecognitionSupportCallback supportListener) {
        Objects.requireNonNull(recognizerIntent);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(supportListener);
        executor.execute(new Runnable() {
            @Override public void run() {
                supportListener.onError(ERROR_CANNOT_CHECK_SUPPORT);
            }
        });
    }

    /**
     * API 33, fire-and-forget form. AOSP's impl forwards to the service and
     * reports nothing back; with no service there is nothing to forward, and
     * the method has no channel on which to say so. Silence is correct HERE
     * (unlike startListening) precisely because the API gives the caller no
     * callback to wait on.
     */
    public void triggerModelDownload(Intent recognizerIntent) {
        Objects.requireNonNull(recognizerIntent);
    }

    /**
     * API 34, with progress. No service to bind to, so the listener gets
     * ERROR_CLIENT -- the same code AOSP's impl reports for a failed
     * connection -- on the supplied Executor.
     */
    public void triggerModelDownload(Intent recognizerIntent, Executor executor,
            ModelDownloadListener listener) {
        Objects.requireNonNull(recognizerIntent);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);
        executor.execute(new Runnable() {
            @Override public void run() { listener.onError(ERROR_CLIENT); }
        });
    }
}
