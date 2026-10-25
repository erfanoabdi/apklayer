// android.view.textservice.TextServicesManager
//
// AOSP's spell-checker / text-services manager. Apps (and the framework's own
// TextView SpellChecker) fetch it via
// getSystemService(TEXT_SERVICES_MANAGER_SERVICE) and query the enabled
// spell-checkers. Mac.apk ships NO spell-checker service, so the enabled lists
// are empty, isSpellCheckerEnabled() is false, and newSpellCheckerSession()
// returns null -- which is precisely what AOSP returns when no spell checker is
// enabled, and what every caller already null-checks for. That is the honest
// "no spell checking" answer rather than a null service that NPEs the EditText
// spell-check path.
//
// ⚠ Three methods here previously used Object / Object[] in place of
// SpellCheckerInfo, SpellCheckerSubtype and SpellCheckerSessionListener, which
// no app could ever link against (the caller invokes the exact AOSP descriptor).
// Those types now exist, so the signatures are the real ones.
//
// Source: frameworks/base core/java/android/view/textservice/TextServicesManager.java
// (android16-release).
package android.view.textservice;

import android.content.Context;

public final class TextServicesManager {

    public TextServicesManager() {}
    public TextServicesManager(Context context) {}

    public boolean isSpellCheckerEnabled() { return false; }

    /** AOSP getCurrentSpellCheckerInfo() (API 31): the checker currently
     *  selected by the user, null when none is. */
    public SpellCheckerInfo getCurrentSpellCheckerInfo() { return null; }

    /** AOSP getEnabledSpellCheckerInfos() (API 31): @NonNull, empty here. */
    public java.util.List<SpellCheckerInfo> getEnabledSpellCheckerInfos() {
        return java.util.Collections.emptyList();
    }

    /** AOSP getCurrentSpellCheckerSubtype(boolean): the subtype of the current
     *  checker. There is no current checker. */
    public SpellCheckerSubtype getCurrentSpellCheckerSubtype(
            boolean allowImplicitlySelectedSubtype) {
        return null;
    }

    /** AOSP getEnabledSpellCheckers(): the array form that predates
     *  getEnabledSpellCheckerInfos(). Empty, consistently with it. */
    public SpellCheckerInfo[] getEnabledSpellCheckers() { return new SpellCheckerInfo[0]; }

    /** AOSP newSpellCheckerSession(Bundle, Locale, SpellCheckerSessionListener,
     *  boolean): returns NULL when no spell checker is available, which AOSP
     *  documents explicitly and every caller handles. */
    public SpellCheckerSession newSpellCheckerSession(android.os.Bundle bundle,
            java.util.Locale locale,
            SpellCheckerSession.SpellCheckerSessionListener listener,
            boolean referToSpellCheckerLanguageSettings) {
        java.util.Objects.requireNonNull(listener);
        return null;
    }

    /** AOSP newSpellCheckerSession(SpellCheckerSessionParams, Executor,
     *  SpellCheckerSessionListener) (API 31): the modern overload, same null
     *  result for the same reason. */
    public SpellCheckerSession newSpellCheckerSession(
            SpellCheckerSession.SpellCheckerSessionParams params,
            java.util.concurrent.Executor executor,
            SpellCheckerSession.SpellCheckerSessionListener listener) {
        java.util.Objects.requireNonNull(params);
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(listener);
        return null;
    }
}
