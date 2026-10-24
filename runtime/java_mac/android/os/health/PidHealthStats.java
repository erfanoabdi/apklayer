package android.os.health;
/** Key constants for a PidHealthStats HealthStats bucket. Mac.apk populates no health data,
 *  but the keys resolve.
 *
 *  Values = HealthKeys.BASE_PID (20000) + the per-field offset AOSP assigns in
 *  frameworks/base/core/java/android/os/health/{PidHealthStats,HealthKeys}.java
 *  -- confirmed via debug/framework_lint.py's constant-value-diff finding
 *  (these previously used an invented 0x10000|offset packing). */
public final class PidHealthStats {
    public static final int MEASUREMENT_WAKE_NESTING_COUNT = 20001;
    public static final int MEASUREMENT_WAKE_SUM_MS = 20002;
    public static final int MEASUREMENT_WAKE_START_MS = 20003;
    private PidHealthStats() {}
}
