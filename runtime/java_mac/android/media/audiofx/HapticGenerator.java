package android.media.audiofx;

/**
 * Derives a vibration waveform from an audio session and plays it on the
 * device's vibrator (API 31).
 *
 * A Mac has no vibrator, and this tree's audiofx layer has no effect engine to
 * insert the generator into (see AudioEffect's class comment). AOSP's own
 * contract for that situation is isAvailable() == false, and it is the one
 * apps check before calling create() -- so answering honestly here means an app
 * simply does not offer haptic ringtones, which is correct for this device.
 *
 * create() still returns a usable object rather than throwing, because AOSP's
 * create() is @NonNull and an app that ignored isAvailable() would otherwise
 * die on a NullPointerException instead of getting an effect that reports
 * itself disabled. setEnabled(true) reports failure, which is what keeps
 * Ringtone.setHapticGeneratorEnabled() and this class from contradicting each
 * other.
 *
 * Source: frameworks/base/media/java/android/media/audiofx/HapticGenerator.java
 */
public class HapticGenerator extends AudioEffect implements AutoCloseable {

    private HapticGenerator(int audioSession) {
        super(EFFECT_TYPE_HAPTIC_GENERATOR, EFFECT_TYPE_NULL, 0, audioSession);
    }

    /** AOSP: whether the device supports haptic playback of generated haptics. */
    public static boolean isAvailable() { return false; }

    /** @NonNull in AOSP. */
    public static HapticGenerator create(int audioSession) {
        return new HapticGenerator(audioSession);
    }

    /**
     * AOSP returns AudioEffect.SUCCESS or an error code. There is no generator
     * to enable (see the class comment), so enabling reports ERROR_NO_INIT --
     * AOSP's own code for "the effect engine is not there" -- while DISABLING
     * trivially succeeds, since the effect is already off.
     */
    @Override public int setEnabled(boolean enabled) {
        if (!enabled) return super.setEnabled(false);
        return ERROR_NO_INIT;
    }

    @Override public void close() { release(); }
}
