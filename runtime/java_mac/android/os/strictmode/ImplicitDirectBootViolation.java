// android.os.strictmode.ImplicitDirectBootViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/ImplicitDirectBootViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class ImplicitDirectBootViolation extends Violation {
    public ImplicitDirectBootViolation() {
        super("Implicitly relying on automatic Direct Boot filtering; request explicit"
                + " filtering with PackageManager.MATCH_DIRECT_BOOT flags");
    }
}
