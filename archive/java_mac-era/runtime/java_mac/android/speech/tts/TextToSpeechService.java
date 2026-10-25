// android.speech.tts.TextToSpeechService -- the base class an APP extends to
// PROVIDE a TTS engine. AOSP:
// frameworks/base/core/java/android/speech/tts/TextToSpeechService.java
//
// Direction, as with RecognitionService: Mac.apk never calls this; a guest app
// SUBCLASSES it. What the app needs is that the class exists, that the six
// abstract methods carry AOSP's exact signatures and protection (so its
// @Override compiles and its subclass links), and that the concrete hooks have
// AOSP's default behaviour.
//
// The binder half cannot work here -- AOSP's onBind returns an
// ITextToSpeechService.Stub that the system's TextToSpeech client binds to,
// and there is no such client in this process. Our onBind returns the Service
// default (null), so an app that declares the service still installs,
// constructs, and receives onCreate/onDestroy; nothing binds to it. That is
// visible and inert rather than silently wrong.
package android.speech.tts;

import android.content.Intent;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class TextToSpeechService extends android.app.Service {

    public TextToSpeechService() {}

    /**
     * Checks whether the engine supports a given language.
     *
     * @return one of {@link TextToSpeech#LANG_AVAILABLE},
     *         {@link TextToSpeech#LANG_COUNTRY_AVAILABLE},
     *         {@link TextToSpeech#LANG_COUNTRY_VAR_AVAILABLE},
     *         {@link TextToSpeech#LANG_MISSING_DATA} or
     *         {@link TextToSpeech#LANG_NOT_SUPPORTED}.
     */
    protected abstract int onIsLanguageAvailable(String lang, String country, String variant);

    /**
     * The currently loaded language as a three-element array
     * {ISO3 language, ISO3 country, variant}.
     */
    protected abstract String[] onGetLanguage();

    /** Loads a language; same return values as {@link #onIsLanguageAvailable}. */
    protected abstract int onLoadLanguage(String lang, String country, String variant);

    /** Notifies the engine that it should stop the current synthesis. */
    protected abstract void onStop();

    /**
     * Performs the synthesis, pushing audio through {@code callback}.
     * Called on the synthesis thread, never the main thread.
     */
    protected abstract void onSynthesizeText(SynthesisRequest request,
            SynthesisCallback callback);

    /**
     * Engine-specific features available for a language. AOSP's base
     * implementation returns null (meaning "no special features"), NOT an
     * empty set -- callers distinguish the two, so this is transcribed
     * literally.
     */
    protected Set<String> onGetFeaturesForLanguage(String lang, String country, String variant) {
        return null;
    }

    /**
     * The voices the engine offers. AOSP's base implementation SYNTHESISES a
     * one-voice-per-supported-locale list from onIsLanguageAvailable rather
     * than returning empty, so an engine that only implements the language
     * methods still reports voices. Mirrored, including the quality/latency
     * defaults and the "not installed" flag derived from LANG_MISSING_DATA.
     */
    public List<Voice> onGetVoices() {
        final List<Voice> voices = new ArrayList<>();
        for (String language : Locale.getISOLanguages()) {
            final Locale locale = new Locale(language);
            if (onIsLanguageAvailable(locale.getISO3Language(), "", "")
                    >= TextToSpeech.LANG_AVAILABLE) {
                final String voiceName = onGetDefaultVoiceNameFor(
                        locale.getISO3Language(), "", "");
                if (voiceName != null && !voiceName.isEmpty()) {
                    voices.add(new Voice(voiceName, locale,
                            Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL,
                            false /* requiresNetworkConnection */,
                            Collections.<String>emptySet()));
                }
            }
        }
        return voices;
    }

    /**
     * The default voice name for a locale. AOSP's base implementation returns
     * the locale string when the language is available and null otherwise --
     * this is what makes the default onGetVoices() above produce anything.
     */
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        final int result = onIsLanguageAvailable(lang, country, variant);
        final Locale locale = new Locale(lang, country, variant);
        switch (result) {
            case TextToSpeech.LANG_AVAILABLE:
                return new Locale(lang).toString();
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                return new Locale(lang, country).toString();
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                return locale.toString();
            default:
                return null;
        }
    }

    /**
     * Loads a voice by name. AOSP's base implementation maps the name back to
     * a Locale and delegates to onLoadLanguage.
     */
    public int onLoadVoice(String voiceName) {
        final Locale locale = macapkLocaleFromName(voiceName);
        if (locale == null) {
            return TextToSpeech.ERROR;
        }
        final int result = onIsLanguageAvailable(locale.getISO3Language(),
                locale.getISO3Country(), locale.getVariant());
        if (result == TextToSpeech.LANG_AVAILABLE
                || result == TextToSpeech.LANG_COUNTRY_AVAILABLE
                || result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
            onLoadLanguage(locale.getISO3Language(), locale.getISO3Country(),
                    locale.getVariant());
            return TextToSpeech.SUCCESS;
        }
        return TextToSpeech.ERROR;
    }

    /**
     * Whether a voice name is one this engine offers. AOSP's base
     * implementation is the same locale round-trip as onLoadVoice, minus the
     * load.
     */
    public int onIsValidVoiceName(String voiceName) {
        final Locale locale = macapkLocaleFromName(voiceName);
        if (locale == null) {
            return TextToSpeech.ERROR;
        }
        final int result = onIsLanguageAvailable(locale.getISO3Language(),
                locale.getISO3Country(), locale.getVariant());
        if (result == TextToSpeech.LANG_AVAILABLE
                || result == TextToSpeech.LANG_COUNTRY_AVAILABLE
                || result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
            return TextToSpeech.SUCCESS;
        }
        return TextToSpeech.ERROR;
    }

    /**
     * AOSP's onGetVoices names voices with Locale.toString(), i.e.
     * "lang_COUNTRY_VARIANT"; this is the inverse. Returns null for anything
     * that does not have that shape, which the callers above turn into
     * TextToSpeech.ERROR.
     */
    private static Locale macapkLocaleFromName(String voiceName) {
        if (voiceName == null || voiceName.isEmpty()) {
            return null;
        }
        final String[] parts = voiceName.split("_", 3);
        try {
            switch (parts.length) {
                case 1: return new Locale(parts[0]);
                case 2: return new Locale(parts[0], parts[1]);
                case 3: return new Locale(parts[0], parts[1], parts[2]);
                default: return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * AOSP does NOT declare this final (unlike RecognitionService.onBind), so
     * a subclass may override it. See the class header for why the default
     * returns the Service base value here.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
