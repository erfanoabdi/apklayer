package android.os.health;
/** Key constants for a ServiceHealthStats HealthStats bucket. Mac.apk populates no health data,
 *  but the keys resolve.
 *
 *  Values = HealthKeys.BASE_SERVICE (50000) + the per-field offset AOSP assigns
 *  in frameworks/base/core/java/android/os/health/{ServiceHealthStats,HealthKeys}.java
 *  -- confirmed via debug/framework_lint.py's constant-value-diff finding
 *  (these previously used an invented 0x10000|offset packing). */
public final class ServiceHealthStats {
    public static final int MEASUREMENT_START_SERVICE_COUNT = 50001;
    public static final int MEASUREMENT_LAUNCH_COUNT = 50002;
    private ServiceHealthStats() {}
}
