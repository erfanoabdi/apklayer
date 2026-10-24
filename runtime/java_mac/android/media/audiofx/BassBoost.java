// android.media.audiofx.BassBoost -- low-frequency boost effect. AOSP
// frameworks/base media/java/android/media/audiofx/BassBoost.java. Inert on
// Mac.apk (no per-session effect engine): strength is stored + read back, audio
// is unchanged.
package android.media.audiofx;

public class BassBoost extends AudioEffect {
    public static final int PARAM_STRENGTH_SUPPORTED = 0;
    public static final int PARAM_STRENGTH = 1;
    private short mStrength = 0;

    public BassBoost(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, priority, audioSession);
    }
    public boolean getStrengthSupported() { return true; }
    public void setStrength(short strength) { mStrength = strength; }
    public short getRoundedStrength() { return mStrength; }
    public void setParameterListener(OnParameterChangeListener listener) {}

    public static class Settings {
        public short strength;
        public Settings() {}
        public Settings(String settings) {}
        @Override public String toString() { return "BassBoost;strength=" + strength; }
    }
    public Settings getProperties() { Settings s = new Settings(); s.strength = mStrength; return s; }
    public void setProperties(Settings settings) { if (settings != null) mStrength = settings.strength; }

    public interface OnParameterChangeListener {
        void onParameterChange(BassBoost effect, int status, int param, short value);
    }
}
