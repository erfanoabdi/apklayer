// android.os.strictmode.InstanceCountViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/InstanceCountViolation.java
// (android16-release). NOT final upstream (the only non-final Violation
// subclass), and it substitutes a synthetic one-frame stack because the
// violation is detected by a periodic heap scan with no meaningful call site.
package android.os.strictmode;

public class InstanceCountViolation extends Violation {
    private final long mInstances;

    private static final StackTraceElement[] FAKE_STACK = {
        new StackTraceElement(
                "android.os.StrictMode", "setClassInstanceLimit", "StrictMode.java", 1)
    };

    public InstanceCountViolation(Class klass, long instances, int limit) {
        super(klass.toString() + "; instances=" + instances + "; limit=" + limit);
        setStackTrace(FAKE_STACK);
        mInstances = instances;
    }

    public long getNumberOfInstances() {
        return mInstances;
    }
}
