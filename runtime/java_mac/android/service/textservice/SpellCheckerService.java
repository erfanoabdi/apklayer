package android.service.textservice;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

/**
 * Base for a spell-checker service. An app (a keyboard, a dictionary, a
 * language pack) subclasses it and returns a Session from createSession().
 *
 * Mac.apk binds no spell checker: TextServicesManager has no service to route
 * to, so the platform never calls createSession() and a guest's checker is
 * never driven. What must work is the CLASS -- the subclass has to load, and
 * Session's two default methods have to behave, because a checker's own tests
 * (and any app that drives its Session directly) call them.
 *
 * Those two defaults ARE implemented rather than stubbed, because AOSP's
 * versions do real work that needs no platform:
 *   - onGetSuggestionsMultiple loops onGetSuggestions and re-stamps each
 *     result's cookie/sequence from the corresponding TextInfo. Returning null
 *     or an empty array would break every checker that relies on the default.
 *   - onGetSentenceSuggestionsMultiple wraps each result as a one-span
 *     sentence. ⚠ DIVERGENCE, stated plainly: AOSP splits the sentence into
 *     words first, using the @hide SentenceLevelAdapter and the session locale.
 *     We do not word-split, so a checker that relies on the default (rather
 *     than overriding it, which most do) gets ONE span per input instead of one
 *     per word. The suggestions themselves come from the checker's own
 *     onGetSuggestions either way.
 *
 * Constant transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   SERVICE_INTERFACE = "android.service.textservice.SpellCheckerService"
 *
 * Source: frameworks/base/core/java/android/service/textservice/SpellCheckerService.java
 */
public abstract class SpellCheckerService extends android.app.Service {

    public static final String SERVICE_INTERFACE =
            "android.service.textservice.SpellCheckerService";

    public SpellCheckerService() { }

    public abstract Session createSession();

    /** AOSP declares this final and returns the text-services binder. */
    @Override public final IBinder onBind(Intent intent) { return null; }

    /** One spell-checking conversation with one client, for one locale. */
    public abstract static class Session {

        private Bundle mBundle;
        private String mLocale;
        private int mSupportedAttributes;

        public Session() { }

        public abstract void onCreate();

        public abstract SuggestionsInfo onGetSuggestions(TextInfo textInfo,
                int suggestionsLimit);

        /** AOSP's real default: loop, then re-stamp the correlation pair so the
         *  client can match each result to the request that produced it. */
        public SuggestionsInfo[] onGetSuggestionsMultiple(TextInfo[] textInfos,
                int suggestionsLimit, boolean sequentialWords) {
            if (textInfos == null) return new SuggestionsInfo[0];
            final int length = textInfos.length;
            final SuggestionsInfo[] retval = new SuggestionsInfo[length];
            for (int i = 0; i < length; ++i) {
                retval[i] = onGetSuggestions(textInfos[i], suggestionsLimit);
                if (retval[i] != null && textInfos[i] != null) {
                    retval[i].setCookieAndSequence(
                            textInfos[i].getCookie(), textInfos[i].getSequence());
                }
            }
            return retval;
        }

        /** See the DIVERGENCE note on the class: one span per input, not one
         *  per word. AOSP returns an empty array when it has no locale, which
         *  is the shape a client already handles for an empty input. */
        public SentenceSuggestionsInfo[] onGetSentenceSuggestionsMultiple(
                TextInfo[] textInfos, int suggestionsLimit) {
            if (textInfos == null || textInfos.length == 0) {
                return new SentenceSuggestionsInfo[0];
            }
            final SentenceSuggestionsInfo[] retval =
                    new SentenceSuggestionsInfo[textInfos.length];
            for (int i = 0; i < textInfos.length; ++i) {
                final TextInfo info = textInfos[i];
                final SuggestionsInfo si = onGetSuggestions(info, suggestionsLimit);
                final String text = info == null ? null : info.getText();
                retval[i] = new SentenceSuggestionsInfo(
                        new SuggestionsInfo[] { si },
                        new int[] { 0 },
                        new int[] { text == null ? 0 : text.length() });
            }
            return retval;
        }

        /** Empty in AOSP too; a session overrides them. */
        public void onCancel() { }

        public void onClose() { }

        /** The locale the client asked for, or null before the session is
         *  bound -- which is always, here. */
        public String getLocale() { return mLocale; }

        public Bundle getBundle() { return mBundle; }

        /** API 33. 0 = no attributes requested, AOSP's value for a session the
         *  client did not configure. */
        public int getSupportedAttributes() { return mSupportedAttributes; }

        /** AOSP's @hide setInternalSession(); package-visible so the fields are
         *  assignable without adding public setters AOSP does not have. */
        void setInternalSession(String locale, Bundle bundle, int supportedAttributes) {
            mLocale = locale;
            mBundle = bundle;
            mSupportedAttributes = supportedAttributes;
        }
    }
}
