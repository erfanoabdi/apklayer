// android.os.VibrationEffect -- describes a haptic pattern.
//
// A Mac has no vibrator, so nothing here ever actuates: Vibrator.hasVibrator()
// answers false and Vibrator.vibrate() is a no-op, which is the honest answer
// for hardware that does not exist. That does NOT make the effect itself a
// stub. A VibrationEffect is a plain VALUE object -- a duration and amplitude,
// or a waveform, or a predefined effect id -- and apps store it in fields, put
// it in Bundles, compare it, and hand it to NotificationChannel. Those uses are
// all real on this platform and none of them need a motor.
//
// WHAT CHANGED AND WHY. This class used to carry the construction parameters
// but marshal NOTHING: writeToParcel was `dest.writeValue(this)` and the
// CREATOR was `readValue(null)`. That pair only closes because Parcel detects
// the self-delegation and falls back to its non-AOSP VAL_MACAPK_LIVEREF tag
// (Parcel.java), handing the very same object back instead of decoding one. It
// is the right answer for the classes on that list that hold a live OS resource
// (a file descriptor, a shared-memory mapping, a bitmap) -- there is no byte
// form of an fd. It is the WRONG answer here, because every field of an effect
// is an int, a long or an array, so a real Parcel round trip is both possible
// and observable: AOSP hands back an EQUAL BUT DISTINCT object, and code that
// relies on that (a Bundle copy that the caller then mutates) diverged.
//
// So this now mirrors AOSP's structure: an abstract base with the four
// concrete effects (OneShot, Waveform, Prebaked, Composed), each with AOSP's
// PARCEL_TOKEN framing, validation, equals/hashCode/toString. The token is
// written FIRST and the base CREATOR dispatches on it, so a nested effect can
// be written body-only (CombinedVibration does exactly that).
//
// AOSP ref: frameworks/base/core/java/android/os/VibrationEffect.java
// (android11-release). The effect ids are the vibrator HAL's, pinned from
// hardware/interfaces/vibrator/aidl/android/hardware/vibrator/Effect.aidl.
package android.os;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class VibrationEffect implements Parcelable {
    private static final int PARCEL_TOKEN_ONE_SHOT = 1;
    private static final int PARCEL_TOKEN_WAVEFORM = 2;
    private static final int PARCEL_TOKEN_EFFECT = 3;
    private static final int PARCEL_TOKEN_COMPOSITION = 4;
    // Mac.apk-local token for Repeating (below). AOSP has no such token: it
    // expresses "repeat this effect forever" as a repeat INDEX inside its
    // segment-based VibrationEffect.Composed, a representation this class does
    // not use (see the file header). The token is only ever read back by the
    // CREATOR in this same file, so a local value is safe here; it is 5 rather
    // than reusing 4 so an old parcel can never be misread as a new one.
    private static final int PARCEL_TOKEN_REPEATING = 5;
    // Same rationale as PARCEL_TOKEN_REPEATING: AOSP encodes an envelope
    // effect as PwleSegment/BasicPwleSegment entries inside its segment-based
    // Composed, a representation this class does not use.
    private static final int PARCEL_TOKEN_ENVELOPE = 6;

    /** The default vibration strength of the device. */
    public static final int DEFAULT_AMPLITUDE = -1;

    /** The maximum amplitude value for a vibration. */
    public static final int MAX_AMPLITUDE = 255;

    // Predefined effect ids (createPredefined / Vibrator.areEffectsSupported).
    // Values are the vibrator HAL's Effect enum, not arbitrary: CLICK = 0,
    // DOUBLE_CLICK = 1, TICK = 2, THUD = 3, POP = 4, HEAVY_CLICK = 5.
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_THUD = 3;
    public static final int EFFECT_POP = 4;
    public static final int EFFECT_HEAVY_CLICK = 5;

    // Effect.RINGTONE_1 .. Effect.RINGTONE_15 and Effect.TEXTURE_TICK. Both are
    // @hide in AOSP, so they are NOT exposed as public constants here -- but
    // Prebaked.validate() accepts them, because AOSP's does (an effect id in
    // that range is a legal prebaked effect, just not one an app names by
    // constant), and rejecting them would throw where Android does not.
    private static final int EFFECT_RINGTONE_MIN = 6;
    private static final int EFFECT_RINGTONE_MAX = 20;
    private static final int EFFECT_TEXTURE_TICK = 21;

    // EffectStrength from the same HAL: LIGHT = 0, MEDIUM = 1, STRONG = 2.
    // AOSP's Prebaked defaults to MEDIUM.
    private static final int EFFECT_STRENGTH_LIGHT = 0;
    private static final int EFFECT_STRENGTH_MEDIUM = 1;
    private static final int EFFECT_STRENGTH_STRONG = 2;

    /** AOSP keeps this public to make the class extensible only inside the
     *  framework; the class is abstract, so an app cannot instantiate it. */
    public VibrationEffect() { }

    /**
     * Create a one shot vibration: vibrate constantly for the given period at
     * the given amplitude, then stop.
     *
     * @param milliseconds duration, must be positive.
     * @param amplitude 1..255, or {@link #DEFAULT_AMPLITUDE}.
     */
    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        VibrationEffect effect = new OneShot(milliseconds, amplitude);
        effect.validate();
        return effect;
    }

    /**
     * Create a waveform vibration from alternating off/on timings, starting
     * with off. AOSP synthesises the amplitude array here rather than in the
     * Waveform constructor, so the resulting effect is indistinguishable from
     * one built with the explicit-amplitude overload -- including through
     * equals() and a Parcel round trip.
     */
    public static VibrationEffect createWaveform(long[] timings, int repeat) {
        int[] amplitudes = new int[timings.length];
        for (int i = 0; i < (timings.length / 2); i++) {
            amplitudes[i * 2 + 1] = VibrationEffect.DEFAULT_AMPLITUDE;
        }
        return createWaveform(timings, amplitudes, repeat);
    }

    /**
     * Create a waveform vibration from explicit timing / amplitude pairs.
     *
     * @param repeat index into timings at which to repeat, or -1 for no repeat.
     */
    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        VibrationEffect effect = new Waveform(timings, amplitudes, repeat);
        effect.validate();
        return effect;
    }

    /** Create one of the platform's predefined effects (EFFECT_CLICK etc). */
    public static VibrationEffect createPredefined(int effectId) {
        return get(effectId, true);
    }

    /**
     * AOSP's @hide/@TestApi accessor that createPredefined delegates to; kept
     * because the fallback flag is part of the constructed value and dropping
     * the overload would make the two paths produce different objects.
     * @hide
     */
    public static VibrationEffect get(int effectId) {
        return get(effectId, true);
    }

    /** @hide */
    public static VibrationEffect get(int effectId, boolean fallback) {
        VibrationEffect effect = new Prebaked(effectId, fallback);
        effect.validate();
        return effect;
    }

    /** Start composing a haptic effect out of primitives. */
    public static Composition startComposition() {
        return new Composition();
    }

    /**
     * The estimated duration in milliseconds, Long.MAX_VALUE for an effect with
     * no defined end (a repeating waveform), or -1 when the length is device
     * dependent (a prebaked or composed effect).
     * @hide
     */
    public abstract long getDuration();

    /**
     * Throw IllegalArgumentException if this effect is malformed. AOSP calls it
     * from each factory AND again in the system server; the app-visible half is
     * the factory call, and getting it wrong is the difference between an app
     * seeing its bad argument immediately and seeing nothing at all.
     * @hide
     */
    public abstract void validate();

    @Override
    public int describeContents() {
        return 0;
    }

    /** A single continuous vibration. @hide */
    public static final class OneShot extends VibrationEffect implements Parcelable {
        private final long mDuration;
        private final int mAmplitude;

        OneShot(Parcel in) {
            mDuration = in.readLong();
            mAmplitude = in.readInt();
        }

        public OneShot(long milliseconds, int amplitude) {
            mDuration = milliseconds;
            mAmplitude = amplitude;
        }

        public long getDuration() {
            return mDuration;
        }

        public int getAmplitude() {
            return mAmplitude;
        }

        @Override
        public void validate() {
            if (mAmplitude < -1 || mAmplitude == 0 || mAmplitude > MAX_AMPLITUDE) {
                throw new IllegalArgumentException(
                        "amplitude must either be DEFAULT_AMPLITUDE, "
                        + "or between 1 and 255 inclusive (amplitude=" + mAmplitude + ")");
            }
            if (mDuration <= 0) {
                throw new IllegalArgumentException(
                        "duration must be positive (duration=" + mDuration + ")");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VibrationEffect.OneShot)) {
                return false;
            }
            VibrationEffect.OneShot other = (VibrationEffect.OneShot) o;
            return other.mDuration == mDuration && other.mAmplitude == mAmplitude;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result += 37 * (int) mDuration;
            result += 37 * mAmplitude;
            return result;
        }

        @Override
        public String toString() {
            return "OneShot{mDuration=" + mDuration + ", mAmplitude=" + mAmplitude + "}";
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_ONE_SHOT);
            out.writeLong(mDuration);
            out.writeInt(mAmplitude);
        }

        public static final Creator<OneShot> CREATOR = new Creator<OneShot>() {
            @Override public OneShot createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new OneShot(in);
            }
            @Override public OneShot[] newArray(int size) { return new OneShot[size]; }
        };
    }

    /** A series of timing / amplitude pairs, optionally repeating. @hide */
    public static final class Waveform extends VibrationEffect implements Parcelable {
        private final long[] mTimings;
        private final int[] mAmplitudes;
        private final int mRepeat;

        Waveform(Parcel in) {
            this(in.createLongArray(), in.createIntArray(), in.readInt());
        }

        public Waveform(long[] timings, int[] amplitudes, int repeat) {
            mTimings = new long[timings.length];
            System.arraycopy(timings, 0, mTimings, 0, timings.length);
            mAmplitudes = new int[amplitudes.length];
            System.arraycopy(amplitudes, 0, mAmplitudes, 0, amplitudes.length);
            mRepeat = repeat;
        }

        public long[] getTimings() {
            return mTimings;
        }

        public int[] getAmplitudes() {
            return mAmplitudes;
        }

        public int getRepeatIndex() {
            return mRepeat;
        }

        @Override
        public long getDuration() {
            if (mRepeat >= 0) {
                return Long.MAX_VALUE;
            }
            long duration = 0;
            for (long d : mTimings) {
                duration += d;
            }
            return duration;
        }

        @Override
        public void validate() {
            if (mTimings.length != mAmplitudes.length) {
                throw new IllegalArgumentException(
                        "timing and amplitude arrays must be of equal length"
                        + " (timings.length=" + mTimings.length
                        + ", amplitudes.length=" + mAmplitudes.length + ")");
            }
            if (!hasNonZeroEntry(mTimings)) {
                throw new IllegalArgumentException("at least one timing must be non-zero"
                        + " (timings=" + Arrays.toString(mTimings) + ")");
            }
            for (long timing : mTimings) {
                if (timing < 0) {
                    throw new IllegalArgumentException("timings must all be >= 0"
                            + " (timings=" + Arrays.toString(mTimings) + ")");
                }
            }
            for (int amplitude : mAmplitudes) {
                if (amplitude < -1 || amplitude > MAX_AMPLITUDE) {
                    throw new IllegalArgumentException(
                            "amplitudes must all be DEFAULT_AMPLITUDE or between 0 and 255"
                            + " (amplitudes=" + Arrays.toString(mAmplitudes) + ")");
                }
            }
            if (mRepeat < -1 || mRepeat >= mTimings.length) {
                throw new IllegalArgumentException(
                        "repeat index must be within the bounds of the timings array"
                        + " (timings.length=" + mTimings.length + ", index=" + mRepeat + ")");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VibrationEffect.Waveform)) {
                return false;
            }
            VibrationEffect.Waveform other = (VibrationEffect.Waveform) o;
            return Arrays.equals(mTimings, other.mTimings)
                && Arrays.equals(mAmplitudes, other.mAmplitudes)
                && mRepeat == other.mRepeat;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result += 37 * Arrays.hashCode(mTimings);
            result += 37 * Arrays.hashCode(mAmplitudes);
            result += 37 * mRepeat;
            return result;
        }

        @Override
        public String toString() {
            return "Waveform{mTimings=" + Arrays.toString(mTimings)
                + ", mAmplitudes=" + Arrays.toString(mAmplitudes)
                + ", mRepeat=" + mRepeat
                + "}";
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_WAVEFORM);
            out.writeLongArray(mTimings);
            out.writeIntArray(mAmplitudes);
            out.writeInt(mRepeat);
        }

        private static boolean hasNonZeroEntry(long[] vals) {
            for (long val : vals) {
                if (val != 0) {
                    return true;
                }
            }
            return false;
        }

        public static final Creator<Waveform> CREATOR = new Creator<Waveform>() {
            @Override public Waveform createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new Waveform(in);
            }
            @Override public Waveform[] newArray(int size) { return new Waveform[size]; }
        };
    }

    /** One of the platform's predefined effects. @hide */
    public static final class Prebaked extends VibrationEffect implements Parcelable {
        private final int mEffectId;
        private final boolean mFallback;
        private int mEffectStrength;

        Prebaked(Parcel in) {
            this(in.readInt(), in.readByte() != 0);
            mEffectStrength = in.readInt();
        }

        public Prebaked(int effectId, boolean fallback) {
            mEffectId = effectId;
            mFallback = fallback;
            mEffectStrength = EFFECT_STRENGTH_MEDIUM;
        }

        public int getId() {
            return mEffectId;
        }

        /** Whether to fall back to a generic pattern when the hardware has no
         *  specific implementation of this effect. */
        public boolean shouldFallback() {
            return mFallback;
        }

        public int getEffectStrength() {
            return mEffectStrength;
        }

        /** @hide */
        public void setEffectStrength(int strength) {
            if (!isValidEffectStrength(strength)) {
                throw new IllegalArgumentException("Invalid effect strength: " + strength);
            }
            mEffectStrength = strength;
        }

        private static boolean isValidEffectStrength(int strength) {
            switch (strength) {
                case EFFECT_STRENGTH_LIGHT:
                case EFFECT_STRENGTH_MEDIUM:
                case EFFECT_STRENGTH_STRONG:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public long getDuration() {
            // AOSP: the length of a prebaked effect is device dependent and not
            // known here.
            return -1;
        }

        @Override
        public void validate() {
            switch (mEffectId) {
                case EFFECT_CLICK:
                case EFFECT_DOUBLE_CLICK:
                case EFFECT_TICK:
                case EFFECT_TEXTURE_TICK:
                case EFFECT_THUD:
                case EFFECT_POP:
                case EFFECT_HEAVY_CLICK:
                    break;
                default:
                    if (mEffectId < EFFECT_RINGTONE_MIN || mEffectId > EFFECT_RINGTONE_MAX) {
                        throw new IllegalArgumentException(
                                "Unknown prebaked effect type (value=" + mEffectId + ")");
                    }
            }
            if (!isValidEffectStrength(mEffectStrength)) {
                throw new IllegalArgumentException(
                        "Unknown prebaked effect strength (value=" + mEffectStrength + ")");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VibrationEffect.Prebaked)) {
                return false;
            }
            VibrationEffect.Prebaked other = (VibrationEffect.Prebaked) o;
            return mEffectId == other.mEffectId
                && mFallback == other.mFallback
                && mEffectStrength == other.mEffectStrength;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result += 37 * mEffectId;
            result += 37 * mEffectStrength;
            return result;
        }

        @Override
        public String toString() {
            return "Prebaked{mEffectId=" + mEffectId
                + ", mEffectStrength=" + mEffectStrength
                + ", mFallback=" + mFallback
                + "}";
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_EFFECT);
            out.writeInt(mEffectId);
            out.writeByte((byte) (mFallback ? 1 : 0));
            out.writeInt(mEffectStrength);
        }

        public static final Creator<Prebaked> CREATOR = new Creator<Prebaked>() {
            @Override public Prebaked createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new Prebaked(in);
            }
            @Override public Prebaked[] newArray(int size) { return new Prebaked[size]; }
        };
    }

    /** The result of {@link Composition#compose()}. @hide */
    public static final class Composed extends VibrationEffect implements Parcelable {
        private final ArrayList<Composition.PrimitiveEffect> mPrimitiveEffects;

        @SuppressWarnings("unchecked")
        Composed(Parcel in) {
            this(in.readArrayList(Composed.class.getClassLoader()));
        }

        public Composed(List<Composition.PrimitiveEffect> effects) {
            mPrimitiveEffects = new ArrayList<Composition.PrimitiveEffect>(
                    Objects.requireNonNull(effects));
        }

        public List<Composition.PrimitiveEffect> getPrimitiveEffects() {
            return mPrimitiveEffects;
        }

        @Override
        public long getDuration() {
            return -1;
        }

        @Override
        public void validate() {
            for (Composition.PrimitiveEffect effect : mPrimitiveEffects) {
                Composition.checkPrimitive(effect.id);
                if (Float.isNaN(effect.scale) || effect.scale < 0.0f || effect.scale > 1.0f) {
                    throw new IllegalArgumentException("scale must be in the range [0.0, 1.0]"
                            + " (scale=" + effect.scale + ")");
                }
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_COMPOSITION);
            out.writeList(mPrimitiveEffects);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Composed composed = (Composed) o;
            return mPrimitiveEffects.equals(composed.mPrimitiveEffects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPrimitiveEffects);
        }

        @Override
        public String toString() {
            return "Composed{mPrimitiveEffects=" + mPrimitiveEffects + '}';
        }

        public static final Creator<Composed> CREATOR = new Creator<Composed>() {
            @Override public Composed createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new Composed(in);
            }
            @Override public Composed[] newArray(int size) { return new Composed[size]; }
        };
    }

    /**
     * A composition of haptic primitives that together make one effect.
     *
     * AOSP ref: frameworks/base/core/java/android/os/VibrationEffect.java
     * (Composition).
     */
    public static final class Composition {
        /** @hide -- the "no haptic effect" primitive, used to build a delay. */
        public static final int PRIMITIVE_NOOP = 0;
        public static final int PRIMITIVE_CLICK = 1;
        /** @hide in AOSP ("not confident enough to expose publicly yet"). */
        public static final int PRIMITIVE_THUD = 2;
        /** @hide in AOSP. */
        public static final int PRIMITIVE_SPIN = 3;
        public static final int PRIMITIVE_QUICK_RISE = 4;
        public static final int PRIMITIVE_SLOW_RISE = 5;
        public static final int PRIMITIVE_QUICK_FALL = 6;
        public static final int PRIMITIVE_TICK = 7;
        /** API 31. Not in the android11 source this class otherwise mirrors;
         *  the value is AOSP's own (android14-release VibrationEffect.java:1027,
         *  {@code public static final int PRIMITIVE_LOW_TICK = 8}). Apps built
         *  against API 31+ reference it, and checkPrimitive's upper bound is
         *  this constant, so the value has to be right or a legal primitive
         *  would be rejected. */
        public static final int PRIMITIVE_LOW_TICK = 8;

        /** AOSP core/java/android/os/VibrationEffect.java:1573 / :1602 (API 36).
         *  DELAY_TYPE_PAUSE measures the delay from the END of the previous
         *  primitive; DELAY_TYPE_RELATIVE_START_OFFSET measures it from that
         *  primitive's START. */
        public static final int DELAY_TYPE_PAUSE = 0;
        public static final int DELAY_TYPE_RELATIVE_START_OFFSET = 1;

        private final ArrayList<PrimitiveEffect> mEffects = new ArrayList<PrimitiveEffect>();

        // Off-time accumulated by addOffDuration() and applied as the delay of
        // the next primitive. See the comment on addOffDuration.
        private int mPendingOffDurationMs = 0;

        Composition() { }

        public Composition addPrimitive(int primitiveId) {
            return addPrimitive(primitiveId, 1.0f, 0);
        }

        public Composition addPrimitive(int primitiveId, float scale) {
            return addPrimitive(primitiveId, scale, 0);
        }

        public Composition addPrimitive(int primitiveId, float scale, int delay) {
            return addPrimitive(primitiveId, scale, delay, DELAY_TYPE_PAUSE);
        }

        /** AOSP :1748 (API 36) -- the same primitive plus an explicit delay
         *  TYPE; the 3-arg form is documented as this one with
         *  {@link #DELAY_TYPE_PAUSE}. The type is carried on the primitive
         *  rather than folded away, because PAUSE and RELATIVE_START_OFFSET
         *  are only equivalent when the preceding primitive has zero duration,
         *  and collapsing them would silently rewrite the caller's timing. */
        public Composition addPrimitive(int primitiveId, float scale, int delay, int delayType) {
            if (delay < 0) {
                throw new IllegalArgumentException("delay must be non-negative (delay="
                        + delay + ")");
            }
            if (delayType != DELAY_TYPE_PAUSE && delayType != DELAY_TYPE_RELATIVE_START_OFFSET) {
                throw new IllegalArgumentException("Unsupported delay type " + delayType);
            }
            final long total = (long) delay + mPendingOffDurationMs;
            mEffects.add(new PrimitiveEffect(checkPrimitive(primitiveId), scale,
                    (int) Math.min(total, (long) Integer.MAX_VALUE), delayType));
            mPendingOffDurationMs = 0;
            return this;
        }

        /**
         * API 31. AOSP models this as a zero-amplitude segment in the newer
         * segment-based effect representation; this class mirrors the android11
         * primitive representation, which has no segment to hold it. The
         * equivalent there is a gap BEFORE the next primitive, which is exactly
         * what a primitive's delay is -- so the duration is banked and folded
         * into the next addPrimitive's delay rather than dropped on the floor.
         *
         * Dropping it (what this method used to do) would have been the worse
         * failure: the composition would still compose, still round trip, and
         * still compare equal to one the app never asked for.
         */
        public Composition addOffDuration(java.time.Duration duration) {
            long ms = Objects.requireNonNull(duration, "duration must not be null").toMillis();
            if (ms < 0) {
                throw new IllegalArgumentException("Off duration must be non-negative (duration="
                        + ms + "ms)");
            }
            // Saturate rather than wrap: AOSP takes (int) duration.toMillis(),
            // and a silently negative delay would fail validation far from here.
            final long total = (long) mPendingOffDurationMs + ms;
            mPendingOffDurationMs = (int) Math.min(total, (long) Integer.MAX_VALUE);
            return this;
        }

        /**
         * Compose the added primitives into one effect. The Composition stays
         * usable afterwards, as AOSP documents.
         */
        public VibrationEffect compose() {
            if (mEffects.isEmpty()) {
                throw new IllegalStateException(
                        "Composition must have at least one element to compose.");
            }
            VibrationEffect effect = new VibrationEffect.Composed(mEffects);
            effect.validate();
            return effect;
        }

        /** @hide */
        static int checkPrimitive(int primitiveId) {
            if (primitiveId < PRIMITIVE_NOOP || primitiveId > PRIMITIVE_LOW_TICK) {
                throw new IllegalArgumentException("primitiveId is out of range of ["
                        + PRIMITIVE_NOOP + ", " + PRIMITIVE_LOW_TICK + "]: " + primitiveId);
            }
            return primitiveId;
        }

        /** @hide */
        public static String primitiveToString(int id) {
            switch (id) {
                case PRIMITIVE_NOOP:       return "PRIMITIVE_NOOP";
                case PRIMITIVE_CLICK:      return "PRIMITIVE_CLICK";
                case PRIMITIVE_THUD:       return "PRIMITIVE_THUD";
                case PRIMITIVE_SPIN:       return "PRIMITIVE_SPIN";
                case PRIMITIVE_QUICK_RISE: return "PRIMITIVE_QUICK_RISE";
                case PRIMITIVE_SLOW_RISE:  return "PRIMITIVE_SLOW_RISE";
                case PRIMITIVE_QUICK_FALL: return "PRIMITIVE_QUICK_FALL";
                case PRIMITIVE_TICK:       return "PRIMITIVE_TICK";
                case PRIMITIVE_LOW_TICK:   return "PRIMITIVE_LOW_TICK";
                default:                   return Integer.toString(id);
            }
        }

        /**
         * One primitive in a composition. Parcelable because Composed marshals
         * the list with writeList/readArrayList, exactly as AOSP does.
         * @hide
         */
        public static final class PrimitiveEffect implements Parcelable {
            public final int id;
            public final float scale;
            public final int delay;
            /** Composition.DELAY_TYPE_PAUSE (API 36's default and the only
             *  meaning the 1/2/3-arg addPrimitive forms ever had) or
             *  DELAY_TYPE_RELATIVE_START_OFFSET. */
            public final int delayType;

            public PrimitiveEffect(int id, float scale, int delay) {
                this(id, scale, delay, Composition.DELAY_TYPE_PAUSE);
            }

            public PrimitiveEffect(int id, float scale, int delay, int delayType) {
                this.id = id;
                this.scale = scale;
                this.delay = delay;
                this.delayType = delayType;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeInt(id);
                dest.writeFloat(scale);
                dest.writeInt(delay);
                dest.writeInt(delayType);
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public String toString() {
                return "PrimitiveEffect{"
                        + "id=" + primitiveToString(id)
                        + ", scale=" + scale
                        + ", delay=" + delay
                        + ", delayType=" + delayType
                        + '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                PrimitiveEffect that = (PrimitiveEffect) o;
                return id == that.id
                        && Float.compare(that.scale, scale) == 0
                        && delay == that.delay
                        && delayType == that.delayType;
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, scale, delay, delayType);
            }

            public static final Creator<PrimitiveEffect> CREATOR =
                    new Creator<PrimitiveEffect>() {
                        @Override public PrimitiveEffect createFromParcel(Parcel in) {
                            return new PrimitiveEffect(in.readInt(), in.readFloat(),
                                                       in.readInt(), in.readInt());
                        }
                        @Override public PrimitiveEffect[] newArray(int size) {
                            return new PrimitiveEffect[size];
                        }
                    };
        }
    }

    /**
     * The effect produced by {@link #createRepeatingEffect(VibrationEffect)} and
     * {@link #createRepeatingEffect(VibrationEffect, VibrationEffect)} (API 36):
     * an optional finite preamble followed by a body repeated indefinitely.
     *
     * AOSP builds the same thing out of
     * `startComposition().addEffect(preamble).repeatEffectIndefinitely(body).compose()`
     * over its segment-based representation. This class mirrors the android11
     * PRIMITIVE representation instead (see the file header), which has no
     * repeat index to set, so the pair is held directly. The observable
     * contract is the same one AOSP documents: the result reports an infinite
     * duration, and constructing one from an already-repeating input throws
     * IllegalArgumentException.
     *
     * Not public: AOSP exposes no such type, only the two factories.
     */
    static final class Repeating extends VibrationEffect implements Parcelable {
        private final VibrationEffect mPreamble;    // may be null
        private final VibrationEffect mRepeating;

        Repeating(VibrationEffect preamble, VibrationEffect repeating) {
            mPreamble = preamble;
            mRepeating = Objects.requireNonNull(repeating);
        }

        Repeating(Parcel in) {
            mPreamble = in.readInt() != 0 ? VibrationEffect.CREATOR.createFromParcel(in) : null;
            mRepeating = VibrationEffect.CREATOR.createFromParcel(in);
        }

        VibrationEffect getPreamble() { return mPreamble; }
        VibrationEffect getRepeatingEffect() { return mRepeating; }

        /** AOSP marks an indefinitely-repeating effect with Long.MAX_VALUE --
         *  createRepeatingEffect's own precondition is
         *  `preamble.getDuration() < Long.MAX_VALUE`. */
        @Override public long getDuration() { return Long.MAX_VALUE; }

        @Override public void validate() {
            if (mPreamble != null) mPreamble.validate();
            mRepeating.validate();
        }

        @Override public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_REPEATING);
            out.writeInt(mPreamble != null ? 1 : 0);
            if (mPreamble != null) mPreamble.writeToParcel(out, flags);
            mRepeating.writeToParcel(out, flags);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Repeating r = (Repeating) o;
            return Objects.equals(mPreamble, r.mPreamble) && mRepeating.equals(r.mRepeating);
        }

        @Override public int hashCode() { return Objects.hash(mPreamble, mRepeating); }

        @Override public String toString() {
            return "Repeating{preamble=" + mPreamble + ", repeating=" + mRepeating + '}';
        }
    }

    /**
     * The effect built by {@link WaveformEnvelopeBuilder} and
     * {@link BasicEnvelopeBuilder} (API 36): a piecewise-linear envelope, i.e.
     * a list of segments each interpolating between a (start, end) pair of two
     * control values over a duration.
     *
     * AOSP represents these as PwleSegment (amplitude + frequencyHz) and
     * BasicPwleSegment (intensity + sharpness) inside its segment-based
     * Composed; both are @hide. This class mirrors the android11 primitive
     * representation (see the file header), so the segments are held here and
     * `basic` records which of the two vocabularies they are in -- the two
     * are NOT interchangeable (frequency is in Hz and unbounded above,
     * sharpness is a normalised 0..1), so collapsing them would corrupt the
     * effect an app built.
     *
     * Not public: AOSP exposes only the two builders.
     */
    static final class Envelope extends VibrationEffect implements Parcelable {
        static final class Segment {
            final float startA, endA;   // amplitude   | intensity
            final float startB, endB;   // frequencyHz | sharpness
            final long durationMs;
            Segment(float startA, float endA, float startB, float endB, long durationMs) {
                this.startA = startA; this.endA = endA;
                this.startB = startB; this.endB = endB;
                this.durationMs = durationMs;
            }
            @Override public boolean equals(Object o) {
                if (!(o instanceof Segment)) return false;
                Segment s = (Segment) o;
                return Float.compare(startA, s.startA) == 0 && Float.compare(endA, s.endA) == 0
                    && Float.compare(startB, s.startB) == 0 && Float.compare(endB, s.endB) == 0
                    && durationMs == s.durationMs;
            }
            @Override public int hashCode() {
                return Objects.hash(startA, endA, startB, endB, durationMs);
            }
            @Override public String toString() {
                return "{" + startA + "->" + endA + ", " + startB + "->" + endB
                        + ", " + durationMs + "ms}";
            }
        }

        private final ArrayList<Segment> mSegments;
        private final boolean mBasic;

        Envelope(List<Segment> segments, boolean basic) {
            mSegments = new ArrayList<Segment>(Objects.requireNonNull(segments));
            mBasic = basic;
        }

        Envelope(Parcel in) {
            mBasic = in.readInt() != 0;
            int n = in.readInt();
            mSegments = new ArrayList<Segment>(Math.max(n, 0));
            for (int i = 0; i < n; i++) {
                mSegments.add(new Segment(in.readFloat(), in.readFloat(),
                                          in.readFloat(), in.readFloat(), in.readLong()));
            }
        }

        boolean isBasic() { return mBasic; }
        List<Segment> getSegments() { return mSegments; }

        @Override public long getDuration() {
            long total = 0;
            for (Segment s : mSegments) total += s.durationMs;
            return total;
        }

        @Override public void validate() {
            if (mSegments.isEmpty()) {
                throw new IllegalArgumentException("Envelope must have at least one segment");
            }
            for (Segment s : mSegments) {
                if (s.durationMs <= 0) {
                    throw new IllegalArgumentException(
                            "Envelope segment duration must be positive (was " + s.durationMs + ")");
                }
                checkUnit(s.startA); checkUnit(s.endA);
                // The second channel is normalised (0..1) only in the BASIC
                // vocabulary; a waveform envelope's frequency is in Hz and has
                // no upper bound, so it is only checked for being non-negative
                // and finite.
                if (mBasic) { checkUnit(s.startB); checkUnit(s.endB); }
                else        { checkNonNegative(s.startB); checkNonNegative(s.endB); }
            }
        }
        private static void checkUnit(float v) {
            if (Float.isNaN(v) || v < 0.0f || v > 1.0f) {
                throw new IllegalArgumentException("value must be in the range [0.0, 1.0]"
                        + " (was " + v + ")");
            }
        }
        private static void checkNonNegative(float v) {
            if (Float.isNaN(v) || v < 0.0f) {
                throw new IllegalArgumentException("frequency must be non-negative (was "
                        + v + ")");
            }
        }

        @Override public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_ENVELOPE);
            out.writeInt(mBasic ? 1 : 0);
            out.writeInt(mSegments.size());
            for (Segment s : mSegments) {
                out.writeFloat(s.startA); out.writeFloat(s.endA);
                out.writeFloat(s.startB); out.writeFloat(s.endB);
                out.writeLong(s.durationMs);
            }
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Envelope e = (Envelope) o;
            return mBasic == e.mBasic && mSegments.equals(e.mSegments);
        }
        @Override public int hashCode() { return Objects.hash(mBasic, mSegments); }
        @Override public String toString() {
            return (mBasic ? "BasicEnvelope" : "WaveformEnvelope") + mSegments;
        }
    }

    /** AOSP :WaveformEnvelopeBuilder (API 36) -- control points in the
     *  (amplitude, frequencyHz) vocabulary. Each addControlPoint appends a
     *  segment that interpolates from the PREVIOUS point to this one, which is
     *  why the builder carries mLastAmplitude / mLastFrequencyHz. Ported
     *  verbatim, including setInitialFrequencyHz's retroactive rewrite of the
     *  first segment's START frequency when it is called after the first
     *  control point. */
    public static final class WaveformEnvelopeBuilder {
        private final ArrayList<Envelope.Segment> mSegments = new ArrayList<Envelope.Segment>();
        private float mLastAmplitude = 0f;
        private float mLastFrequencyHz = Float.NaN;

        public WaveformEnvelopeBuilder() {}

        public WaveformEnvelopeBuilder setInitialFrequencyHz(float initialFrequencyHz) {
            if (mSegments.isEmpty()) {
                mLastFrequencyHz = initialFrequencyHz;
            } else {
                Envelope.Segment f = mSegments.get(0);
                mSegments.set(0, new Envelope.Segment(f.startA, f.endA,
                        initialFrequencyHz, f.endB, f.durationMs));
            }
            return this;
        }

        public WaveformEnvelopeBuilder addControlPoint(float amplitude, float frequencyHz,
                                                       long durationMillis) {
            if (Float.isNaN(mLastFrequencyHz)) {
                mLastFrequencyHz = frequencyHz;
            }
            mSegments.add(new Envelope.Segment(mLastAmplitude, amplitude,
                    mLastFrequencyHz, frequencyHz, durationMillis));
            mLastAmplitude = amplitude;
            mLastFrequencyHz = frequencyHz;
            return this;
        }

        public VibrationEffect build() {
            if (mSegments.isEmpty()) {
                throw new IllegalStateException(
                        "WaveformEnvelopeBuilder must have at least one control point to build.");
            }
            VibrationEffect effect = new Envelope(mSegments, /* basic= */ false);
            effect.validate();
            return effect;
        }
    }

    /** AOSP :BasicEnvelopeBuilder (API 36) -- the same shape in the
     *  (intensity, sharpness) vocabulary, plus AOSP's extra build() rule that
     *  the LAST control point must have zero intensity. */
    public static final class BasicEnvelopeBuilder {
        private final ArrayList<Envelope.Segment> mSegments = new ArrayList<Envelope.Segment>();
        private float mLastIntensity = 0f;
        private float mLastSharpness = Float.NaN;

        public BasicEnvelopeBuilder() {}

        public BasicEnvelopeBuilder setInitialSharpness(float initialSharpness) {
            if (mSegments.isEmpty()) {
                mLastSharpness = initialSharpness;
            } else {
                Envelope.Segment f = mSegments.get(0);
                mSegments.set(0, new Envelope.Segment(f.startA, f.endA,
                        initialSharpness, f.endB, f.durationMs));
            }
            return this;
        }

        public BasicEnvelopeBuilder addControlPoint(float intensity, float sharpness,
                                                    long durationMillis) {
            if (Float.isNaN(mLastSharpness)) {
                mLastSharpness = sharpness;
            }
            mSegments.add(new Envelope.Segment(mLastIntensity, intensity,
                    mLastSharpness, sharpness, durationMillis));
            mLastIntensity = intensity;
            mLastSharpness = sharpness;
            return this;
        }

        public VibrationEffect build() {
            if (mSegments.isEmpty()) {
                throw new IllegalStateException(
                        "BasicEnvelopeBuilder must have at least one control point to build.");
            }
            if (mSegments.get(mSegments.size() - 1).endA != 0) {
                throw new IllegalStateException(
                        "Basic envelope effects must end at a zero intensity control point.");
            }
            VibrationEffect effect = new Envelope(mSegments, /* basic= */ true);
            effect.validate();
            return effect;
        }
    }

    /** AOSP core/java/android/os/VibrationEffect.java:1421 -- "Creates a new
     *  VibrationEffect that repeats the given effect indefinitely. The input
     *  vibration must not be a repeating vibration. If it is, an
     *  IllegalArgumentException will be thrown." */
    public static VibrationEffect createRepeatingEffect(VibrationEffect effect) {
        Objects.requireNonNull(effect);
        checkNotAlreadyRepeating(effect, "effect");
        VibrationEffect out = new Repeating(null, effect);
        out.validate();
        return out;
    }

    /** AOSP :1442 -- preamble once, then repeatingEffect forever. AOSP's own
     *  precondition message is "Can't repeat an indefinitely repeating
     *  effect."; it checks the preamble explicitly and the body through
     *  repeatEffectIndefinitely(). Both are checked here. */
    public static VibrationEffect createRepeatingEffect(VibrationEffect preamble,
                                                        VibrationEffect repeatingEffect) {
        Objects.requireNonNull(preamble);
        Objects.requireNonNull(repeatingEffect);
        checkNotAlreadyRepeating(preamble, "preamble");
        checkNotAlreadyRepeating(repeatingEffect, "repeatingEffect");
        VibrationEffect out = new Repeating(preamble, repeatingEffect);
        out.validate();
        return out;
    }

    private static void checkNotAlreadyRepeating(VibrationEffect e, String what) {
        // Two ways an effect can already repeat in this representation: the
        // Repeating wrapper above, and a Waveform with a repeat index >= 0
        // (createWaveform's own "repeat" parameter), which is how AOSP's
        // getDuration() reports Long.MAX_VALUE.
        if (e instanceof Repeating
                || (e instanceof Waveform && ((Waveform) e).getRepeatIndex() >= 0)
                || e.getDuration() == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Can't repeat an indefinitely repeating effect (" + what + ")");
        }
    }

    public static final Creator<VibrationEffect> CREATOR = new Creator<VibrationEffect>() {
        @Override public VibrationEffect createFromParcel(Parcel in) {
            int token = in.readInt();
            if (token == PARCEL_TOKEN_ONE_SHOT) {
                return new OneShot(in);
            } else if (token == PARCEL_TOKEN_WAVEFORM) {
                return new Waveform(in);
            } else if (token == PARCEL_TOKEN_EFFECT) {
                return new Prebaked(in);
            } else if (token == PARCEL_TOKEN_COMPOSITION) {
                return new Composed(in);
            } else if (token == PARCEL_TOKEN_REPEATING) {
                return new Repeating(in);
            } else if (token == PARCEL_TOKEN_ENVELOPE) {
                return new Envelope(in);
            } else {
                throw new IllegalStateException(
                        "Unexpected vibration event type token in parcel.");
            }
        }
        @Override public VibrationEffect[] newArray(int size) {
            return new VibrationEffect[size];
        }
    };
}
