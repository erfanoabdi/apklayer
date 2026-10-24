// android.view.translation.UiTranslationManager -- Context.UI_TRANSLATION_SERVICE.
// Source: frameworks/base
// core/java/android/view/translation/UiTranslationManager.java (android16-release).
//
// UI translation is driven by the SYSTEM (the user asks the assistant to
// translate the screen), never by the app. Mac.apk has no such system surface
// and no on-device translator (see TranslationManager), so the state never
// changes and a registered callback is never invoked -- which is exactly what
// happens on a device where the user never requests a translation. The callback
// is still retained so an app's register/unregister pairing behaves.
package android.view.translation;

public final class UiTranslationManager {
    private final java.util.ArrayList<UiTranslationStateCallback> mCallbacks =
            new java.util.ArrayList<UiTranslationStateCallback>();

    public UiTranslationManager() {}

    public void registerUiTranslationStateCallback(java.util.concurrent.Executor executor,
            UiTranslationStateCallback callback) {
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(callback);
        synchronized (mCallbacks) { mCallbacks.add(callback); }
    }

    public void unregisterUiTranslationStateCallback(UiTranslationStateCallback callback) {
        java.util.Objects.requireNonNull(callback);
        synchronized (mCallbacks) { mCallbacks.remove(callback); }
    }
}
