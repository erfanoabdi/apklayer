// android.view.translation.TranslationManager -- Context.TRANSLATION_MANAGER_SERVICE.
// Source: frameworks/base
// core/java/android/view/translation/TranslationManager.java (android16-release).
//
// Mac.apk performs NO on-device translation and does not pretend otherwise:
// getOnDeviceTranslationCapabilities() reports an EMPTY set, which is what a
// device with no translation service installed reports, and every downstream
// decision an app makes follows correctly from that. createOnDeviceTranslator
// therefore never produces a Translator -- AOSP's own behaviour when the
// requested TranslationContext is unsupported, where the callback is simply not
// invoked with a translator.
package android.view.translation;

public final class TranslationManager {
    public TranslationManager() {}

    /** AOSP getOnDeviceTranslationCapabilities(int sourceFormat, int targetFormat):
     *  @NonNull, and empty here -- there is no on-device translator. */
    public java.util.Set<TranslationCapability> getOnDeviceTranslationCapabilities(
            int sourceFormat, int targetFormat) {
        return java.util.Collections.emptySet();
    }

    /** AOSP addOnDeviceTranslationCapabilityUpdateListener(Executor, Consumer):
     *  told when a language pack is downloaded or removed. No packs exist and
     *  none can be downloaded, so the capability set never changes and no
     *  callback is ever delivered. */
    public void addOnDeviceTranslationCapabilityUpdateListener(
            java.util.concurrent.Executor executor,
            java.util.function.Consumer<TranslationCapability> capabilityListener) {
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(capabilityListener);
    }
    public void removeOnDeviceTranslationCapabilityUpdateListener(
            java.util.function.Consumer<TranslationCapability> capabilityListener) {
        java.util.Objects.requireNonNull(capabilityListener);
    }

    /** AOSP createOnDeviceTranslator(TranslationContext, Executor,
     *  Consumer&lt;Translator&gt;): AOSP invokes the callback ONLY when a
     *  translator could be created, and getOnDeviceTranslationCapabilities()
     *  above is the documented way to find out beforehand -- it reports none, so
     *  no translator is created and the callback is not invoked. */
    public void createOnDeviceTranslator(TranslationContext translationContext,
            java.util.concurrent.Executor executor,
            java.util.function.Consumer<Translator> callback) {
        java.util.Objects.requireNonNull(translationContext);
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(callback);
    }

    /** AOSP getOnDeviceTranslationSettingsActivityIntent(): the Settings screen
     *  for the translation service, null when there is no such service. */
    public android.app.PendingIntent getOnDeviceTranslationSettingsActivityIntent() {
        return null;
    }
}
