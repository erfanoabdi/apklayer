// android.os.DeadSystemException -- thrown when the system server has died.
// extends DeadObjectException with a fixed message, matching AOSP.
//
// AOSP ref: frameworks/base/core/java/android/os/DeadSystemException.java
package android.os;

public class DeadSystemException extends DeadObjectException {
    public DeadSystemException() {
        super("The system died; earlier logs will point to the root cause");
    }
}
