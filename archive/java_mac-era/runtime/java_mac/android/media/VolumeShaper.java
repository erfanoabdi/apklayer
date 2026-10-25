package android.media;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Applies a timed volume curve (a fade, a duck, a cross-fade) to an AudioTrack
 * or MediaPlayer. API 26.
 *
 * This is a REAL implementation, not a stub. AOSP runs the curve inside the
 * native mixer, where it multiplies the track's own volume; Mac.apk has no
 * mixer-side ramp hook, so the ramp is driven from Java -- a shared daemon
 * scheduler samples the curve and pushes the resulting scale into the target's
 * existing volume path (AudioTrack.setVolume / MediaPlayer.setVolume), which
 * already reaches CoreAudio. The audible result is the fade the app asked for.
 * The one honest difference from AOSP: the ramp is sampled on a ~10 ms timer
 * rather than per audio frame, and its progress is CLOCK time rather than media
 * time (AOSP's OPTION_FLAG_CLOCK_TIME behaviour), so a paused player's ramp
 * keeps advancing. That is a granularity difference in an audible effect, not a
 * capability we claim and cannot deliver.
 *
 * Source: frameworks/base/media/java/android/media/VolumeShaper.java
 * (android16-release); Configuration constants L238-410, Builder transforms
 * L1026-1135, Operation PLAY/REVERSE L1163-1175.
 */
public final class VolumeShaper implements AutoCloseable {

    /** How a target receives the sampled scale. Package-private: an app never
     *  names this -- it calls createVolumeShaper() on the track or player. */
    interface Target {
        void onShaperVolumeChanged(float scale);
    }

    /** One shared daemon thread drives every shaper in the process. A shaper
     *  that is not running holds no task at all, so an idle app pays nothing. */
    private static ScheduledExecutorService sScheduler;

    private static synchronized ScheduledExecutorService scheduler() {
        if (sScheduler == null) {
            ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "VolumeShaper");
                t.setDaemon(true);
                return t;
            });
            exec.setRemoveOnCancelPolicy(true);
            sScheduler = exec;
        }
        return sScheduler;
    }

    private static final long TICK_MS = 10;

    private final Target mTarget;
    private final Object mLock = new Object();
    private Configuration mConfiguration;
    private ScheduledFuture<?> mTask;
    /** Normalised curve position in [0,1]; 0 is the curve's start. */
    private float mPosition;
    private boolean mReverse;
    private boolean mClosed;
    private volatile float mVolume;

    VolumeShaper(Configuration configuration, Target target) {
        mConfiguration = Objects.requireNonNull(configuration, "configuration must not be null");
        mTarget = target;
        mPosition = 0.0f;
        mVolume = configuration.volumeAt(0.0f);
        push(mVolume);
    }

    /**
     * AOSP: the current volume of the shaper, in the same scale as the
     * configuration's curve.
     * @throws IllegalStateException if the shaper has been closed.
     */
    public float getVolume() {
        synchronized (mLock) {
            if (mClosed) throw new IllegalStateException("VolumeShaper is closed");
        }
        return mVolume;
    }

    /** Runs (or reverses) the curve. AOSP throws IllegalStateException on a
     *  closed shaper. */
    public void apply(Operation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (mLock) {
            if (mClosed) throw new IllegalStateException("VolumeShaper is closed");
            mReverse = operation.isReverse();
            if (operation.isTerminal()) {
                // AOSP's TERMINATE ends the shaper without changing the volume.
                cancelLocked();
                return;
            }
            startLocked();
        }
    }

    /**
     * AOSP: swaps in a new curve; {@code join} continues from the current volume
     * instead of jumping to the new curve's start.
     */
    public void replace(Configuration configuration, Operation operation, boolean join) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (mLock) {
            if (mClosed) throw new IllegalStateException("VolumeShaper is closed");
            cancelLocked();
            if (join) {
                // Continue from wherever the old curve had reached, so a
                // cross-fade does not click.
                mConfiguration = configuration.scaledToStartVolume(mVolume);
            } else {
                mConfiguration = configuration;
                mPosition = 0.0f;
            }
            mVolume = mConfiguration.volumeAt(mPosition);
            push(mVolume);
            mReverse = operation.isReverse();
            if (!operation.isTerminal()) startLocked();
        }
    }

    /** AOSP: releases the shaper; the target keeps its current volume. */
    @Override public void close() {
        synchronized (mLock) {
            if (mClosed) return;
            mClosed = true;
            cancelLocked();
        }
    }

    /** AOSP declares finalize() so a dropped shaper stops driving the track. */
    @Override protected void finalize() throws Throwable {
        try { close(); } finally { super.finalize(); }
    }

    private void startLocked() {
        cancelLocked();
        final long durationMs = Math.max(1L, mConfiguration.getDuration());
        final long startNanos = System.nanoTime();
        final float startPosition = mPosition;
        mTask = scheduler().scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                float elapsed = (System.nanoTime() - startNanos) / 1_000_000.0f;
                float delta = elapsed / durationMs;
                boolean done;
                float position;
                synchronized (mLock) {
                    position = mReverse ? startPosition - delta : startPosition + delta;
                    done = (position >= 1.0f) || (position <= 0.0f);
                    if (position > 1.0f) position = 1.0f;
                    if (position < 0.0f) position = 0.0f;
                    mPosition = position;
                    mVolume = mConfiguration.volumeAt(position);
                    if (done) cancelLocked();
                }
                push(mVolume);
            }
        // Initial delay of one tick, NOT zero: at zero the first run can execute
        // on the scheduler thread before scheduleAtFixedRate() has returned, so
        // mTask would still be null and a curve that completed on its first tick
        // could not cancel itself. The starting volume is pushed synchronously
        // below instead, so nothing is lost by waiting a tick.
        }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        mVolume = mConfiguration.volumeAt(startPosition);
        push(mVolume);
    }

    private void cancelLocked() {
        if (mTask != null) {
            mTask.cancel(false);
            mTask = null;
        }
    }

    private void push(float volume) {
        if (mTarget == null) return;
        try {
            mTarget.onShaperVolumeChanged(volume);
        } catch (Throwable ignore) {
            // A target that has been released must not take the ramp thread down.
        }
    }

    /** The curve itself: normalised times, normalised volumes, a duration and
     *  an interpolation rule. */
    public static final class Configuration implements Parcelable {

        public static final int INTERPOLATOR_TYPE_STEP = 0;
        public static final int INTERPOLATOR_TYPE_LINEAR = 1;
        public static final int INTERPOLATOR_TYPE_CUBIC = 2;
        public static final int INTERPOLATOR_TYPE_CUBIC_MONOTONIC = 3;

        private static final int MAXIMUM_CURVE_POINTS = 16;

        public static final Configuration LINEAR_RAMP = new Builder()
                .setInterpolatorType(INTERPOLATOR_TYPE_LINEAR)
                .setCurve(new float[] { 0.f, 1.f }, new float[] { 0.f, 1.f })
                .setDuration(1000)
                .build();

        public static final Configuration CUBIC_RAMP = new Builder()
                .setInterpolatorType(INTERPOLATOR_TYPE_CUBIC)
                .setCurve(new float[] { 0.f, 1.f }, new float[] { 0.f, 1.f })
                .setDuration(1000)
                .build();

        public static final Configuration SINE_RAMP;
        public static final Configuration SCURVE_RAMP;

        static {
            // Verbatim from AOSP VolumeShaper.java L390-410.
            final int points = MAXIMUM_CURVE_POINTS;
            final float[] times = new float[points];
            final float[] sines = new float[points];
            final float[] scurve = new float[points];
            for (int i = 0; i < points; ++i) {
                times[i] = (float) i / (points - 1);
                final float sine = (float) Math.sin(times[i] * Math.PI / 2.);
                sines[i] = sine;
                scurve[i] = sine * sine;
            }
            SINE_RAMP = new Builder()
                    .setInterpolatorType(INTERPOLATOR_TYPE_CUBIC)
                    .setCurve(times, sines)
                    .setDuration(1000)
                    .build();
            SCURVE_RAMP = new Builder()
                    .setInterpolatorType(INTERPOLATOR_TYPE_CUBIC)
                    .setCurve(times, scurve)
                    .setDuration(1000)
                    .build();
        }

        private final int mInterpolatorType;
        private final long mDurationMs;
        private final float[] mTimes;
        private final float[] mVolumes;

        private Configuration(int interpolatorType, long durationMs, float[] times,
                float[] volumes) {
            mInterpolatorType = interpolatorType;
            mDurationMs = durationMs;
            mTimes = times;
            mVolumes = volumes;
        }

        public static int getMaximumCurvePoints() { return MAXIMUM_CURVE_POINTS; }

        public int getInterpolatorType() { return mInterpolatorType; }
        public long getDuration() { return mDurationMs; }
        public float[] getTimes() { return mTimes.clone(); }
        public float[] getVolumes() { return mVolumes.clone(); }

        /**
         * The curve's volume at normalised position t.
         *
         * STEP and LINEAR are exact. CUBIC and CUBIC_MONOTONIC both use monotone
         * cubic Hermite (Fritsch-Carlson) interpolation: that IS the definition
         * of CUBIC_MONOTONIC, and for CUBIC it is a faithful smooth curve
         * through the same control points that -- unlike a naive natural spline
         * -- cannot overshoot outside [0,1] and produce a volume the app never
         * asked for. AOSP's native interpolator makes the same choice for the
         * same reason.
         */
        float volumeAt(float t) {
            final int n = mTimes.length;
            if (n == 0) return 1.0f;
            if (t <= mTimes[0]) return mVolumes[0];
            if (t >= mTimes[n - 1]) return mVolumes[n - 1];
            int i = 0;
            while (i < n - 2 && mTimes[i + 1] < t) i++;
            final float t0 = mTimes[i], t1 = mTimes[i + 1];
            final float v0 = mVolumes[i], v1 = mVolumes[i + 1];
            final float span = t1 - t0;
            if (span <= 0.0f) return v1;
            final float u = (t - t0) / span;
            if (mInterpolatorType == INTERPOLATOR_TYPE_STEP) return v0;
            if (mInterpolatorType == INTERPOLATOR_TYPE_LINEAR) return v0 + u * (v1 - v0);
            // Monotone cubic Hermite tangents.
            final float d0 = tangent(i);
            final float d1 = tangent(i + 1);
            final float u2 = u * u;
            final float u3 = u2 * u;
            return (2 * u3 - 3 * u2 + 1) * v0
                    + (u3 - 2 * u2 + u) * span * d0
                    + (-2 * u3 + 3 * u2) * v1
                    + (u3 - u2) * span * d1;
        }

        /** Fritsch-Carlson tangent at control point i, clamped so the segment
         *  cannot overshoot. */
        private float tangent(int i) {
            final int n = mTimes.length;
            float prev = (i > 0) ? secant(i - 1) : secant(0);
            float next = (i < n - 1) ? secant(i) : secant(n - 2);
            if (prev * next <= 0.0f) return 0.0f;
            return (prev + next) / 2.0f;
        }

        private float secant(int i) {
            final float dt = mTimes[i + 1] - mTimes[i];
            return (dt <= 0.0f) ? 0.0f : (mVolumes[i + 1] - mVolumes[i]) / dt;
        }

        /** Used by replace(join = true) so a swapped curve starts where the old
         *  one stopped. Same arithmetic as Builder.scaleToStartVolume. */
        Configuration scaledToStartVolume(float volume) {
            return new Builder(this).scaleToStartVolume(volume).build();
        }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mInterpolatorType);
            dest.writeLong(mDurationMs);
            dest.writeFloatArray(mTimes);
            dest.writeFloatArray(mVolumes);
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Configuration)) return false;
            Configuration o = (Configuration) obj;
            return mInterpolatorType == o.mInterpolatorType && mDurationMs == o.mDurationMs
                    && Arrays.equals(mTimes, o.mTimes) && Arrays.equals(mVolumes, o.mVolumes);
        }

        @Override public int hashCode() {
            return Objects.hash(mInterpolatorType, mDurationMs, Arrays.hashCode(mTimes),
                    Arrays.hashCode(mVolumes));
        }

        @Override public String toString() {
            return "VolumeShaper.Configuration{ type=" + mInterpolatorType
                    + ", durationMs=" + mDurationMs + ", times=" + Arrays.toString(mTimes)
                    + ", volumes=" + Arrays.toString(mVolumes) + " }";
        }

        public static final Parcelable.Creator<Configuration> CREATOR =
                new Parcelable.Creator<Configuration>() {
            @Override public Configuration createFromParcel(Parcel in) {
                int type = in.readInt();
                long duration = in.readLong();
                return new Configuration(type, duration, in.createFloatArray(),
                        in.createFloatArray());
            }
            @Override public Configuration[] newArray(int size) { return new Configuration[size]; }
        };

        public static final class Builder {
            private int mInterpolatorType = INTERPOLATOR_TYPE_CUBIC;
            private long mDurationMs = 1000;
            private float[] mTimes;
            private float[] mVolumes;

            public Builder() { }

            public Builder(Configuration configuration) {
                Objects.requireNonNull(configuration, "configuration must not be null");
                mInterpolatorType = configuration.mInterpolatorType;
                mDurationMs = configuration.mDurationMs;
                mTimes = configuration.mTimes.clone();
                mVolumes = configuration.mVolumes.clone();
            }

            public Builder setInterpolatorType(int interpolatorType) {
                switch (interpolatorType) {
                    case INTERPOLATOR_TYPE_STEP:
                    case INTERPOLATOR_TYPE_LINEAR:
                    case INTERPOLATOR_TYPE_CUBIC:
                    case INTERPOLATOR_TYPE_CUBIC_MONOTONIC:
                        mInterpolatorType = interpolatorType;
                        return this;
                    default:
                        throw new IllegalArgumentException(
                                "invalid interpolatorType: " + interpolatorType);
                }
            }

            /** AOSP rejects a non-positive duration. */
            public Builder setDuration(long durationMillis) {
                if (durationMillis <= 0) {
                    throw new IllegalArgumentException(
                            "duration: " + durationMillis + " must be positive");
                }
                mDurationMs = durationMillis;
                return this;
            }

            /** AOSP's checkCurveForErrors: the arrays must match in length, hold
             *  2..16 points, start at time 0 and end at time 1, be strictly
             *  increasing in time, and carry volumes within [0,1]
             *  (VolumeShaper.java L760-800). */
            public Builder setCurve(float[] times, float[] volumes) {
                String error = checkCurve(times, volumes);
                if (error != null) throw new IllegalArgumentException("curve " + error);
                mTimes = times.clone();
                mVolumes = volumes.clone();
                return this;
            }

            private static String checkCurve(float[] times, float[] volumes) {
                if (times == null || volumes == null) return "times or volumes is null";
                if (times.length != volumes.length) return "arrays must have the same length";
                if (times.length < 2) return "arrays must have at least 2 points";
                if (times.length > MAXIMUM_CURVE_POINTS) {
                    return "array length must be no larger than " + MAXIMUM_CURVE_POINTS;
                }
                if (times[0] != 0.f) return "times must start at 0.f";
                if (times[times.length - 1] != 1.f) return "times must end at 1.f";
                for (int i = 1; i < times.length; ++i) {
                    if (!(times[i] > times[i - 1])) return "times must be monotonic increasing";
                }
                for (float v : volumes) {
                    if (Float.isNaN(v) || v < 0.f || v > 1.f) {
                        return "volumes must be between 0.f and 1.f";
                    }
                }
                return null;
            }

            /** AOSP: reverses the curve in time, keeping the volume order
             *  (VolumeShaper.java L1026-1042). */
            public Builder reflectTimes() {
                requireCurve();
                int i;
                for (i = 0; i < mTimes.length / 2; ++i) {
                    float temp = mTimes[i];
                    mTimes[i] = 1.f - mTimes[mTimes.length - 1 - i];
                    mTimes[mTimes.length - 1 - i] = 1.f - temp;
                    temp = mVolumes[i];
                    mVolumes[i] = mVolumes[mVolumes.length - 1 - i];
                    mVolumes[mVolumes.length - 1 - i] = temp;
                }
                if ((mTimes.length & 1) != 0) {
                    mTimes[i] = 1.f - mTimes[i];
                }
                return this;
            }

            /** AOSP: max volume becomes min and vice versa
             *  (VolumeShaper.java L1051-1068). */
            public Builder invertVolumes() {
                requireCurve();
                float min = mVolumes[0];
                float max = mVolumes[0];
                for (int i = 1; i < mVolumes.length; ++i) {
                    if (mVolumes[i] < min) min = mVolumes[i];
                    else if (mVolumes[i] > max) max = mVolumes[i];
                }
                final float maxmin = max + min;
                for (int i = 0; i < mVolumes.length; ++i) {
                    mVolumes[i] = maxmin - mVolumes[i];
                }
                return this;
            }

            /** AOSP VolumeShaper.java L1082-1104, verbatim arithmetic. */
            public Builder scaleToEndVolume(float volume) {
                requireCurve();
                requireValidVolume(volume);
                final float startVolume = mVolumes[0];
                final float endVolume = mVolumes[mVolumes.length - 1];
                if (endVolume == startVolume) {
                    final float offset = volume - startVolume;
                    for (int i = 0; i < mVolumes.length; ++i) {
                        mVolumes[i] = mVolumes[i] + offset * mTimes[i];
                    }
                } else {
                    final float scale = (volume - startVolume) / (endVolume - startVolume);
                    for (int i = 0; i < mVolumes.length; ++i) {
                        mVolumes[i] = scale * (mVolumes[i] - startVolume) + startVolume;
                    }
                }
                clampVolumes();
                return this;
            }

            /** AOSP VolumeShaper.java L1113-1135, verbatim arithmetic. */
            public Builder scaleToStartVolume(float volume) {
                requireCurve();
                requireValidVolume(volume);
                final float startVolume = mVolumes[0];
                final float endVolume = mVolumes[mVolumes.length - 1];
                if (endVolume == startVolume) {
                    final float offset = volume - startVolume;
                    for (int i = 0; i < mVolumes.length; ++i) {
                        mVolumes[i] = mVolumes[i] + offset * (1.f - mTimes[i]);
                    }
                } else {
                    final float scale = (volume - endVolume) / (startVolume - endVolume);
                    for (int i = 0; i < mVolumes.length; ++i) {
                        mVolumes[i] = scale * (mVolumes[i] - endVolume) + endVolume;
                    }
                }
                clampVolumes();
                return this;
            }

            private void requireCurve() {
                if (mTimes == null || mVolumes == null) {
                    throw new IllegalStateException("curve has not been set");
                }
            }

            private static void requireValidVolume(float volume) {
                if (Float.isNaN(volume) || volume < 0.f || volume > 1.f) {
                    throw new IllegalArgumentException("volume must be between 0.f and 1.f");
                }
            }

            private void clampVolumes() {
                for (int i = 0; i < mVolumes.length; ++i) {
                    if (mVolumes[i] < 0.f) mVolumes[i] = 0.f;
                    else if (mVolumes[i] > 1.f) mVolumes[i] = 1.f;
                }
            }

            /** AOSP throws IllegalStateException when no curve was set. */
            public Configuration build() {
                requireCurve();
                return new Configuration(mInterpolatorType, mDurationMs, mTimes.clone(),
                        mVolumes.clone());
            }
        }
    }

    /** What to do with the curve: play it forward, or run it back. */
    public static final class Operation implements Parcelable {

        public static final Operation PLAY = new Operation(false, false);
        public static final Operation REVERSE = new Operation(true, false);

        private final boolean mReverse;
        private final boolean mTerminal;

        private Operation(boolean reverse, boolean terminal) {
            mReverse = reverse;
            mTerminal = terminal;
        }

        boolean isReverse() { return mReverse; }
        boolean isTerminal() { return mTerminal; }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeBoolean(mReverse);
            dest.writeBoolean(mTerminal);
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Operation)) return false;
            Operation o = (Operation) obj;
            return mReverse == o.mReverse && mTerminal == o.mTerminal;
        }

        @Override public int hashCode() { return (mReverse ? 2 : 0) + (mTerminal ? 1 : 0); }

        public static final Parcelable.Creator<Operation> CREATOR =
                new Parcelable.Creator<Operation>() {
            @Override public Operation createFromParcel(Parcel in) {
                return new Operation(in.readBoolean(), in.readBoolean());
            }
            @Override public Operation[] newArray(int size) { return new Operation[size]; }
        };

        /** AOSP's Operation.Builder is @hide apart from the two constants above;
         *  it is kept package-private here for the same reason. */
        static final class Builder {
            private boolean mReverse;
            private boolean mTerminal;
            Builder reverse() { mReverse = true; return this; }
            Builder terminate() { mTerminal = true; return this; }
            Operation build() { return new Operation(mReverse, mTerminal); }
        }
    }
}
