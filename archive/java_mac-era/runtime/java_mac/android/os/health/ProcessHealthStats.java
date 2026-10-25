package android.os.health;
/** Key constants for a ProcessHealthStats HealthStats bucket. Mac.apk populates no health data,
 *  but the keys resolve.
 *
 *  Values = HealthKeys.BASE_PROCESS (30000) + the per-field offset AOSP assigns
 *  in frameworks/base/core/java/android/os/health/{ProcessHealthStats,HealthKeys}.java
 *  -- confirmed via debug/framework_lint.py's constant-value-diff finding
 *  (these previously used an invented 0x10000|offset packing). */
public final class ProcessHealthStats {
    public static final int MEASUREMENT_USER_TIME_MS = 30001;
    public static final int MEASUREMENT_SYSTEM_TIME_MS = 30002;
    public static final int MEASUREMENT_STARTS_COUNT = 30003;
    public static final int MEASUREMENT_CRASHES_COUNT = 30004;
    public static final int MEASUREMENT_ANR_COUNT = 30005;
    // AOSP frameworks/base/core/java/android/os/health/ProcessHealthStats.java
    // (android16-release): HealthKeys.BASE_PROCESS (30000) + 6.
    public static final int MEASUREMENT_FOREGROUND_MS = 30006;
    private ProcessHealthStats() {}
}
