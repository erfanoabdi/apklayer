package android.speech.tts;

// android.speech.tts.UtteranceProgressListener -- callbacks for the progress of
// an utterance through the synthesis queue (API 15+). AOSP frameworks/base
// core/java/android/speech/tts/UtteranceProgressListener.java. Mac.apk never
// synthesizes audio, so these callbacks are never invoked; the type exists so
// TextToSpeech.setOnUtteranceProgressListener(...) resolves and apps can
// subclass it. The abstract methods and the non-abstract convenience overloads
// mirror AOSP so an app's subclass compiles unchanged.
public abstract class UtteranceProgressListener {

    public abstract void onStart(String utteranceId);

    public abstract void onDone(String utteranceId);

    @Deprecated
    public abstract void onError(String utteranceId);

    public void onError(String utteranceId, int errorCode) {
        onError(utteranceId);
    }

    public void onStop(String utteranceId, boolean interrupted) { }

    public void onBeginSynthesis(String utteranceId, int sampleRateInHz,
                                 int audioFormat, int channelCount) { }

    public void onAudioAvailable(String utteranceId, byte[] audio) { }

    public void onRangeStart(String utteranceId, int start, int end, int frame) { }
}
