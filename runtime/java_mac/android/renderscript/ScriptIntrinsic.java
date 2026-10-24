package android.renderscript;

/**
 * Base class for the built-in RenderScript kernels.
 *
 * Unlike {@link ScriptC}, these DO run on Mac.apk. Each subclass carries a Java
 * port of the corresponding kernel from AOSP's CPU reference implementation
 * (frameworks/rs/cpu_ref/rsCpuIntrinsic*.cpp), which is the same code path a
 * current Android device takes now that the GPU and DSP backends are gone.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsic.java
 */
public abstract class ScriptIntrinsic extends Script {

    ScriptIntrinsic(RenderScript rs) {
        super(rs);
    }
}
