// android.util.MutableBoolean -- a boxed-but-writable primitive, used as an
// out-parameter. AOSP: frameworks/base/core/java/android/util/MutableBoolean.java
// @deprecated in AOSP (use a one-element array), but still referenced by
// older library code.
package android.util;

/** @deprecated Use a size-one array instead. */
@Deprecated
public final class MutableBoolean {
    public boolean value;
    public MutableBoolean(boolean value) { this.value = value; }
}
