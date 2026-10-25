package android.renderscript;

/**
 * Thrown when a RenderScript object is used after destroy(), or when a context
 * operation is attempted on a dead context.
 *
 * Source: frameworks/rs/java/android/renderscript/RSInvalidStateException.java
 */
public class RSInvalidStateException extends RSRuntimeException {

    public RSInvalidStateException(String string) {
        super(string);
    }
}
