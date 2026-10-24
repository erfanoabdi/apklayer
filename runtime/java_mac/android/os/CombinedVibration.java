// android.os.CombinedVibration -- a VibrationEffect (or several) played across
// the vibrators of a multi-actuator device.
//
// A Mac has no vibrator, so VibratorManager.vibrate() is a no-op. The
// combination itself is still a plain VALUE object -- an effect, or a
// vibrator-id-to-effect map, or an ordered list with delays -- so it marshals
// for real. It used to write NOTHING (writeToParcel was `dest.writeValue(this)`
// and CREATOR was `readValue(null)`), which only closed because Parcel detects
// the self-delegation and falls back to its non-AOSP VAL_MACAPK_LIVEREF tag,
// handing the SAME object back rather than decoding one.
//
// The nested effects are written BODY-ONLY (mEffect.writeToParcel, not
// writeValue), exactly as AOSP does -- VibrationEffect's own writeToParcel puts
// its PARCEL_TOKEN first and VibrationEffect.CREATOR dispatches on it, so no
// class name is needed in the stream.
//
// AOSP ref: frameworks/base/core/java/android/os/CombinedVibration.java
// (android14-release).
package android.os;

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

public abstract class CombinedVibration implements Parcelable {
    private static final int PARCEL_TOKEN_MONO = 1;
    private static final int PARCEL_TOKEN_STEREO = 2;
    private static final int PARCEL_TOKEN_SEQUENTIAL = 3;

    /** Prevent subclassing from outside of the framework. */
    CombinedVibration() {}

    /** Play a single effect in parallel on all vibrators. */
    public static CombinedVibration createParallel(VibrationEffect effect) {
        CombinedVibration combined = new Mono(effect);
        combined.validate();
        return combined;
    }

    /** Start building a vibration that plays effects in parallel on one or more
     *  vibrators. */
    public static ParallelCombination startParallel() {
        return new ParallelCombination();
    }

    /** Start building a vibration that plays combinations in sequence.
     *  @hide (@TestApi in AOSP) */
    public static SequentialCombination startSequential() {
        return new SequentialCombination();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Estimated duration in milliseconds. Long.MAX_VALUE for a combination with
     * no defined end, -1 when any part has an unknown (device dependent) length.
     * @hide
     */
    public abstract long getDuration();

    /** Throws IllegalArgumentException if this combination is malformed. @hide */
    public abstract void validate();

    /** Whether this combination targets the given vibrator id. @hide */
    public abstract boolean hasVibrator(int vibratorId);

    /** One effect, played on every vibrator. @hide */
    public static final class Mono extends CombinedVibration {
        private final VibrationEffect mEffect;

        Mono(Parcel in) {
            mEffect = VibrationEffect.CREATOR.createFromParcel(in);
        }

        Mono(VibrationEffect effect) {
            mEffect = effect;
        }

        public VibrationEffect getEffect() {
            return mEffect;
        }

        @Override
        public long getDuration() {
            return mEffect.getDuration();
        }

        @Override
        public void validate() {
            mEffect.validate();
        }

        @Override
        public boolean hasVibrator(int vibratorId) {
            // The same effect goes to every vibrator, so every id matches.
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CombinedVibration.Mono)) return false;
            CombinedVibration.Mono other = (CombinedVibration.Mono) o;
            return mEffect.equals(other.mEffect);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(mEffect);
        }

        @Override
        public String toString() {
            return "Mono{mEffect=" + mEffect + '}';
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_MONO);
            mEffect.writeToParcel(out, flags);
        }

        public static final Creator<Mono> CREATOR = new Creator<Mono>() {
            @Override public Mono createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new Mono(in);
            }
            @Override public Mono[] newArray(int size) { return new Mono[size]; }
        };
    }

    /** A vibrator-id to effect mapping, all played at once. @hide */
    public static final class Stereo extends CombinedVibration {
        private final SparseArray<VibrationEffect> mEffects;

        Stereo(Parcel in) {
            int size = in.readInt();
            mEffects = new SparseArray<VibrationEffect>(size);
            for (int i = 0; i < size; i++) {
                int vibratorId = in.readInt();
                mEffects.put(vibratorId, VibrationEffect.CREATOR.createFromParcel(in));
            }
        }

        Stereo(SparseArray<VibrationEffect> effects) {
            // Copy: the builder stays usable after combine(), so sharing its
            // SparseArray would let a later addVibrator mutate a finished object.
            mEffects = new SparseArray<VibrationEffect>(effects.size());
            for (int i = 0; i < effects.size(); i++) {
                mEffects.put(effects.keyAt(i), effects.valueAt(i));
            }
        }

        public SparseArray<VibrationEffect> getEffects() {
            return mEffects;
        }

        @Override
        public long getDuration() {
            long maxDuration = Long.MIN_VALUE;
            boolean hasUnknownStep = false;
            for (int i = 0; i < mEffects.size(); i++) {
                long duration = mEffects.valueAt(i).getDuration();
                if (duration == Long.MAX_VALUE) {
                    // A repeating step makes the whole combination repeating.
                    return duration;
                }
                maxDuration = Math.max(maxDuration, duration);
                // An unknown step makes the combination unknown -- unless some
                // LATER step turns out to be repeating, which takes precedence,
                // so the loop does not break here.
                hasUnknownStep |= duration < 0;
            }
            if (hasUnknownStep) {
                return -1;
            }
            return maxDuration;
        }

        @Override
        public void validate() {
            if (mEffects.size() <= 0) {
                throw new IllegalArgumentException(
                        "There should be at least one effect set for a combined effect");
            }
            for (int i = 0; i < mEffects.size(); i++) {
                mEffects.valueAt(i).validate();
            }
        }

        @Override
        public boolean hasVibrator(int vibratorId) {
            return mEffects.indexOfKey(vibratorId) >= 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CombinedVibration.Stereo)) return false;
            CombinedVibration.Stereo other = (CombinedVibration.Stereo) o;
            if (mEffects.size() != other.mEffects.size()) {
                return false;
            }
            for (int i = 0; i < mEffects.size(); i++) {
                // A lookup BY KEY, not by index: two Stereos built in a
                // different order are equal, which is what AOSP compares.
                if (!mEffects.valueAt(i).equals(other.mEffects.get(mEffects.keyAt(i)))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return mEffects.contentHashCode();
        }

        @Override
        public String toString() {
            return "Stereo{mEffects=" + mEffects + '}';
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_STEREO);
            out.writeInt(mEffects.size());
            for (int i = 0; i < mEffects.size(); i++) {
                out.writeInt(mEffects.keyAt(i));
                mEffects.valueAt(i).writeToParcel(out, flags);
            }
        }

        public static final Creator<Stereo> CREATOR = new Creator<Stereo>() {
            @Override public Stereo createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new Stereo(in);
            }
            @Override public Stereo[] newArray(int size) { return new Stereo[size]; }
        };
    }

    /** An ordered list of combinations, each preceded by a delay. @hide */
    public static final class Sequential extends CombinedVibration {
        private final List<CombinedVibration> mEffects;
        private final List<Integer> mDelays;

        Sequential(Parcel in) {
            int size = in.readInt();
            mEffects = new ArrayList<CombinedVibration>(size);
            mDelays = new ArrayList<Integer>(size);
            for (int i = 0; i < size; i++) {
                mDelays.add(in.readInt());
                mEffects.add(CombinedVibration.CREATOR.createFromParcel(in));
            }
        }

        Sequential(List<CombinedVibration> effects, List<Integer> delays) {
            mEffects = new ArrayList<CombinedVibration>(effects);
            mDelays = new ArrayList<Integer>(delays);
        }

        public List<CombinedVibration> getEffects() {
            return mEffects;
        }

        /** Delay applied before each entry of {@link #getEffects()}. */
        public List<Integer> getDelays() {
            return mDelays;
        }

        @Override
        public long getDuration() {
            boolean hasUnknownStep = false;
            long durations = 0;
            final int effectCount = mEffects.size();
            for (int i = 0; i < effectCount; i++) {
                CombinedVibration effect = mEffects.get(i);
                long duration = effect.getDuration();
                if (duration == Long.MAX_VALUE) {
                    // A repeating step never ends, so neither does the sequence.
                    return duration;
                }
                durations += duration;
                // An unknown step makes the total unknown -- unless some LATER
                // step is repeating, which takes precedence, so no early break.
                hasUnknownStep |= duration < 0;
            }
            if (hasUnknownStep) {
                return -1;
            }
            long delays = 0;
            for (int i = 0; i < effectCount; i++) {
                delays += mDelays.get(i);
            }
            return durations + delays;
        }

        @Override
        public void validate() {
            if (mEffects.size() <= 0) {
                throw new IllegalArgumentException(
                        "There should be at least one effect set for a combined effect");
            }
            if (mEffects.size() != mDelays.size()) {
                throw new IllegalArgumentException(
                        "Effect and delays should have equal length");
            }
            final int effectCount = mEffects.size();
            for (int i = 0; i < effectCount; i++) {
                if (mDelays.get(i) < 0) {
                    throw new IllegalArgumentException("Delays must all be >= 0"
                            + " (delays=" + mDelays + ")");
                }
            }
            for (int i = 0; i < effectCount; i++) {
                CombinedVibration effect = mEffects.get(i);
                if (effect instanceof Sequential) {
                    throw new IllegalArgumentException(
                            "There should be no nested sequential effects in a combined effect");
                }
                effect.validate();
            }
        }

        @Override
        public boolean hasVibrator(int vibratorId) {
            final int effectCount = mEffects.size();
            for (int i = 0; i < effectCount; i++) {
                if (mEffects.get(i).hasVibrator(vibratorId)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CombinedVibration.Sequential)) return false;
            CombinedVibration.Sequential other = (CombinedVibration.Sequential) o;
            return mDelays.equals(other.mDelays) && mEffects.equals(other.mEffects);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(mEffects, mDelays);
        }

        @Override
        public String toString() {
            return "Sequential{mEffects=" + mEffects + ", mDelays=" + mDelays + '}';
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_SEQUENTIAL);
            out.writeInt(mEffects.size());
            for (int i = 0; i < mEffects.size(); i++) {
                out.writeInt(mDelays.get(i));
                mEffects.get(i).writeToParcel(out, flags);
            }
        }

        public static final Creator<Sequential> CREATOR = new Creator<Sequential>() {
            @Override public Sequential createFromParcel(Parcel in) {
                in.readInt();   // skip the type token
                return new Sequential(in);
            }
            @Override public Sequential[] newArray(int size) { return new Sequential[size]; }
        };
    }

    /** Builder mapping vibratorId -> effect, all played at once. */
    public static final class ParallelCombination {
        private final SparseArray<VibrationEffect> mEffects = new SparseArray<VibrationEffect>();

        ParallelCombination() {}

        /** Add or replace the effect one vibrator should play. */
        public ParallelCombination addVibrator(int vibratorId, VibrationEffect effect) {
            mEffects.put(vibratorId, effect);
            return this;
        }

        /** AOSP throws rather than returning an empty combination: an empty one
         *  would be accepted by every consumer and simply never do anything,
         *  which is the failure mode that is impossible to debug. */
        public CombinedVibration combine() {
            if (mEffects.size() == 0) {
                throw new IllegalStateException(
                        "Combination must have at least one element to combine.");
            }
            CombinedVibration combined = new Stereo(mEffects);
            combined.validate();
            return combined;
        }
    }

    /** Builder for a sequence of combinations. @hide (@TestApi in AOSP) */
    public static final class SequentialCombination {
        private final ArrayList<CombinedVibration> mEffects = new ArrayList<CombinedVibration>();
        private final ArrayList<Integer> mDelays = new ArrayList<Integer>();

        SequentialCombination() {}

        public SequentialCombination addNext(int vibratorId, VibrationEffect effect) {
            return addNext(vibratorId, effect, /* delay= */ 0);
        }

        public SequentialCombination addNext(int vibratorId, VibrationEffect effect, int delay) {
            return addNext(
                    CombinedVibration.startParallel().addVibrator(vibratorId, effect).combine(),
                    delay);
        }

        public SequentialCombination addNext(CombinedVibration effect) {
            return addNext(effect, /* delay= */ 0);
        }

        public SequentialCombination addNext(CombinedVibration effect, int delay) {
            if (effect instanceof Sequential) {
                // AOSP FLATTENS a nested sequence into this one rather than
                // rejecting it, carrying the nested delays over and folding the
                // caller's delay into the first step. Sequential.validate()
                // forbids nesting, so this is the only way the two agree. The
                // set() is unguarded in AOSP and stays unguarded here: a
                // Sequential cannot be empty (combine() rejects that), so the
                // index always exists.
                Sequential sequentialEffect = (Sequential) effect;
                int firstEffectIndex = mDelays.size();
                mEffects.addAll(sequentialEffect.getEffects());
                mDelays.addAll(sequentialEffect.getDelays());
                mDelays.set(firstEffectIndex, delay + mDelays.get(firstEffectIndex));
            } else {
                mEffects.add(effect);
                mDelays.add(delay);
            }
            return this;
        }

        public CombinedVibration combine() {
            if (mEffects.size() == 0) {
                throw new IllegalStateException(
                        "Combination must have at least one element to combine.");
            }
            CombinedVibration combined = new Sequential(mEffects, mDelays);
            combined.validate();
            return combined;
        }
    }

    public static final Creator<CombinedVibration> CREATOR = new Creator<CombinedVibration>() {
        @Override public CombinedVibration createFromParcel(Parcel in) {
            int token = in.readInt();
            if (token == PARCEL_TOKEN_MONO) {
                return new Mono(in);
            } else if (token == PARCEL_TOKEN_STEREO) {
                return new Stereo(in);
            } else if (token == PARCEL_TOKEN_SEQUENTIAL) {
                return new Sequential(in);
            } else {
                throw new IllegalStateException(
                        "Unexpected combined vibration event type token in parcel.");
            }
        }
        @Override public CombinedVibration[] newArray(int size) {
            return new CombinedVibration[size];
        }
    };
}
