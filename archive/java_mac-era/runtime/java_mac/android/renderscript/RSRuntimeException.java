package android.renderscript;

/**
 * Base class for all RenderScript exceptions.
 *
 * Unlike the previous Mac.apk stub, this type IS thrown: the package now has a
 * real CPU implementation of the intrinsics and honest failures everywhere it
 * does not (ScriptC bitcode, ScriptGroup, graphics-only Elements). AOSP throws
 * the three subclasses below from argument validation, so an app's catch block
 * has to see the same type it would see on a device.
 *
 * Source: frameworks/rs/java/android/renderscript/RSRuntimeException.java
 */
public class RSRuntimeException extends RuntimeException {

    public RSRuntimeException(String string) {
        super(string);
    }
}
