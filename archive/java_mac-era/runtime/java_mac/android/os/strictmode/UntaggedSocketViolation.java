// android.os.strictmode.UntaggedSocketViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/UntaggedSocketViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class UntaggedSocketViolation extends Violation {
    public UntaggedSocketViolation() {
        super("Untagged socket detected; use TrafficStats.setTrafficStatsTag() to "
                + "track all network usage");
    }
}
