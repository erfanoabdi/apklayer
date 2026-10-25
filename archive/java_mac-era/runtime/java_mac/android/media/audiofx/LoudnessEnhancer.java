// android.media.audiofx.LoudnessEnhancer -- automatic gain/loudness boost. AOSP
// frameworks/base media/java/android/media/audiofx/LoudnessEnhancer.java. Inert
// on Mac.apk: target gain stored + read back, audio unchanged.
package android.media.audiofx;

public class LoudnessEnhancer extends AudioEffect {
    public static final int PARAM_TARGET_GAIN_MB = 0;
    private float mTargetGainmB = 0f;

    public LoudnessEnhancer(int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, 0, audioSession);
    }
    public LoudnessEnhancer(int audioSession, int priority) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, priority, audioSession);
    }
    public void setTargetGain(int gainmB) { mTargetGainmB = gainmB; }
    public float getTargetGain() { return mTargetGainmB; }
    public void setParameterListener(OnParameterChangeListener listener) {}

    public interface OnParameterChangeListener {
        void onParameterChange(LoudnessEnhancer effect, int status, int param, int value);
    }
}
