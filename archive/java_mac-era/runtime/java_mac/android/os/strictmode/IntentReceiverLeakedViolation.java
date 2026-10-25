// android.os.strictmode.IntentReceiverLeakedViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/IntentReceiverLeakedViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class IntentReceiverLeakedViolation extends Violation {
    public IntentReceiverLeakedViolation(Throwable originStack) {
        super(null);
        setStackTrace(originStack.getStackTrace());
    }
}
