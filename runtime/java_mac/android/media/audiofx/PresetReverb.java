// android.media.audiofx.PresetReverb -- preset-based reverb. AOSP frameworks/base
// media/java/android/media/audiofx/PresetReverb.java. Inert on Mac.apk: preset
// stored + read back, audio unchanged.
package android.media.audiofx;

public class PresetReverb extends AudioEffect {
    public static final int PARAM_PRESET = 0;
    public static final short PRESET_NONE = 0;
    public static final short PRESET_SMALLROOM = 1;
    public static final short PRESET_MEDIUMROOM = 2;
    public static final short PRESET_LARGEROOM = 3;
    public static final short PRESET_MEDIUMHALL = 4;
    public static final short PRESET_LARGEHALL = 5;
    public static final short PRESET_PLATE = 6;
    private short mPreset = PRESET_NONE;

    public PresetReverb(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, priority, audioSession);
    }
    public void setPreset(short preset) { mPreset = preset; }
    public short getPreset() { return mPreset; }
    public void setParameterListener(OnParameterChangeListener listener) {}

    public static class Settings {
        public short preset;
        public Settings() {}
        public Settings(String settings) {}
        @Override public String toString() { return "PresetReverb;preset=" + preset; }
    }
    public Settings getProperties() { Settings s = new Settings(); s.preset = mPreset; return s; }
    public void setProperties(Settings settings) { if (settings != null) mPreset = settings.preset; }

    public interface OnParameterChangeListener {
        void onParameterChange(PresetReverb effect, int status, int param, short value);
    }
}
