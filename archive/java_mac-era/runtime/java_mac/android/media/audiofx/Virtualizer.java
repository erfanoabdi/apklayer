// android.media.audiofx.Virtualizer -- stereo/surround virtualization effect.
// AOSP frameworks/base media/java/android/media/audiofx/Virtualizer.java. Inert
// on Mac.apk: strength stored + read back, audio unchanged.
package android.media.audiofx;

public class Virtualizer extends AudioEffect {
    public static final int PARAM_STRENGTH_SUPPORTED = 0;
    public static final int PARAM_STRENGTH = 1;
    public static final int VIRTUALIZATION_MODE_OFF = 0;
    public static final int VIRTUALIZATION_MODE_AUTO = 1;
    public static final int VIRTUALIZATION_MODE_BINAURAL = 2;
    public static final int VIRTUALIZATION_MODE_TRANSAURAL = 3;
    private short mStrength = 0;

    public Virtualizer(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, priority, audioSession);
    }
    public boolean getStrengthSupported() { return true; }
    public void setStrength(short strength) { mStrength = strength; }
    public short getRoundedStrength() { return mStrength; }
    public boolean canVirtualize(int inputChannelMask, int virtualizationMode) { return false; }

    /**
     * AOSP: "Queries the virtual speaker angles for a channel mask / device
     * combination", filling angles[] with (channel mask, azimuth, elevation)
     * triples and returning FALSE when the configuration cannot be virtualized.
     *
     * false here, matching canVirtualize() above and the class's present-but-
     * inert contract (see AudioEffect): there is no virtualizer engine, so there
     * are no virtual speaker positions to report. AOSP's own contract says
     * angles[] is left untouched when the method returns false, so a caller
     * reading it back sees exactly what it wrote -- no fabricated geometry.
     */
    public boolean getSpeakerAngles(int inputChannelMask, int deviceType, int[] angles) {
        return false;
    }

    /**
     * AOSP: the virtualization mode currently in effect, or
     * VIRTUALIZATION_MODE_OFF when the effect is not enabled or the output is
     * not being virtualized. Nothing is virtualized here, so OFF -- and it
     * agrees with forceVirtualizationMode(), which reports that it could not
     * apply a mode.
     */
    public int getVirtualizationMode() { return VIRTUALIZATION_MODE_OFF; }
    public boolean getVirtualizationMode(int[] modes) { return false; }
    public boolean forceVirtualizationMode(int virtualizationMode) { return false; }
    public void setParameterListener(OnParameterChangeListener listener) {}

    public static class Settings {
        public short strength;
        public Settings() {}
        public Settings(String settings) {}
        @Override public String toString() { return "Virtualizer;strength=" + strength; }
    }
    public Settings getProperties() { Settings s = new Settings(); s.strength = mStrength; return s; }
    public void setProperties(Settings settings) { if (settings != null) mStrength = settings.strength; }

    public interface OnParameterChangeListener {
        void onParameterChange(Virtualizer effect, int status, int param, short value);
    }
}
