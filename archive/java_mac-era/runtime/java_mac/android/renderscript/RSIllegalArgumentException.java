package android.renderscript;

/**
 * Thrown when a RenderScript API is handed an argument it cannot use -- a
 * vector size outside 2..4, a sub-element index out of range, mismatched
 * Allocation dimensions, and so on.
 *
 * NOTE it does NOT extend java.lang.IllegalArgumentException; AOSP roots the
 * whole family at RSRuntimeException. Code that catches IllegalArgumentException
 * around a RenderScript call therefore does NOT catch this, which is exactly
 * the observable behaviour we have to reproduce.
 *
 * Source: frameworks/rs/java/android/renderscript/RSIllegalArgumentException.java
 */
public class RSIllegalArgumentException extends RSRuntimeException {

    public RSIllegalArgumentException(String string) {
        super(string);
    }
}
