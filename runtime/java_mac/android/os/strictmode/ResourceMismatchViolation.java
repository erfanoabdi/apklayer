// android.os.strictmode.ResourceMismatchViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/ResourceMismatchViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class ResourceMismatchViolation extends Violation {
    public ResourceMismatchViolation(Object tag) {
        super(tag.toString());
    }
}
