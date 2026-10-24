// android.os.LimitExceededException -- thrown when a quota/limit on a system
// resource is exceeded (API 30+). extends IllegalStateException, matching AOSP.
//
// AOSP ref: frameworks/base/core/java/android/os/LimitExceededException.java
package android.os;

public class LimitExceededException extends IllegalStateException {
    public LimitExceededException() {
        super("limit exceeded");
    }
    public LimitExceededException(String message) {
        super(message);
    }
}
