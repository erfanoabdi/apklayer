// android.speech.tts.SynthesisRequest -- one unit of work handed to a
// TextToSpeechService. AOSP:
// frameworks/base/core/java/android/speech/tts/SynthesisRequest.java
//
// The language/country/variant, voice name, speech rate, pitch and caller uid
// are set by the FRAMEWORK through @hide package-private setters after
// construction; a guest app only ever reads them. The setters are mirrored as
// package-private here for exactly that reason -- widening them would let an
// app mutate a request in a way the real platform does not allow.
package android.speech.tts;

import android.os.Bundle;

public final class SynthesisRequest {
    private final CharSequence mText;
    private final Bundle mParams;
    private String mVoiceName;
    private String mLanguage;
    private String mCountry;
    private String mVariant;
    private int mSpeechRate;
    private int mPitch;
    private int mCallerUid;

    public SynthesisRequest(String text, Bundle params) {
        mText = text;
        // AOSP takes a defensive copy: the framework reuses the caller's
        // Bundle, and a service that stashed the reference would see it
        // change underneath.
        mParams = new Bundle(params);
    }

    public SynthesisRequest(CharSequence text, Bundle params) {
        mText = text;
        mParams = new Bundle(params);
    }

    /**
     * @deprecated As of API 21, replaced by {@link #getCharSequenceText}.
     * AOSP returns null when the request was built from a CharSequence that is
     * not a String -- mirrored, because a caller on the deprecated path must
     * see the same null it sees on a device rather than a lossy toString().
     */
    @Deprecated
    public String getText() {
        return (mText instanceof String) ? (String) mText : null;
    }

    /** The text to synthesize. */
    public CharSequence getCharSequenceText() { return mText; }

    /** ISO 3-letter language code. */
    public String getLanguage() { return mLanguage; }

    /** ISO 3-letter country code. */
    public String getCountry() { return mCountry; }

    /** Language variant. */
    public String getVariant() { return mVariant; }

    /** The name of the voice to use, or null for the language default. */
    public String getVoiceName() { return mVoiceName; }

    /** Speech rate, 100 = normal. */
    public int getSpeechRate() { return mSpeechRate; }

    /** Pitch, 100 = normal. */
    public int getPitch() { return mPitch; }

    /** The uid of the app that requested the synthesis. */
    public int getCallerUid() { return mCallerUid; }

    /** The engine-specific parameters the client supplied. */
    public Bundle getParams() { return mParams; }

    // ---- @hide, framework-only, package-private in AOSP -------------------
    void setLanguage(String language, String country, String variant) {
        mLanguage = language;
        mCountry = country;
        mVariant = variant;
    }

    void setVoiceName(String voiceName) { mVoiceName = voiceName; }

    void setSpeechRate(int speechRate) { mSpeechRate = speechRate; }

    void setPitch(int pitch) { mPitch = pitch; }

    void setCallerUid(int uid) { mCallerUid = uid; }
}
