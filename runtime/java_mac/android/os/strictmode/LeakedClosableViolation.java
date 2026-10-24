// android.os.strictmode.LeakedClosableViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/LeakedClosableViolation.java
// (android16-release). Both constructors are AOSP's.
package android.os.strictmode;

public final class LeakedClosableViolation extends Violation {
    public LeakedClosableViolation(String message, Throwable allocationSite) {
        super(message);
        initCause(allocationSite);
    }

    public LeakedClosableViolation(String message) {
        super(message);
    }
}
