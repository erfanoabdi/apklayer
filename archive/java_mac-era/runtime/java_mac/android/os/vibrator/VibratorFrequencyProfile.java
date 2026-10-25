// android.os.vibrator.VibratorFrequencyProfile (API 36) -- "describes the
// output of a Vibrator for different vibration frequencies ... Vibrators
// without independent frequency control do not have a frequency profile."
//
// AOSP ref: frameworks/base/core/java/android/os/vibrator/
// VibratorFrequencyProfile.java (android16-release). AOSP backs this with
// VibratorInfo.FrequencyProfile, which is @hide and reads the vibrator HAL;
// here the same data (the frequency grid and its output accelerations) is held
// directly, and every accessor computes from it exactly as AOSP's does --
// including the linear interpolation getOutputAccelerationGs() documents.
//
// Mac.apk has no haptics hardware, so Vibrator.getFrequencyProfile() returns
// null, which is AOSP's own documented answer for a vibrator with no frequency
// control. The class still exists (and is constructible) because an app may
// reference the TYPE -- a field, a cast, a method signature -- whether or not
// it ever receives an instance.
package android.os.vibrator;

import android.util.Range;
import android.util.SparseArray;

import java.util.Objects;

public final class VibratorFrequencyProfile {
    private final float[] mFrequenciesHz;          // ascending
    private final float[] mOutputAccelerationsGs;  // parallel to mFrequenciesHz
    private final SparseArray<Float> mFrequenciesOutputAcceleration;

    /** AOSP's constructor is @hide and takes VibratorInfo.FrequencyProfile;
     *  this takes the same two parallel arrays that type carries. */
    public VibratorFrequencyProfile(float[] frequenciesHz, float[] outputAccelerationsGs) {
        Objects.requireNonNull(frequenciesHz);
        Objects.requireNonNull(outputAccelerationsGs);
        if (frequenciesHz.length == 0
                || frequenciesHz.length != outputAccelerationsGs.length) {
            // AOSP: Preconditions.checkArgument(!frequencyProfile.isEmpty(),
            //       "Frequency profile must not be empty");
            throw new IllegalArgumentException("Frequency profile must not be empty");
        }
        mFrequenciesHz = frequenciesHz.clone();
        mOutputAccelerationsGs = outputAccelerationsGs.clone();
        mFrequenciesOutputAcceleration = generateFrequencyToAccelerationMap();
    }

    public SparseArray<Float> getFrequenciesOutputAcceleration() {
        return mFrequenciesOutputAcceleration;
    }

    public float getMaxOutputAccelerationGs() {
        float max = 0f;
        for (float g : mOutputAccelerationsGs) {
            if (g > max) max = g;
        }
        return max;
    }

    /** AOSP: "the frequency range where the vibrator can sustain at least the
     *  given minimum output acceleration, or null if the minimum output
     *  acceleration cannot be achieved." */
    public Range<Float> getFrequencyRange(float minOutputAccelerationGs) {
        int lo = -1, hi = -1;
        for (int i = 0; i < mFrequenciesHz.length; i++) {
            if (mOutputAccelerationsGs[i] >= minOutputAccelerationGs) {
                if (lo < 0) lo = i;
                hi = i;
            }
        }
        if (lo < 0) return null;
        return Range.create(mFrequenciesHz[lo], mFrequenciesHz[hi]);
    }

    /** AOSP: the acceleration at this frequency, "using linear interpolation
     *  over getFrequenciesOutputAcceleration()". Outside the supported range
     *  the vibrator produces nothing, so 0. */
    public float getOutputAccelerationGs(float frequencyHz) {
        int n = mFrequenciesHz.length;
        if (frequencyHz < mFrequenciesHz[0] || frequencyHz > mFrequenciesHz[n - 1]) {
            return 0f;
        }
        for (int i = 0; i < n; i++) {
            if (frequencyHz == mFrequenciesHz[i]) return mOutputAccelerationsGs[i];
            if (frequencyHz < mFrequenciesHz[i]) {
                float f0 = mFrequenciesHz[i - 1], f1 = mFrequenciesHz[i];
                float a0 = mOutputAccelerationsGs[i - 1], a1 = mOutputAccelerationsGs[i];
                float t = (frequencyHz - f0) / (f1 - f0);
                return a0 + t * (a1 - a0);
            }
        }
        return mOutputAccelerationsGs[n - 1];
    }

    public float getMinFrequencyHz() { return mFrequenciesHz[0]; }

    public float getMaxFrequencyHz() { return mFrequenciesHz[mFrequenciesHz.length - 1]; }

    // AOSP generateFrequencyToAccelerationMap(): truncate each frequency to an
    // int key, skipping a repeat of the previous key.
    private SparseArray<Float> generateFrequencyToAccelerationMap() {
        SparseArray<Float> out = new SparseArray<>(mFrequenciesHz.length);
        int lastFrequency = -1;
        for (int i = 0; i < mFrequenciesHz.length; i++) {
            int frequency = (int) mFrequenciesHz[i];
            if (frequency == lastFrequency) continue;
            out.put(frequency, getOutputAccelerationGs(frequency));
            lastFrequency = frequency;
        }
        return out;
    }
}
