// android.system.Int64Ref -- a mutable signed 64-bit reference for Os calls
// that update an offset in place (Os.sendfile) (API 28).
//   AOSP: libcore/luni/src/main/java/android/system/Int64Ref.java
//         (android14-release), mirrored member for member (NOT final in AOSP).
package android.system;

import libcore.util.Objects;

/**
 * A signed 64bit integer reference suitable for passing to lower-level system calls.
 */
public class Int64Ref {
    public long value;

    public Int64Ref(long value) {
        this.value = value;
    }

    @Override public String toString() {
        return Objects.toString(this);
    }
}
