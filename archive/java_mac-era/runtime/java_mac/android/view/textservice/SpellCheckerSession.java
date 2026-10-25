// android.view.textservice.SpellCheckerSession -- a live conversation with a
// spell-checker service. Source: frameworks/base
// core/java/android/view/textservice/SpellCheckerSession.java (android16-release).
//
// Mac.apk ships NO spell-checker service, so TextServicesManager.
// newSpellCheckerSession() returns null (AOSP's own answer when no checker is
// enabled) and no session is ever created by the framework. A session an app
// somehow holds reports itself DISCONNECTED, which is the state AOSP puts a
// session into when its service dies -- and in that state AOSP's own
// getSuggestions / getSentenceSuggestions do nothing, which is what these do.
//
// ⚠ The listener's two callbacks previously took Object[] because
// SuggestionsInfo / SentenceSuggestionsInfo were not modelled. That was WORSE
// than absent: an app implements onGetSuggestions(SuggestionsInfo[]), which does
// NOT override an Object[] method, so the interface stayed abstract and the app
// would have hit AbstractMethodError rather than a diagnosable link failure.
// Both types now exist, so the callbacks carry AOSP's real descriptors.
package android.view.textservice;

public class SpellCheckerSession {
    public static final String SERVICE_META_DATA = "android.view.textservice.scs";

    public SpellCheckerSession() {}

    public void close() {}
    public void cancel() {}

    /** AOSP isSessionDisconnected(): true once the bound service has gone. No
     *  service is ever bound here, so a session is disconnected from birth. */
    public boolean isSessionDisconnected() { return true; }

    /** AOSP getSpellChecker(): the service backing this session. There is none. */
    public SpellCheckerInfo getSpellChecker() { return null; }

    /** AOSP getSuggestions(TextInfo, int suggestionsLimit), deprecated at API 34
     *  in favour of getSentenceSuggestions. A disconnected session performs no
     *  request, so the listener is not called -- matching AOSP, whose
     *  SpellCheckerSessionListenerImpl drops requests once disconnected. */
    @Deprecated
    public void getSuggestions(TextInfo textInfo, int suggestionsLimit) {}

    /** AOSP getSuggestions(TextInfo[], int, boolean), same note. */
    @Deprecated
    public void getSuggestions(TextInfo[] textInfos, int suggestionsLimit,
            boolean sequentialWords) {}

    /** AOSP getSentenceSuggestions(TextInfo[], int suggestionsLimit): the
     *  sentence-level entry point TextView's SpellChecker uses. Same note. */
    public void getSentenceSuggestions(TextInfo[] textInfos, int suggestionsLimit) {}

    /** AOSP SpellCheckerSession.SpellCheckerSessionListener. */
    public interface SpellCheckerSessionListener {
        void onGetSuggestions(SuggestionsInfo[] results);
        void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results);
    }

    /** AOSP SpellCheckerSession.SpellCheckerSessionParams (API 31): the
     *  parameter object the modern newSpellCheckerSession overload takes. */
    public static class SpellCheckerSessionParams {
        private final java.util.Locale mLocale;
        private final boolean mShouldReferToSpellCheckerLanguageSettings;
        private final int mSupportedAttributes;
        private final android.os.Bundle mExtras;

        private SpellCheckerSessionParams(Builder b) {
            mLocale = b.mLocale;
            mShouldReferToSpellCheckerLanguageSettings = b.mShouldReferToSpellCheckerLanguageSettings;
            mSupportedAttributes = b.mSupportedAttributes;
            mExtras = b.mExtras;
        }

        public java.util.Locale getLocale() { return mLocale; }
        public boolean shouldReferToSpellCheckerLanguageSettings() {
            return mShouldReferToSpellCheckerLanguageSettings;
        }
        public int getSupportedAttributes() { return mSupportedAttributes; }
        public android.os.Bundle getExtras() { return mExtras; }

        public static final class Builder {
            private java.util.Locale mLocale;
            private boolean mShouldReferToSpellCheckerLanguageSettings;
            private int mSupportedAttributes;
            private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

            public Builder() {}
            public Builder setLocale(java.util.Locale locale) { mLocale = locale; return this; }
            public Builder setShouldReferToSpellCheckerLanguageSettings(boolean value) {
                mShouldReferToSpellCheckerLanguageSettings = value;
                return this;
            }
            public Builder setSupportedAttributes(int supportedAttributes) {
                mSupportedAttributes = supportedAttributes;
                return this;
            }
            public Builder setExtras(android.os.Bundle extras) {
                mExtras = java.util.Objects.requireNonNull(extras);
                return this;
            }
            public SpellCheckerSessionParams build() {
                // AOSP: exactly one of the two locale sources must be given.
                if (mLocale == null && !mShouldReferToSpellCheckerLanguageSettings) {
                    throw new IllegalArgumentException(
                            "mLocale should not be null if "
                            + "shouldReferToSpellCheckerLanguageSettings is false.");
                }
                return new SpellCheckerSessionParams(this);
            }
        }
    }
}
