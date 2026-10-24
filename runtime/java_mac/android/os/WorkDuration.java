// android.os.WorkDuration (API 35) -- the CPU/GPU breakdown a game reports
// back to ADPF through PerformanceHintManager.Session.reportActualWorkDuration.
//
// AOSP ref: frameworks/base/core/java/android/os/WorkDuration.java
// (android16-release). Ported verbatim, INCLUDING every setter's
// IllegalArgumentException: those are the only observable behaviour the class
// has, and a caller that passes a bad value must find out here rather than
// have it silently accepted. Note the asymmetry AOSP has -- total duration and
// the start timestamp must be > 0, while the CPU and GPU durations may be 0.
package android.os;

import java.util.Objects;

public final class WorkDuration {
    long mActualTotalDurationNanos = 0;
    long mWorkPeriodStartTimestampNanos = 0;
    long mActualCpuDurationNanos = 0;
    long mActualGpuDurationNanos = 0;

    public WorkDuration() {}

    /** AOSP marks the 4-arg constructor @hide but public. */
    public WorkDuration(long workPeriodStartTimestampNanos,
                        long actualTotalDurationNanos,
                        long actualCpuDurationNanos,
                        long actualGpuDurationNanos) {
        mActualTotalDurationNanos = actualTotalDurationNanos;
        mWorkPeriodStartTimestampNanos = workPeriodStartTimestampNanos;
        mActualCpuDurationNanos = actualCpuDurationNanos;
        mActualGpuDurationNanos = actualGpuDurationNanos;
    }

    public void setActualTotalDurationNanos(long actualTotalDurationNanos) {
        if (actualTotalDurationNanos <= 0) {
            throw new IllegalArgumentException(
                "the actual total duration should be greater than zero.");
        }
        mActualTotalDurationNanos = actualTotalDurationNanos;
    }

    public void setWorkPeriodStartTimestampNanos(long workPeriodStartTimestampNanos) {
        if (workPeriodStartTimestampNanos <= 0) {
            throw new IllegalArgumentException(
                "the work period start timestamp should be greater than zero.");
        }
        mWorkPeriodStartTimestampNanos = workPeriodStartTimestampNanos;
    }

    public void setActualCpuDurationNanos(long actualCpuDurationNanos) {
        if (actualCpuDurationNanos < 0) {
            throw new IllegalArgumentException(
                "the actual CPU duration should be greater than or equal to zero.");
        }
        mActualCpuDurationNanos = actualCpuDurationNanos;
    }

    public void setActualGpuDurationNanos(long actualGpuDurationNanos) {
        if (actualGpuDurationNanos < 0) {
            throw new IllegalArgumentException(
                "the actual GPU duration should be greater than or equal to zero.");
        }
        mActualGpuDurationNanos = actualGpuDurationNanos;
    }

    public long getActualTotalDurationNanos() { return mActualTotalDurationNanos; }
    public long getWorkPeriodStartTimestampNanos() { return mWorkPeriodStartTimestampNanos; }
    public long getActualCpuDurationNanos() { return mActualCpuDurationNanos; }
    public long getActualGpuDurationNanos() { return mActualGpuDurationNanos; }

    @Override public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof WorkDuration)) return false;
        WorkDuration workDuration = (WorkDuration) obj;
        return workDuration.mActualTotalDurationNanos == this.mActualTotalDurationNanos
            && workDuration.mWorkPeriodStartTimestampNanos == this.mWorkPeriodStartTimestampNanos
            && workDuration.mActualCpuDurationNanos == this.mActualCpuDurationNanos
            && workDuration.mActualGpuDurationNanos == this.mActualGpuDurationNanos;
    }

    @Override public int hashCode() {
        return Objects.hash(mWorkPeriodStartTimestampNanos, mActualTotalDurationNanos,
                            mActualCpuDurationNanos, mActualGpuDurationNanos);
    }
}
