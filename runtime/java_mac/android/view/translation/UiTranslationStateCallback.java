// android.view.translation.UiTranslationStateCallback -- told when the system
// starts / pauses / resumes / finishes translating an activity's UI.
// Source: frameworks/base
// core/java/android/view/translation/UiTranslationStateCallback.java
// (android16-release). AOSP declares onStarted/onResumed and the packageName
// overloads as `default`, and onPaused/onFinished as required.
package android.view.translation;

public interface UiTranslationStateCallback {
    void onPaused();
    default void onPaused(String packageName) { onPaused(); }

    void onFinished();
    default void onFinished(String packageName) { onFinished(); }

    default void onStarted(android.icu.util.ULocale sourceLocale,
            android.icu.util.ULocale targetLocale) { }
    default void onStarted(android.icu.util.ULocale sourceLocale,
            android.icu.util.ULocale targetLocale, String packageName) {
        onStarted(sourceLocale, targetLocale);
    }

    default void onResumed(android.icu.util.ULocale sourceLocale,
            android.icu.util.ULocale targetLocale) { }
    default void onResumed(android.icu.util.ULocale sourceLocale,
            android.icu.util.ULocale targetLocale, String packageName) {
        onResumed(sourceLocale, targetLocale);
    }
}
