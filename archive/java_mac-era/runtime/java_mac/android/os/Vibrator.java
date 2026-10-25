// android.os.Vibrator -- Mac.apk has no haptics hardware, so every vibrate
// path is a no-op and hasVibrator() reports false. The full API surface is
// mirrored so modern apps (which call the VibrationEffect-based overloads and
// query hasAmplitudeControl/areEffectsSupported during init) do not hit
// NoSuchMethodError. AOSP marks Vibrator abstract with a SystemVibrator impl
// behind getSystemService(VIBRATOR_SERVICE); we make it concrete so a plain
// `new Vibrator()` (and the service lookup) both work.
//
// AOSP ref: frameworks/base/core/java/android/os/Vibrator.java
package android.os;

import android.media.AudioAttributes;

public class Vibrator {
    public Vibrator() {}

    // AOSP VIBRATION_EFFECT_SUPPORT_* (areEffectsSupported result codes).
    public static final int VIBRATION_EFFECT_SUPPORT_UNKNOWN = 0;
    public static final int VIBRATION_EFFECT_SUPPORT_YES = 1;
    public static final int VIBRATION_EFFECT_SUPPORT_NO = 2;

    public boolean hasVibrator() { return false; }
    public boolean hasAmplitudeControl() { return false; }
    public int getId() { return -1; }

    // Effect-support queries. No hardware -> UNKNOWN for every effect; false for
    // primitive support. Arrays sized to the query so callers index safely.
    public int[] areEffectsSupported(int... effectIds) {
        int n = (effectIds == null) ? 0 : effectIds.length;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = VIBRATION_EFFECT_SUPPORT_UNKNOWN;
        return out;
    }
    public boolean[] arePrimitivesSupported(int... primitiveIds) {
        int n = (primitiveIds == null) ? 0 : primitiveIds.length;
        return new boolean[n];
    }

    // AOSP Vibrator.areAllEffectsSupported / areAllPrimitivesSupported
    // (core/java/android/os/Vibrator.java lines 744-760 and 801-810):
    // aggregate convenience wrappers over the per-id array forms above. AOSP
    // sources each per-id result from VibratorInfo directly; we have no
    // VibratorInfo, so aggregate over our own areEffectsSupported/
    // arePrimitivesSupported results instead, preserving AOSP's exact
    // precedence (NO wins immediately, else UNKNOWN if any id is unknown,
    // else YES; empty query defaults to YES / true).
    public final int areAllEffectsSupported(int... effectIds) {
        int allSupported = VIBRATION_EFFECT_SUPPORT_YES;
        for (int r : areEffectsSupported(effectIds)) {
            switch (r) {
                case VIBRATION_EFFECT_SUPPORT_NO:
                    return VIBRATION_EFFECT_SUPPORT_NO;
                case VIBRATION_EFFECT_SUPPORT_YES:
                    continue;
                default: // VIBRATION_EFFECT_SUPPORT_UNKNOWN
                    allSupported = VIBRATION_EFFECT_SUPPORT_UNKNOWN;
                    break;
            }
        }
        return allSupported;
    }
    public final boolean areAllPrimitivesSupported(int... primitiveIds) {
        for (boolean b : arePrimitivesSupported(primitiveIds)) {
            if (!b) return false;
        }
        return true;
    }
    public int[] getPrimitiveDurations(int... primitiveIds) {
        int n = (primitiveIds == null) ? 0 : primitiveIds.length;
        return new int[n];
    }

    // Legacy long/pattern vibrate API (deprecated on AOSP, still widely called).
    public void vibrate(long milliseconds) {}
    public void vibrate(long milliseconds, AudioAttributes attributes) {}
    public void vibrate(long[] pattern, int repeat) {}
    public void vibrate(long[] pattern, int repeat, AudioAttributes attributes) {}

    // Modern VibrationEffect API (API 26+).
    public void vibrate(VibrationEffect vibe) {}
    public void vibrate(VibrationEffect vibe, AudioAttributes attributes) {}
    public void vibrate(VibrationEffect vibe, VibrationAttributes attributes) {}

    public void cancel() {}
    public void cancel(int usageFilter) {}

    /** AOSP core/java/android/os/Vibrator.java:300 -- "the resonant frequency
     *  of the vibrator, or Float.NaN if it's unknown, not applicable, or if
     *  this vibrator is a composite of multiple physical devices". No vibrator
     *  here, so "not applicable" -- and NaN, not 0f, is the value AOSP
     *  reserves for that; 0f would read as a real measurement of zero. */
    public float getResonantFrequency() { return Float.NaN; }

    /** AOSP :311 -- same NaN contract for the Q factor. */
    public float getQFactor() { return Float.NaN; }

    /** AOSP :389 -- `getInfo().areEnvelopeEffectsSupported()`. */
    public boolean areEnvelopeEffectsSupported() { return false; }

    /** AOSP :404 -- @NonNull even when unsupported: every getter on the
     *  returned object is documented to "return 0 if envelope effects are not
     *  supported", so the all-zero info IS the documented answer rather than a
     *  filler object. */
    public android.os.vibrator.VibratorEnvelopeEffectInfo getEnvelopeEffectInfo() {
        return new android.os.vibrator.VibratorEnvelopeEffectInfo(0, 0L, 0L);
    }

    /** AOSP :352 -- @Nullable, "null if the vibrator does not have frequency
     *  control" (AOSP returns null whenever the HAL's profile isEmpty()). */
    public android.os.vibrator.VibratorFrequencyProfile getFrequencyProfile() {
        return null;
    }
}
