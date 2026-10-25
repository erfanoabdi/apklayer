// android.os.strictmode.NonSdkApiUsedViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/NonSdkApiUsedViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class NonSdkApiUsedViolation extends Violation {
    public NonSdkApiUsedViolation(String msg) {
        super(msg);
    }
}
