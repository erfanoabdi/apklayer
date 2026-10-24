// android.os.strictmode.IncorrectContextUseViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/
// IncorrectContextUseViolation.java (android16-release).
package android.os.strictmode;

public final class IncorrectContextUseViolation extends Violation {
    public IncorrectContextUseViolation(String message, Throwable originStack) {
        super(message);
        initCause(originStack);
    }
}
