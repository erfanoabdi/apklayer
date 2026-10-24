package android.renderscript;

import android.content.res.Resources;

/**
 * Base class for an APP-AUTHORED RenderScript kernel: the thing the tool chain
 * generates a ScriptC_<name> subclass for, from a .rs source file.
 *
 * THIS IS THE ONE CAPABILITY MAC.APK DOES NOT HAVE, and it says so instead of
 * pretending. A .rs file is compiled by slang into LLVM BITCODE, shipped inside
 * the APK as a raw resource, and JIT-compiled at runtime by bcc (or run through
 * the CPU reference interpreter). Executing it therefore needs an LLVM bitcode
 * engine that understands the RenderScript runtime ABI. Mac.apk has no such
 * engine, and inventing one is a different project.
 *
 * The alternatives were considered and rejected:
 *
 *  - Constructing successfully and letting forEach quietly do nothing. That is
 *    the worst option: the output Allocation keeps whatever it held, the app
 *    believes the kernel ran, and the resulting corruption surfaces somewhere
 *    far from here with no clue pointing back.
 *  - Constructing successfully and throwing only on launch. Better, but it
 *    defers the failure past all the setVar calls the app makes first.
 *
 * So the constructors throw RSDriverException, which extends RSRuntimeException
 * and is exactly what the platform raises when the driver cannot load a script.
 * An app that guards script creation and falls back to a Java implementation
 * (a common pattern once RenderScript was deprecated) takes its fallback path
 * cleanly, and one that does not fails at the right line with a message that
 * says why.
 *
 * The BUILT-IN kernels are unaffected and do run: see {@link ScriptIntrinsic}.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptC.java
 */
public class ScriptC extends Script {

    private static final String NO_ENGINE =
            "This app ships a compiled RenderScript kernel (.rs source compiled to LLVM"
            + " bitcode by slang). Executing one needs the RenderScript bitcode engine,"
            + " which Mac.apk does not have. The built-in ScriptIntrinsic kernels (blur,"
            + " colour matrix, convolve, LUT, blend, resize, YUV, histogram) ARE"
            + " implemented and run on the CPU.";

    protected ScriptC(int id, RenderScript rs) {
        super(rs);
        throw new RSDriverException(NO_ENGINE);
    }

    protected ScriptC(long id, RenderScript rs) {
        super(rs);
        throw new RSDriverException(NO_ENGINE);
    }

    /** @param resourceID the raw resource holding the compiled bitcode. */
    protected ScriptC(RenderScript rs, Resources resources, int resourceID) {
        super(rs);
        throw new RSDriverException(NO_ENGINE);
    }

    /**
     * @param bitcode32 bitcode for a 32 bit driver
     * @param bitcode64 bitcode for a 64 bit driver
     */
    protected ScriptC(RenderScript rs, String resName, byte[] bitcode32, byte[] bitcode64) {
        super(rs);
        throw new RSDriverException(NO_ENGINE);
    }
}
