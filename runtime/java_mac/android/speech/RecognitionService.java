// android.speech.RecognitionService -- the base class an APP implements to
// PROVIDE speech recognition to the rest of the system. AOSP:
// frameworks/base/core/java/android/speech/RecognitionService.java
//
// The direction matters for what "implemented" means here. This class is not
// something Mac.apk calls; it is something a guest app EXTENDS. What the app
// needs is: the class to exist, the abstract methods to have AOSP's exact
// signatures (so its @Override compiles and its subclass links), the
// SERVICE_INTERFACE / SERVICE_META_DATA constants, and a Callback whose
// methods it can invoke without exploding.
//
// The one thing that genuinely cannot work is the BINDER side: AOSP's onBind
// returns an IRecognitionService.Stub that the system server talks to, and
// Mac.apk has no system server to bind from. AOSP's own onBind returns the
// stub unconditionally, so returning our (null-binder) default is the honest
// analogue -- nothing in this process will ever bind to it, and an app that
// declares such a service in its manifest still installs, still constructs,
// and still receives onCreate/onDestroy.
package android.speech;

import android.content.AttributionSource;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

public abstract class RecognitionService extends android.app.Service {

    /** The {@link Intent} that must be declared as handled by the service. */
    public static final String SERVICE_INTERFACE = "android.speech.RecognitionService";

    /** Name under which a RecognitionService component publishes information
     *  about itself (a {@code <recognition-service>} meta-data tag). */
    public static final String SERVICE_META_DATA = "android.speech";

    public RecognitionService() {}

    /**
     * Notifies the service that it should start listening for speech.
     * Called on the main application thread.
     */
    protected abstract void onStartListening(Intent recognizerIntent, Callback listener);

    /**
     * Notifies the service that it should stop listening. The recognizer is
     * still expected to deliver its results.
     */
    protected abstract void onStopListening(Callback listener);

    /**
     * Notifies the service that it should cancel the recognition and NOT
     * deliver results.
     */
    protected abstract void onCancel(Callback listener);

    /**
     * AOSP: "Queries the service on whether it would support a
     * #onStartListening(Intent, Callback) for the same {@code
     * recognizerIntent}." The base implementation answers
     * SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT, which is the documented
     * behaviour for a service that has not overridden it -- NOT a stub we
     * invented.
     */
    public void onCheckRecognitionSupport(Intent recognizerIntent,
            SupportCallback supportCallback) {
        supportCallback.onError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT);
    }

    /** API 34 overload carrying the caller's AttributionSource. AOSP's base
     *  implementation delegates to the two-argument form. */
    public void onCheckRecognitionSupport(Intent recognizerIntent,
            AttributionSource attributionSource, SupportCallback supportCallback) {
        onCheckRecognitionSupport(recognizerIntent, supportCallback);
    }

    /**
     * AOSP: "Requests the download of the recognizer support for
     * {@code recognizerIntent}." The base implementation does nothing; a
     * service that supports downloads overrides it.
     */
    public void onTriggerModelDownload(Intent recognizerIntent) {}

    /** API 34 overload carrying the caller's AttributionSource. */
    public void onTriggerModelDownload(Intent recognizerIntent,
            AttributionSource attributionSource) {
        onTriggerModelDownload(recognizerIntent);
    }

    /** API 34 overload that reports progress. AOSP's base implementation
     *  answers ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS, which is the code that
     *  exists precisely for "this service does not report download events". */
    public void onTriggerModelDownload(Intent recognizerIntent,
            AttributionSource attributionSource, ModelDownloadListener listener) {
        listener.onError(SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS);
    }

    /**
     * AOSP: "Returns the maximal number of recognition sessions ongoing at the
     * same time." The default is 1.
     */
    public int getMaxConcurrentSessionsCount() {
        return 1;
    }

    /**
     * AOSP declares this FINAL: a RecognitionService subclass must not
     * substitute its own binder, because the system server expects the
     * IRecognitionService stub. Mirrored, including the finality.
     *
     * There is no system server in this process to bind from, so the
     * superclass default (null) is returned -- see the class header.
     */
    @Override
    public final IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    /**
     * The channel a RecognitionService implementation uses to push results
     * back. AOSP's real Callback forwards each call over Binder to the
     * client's RecognitionListener and therefore declares {@code throws
     * RemoteException} on most methods; those declarations are part of the
     * public API, so subclass code that catches RemoteException still
     * compiles.
     *
     * This base class is INSTANTIABLE and non-abstract in AOSP too -- it is
     * created by the framework, not by the app -- so an app never constructs
     * one and the default bodies below are only reachable if it does.
     */
    public static class Callback {
        /** @hide framework-constructed in AOSP. */
        public Callback() {}

        public void beginningOfSpeech() throws RemoteException {}

        public void bufferReceived(byte[] buffer) throws RemoteException {}

        public void endOfSpeech() throws RemoteException {}

        public void error(int error) throws RemoteException {}

        public void partialResults(Bundle partialResults) throws RemoteException {}

        public void readyForSpeech(Bundle params) throws RemoteException {}

        public void results(Bundle results) throws RemoteException {}

        public void rmsChanged(float rmsdB) throws RemoteException {}

        public void segmentResults(Bundle results) throws RemoteException {}

        public void endOfSegmentedSession() throws RemoteException {}

        /** AOSP does NOT declare RemoteException on this one -- it swallows
         *  the failure internally. Mirrored, because a subclass that calls it
         *  inside a try/catch(RemoteException) would not compile if we added
         *  a checked exception AOSP does not have. */
        public void languageDetection(Bundle results) {}

        /**
         * The UID of the process that requested the recognition. Without a
         * Binder transaction there is no remote caller, so this is our own
         * uid -- which is also what AOSP reports for a service bound from the
         * same process.
         */
        public int getCallingUid() {
            return android.os.Process.myUid();
        }

        /** API 34: the caller's AttributionSource. Same reasoning as
         *  getCallingUid: the caller is this process. */
        public AttributionSource getCallingAttributionSource() {
            return AttributionSource.myAttributionSource();
        }
    }

    /** The reply channel for {@link #onCheckRecognitionSupport}. Concrete and
     *  framework-constructed in AOSP, exactly as {@link Callback} is. */
    public static class SupportCallback {
        /** @hide framework-constructed in AOSP. */
        public SupportCallback() {}

        public void onSupportResult(RecognitionSupport recognitionSupport) {}

        public void onError(int error) {}
    }
}
