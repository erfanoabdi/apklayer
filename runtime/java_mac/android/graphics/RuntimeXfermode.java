package android.graphics;

// android.graphics.RuntimeXfermode -- API 36 programmable AGSL BLENDER
// (frameworks/base/graphics/java/android/graphics/RuntimeXfermode.java). Real
// Android compiles the AGSL string into an SkRuntimeEffect blender and runs it
// per pixel. Our Canvas is software/AWT-backed and has no AGSL runtime, so this
// is a value holder with the exact AOSP method surface: construction succeeds,
// uniforms are accepted, nothing executes. Same posture, and the same reason, as
// RuntimeShader in this package -- see its header. A Paint carrying one of these
// composites with the Paint's normal (SRC_OVER) rule.
//
// The point is binary compatibility: an app that builds a RuntimeXfermode must
// not die with NoClassDefFoundError before it draws anything else.
public class RuntimeXfermode extends Xfermode {

    // Kept for the contract / debugging, exactly as RuntimeShader keeps its AGSL.
    private final String mAgsl;

    public RuntimeXfermode(String agsl) {
        if (agsl == null) throw new NullPointerException("agsl must not be null");
        mAgsl = agsl;
    }

    public void setFloatUniform(String uniformName, float value) { }
    public void setFloatUniform(String uniformName, float v1, float v2) { }
    public void setFloatUniform(String uniformName, float v1, float v2, float v3) { }
    public void setFloatUniform(String uniformName, float v1, float v2, float v3, float v4) { }
    public void setFloatUniform(String uniformName, float[] values) { }

    public void setIntUniform(String uniformName, int value) { }
    public void setIntUniform(String uniformName, int v1, int v2) { }
    public void setIntUniform(String uniformName, int v1, int v2, int v3) { }
    public void setIntUniform(String uniformName, int v1, int v2, int v3, int v4) { }
    public void setIntUniform(String uniformName, int[] values) { }

    public void setColorUniform(String uniformName, int color) { }
    public void setColorUniform(String uniformName, long color) { }
    public void setColorUniform(String uniformName, Color color) { }

    public void setInputShader(String shaderName, Shader shader) { }
    public void setInputColorFilter(String colorFilterName, ColorFilter colorFilter) { }
    public void setInputXfermode(String xfermodeName, RuntimeXfermode xfermode) { }
}
