package android.os.health;
/** Key constants for a PackageHealthStats HealthStats bucket. Mac.apk populates no health data,
 *  but the keys resolve.
 *
 *  Values = HealthKeys.BASE_PACKAGE (40000) + the per-field offset AOSP assigns
 *  in frameworks/base/core/java/android/os/health/{PackageHealthStats,HealthKeys}.java
 *  -- confirmed via debug/framework_lint.py's constant-value-diff finding
 *  (these previously used an invented 0x10000|offset packing). The second field
 *  is also renamed MEASUREMENT_ -> MEASUREMENTS_ (plural): AOSP types it
 *  @HealthKeys.Constant(type=TYPE_MEASUREMENTS) (a Map<String,Long> bucket, one
 *  per-wakeup-alarm-tag), not TYPE_MEASUREMENT (a single scalar) -- the plural
 *  name is the real field a guest compiled against AOSP references; the old
 *  singular name would NoSuchFieldError at link time. */
public final class PackageHealthStats {
    public static final int STATS_SERVICES = 40001;
    public static final int MEASUREMENTS_WAKEUP_ALARMS_COUNT = 40002;
    private PackageHealthStats() {}
}
