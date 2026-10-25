// android.media.audiofx.EnvironmentalReverb -- parametric reverb. AOSP
// frameworks/base media/java/android/media/audiofx/EnvironmentalReverb.java.
// Inert on Mac.apk: parameters stored + read back, audio unchanged.
package android.media.audiofx;

public class EnvironmentalReverb extends AudioEffect {
    public static final int PARAM_ROOM_LEVEL = 0;
    public static final int PARAM_ROOM_HF_LEVEL = 1;
    public static final int PARAM_DECAY_TIME = 2;
    public static final int PARAM_DECAY_HF_RATIO = 3;
    public static final int PARAM_REFLECTIONS_LEVEL = 4;
    public static final int PARAM_REFLECTIONS_DELAY = 5;
    public static final int PARAM_REVERB_LEVEL = 6;
    public static final int PARAM_REVERB_DELAY = 7;
    public static final int PARAM_DIFFUSION = 8;
    public static final int PARAM_DENSITY = 9;

    private short mRoomLevel, mRoomHFLevel, mReflectionsLevel, mReverbLevel, mDiffusion, mDensity;
    private int mDecayTime, mReflectionsDelay, mReverbDelay;
    private short mDecayHFRatio;

    public EnvironmentalReverb(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, priority, audioSession);
    }
    public void setRoomLevel(short room) { mRoomLevel = room; }
    public short getRoomLevel() { return mRoomLevel; }
    public void setRoomHFLevel(short roomHF) { mRoomHFLevel = roomHF; }
    public short getRoomHFLevel() { return mRoomHFLevel; }
    public void setDecayTime(int decayTime) { mDecayTime = decayTime; }
    public int getDecayTime() { return mDecayTime; }
    public void setDecayHFRatio(short decayHFRatio) { mDecayHFRatio = decayHFRatio; }
    public short getDecayHFRatio() { return mDecayHFRatio; }
    public void setReflectionsLevel(short reflectionsLevel) { mReflectionsLevel = reflectionsLevel; }
    public short getReflectionsLevel() { return mReflectionsLevel; }
    public void setReflectionsDelay(int reflectionsDelay) { mReflectionsDelay = reflectionsDelay; }
    public int getReflectionsDelay() { return mReflectionsDelay; }
    public void setReverbLevel(short reverbLevel) { mReverbLevel = reverbLevel; }
    public short getReverbLevel() { return mReverbLevel; }
    public void setReverbDelay(int reverbDelay) { mReverbDelay = reverbDelay; }
    public int getReverbDelay() { return mReverbDelay; }
    public void setDiffusion(short diffusion) { mDiffusion = diffusion; }
    public short getDiffusion() { return mDiffusion; }
    public void setDensity(short density) { mDensity = density; }
    public short getDensity() { return mDensity; }
    public void setParameterListener(OnParameterChangeListener listener) {}

    public static class Settings {
        public short roomLevel, roomHFLevel, decayHFRatio, reflectionsLevel, reverbLevel, diffusion, density;
        public int decayTime, reflectionsDelay, reverbDelay;
        public Settings() {}
        public Settings(String settings) {}
    }
    public Settings getProperties() { return new Settings(); }
    public void setProperties(Settings settings) {}

    public interface OnParameterChangeListener {
        void onParameterChange(EnvironmentalReverb effect, int status, int param, int value);
    }
}
