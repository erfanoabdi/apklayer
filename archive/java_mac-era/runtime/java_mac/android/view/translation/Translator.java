// android.view.translation.Translator -- a live on-device translation session.
// Source: frameworks/base core/java/android/view/translation/Translator.java
// (android16-release).
//
// Mac.apk ships NO on-device translator: TranslationManager reports an empty
// capability set and never calls back with a Translator, so nothing in this tree
// hands one out. The type exists because createOnDeviceTranslator names it, and
// because a caller that somehow holds one must not hang -- translate() answers
// TRANSLATION_STATUS_CONTEXT_UNSUPPORTED on the caller's Executor, which is
// AOSP's own status for a context the translator cannot serve.
package android.view.translation;

public class Translator {
    private boolean mDestroyed;

    /** @hide -- only TranslationManager constructs one. */
    Translator() {}

    public void translate(TranslationRequest request,
            android.os.CancellationSignal cancellationSignal,
            java.util.concurrent.Executor executor,
            java.util.function.Consumer<TranslationResponse> callback) {
        java.util.Objects.requireNonNull(request);
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(callback);
        if (mDestroyed) {
            throw new IllegalStateException("This translator has been destroyed");
        }
        final TranslationResponse response = new TranslationResponse.Builder(
                TranslationResponse.TRANSLATION_STATUS_CONTEXT_UNSUPPORTED).build();
        executor.execute(() -> callback.accept(response));
    }

    public void destroy() { mDestroyed = true; }
    public boolean isDestroyed() { return mDestroyed; }
}
