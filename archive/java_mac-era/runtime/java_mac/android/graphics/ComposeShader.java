// android.graphics.ComposeShader -- a Shader that composites two other shaders
// (shaderA over/with shaderB) through a PorterDuff/BlendMode/Xfermode compositing
// rule (e.g. mask + content fills). AOSP source:
// frameworks/base/graphics/java/android/graphics/ComposeShader.java
//
// Our Canvas is software (AWT-backed) and does not yet truly sample, let alone
// compose, two shaders. We extend Shader and store both child shaders + the mode
// so that constructing one where a Shader is expected resolves at the type level
// (prevents NoClassDefFoundError / type mismatch during Paint setup). The
// macapkA()/macapkB() accessors let Canvas fall back to sampling shaderA alone
// when it cannot perform real composition.
package android.graphics;

public class ComposeShader extends Shader {
    private final Shader shaderA;
    private final Shader shaderB;
    // Exactly one of these holds the compositing rule, mirroring whichever ctor
    // the app used; the others stay null. We retain it for the contract only.
    private final PorterDuff.Mode porterDuffMode;
    private final BlendMode blendMode;
    private final Xfermode xfermode;

    public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) {
        this.shaderA = shaderA;
        this.shaderB = shaderB;
        this.porterDuffMode = mode;
        this.blendMode = null;
        this.xfermode = null;
    }

    public ComposeShader(Shader shaderA, Shader shaderB, BlendMode blendMode) {
        this.shaderA = shaderA;
        this.shaderB = shaderB;
        this.porterDuffMode = null;
        this.blendMode = blendMode;
        this.xfermode = null;
    }

    @Deprecated
    public ComposeShader(Shader shaderA, Shader shaderB, Xfermode mode) {
        this.shaderA = shaderA;
        this.shaderB = shaderB;
        this.porterDuffMode = null;
        this.blendMode = null;
        this.xfermode = mode;
    }

    // Canvas fallback accessors: with no real composition, sample shaderA (the
    // "source" child) so a composed fill at least shows the foreground shader.
    public Shader macapkA() { return shaderA; }
    public Shader macapkB() { return shaderB; }

    // Retained-contract accessors for the compositing rule (whichever was set).
    public PorterDuff.Mode macapkPorterDuffMode() { return porterDuffMode; }
    public BlendMode macapkBlendMode() { return blendMode; }
    public Xfermode macapkXfermode() { return xfermode; }
}
