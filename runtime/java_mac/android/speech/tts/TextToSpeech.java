package android.speech.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// android.speech.tts.TextToSpeech -- the client-side synthesis-to-speech API
// (API 4+). AOSP frameworks/base core/java/android/speech/tts/TextToSpeech.java.
// Mac.apk has no on-device TTS engine, so this is a benign no-op implementation:
// it exists so apps that hold a TextToSpeech field or narrate accessibility
// text link and run (Minecraft's MainActivity constructs one in onCreate and
// aborts the whole activity -- never reaching setContentView or starting its
// native game surface -- if the class is absent). Behavior mirrors a correctly
// initialized engine that simply has nothing to speak: init reports SUCCESS,
// speak/stop/language calls succeed silently, and voice/language queries report
// "nothing installed" the way a real engine with no voice data would.
//
// The public surface (constructors, methods, constants, nested types) mirrors
// AOSP verbatim so any app -- not just Minecraft -- resolves against it.
public class TextToSpeech {

    // Result codes.
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int STOPPED = -2;
    public static final int ERROR_SYNTHESIS = -3;
    public static final int ERROR_SERVICE = -4;
    public static final int ERROR_OUTPUT = -5;
    public static final int ERROR_NETWORK = -6;
    public static final int ERROR_NETWORK_TIMEOUT = -7;
    public static final int ERROR_INVALID_REQUEST = -8;
    public static final int ERROR_NOT_INSTALLED_YET = -9;

    // Queue modes.
    public static final int QUEUE_FLUSH = 0;
    public static final int QUEUE_ADD = 1;

    // Language-availability codes.
    public static final int LANG_COUNTRY_VAR_AVAILABLE = 2;
    public static final int LANG_COUNTRY_AVAILABLE = 1;
    public static final int LANG_AVAILABLE = 0;
    public static final int LANG_MISSING_DATA = -1;
    public static final int LANG_NOT_SUPPORTED = -2;

    public static final String ACTION_TTS_QUEUE_PROCESSING_COMPLETED =
        "android.speech.tts.TTS_QUEUE_PROCESSING_COMPLETED";

    // Listener invoked when the engine finishes (or fails) initialization. Real
    // Android binds to the TTS service asynchronously and delivers this on the
    // caller's thread; we post SUCCESS to the main Looper so it lands after the
    // constructor returns (the caller has usually just done a putfield with the
    // new instance, e.g. Minecraft's textToSpeechManager, and its onInit body
    // may read that field back).
    public interface OnInitListener {
        void onInit(int status);
    }

    // Deprecated per-utterance completion callback (superseded by
    // UtteranceProgressListener). Kept for API completeness.
    public interface OnUtteranceCompletedListener {
        void onUtteranceCompleted(String utteranceId);
    }

    private Locale mLanguage = Locale.getDefault();
    private float mSpeechRate = 1.0f;
    private float mPitch = 1.0f;

    public TextToSpeech(Context context, OnInitListener listener) {
        this(context, listener, null);
    }

    public TextToSpeech(Context context, OnInitListener listener, String engine) {
        this(context, listener, engine, null, true);
    }

    public TextToSpeech(Context context, OnInitListener listener, String engine,
                        String packageName, boolean useFallback) {
        dispatchOnInit(listener);
    }

    private static void dispatchOnInit(final OnInitListener listener) {
        if (listener == null) {
            return;
        }
        Looper main = Looper.getMainLooper();
        Runnable r = new Runnable() {
            @Override public void run() {
                listener.onInit(SUCCESS);
            }
        };
        if (main != null) {
            new Handler(main).post(r);
        } else {
            // No looper available; deliver inline as a last resort.
            r.run();
        }
    }

    public int setLanguage(final Locale loc) {
        if (loc != null) {
            mLanguage = loc;
        }
        // A real engine with the requested language installed returns
        // LANG_AVAILABLE; reporting availability keeps callers on their happy
        // path (they will not prompt the user to install voice data).
        return LANG_AVAILABLE;
    }

    public Locale getLanguage() { return mLanguage; }

    public Locale getDefaultLanguage() { return Locale.getDefault(); }

    public int isLanguageAvailable(final Locale loc) { return LANG_AVAILABLE; }

    public Set<Locale> getAvailableLanguages() { return Collections.emptySet(); }

    public int setSpeechRate(float speechRate) { mSpeechRate = speechRate; return SUCCESS; }

    public int setPitch(float pitch) { mPitch = pitch; return SUCCESS; }

    public int setAudioAttributes(AudioAttributes audioAttributes) { return SUCCESS; }

    public int speak(final CharSequence text, final int queueMode,
                     final Bundle params, final String utteranceId) {
        return SUCCESS;
    }

    public int speak(final String text, final int queueMode,
                     final HashMap<String, String> params) {
        return SUCCESS;
    }

    public int playEarcon(final String earcon, final int queueMode,
                          final Bundle params, final String utteranceId) {
        return SUCCESS;
    }

    public int playEarcon(final String earcon, final int queueMode,
                          final HashMap<String, String> params) {
        return SUCCESS;
    }

    public int playSilentUtterance(final long durationInMs, final int queueMode,
                                   final String utteranceId) {
        return SUCCESS;
    }

    public int playSilence(final long durationInMs, final int queueMode,
                           final HashMap<String, String> params) {
        return SUCCESS;
    }

    public int addSpeech(String text, String packagename, int resourceId) { return SUCCESS; }
    public int addSpeech(CharSequence text, String packagename, int resourceId) { return SUCCESS; }
    public int addSpeech(String text, String filename) { return SUCCESS; }
    public int addSpeech(CharSequence text, File file) { return SUCCESS; }
    public int addSpeech(CharSequence text, Uri uri) { return SUCCESS; }

    public int addEarcon(String earcon, String packagename, int resourceId) { return SUCCESS; }
    public int addEarcon(String earcon, String filename) { return SUCCESS; }
    public int addEarcon(String earcon, File file) { return SUCCESS; }
    public int addEarcon(String earcon, Uri uri) { return SUCCESS; }

    public boolean isSpeaking() { return false; }

    public int stop() { return SUCCESS; }

    public void shutdown() { }

    public Set<String> getFeatures(final Locale locale) { return Collections.emptySet(); }

    public Set<Voice> getVoices() { return Collections.emptySet(); }

    public int setVoice(final Voice voice) { return SUCCESS; }

    public Voice getVoice() { return null; }

    public Voice getDefaultVoice() { return null; }

    public int synthesizeToFile(final CharSequence text, final Bundle params,
                                final ParcelFileDescriptor fileDescriptor,
                                final String utteranceId) {
        return ERROR;
    }

    public int synthesizeToFile(final CharSequence text, final Bundle params,
                                final File file, final String utteranceId) {
        return ERROR;
    }

    public int synthesizeToFile(final String text, final HashMap<String, String> params,
                                final String filename) {
        return ERROR;
    }

    public int setOnUtteranceCompletedListener(final OnUtteranceCompletedListener listener) {
        return SUCCESS;
    }

    public int setOnUtteranceProgressListener(UtteranceProgressListener listener) {
        return SUCCESS;
    }

    public int setEngineByPackageName(String enginePackageName) { return SUCCESS; }

    public String getDefaultEngine() { return Engine.DEFAULT_ENGINE; }

    /** AOSP getCurrentEngine(): the package name of the engine in use. We run the
     *  single default engine, so this equals getDefaultEngine(). */
    public String getCurrentEngine() { return Engine.DEFAULT_ENGINE; }

    public boolean areDefaultsEnforced() { return false; }

    public List<EngineInfo> getEngines() { return Collections.emptyList(); }

    /** AOSP getMaxSpeechInputLength(): the maximum String length accepted by
     *  speak(). Public SDK API apps use to chunk long text. AOSP returns 4000. */
    public static int getMaxSpeechInputLength() { return 4000; }

    // Engine-level constants and intent actions. AOSP declares these on the
    // nested Engine class; apps reference e.g. TextToSpeech.Engine.KEY_PARAM_*.
    public static class Engine {
        public static final int DEFAULT_RATE = 100;
        public static final int DEFAULT_PITCH = 100;
        public static final float DEFAULT_VOLUME = 1.0f;
        public static final float DEFAULT_PAN = 0.0f;
        public static final int USE_DEFAULTS = 0;
        public static final String DEFAULT_ENGINE = "com.svox.pico";
        public static final int DEFAULT_STREAM = 3; // AudioManager.STREAM_MUSIC
        public static final int CHECK_VOICE_DATA_PASS = 1;
        public static final int CHECK_VOICE_DATA_FAIL = 0;
        public static final int CHECK_VOICE_DATA_BAD_DATA = -1;
        public static final int CHECK_VOICE_DATA_MISSING_DATA = -2;
        public static final int CHECK_VOICE_DATA_MISSING_VOLUME = -3;
        public static final String INTENT_ACTION_TTS_SERVICE = "android.intent.action.TTS_SERVICE";
        public static final String SERVICE_META_DATA = "android.speech.tts";
        public static final String ACTION_INSTALL_TTS_DATA = "android.speech.tts.engine.INSTALL_TTS_DATA";
        public static final String ACTION_TTS_DATA_INSTALLED = "android.speech.tts.engine.TTS_DATA_INSTALLED";
        public static final String ACTION_CHECK_TTS_DATA = "android.speech.tts.engine.CHECK_TTS_DATA";
        public static final String ACTION_GET_SAMPLE_TEXT = "android.speech.tts.engine.GET_SAMPLE_TEXT";
        public static final String EXTRA_SAMPLE_TEXT = "sampleText";
        public static final String EXTRA_AVAILABLE_VOICES = "availableVoices";
        public static final String EXTRA_UNAVAILABLE_VOICES = "unavailableVoices";
        public static final String EXTRA_VOICE_DATA_ROOT_DIRECTORY = "dataRoot";
        public static final String EXTRA_VOICE_DATA_FILES = "dataFiles";
        public static final String EXTRA_VOICE_DATA_FILES_INFO = "dataFilesInfo";
        public static final String EXTRA_CHECK_VOICE_DATA_FOR = "checkVoiceDataFor";
        public static final String EXTRA_TTS_DATA_INSTALLED = "dataInstalled";
        public static final String KEY_PARAM_RATE = "rate";
        public static final String KEY_PARAM_VOICE_NAME = "voiceName";
        public static final String KEY_PARAM_LANGUAGE = "language";
        public static final String KEY_PARAM_COUNTRY = "country";
        public static final String KEY_PARAM_VARIANT = "variant";
        public static final String KEY_PARAM_ENGINE = "engine";
        public static final String KEY_PARAM_PITCH = "pitch";
        public static final String KEY_PARAM_STREAM = "streamType";
        public static final String KEY_PARAM_AUDIO_ATTRIBUTES = "audioAttributes";
        public static final String KEY_PARAM_UTTERANCE_ID = "utteranceId";
        public static final String KEY_PARAM_VOLUME = "volume";
        public static final String KEY_PARAM_PAN = "pan";
        public static final String KEY_FEATURE_NETWORK_SYNTHESIS = "networkTts";
        public static final String KEY_FEATURE_EMBEDDED_SYNTHESIS = "embeddedTts";
        public static final String KEY_PARAM_SESSION_ID = "sessionId";
        public static final String KEY_FEATURE_NOT_INSTALLED = "notInstalled";
        public static final String KEY_FEATURE_NETWORK_TIMEOUT_MS = "networkTimeoutMs";
        public static final String KEY_FEATURE_NETWORK_RETRIES_COUNT = "networkRetriesCount";
    }

    // Describes an installed engine. AOSP populates these from resolved TTS
    // services; Mac.apk has none, so getEngines() returns an empty list and this
    // exists only for the return element type to resolve.
    public static class EngineInfo {
        public String name;
        public String label;
        public int icon;
        public int priority;
        public boolean system;

        @Override public String toString() {
            return "EngineInfo{name=" + name + "}";
        }
    }
}
