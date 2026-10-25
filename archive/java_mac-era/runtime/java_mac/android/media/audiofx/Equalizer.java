// android.media.audiofx.Equalizer -- multi-band graphic equalizer. AOSP
// frameworks/base media/java/android/media/audiofx/Equalizer.java.
//
// Mac.apk has no per-session EQ in the mixer, so this presents a faithful
// 5-band EQ surface (the AOSP reference effect's default band count) with a
// flat, settable level map. Music players query band count / center freqs /
// level range and set per-band levels; reads reflect writes, and the audio is
// unchanged (a flat EQ) -- honest for a pass-through host.
package android.media.audiofx;

public class Equalizer extends AudioEffect {
    public static final int PARAM_NUM_BANDS = 0;
    public static final int PARAM_LEVEL_RANGE = 1;
    public static final int PARAM_BAND_LEVEL = 2;
    public static final int PARAM_CENTER_FREQ = 3;
    public static final int PARAM_BAND_FREQ_RANGE = 4;
    public static final int PARAM_GET_BAND = 5;
    public static final int PARAM_CURRENT_PRESET = 6;
    public static final int PARAM_GET_NUM_OF_PRESETS = 7;
    public static final int PARAM_GET_PRESET_NAME = 8;

    private static final short NUM_BANDS = 5;
    private static final int[] CENTER_FREQ = { 60000, 230000, 910000, 3600000, 14000000 };
    private final short[] mBandLevels = new short[NUM_BANDS];
    private short mCurrentPreset = -1;
    private final String[] mPresets = { "Normal", "Classical", "Dance", "Flat", "Folk",
                                        "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock" };

    public Equalizer(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, priority, audioSession);
    }

    public short getNumberOfBands() { return NUM_BANDS; }
    public short[] getBandLevelRange() { return new short[] { (short) -1500, (short) 1500 }; }
    // AOSP returns void and signals failure by throwing, not by a status int.
    // frameworks/base/media/java/android/media/audiofx/Equalizer.java
    public void setBandLevel(short band, short level) {
        if (band >= 0 && band < NUM_BANDS) mBandLevels[band] = level;
    }
    public short getBandLevel(short band) {
        return (band >= 0 && band < NUM_BANDS) ? mBandLevels[band] : 0;
    }
    public int getCenterFreq(short band) {
        return (band >= 0 && band < NUM_BANDS) ? CENTER_FREQ[band] : 0;
    }
    public int[] getBandFreqRange(short band) {
        int center = getCenterFreq(band);
        return new int[] { Math.max(0, center / 2), center * 2 };
    }
    public short getBand(int frequency) {
        for (short i = 0; i < NUM_BANDS; i++) {
            if (frequency <= CENTER_FREQ[i]) return i;
        }
        return (short) (NUM_BANDS - 1);
    }
    public short getNumberOfPresets() { return (short) mPresets.length; }
    public short getCurrentPreset() { return mCurrentPreset; }
    public void usePreset(short preset) { mCurrentPreset = preset; }
    public String getPresetName(short preset) {
        return (preset >= 0 && preset < mPresets.length) ? mPresets[preset] : "";
    }

    public Settings getProperties() {
        Settings s = new Settings();
        s.curPreset = mCurrentPreset;
        s.numBands = NUM_BANDS;
        s.bandLevels = mBandLevels.clone();
        return s;
    }
    public void setProperties(Settings settings) {
        if (settings == null) return;
        mCurrentPreset = settings.curPreset;
        if (settings.bandLevels != null) {
            System.arraycopy(settings.bandLevels, 0, mBandLevels, 0,
                Math.min(settings.bandLevels.length, NUM_BANDS));
        }
    }
    public void setParameterListener(OnParameterChangeListener listener) {}

    public static class Settings {
        public short curPreset;
        public short numBands = 0;
        public short[] bandLevels = null;
        public Settings() {}
        public Settings(String settings) {}
        @Override public String toString() { return "Equalizer;curPreset=" + curPreset; }
    }
    public interface OnParameterChangeListener {
        void onParameterChange(Equalizer effect, int status, int param1, int param2, int value);
    }
}
