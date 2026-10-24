package android.renderscript;

/**
 * Thrown when the RenderScript driver cannot service a request.
 *
 * This is the type Mac.apk uses to report the ONE capability it genuinely does
 * not have: executing app-authored .rs kernels, which on a device are compiled
 * to LLVM bitcode by slang and JIT-ed by bcc. We have no bitcode engine, and
 * inventing one is out of scope, so ScriptC and ScriptGroup say so here rather
 * than silently returning the input buffer unprocessed. AOSP itself raises
 * RSDriverException for driver-side failures, so the app sees a failure shape
 * it already has to handle.
 *
 * Source: frameworks/rs/java/android/renderscript/RSDriverException.java
 */
public class RSDriverException extends RSRuntimeException {

    public RSDriverException(String string) {
        super(string);
    }
}
