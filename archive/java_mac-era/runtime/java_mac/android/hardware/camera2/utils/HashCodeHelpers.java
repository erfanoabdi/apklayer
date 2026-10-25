// android.hardware.camera2.utils.HashCodeHelpers -- AOSP's internal hash mixer,
// used by every camera2 params value class so that two objects built the same
// way hash the same way.
//
// Verbatim from frameworks/base/core/java/android/hardware/camera2/utils/
// HashCodeHelpers.java (aosp-mirror, android16-release). @hide in AOSP, so it
// is not part of the public API surface -- it exists here for the same reason
// it exists there: the params classes call it.
package android.hardware.camera2.utils;

public final class HashCodeHelpers {

    /** h = (h * 31) XOR x, seeded at 1. */
    public static int hashCode(int... array) {
        if (array == null) return 0;
        int h = 1;
        for (int x : array) {
            h = ((h << 5) - h) ^ x;
        }
        return h;
    }

    public static int hashCode(float... array) {
        if (array == null) return 0;
        int h = 1;
        for (float f : array) {
            int x = Float.floatToIntBits(f);
            h = ((h << 5) - h) ^ x;
        }
        return h;
    }

    @SafeVarargs
    public static <T> int hashCodeGeneric(T... array) {
        if (array == null) return 0;
        int h = 1;
        for (T o : array) {
            int x = (o == null) ? 0 : o.hashCode();
            h = ((h << 5) - h) ^ x;
        }
        return h;
    }

    private HashCodeHelpers() {}
}
