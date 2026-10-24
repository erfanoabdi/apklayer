// android.os.strictmode.ServiceConnectionLeakedViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/ServiceConnectionLeakedViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class ServiceConnectionLeakedViolation extends Violation {
    public ServiceConnectionLeakedViolation(Throwable originStack) {
        super(null);
        setStackTrace(originStack.getStackTrace());
    }
}
