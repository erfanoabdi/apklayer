// android.opengl.GLES20 -- the GLES 2.0 binding apps use directly.
//
// Phase 1 (current): every method is a Java stub. Apps boot through the
// AndEngine init pipeline without UnsatisfiedLinkError, but no pixels
// reach the screen. Useful for shaking out the rest of the framework
// without ANGLE complications.
//
// Phase 2 (next): each method becomes `native` and is RegisterNatives'd
// in src/native_methods.cpp, where it routes to runtime's copy of
// the angle_glue layer. Drop-in: the public Java signatures don't
// change so the app side keeps compiling.
package android.opengl;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

// Not `final`, and a public no-arg ctor: mirrors AOSP android.opengl.GLES20
// (which is a plain public class) so GLES30 extends GLES20 / GLES31 extends
// GLES30 / GLES32 extends GLES31 compiles, exactly as on real Android. Apps
// still call the static gl* methods; nobody instantiates these.
public class GLES20 {
    public GLES20() {}

    // ---- Core constants (subset; AndEngine touches most of these). ----
    public static final int GL_DEPTH_BUFFER_BIT   = 0x0100;
    public static final int GL_STENCIL_BUFFER_BIT = 0x0400;
    public static final int GL_COLOR_BUFFER_BIT   = 0x4000;
    public static final int GL_FALSE = 0;
    public static final int GL_TRUE  = 1;

    public static final int GL_POINTS         = 0;
    public static final int GL_LINES          = 1;
    public static final int GL_LINE_LOOP      = 2;
    public static final int GL_LINE_STRIP     = 3;
    public static final int GL_TRIANGLES      = 4;
    public static final int GL_TRIANGLE_STRIP = 5;
    public static final int GL_TRIANGLE_FAN   = 6;

    public static final int GL_NEVER    = 0x0200;
    public static final int GL_LESS     = 0x0201;
    public static final int GL_EQUAL    = 0x0202;
    public static final int GL_LEQUAL   = 0x0203;
    public static final int GL_GREATER  = 0x0204;
    public static final int GL_NOTEQUAL = 0x0205;
    public static final int GL_GEQUAL   = 0x0206;
    public static final int GL_ALWAYS   = 0x0207;

    public static final int GL_ZERO                     = 0;
    public static final int GL_ONE                      = 1;
    public static final int GL_SRC_COLOR                = 0x0300;
    public static final int GL_ONE_MINUS_SRC_COLOR      = 0x0301;
    public static final int GL_SRC_ALPHA                = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA      = 0x0303;
    public static final int GL_DST_ALPHA                = 0x0304;
    public static final int GL_ONE_MINUS_DST_ALPHA      = 0x0305;
    public static final int GL_DST_COLOR                = 0x0306;
    public static final int GL_ONE_MINUS_DST_COLOR      = 0x0307;
    public static final int GL_SRC_ALPHA_SATURATE       = 0x0308;

    public static final int GL_BLEND          = 0x0BE2;
    public static final int GL_DITHER         = 0x0BD0;
    public static final int GL_DEPTH_TEST     = 0x0B71;
    public static final int GL_STENCIL_TEST   = 0x0B90;
    public static final int GL_CULL_FACE      = 0x0B44;
    public static final int GL_SCISSOR_TEST   = 0x0C11;
    public static final int GL_POLYGON_OFFSET_FILL = 0x8037;

    public static final int GL_TEXTURE_2D       = 0x0DE1;
    public static final int GL_TEXTURE_CUBE_MAP = 0x8513;
    public static final int GL_TEXTURE0         = 0x84C0;
    public static final int GL_TEXTURE1         = 0x84C1;
    public static final int GL_ACTIVE_TEXTURE   = 0x84E0;
    public static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_WRAP_S     = 0x2802;
    public static final int GL_TEXTURE_WRAP_T     = 0x2803;

    public static final int GL_NEAREST                 = 0x2600;
    public static final int GL_LINEAR                  = 0x2601;
    public static final int GL_NEAREST_MIPMAP_NEAREST  = 0x2700;
    public static final int GL_LINEAR_MIPMAP_NEAREST   = 0x2701;
    public static final int GL_NEAREST_MIPMAP_LINEAR   = 0x2702;
    public static final int GL_LINEAR_MIPMAP_LINEAR    = 0x2703;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_REPEAT        = 0x2901;
    public static final int GL_MIRRORED_REPEAT = 0x8370;

    public static final int GL_BYTE           = 0x1400;
    public static final int GL_UNSIGNED_BYTE  = 0x1401;
    public static final int GL_SHORT          = 0x1402;
    public static final int GL_UNSIGNED_SHORT = 0x1403;
    public static final int GL_INT            = 0x1404;
    public static final int GL_UNSIGNED_INT   = 0x1405;
    public static final int GL_FLOAT          = 0x1406;
    public static final int GL_FIXED          = 0x140C;

    public static final int GL_ARRAY_BUFFER         = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_STATIC_DRAW   = 0x88E4;
    public static final int GL_DYNAMIC_DRAW  = 0x88E8;
    public static final int GL_STREAM_DRAW   = 0x88E0;

    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_VERTEX_SHADER   = 0x8B31;
    public static final int GL_MAX_VERTEX_ATTRIBS = 0x8869;
    public static final int GL_LINK_STATUS    = 0x8B82;
    public static final int GL_COMPILE_STATUS = 0x8B81;
    public static final int GL_INFO_LOG_LENGTH = 0x8B84;
    public static final int GL_VALIDATE_STATUS = 0x8B83;
    public static final int GL_CURRENT_PROGRAM = 0x8B8D;
    public static final int GL_ACTIVE_UNIFORMS = 0x8B86;
    public static final int GL_ACTIVE_ATTRIBUTES = 0x8B89;
    /** GLES 2.0 enum, needed by the String-returning glGetActiveAttrib /
     *  glGetActiveUniform / glGetShaderSource overloads below to size their
     *  own scratch buffer. Values from the Khronos gl2.h registry. */
    public static final int GL_ACTIVE_UNIFORM_MAX_LENGTH = 0x8B87;
    public static final int GL_SHADER_SOURCE_LENGTH = 0x8B88;
    public static final int GL_ACTIVE_ATTRIBUTE_MAX_LENGTH = 0x8B8A;

    public static final int GL_RGBA       = 0x1908;
    public static final int GL_RGB        = 0x1907;
    public static final int GL_LUMINANCE  = 0x1909;
    public static final int GL_ALPHA      = 0x1906;
    public static final int GL_LUMINANCE_ALPHA = 0x190A;
    public static final int GL_UNSIGNED_SHORT_4_4_4_4 = 0x8033;
    public static final int GL_UNSIGNED_SHORT_5_5_5_1 = 0x8034;
    public static final int GL_UNSIGNED_SHORT_5_6_5   = 0x8363;

    public static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    public static final int GL_PACK_ALIGNMENT   = 0x0D05;
    public static final int GL_VIEWPORT = 0x0BA2;

    public static final int GL_FRAMEBUFFER         = 0x8D40;
    public static final int GL_RENDERBUFFER        = 0x8D41;
    public static final int GL_COLOR_ATTACHMENT0   = 0x8CE0;
    public static final int GL_DEPTH_ATTACHMENT    = 0x8D00;
    public static final int GL_STENCIL_ATTACHMENT  = 0x8D20;
    public static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    public static final int GL_DEPTH_COMPONENT16   = 0x81A5;

    public static final int GL_VENDOR     = 0x1F00;
    public static final int GL_RENDERER   = 0x1F01;
    public static final int GL_VERSION    = 0x1F02;
    public static final int GL_EXTENSIONS = 0x1F03;
    public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;

    public static final int GL_NO_ERROR = 0;
    public static final int GL_INVALID_ENUM   = 0x0500;
    public static final int GL_INVALID_VALUE  = 0x0501;
    public static final int GL_INVALID_OPERATION = 0x0502;
    public static final int GL_OUT_OF_MEMORY  = 0x0505;

    public static final int GL_FRONT     = 0x0404;
    public static final int GL_BACK      = 0x0405;
    public static final int GL_FRONT_AND_BACK = 0x0408;
    public static final int GL_CW = 0x0900;
    public static final int GL_CCW = 0x0901;

    // ---- Whole-state functions (stubs). ----

    // (v1.0.836) The `nextName` fake-name counter is GONE. glGenFramebuffers /
    // glGenRenderbuffers used to hand out monotonically increasing invented
    // names; combined with the real native glBindFramebuffer that is actively
    // harmful, because GLES2 requires glBindFramebuffer to raise
    // GL_INVALID_OPERATION for a name that glGenFramebuffers never returned, so
    // the FBO never actually became bindable. `currentProgram` went with it: it
    // was written by nothing and read by nothing.

    public static native void glActiveTexture(int texture);
    public static native void glAttachShader(int program, int shader);
    public static native void glBindAttribLocation(int program, int index, String name);
    public static native void glBindBuffer(int target, int buffer);
    public static native void glBindFramebuffer(int target, int fb);
    public static native void glBindRenderbuffer(int target, int rb);
    public static native void glBindTexture(int target, int texture);
    public static native void glBlendColor(float r, float g, float b, float a);
    public static native void glBlendEquation(int mode);
    public static native void glBlendEquationSeparate(int modeRGB, int modeAlpha);
    public static native void glBlendFunc(int sfactor, int dfactor);
    public static native void glBlendFuncSeparate(int sRGB, int dRGB, int sA, int dA);
    // glBufferData routes the Buffer directly to native. The C++ side
    // uses GetDirectBufferAddress for zero-copy when the buffer is
    // direct (AndEngine's standard pattern, allocateDirect()), and
    // falls back to a JNI element copy for heap-backed buffers.
    public static native void glBufferData(int target, int size, Buffer data, int usage);
    public static native void glBufferSubData(int target, int offset, int size, Buffer data);
    // AOSP: `public static native int glCheckFramebufferStatus(int target)`.
    // Until v1.0.836 this returned GL_FRAMEBUFFER_COMPLETE unconditionally,
    // which is the most dangerous shape of lie in this file: libGDX's
    // GLFrameBuffer.build() calls it and throws IllegalStateException on
    // failure, so an FBO that did NOT build was reported healthy and the app
    // proceeded to render into a broken target. See
    // docs/gles20-noop-sweep_2026-07-29.md.
    public static native int  glCheckFramebufferStatus(int target);
    public static native void glClear(int mask);
    public static native void glClearColor(float r, float g, float b, float a);
    public static native void glClearDepthf(float depth);
    public static native void glClearStencil(int s);
    public static native void glColorMask(boolean red, boolean green, boolean blue, boolean alpha);
    public static native void glCompileShader(int shader);
    public static native void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data);
    public static native void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data);
    public static native void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border);
    public static native void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    public static native int  glCreateProgram();
    public static native int  glCreateShader(int type);
    public static native void glCullFace(int mode);
    public static native void glDeleteBuffers(int n, IntBuffer buffers);
    public static native void glDeleteBuffers(int n, int[] buffers, int offset);
    public static native void glDeleteFramebuffers(int n, IntBuffer fbs);
    public static native void glDeleteFramebuffers(int n, int[] fbs, int offset);
    public static native void glDeleteProgram(int program);
    public static native void glDeleteRenderbuffers(int n, IntBuffer rbs);
    public static native void glDeleteRenderbuffers(int n, int[] rbs, int offset);
    public static native void glDeleteShader(int shader);
    public static native void glDeleteTextures(int n, IntBuffer textures);
    public static native void glDeleteTextures(int n, int[] textures, int offset);
    public static native void glDepthFunc(int func);
    public static native void glDepthMask(boolean flag);
    public static native void glDepthRangef(float zNear, float zFar);
    public static native void glDetachShader(int program, int shader);
    public static native void glDisable(int cap);
    public static native void glDisableVertexAttribArray(int index);
    public static native void glDrawArrays(int mode, int first, int count);
    public static native void glDrawElements(int mode, int count, int type, int offset);
    // AOSP declares this native (android_opengl_GLES20.cpp,
    // android_glDrawElements__IIILjava_nio_Buffer_2): the Buffer is resolved to a
    // CLIENT-SIDE index pointer. It was an EMPTY BODY until v1.0.834, which silently
    // discarded every draw from libGDX's client-array Mesh path -- any libGDX build
    // older than 1.13.1, whose changelog reads "Change defaults of SpriteBatch to use
    // VBO instead of VertexArray on gl2/gles2.0". Newer builds take the
    // VertexBufferObject path and call the (int offset) overload above, which WAS
    // native, which is exactly why feudaltactics rendered and klooni / retrowars /
    // appleflinger did not. An empty-bodied framework method has no failure
    // signature at all: no GL error, no exception, 60 fps, assets loaded, stage.draw()
    // running every frame -- just a window showing the clear colour.
    // See docs/libgdx-blank-cluster_2026-07-29.md.
    public static native void glDrawElements(int mode, int count, int type, Buffer indices);
    public static native void glEnable(int cap);
    public static native void glEnableVertexAttribArray(int index);
    public static native void glFinish();
    public static native void glFlush();
    public static native void glFramebufferRenderbuffer(int target, int attachment, int rbtarget, int rb);
    public static native void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
    public static native void glFrontFace(int mode);
    public static void glGenBuffers(int n, IntBuffer buffers) {
        int[] tmp = new int[n]; nativeGlGenBuffers(n, tmp);
        for (int i = 0; i < n; ++i) buffers.put(tmp[i]);
    }
    public static void glGenBuffers(int n, int[] buffers, int offset) {
        int[] tmp = new int[n]; nativeGlGenBuffers(n, tmp);
        System.arraycopy(tmp, 0, buffers, offset, n);
    }
    private static native void nativeGlGenBuffers(int n, int[] out);
    public static native void glGenerateMipmap(int target);
    // Real names from ANGLE, exactly like glGenBuffers/glGenTextures above.
    // These fabricated `nextName++` values until v1.0.836; a fabricated name is
    // not a name GL knows, so the subsequent glBindFramebuffer/glBindRenderbuffer
    // raised GL_INVALID_OPERATION and the object never existed.
    public static void glGenFramebuffers(int n, IntBuffer fbs) {
        int[] tmp = new int[n]; nativeGlGenFramebuffers(n, tmp);
        for (int i = 0; i < n; ++i) fbs.put(tmp[i]);
    }
    public static void glGenFramebuffers(int n, int[] fbs, int offset) {
        int[] tmp = new int[n]; nativeGlGenFramebuffers(n, tmp);
        System.arraycopy(tmp, 0, fbs, offset, n);
    }
    private static native void nativeGlGenFramebuffers(int n, int[] out);
    public static void glGenRenderbuffers(int n, IntBuffer rbs) {
        int[] tmp = new int[n]; nativeGlGenRenderbuffers(n, tmp);
        for (int i = 0; i < n; ++i) rbs.put(tmp[i]);
    }
    public static void glGenRenderbuffers(int n, int[] rbs, int offset) {
        int[] tmp = new int[n]; nativeGlGenRenderbuffers(n, tmp);
        System.arraycopy(tmp, 0, rbs, offset, n);
    }
    private static native void nativeGlGenRenderbuffers(int n, int[] out);
    public static void glGenTextures(int n, IntBuffer textures) {
        int[] tmp = new int[n]; nativeGlGenTextures(n, tmp);
        for (int i = 0; i < n; ++i) textures.put(tmp[i]);
    }
    public static void glGenTextures(int n, int[] textures, int offset) {
        int[] tmp = new int[n]; nativeGlGenTextures(n, tmp);
        System.arraycopy(tmp, 0, textures, offset, n);
    }
    private static native void nativeGlGenTextures(int n, int[] out);
    public static native void glGetActiveAttrib(int program, int index, int bufsize, int[] length, int lengthOffset, int[] size, int sizeOffset, int[] type, int typeOffset, byte[] name, int nameOffset);
    public static native void glGetActiveUniform(int program, int index, int bufsize, int[] length, int lengthOffset, int[] size, int sizeOffset, int[] type, int typeOffset, byte[] name, int nameOffset);
    public static native void glGetAttachedShaders(int program, int maxcount, int[] count, int countOffset, int[] shaders, int shadersOffset);
    public static native int  glGetAttribLocation(int program, String name);
    public static native void glGetBooleanv(int pname, boolean[] params, int offset);
    public static void glGetBooleanv(int pname, IntBuffer params) {
        // AOSP's Buffer overload of glGetBooleanv takes an IntBuffer and
        // writes 0/1; no-advance write, matching glGetIntegerv.
        if (params == null) return;
        int pos = params.position(), n = macapkComponents(pname, params.limit() - pos);
        if (n <= 0) return;
        boolean[] tmp = new boolean[n]; glGetBooleanv(pname, tmp, 0);
        for (int i = 0; i < n; ++i) params.put(pos + i, tmp[i] ? 1 : 0);
    }
    // How many components a glGet* pname actually returns -- the Java-side
    // equivalent of getNeededCount() in AOSP's generated android_opengl_GLES*.cpp.
    //
    // ⚠ THIS IS LOAD-BEARING, and getting it wrong is invisible. Until v1.0.836
    // the three Buffer-output glGet* overloads below hardcoded `new int[1]` and
    // wrote exactly ONE element, so every MULTI-VALUED query silently returned
    // its first component and left the caller's remaining slots untouched.
    // The measured victim was retrowars: gdx-vfx's VfxFrameBuffer.begin() saves
    // the viewport with glGetIntegerv(GL_VIEWPORT, IntBuffer) and end() restores
    // it with glViewport(v[0],v[1],v[2],v[3]) -- so it read back x=0 plus three
    // zeros and applied glViewport(0,0,0,0) twice per frame, clipping every draw
    // in its post-processing pass to nothing. 11,162 zero viewports in a 75 s run.
    // See docs/gles20-noop-sweep_2026-07-29.md.
    private static int macapkComponents(int pname, int avail) {
        int needed;
        switch (pname) {
            case 0x0B70:   // GL_DEPTH_RANGE
            case 0x0D3A:   // GL_MAX_VIEWPORT_DIMS
            case 0x846D:   // GL_ALIASED_POINT_SIZE_RANGE
            case 0x846E:   // GL_ALIASED_LINE_WIDTH_RANGE
                needed = 2; break;
            case 0x0BA2:   // GL_VIEWPORT
            case 0x0C10:   // GL_SCISSOR_BOX
            case 0x0C22:   // GL_COLOR_CLEAR_VALUE
            case 0x0C23:   // GL_COLOR_WRITEMASK
            case 0x8005:   // GL_BLEND_COLOR
                needed = 4; break;
            case 0x86A3:   // GL_COMPRESSED_TEXTURE_FORMATS -- count is itself a query
            case 0x8DF8:   // GL_SHADER_BINARY_FORMATS      -- ditto
                needed = avail; break;
            default:
                needed = 1; break;
        }
        // Never promise more than the native bridge can actually fill: its
        // scratch is GLint[16] (see GLES20_glGetIntegerv in native_methods.cpp),
        // so asking for more would copy back zeros GL never wrote -- which is the
        // very failure this method exists to stop. The cap only bites on the two
        // list-valued pnames above, where it was already the effective limit.
        if (needed > 16) needed = 16;
        return Math.min(needed, avail);
    }
    public static native void glGetBufferParameteriv(int target, int pname, int[] params, int offset);
    public static native int glGetError();
    public static native void glGetFloatv(int pname, float[] params, int offset);
    public static void glGetFloatv(int pname, FloatBuffer params) {
        // No-advance write at the current position, matching glGetIntegerv.
        // Multi-component pnames (GL_COLOR_CLEAR_VALUE, GL_DEPTH_RANGE,
        // GL_BLEND_COLOR, ...) return all of their components as of v1.0.836;
        // see macapkComponents.
        if (params == null) return;
        int pos = params.position(), n = macapkComponents(pname, params.limit() - pos);
        if (n <= 0) return;
        float[] tmp = new float[n]; glGetFloatv(pname, tmp, 0);
        for (int i = 0; i < n; ++i) params.put(pos + i, tmp[i]);
    }
    public static native void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, int[] params, int offset);
    public static native void glGetIntegerv(int pname, int[] params, int offset);
    public static void glGetIntegerv(int pname, IntBuffer params) {
        // Write at the buffer's CURRENT position WITHOUT advancing it, per
        // the AOSP GLES Java binding for Buffer-output glGet* methods. The
        // old `params.put(tmp[0])` advanced the position, so a caller doing
        // `glGetIntegerv(p, buf); buf.get();` read PAST the value ->
        // BufferUnderflowException. See glGetShaderiv below.
        //
        // ALL components, not just the first (v1.0.836): the `new int[1]` this
        // used to allocate is what made glGetIntegerv(GL_VIEWPORT, buf) hand back
        // {x,0,0,0} and gave retrowars 11,162 glViewport(0,0,0,0) calls. See
        // macapkComponents above.
        if (params == null) return;
        int pos = params.position(), n = macapkComponents(pname, params.limit() - pos);
        if (n <= 0) return;
        int[] tmp = new int[n]; glGetIntegerv(pname, tmp, 0);
        for (int i = 0; i < n; ++i) params.put(pos + i, tmp[i]);
    }
    public static native void glGetProgramiv(int program, int pname, int[] params, int offset);
    public static void glGetProgramiv(int program, int pname, IntBuffer params) {
        int[] tmp = new int[1]; glGetProgramiv(program, pname, tmp, 0);
        params.put(params.position(), tmp[0]);   // no-advance; see glGetIntegerv
    }
    public static native String glGetProgramInfoLog(int program);
    public static native void glGetRenderbufferParameteriv(int target, int pname, int[] params, int offset);
    public static native void glGetShaderiv(int shader, int pname, int[] params, int offset);
    public static void glGetShaderiv(int shader, int pname, IntBuffer params) {
        // No-advance write (see glGetIntegerv). The advancing version made
        // libGDX/watabou's Shader.compile() -- `glGetShaderiv(s,
        // GL_COMPILE_STATUS, buf); buf.get();` -- throw
        // BufferUnderflowException, so NO shader ever compiled (ShatteredPD
        // and every libGDX game: black screen, Program null at draw time).
        int[] tmp = new int[1]; glGetShaderiv(shader, pname, tmp, 0);
        params.put(params.position(), tmp[0]);
    }
    public static native String glGetShaderInfoLog(int shader);
    // All five of these were empty bodies (out-parameter never written, so the
    // caller read back its own zeros) until v1.0.836. AOSP declares every one
    // native. glGetShaderPrecisionFormat is the one with teeth: an engine that
    // queries it to decide whether to emit `highp` reads range={0,0} and
    // concludes high precision is unavailable. See
    // docs/gles20-noop-sweep_2026-07-29.md.
    public static native void glGetShaderPrecisionFormat(int shadertype, int precisiontype, int[] range, int rangeOffset, int[] precision, int precisionOffset);
    public static native void glGetShaderSource(int shader, int bufsize, int[] length, int lengthOffset, byte[] source, int sourceOffset);
    public static native String glGetString(int name);
    public static native void glGetTexParameterfv(int target, int pname, float[] params, int offset);
    public static native void glGetTexParameteriv(int target, int pname, int[] params, int offset);
    public static native void glGetUniformfv(int program, int location, float[] params, int offset);
    public static native void glGetUniformiv(int program, int location, int[] params, int offset);
    public static native int  glGetUniformLocation(int program, String name);
    public static native void glGetVertexAttribfv(int index, int pname, float[] params, int offset);
    public static native void glGetVertexAttribiv(int index, int pname, int[] params, int offset);
    public static native void glHint(int target, int mode);
    // Per the GLES spec (glIsBuffer/glIsTexture/etc.), object name 0 is
    // never a valid generated name in any GL implementation, so
    // glIs*(0) must return false unconditionally. That holds regardless
    // of driver/backend and needs no native round-trip, unlike the
    // nonzero case: telling a real live object apart from a name this
    // process merely handed out (or a name that was since deleted)
    // needs ANGLE's real object table, which native_methods.cpp does
    // not yet expose (gles-conformance-13). Nonzero names stay the
    // conservative true until that lands.
    public static boolean glIsBuffer(int buffer) { return buffer != 0; }
    // AOSP: native. Returning a hardcoded `false` until v1.0.836 broke the
    // save/restore idiom every state-preserving engine uses --
    // `boolean was = glIsEnabled(GL_BLEND); ...; if (was) glEnable(GL_BLEND);`
    // always restored the capability to OFF, silently, with a clean log.
    public static native boolean glIsEnabled(int cap);
    public static boolean glIsFramebuffer(int framebuffer) { return framebuffer != 0; }
    public static boolean glIsProgram(int program) { return program != 0; }
    public static boolean glIsRenderbuffer(int renderbuffer) { return renderbuffer != 0; }
    public static boolean glIsShader(int shader) { return shader != 0; }
    public static boolean glIsTexture(int texture) { return texture != 0; }
    public static native void glLineWidth(float width);
    public static native void glLinkProgram(int program);
    public static native void glPixelStorei(int pname, int param);
    public static native void glPolygonOffset(float factor, float units);
    // AOSP: native (android_glReadPixels__IIIIIILjava_nio_Buffer_2), which
    // resolves the Buffer to a raw pointer with no size check -- the app owns
    // sizing, exactly as on real Android. An empty body until v1.0.836 handed
    // every caller back its own untouched buffer, i.e. a black screenshot from
    // libGDX's ScreenUtils.getFrameBufferPixels and from any in-app capture.
    //
    // This does NOT feed Mac.apk's own render instrument: self-capture and the
    // GL frame dumps call the C++ glReadPixels directly (runtime/src/angle_glue.cpp
    // and cocoa_bridge.mm), never this Java binding, so wiring it up cannot
    // perturb a render verdict. See docs/gles20-noop-sweep_2026-07-29.md.
    public static native void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels);
    public static native void glReleaseShaderCompiler();
    public static native void glRenderbufferStorage(int target, int internalformat, int width, int height);
    public static native void glSampleCoverage(float value, boolean invert);
    public static native void glScissor(int x, int y, int width, int height);
    // AOSP: native. Bridged in v1.0.836 even though ANGLE advertises ZERO
    // shader-binary formats -- precisely BECAUSE it does. A silent no-op told
    // the caller "loaded" (glGetError()==0), so a precompiled-shader loader
    // skipped its source fallback and then failed at glLinkProgram with a
    // confusing error. The real entry point raises GL_INVALID_ENUM per the GLES
    // spec (unsupported binaryformat), which is the answer that makes the
    // caller's fallback path run.
    public static native void glShaderBinary(int n, int[] shaders, int offset, int binaryformat, Buffer binary, int length);
    public static native void glShaderSource(int shader, String source);
    public static native void glStencilFunc(int func, int ref, int mask);
    public static native void glStencilFuncSeparate(int face, int func, int ref, int mask);
    public static native void glStencilMask(int mask);
    public static native void glStencilMaskSeparate(int face, int mask);
    public static native void glStencilOp(int fail, int zfail, int zpass);
    public static native void glStencilOpSeparate(int face, int fail, int zfail, int zpass);
    public static native void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels);
    public static native void glTexParameterf(int target, int pname, float param);
    // AOSP: both overloads native. Empty bodies until v1.0.836 dropped every
    // array-form filter/wrap/anisotropy request on the floor -- the same class
    // of silent loss the scalar glTexParameterf was fixed for in v1.0.682.
    public static native void glTexParameterfv(int target, int pname, float[] params, int offset);
    public static void glTexParameterfv(int target, int pname, FloatBuffer params) {
        // Buffer overloads delegate onto the array form (the established pattern
        // in this file -- see glGetIntegerv/glUniformMatrix4fv). Reads from
        // position() WITHOUT advancing, per the AOSP GLES Java binding.
        if (params == null) return;
        int p = params.position(), n = Math.min(4, params.limit() - p);
        if (n <= 0) return;
        float[] tmp = new float[n];
        for (int i = 0; i < n; ++i) tmp[i] = params.get(p + i);
        glTexParameterfv(target, pname, tmp, 0);
    }
    public static native void glTexParameteri(int target, int pname, int param);
    public static void glTexParameteriv(int target, int pname, int[] params, int offset) {
        if (params != null && offset < params.length) glTexParameteri(target, pname, params[offset]);
    }
    public static void glTexParameteriv(int target, int pname, IntBuffer params) {
        if (params != null) glTexParameteri(target, pname, params.get(params.position()));
    }
    public static native void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels);
    public static native void glUniform1f(int location, float x);
    public static native void glUniform1fv(int location, int count, float[] v, int offset);
    public static native void glUniform1fv(int location, int count, FloatBuffer v);
    public static native void glUniform1i(int location, int x);
    public static native void glUniform1iv(int location, int count, int[] v, int offset);
    public static native void glUniform1iv(int location, int count, IntBuffer v);
    public static native void glUniform2f(int location, float x, float y);
    public static native void glUniform2fv(int location, int count, float[] v, int offset);
    public static native void glUniform2fv(int location, int count, FloatBuffer v);
    public static native void glUniform2i(int location, int x, int y);
    public static native void glUniform2iv(int location, int count, int[] v, int offset);
    public static native void glUniform2iv(int location, int count, IntBuffer v);
    public static native void glUniform3f(int location, float x, float y, float z);
    public static native void glUniform3fv(int location, int count, float[] v, int offset);
    public static native void glUniform3fv(int location, int count, FloatBuffer v);
    public static native void glUniform3i(int location, int x, int y, int z);
    public static native void glUniform3iv(int location, int count, int[] v, int offset);
    // AOSP declares both the array+offset and IntBuffer overloads native
    // for every glUniformNiv. This IntBuffer form was missing entirely
    // (NoSuchMethodError for guest bytecode calling it, instead of the
    // no-op every other stub here gives), not merely unwired like its
    // siblings. Added as a stub matching glUniform1iv/glUniform2iv's
    // current no-op status above; still needs the real native bridge
    // (gles-conformance-03, requires native_methods.cpp).
    public static native void glUniform3iv(int location, int count, IntBuffer v);
    public static native void glUniform4f(int location, float x, float y, float z, float w);
    public static native void glUniform4fv(int location, int count, float[] v, int offset);
    public static native void glUniform4fv(int location, int count, FloatBuffer v);
    public static native void glUniform4i(int location, int x, int y, int z, int w);
    public static native void glUniform4iv(int location, int count, int[] v, int offset);
    public static native void glUniform4iv(int location, int count, IntBuffer v);
    public static native void glUniformMatrix2fv(int location, int count, boolean transpose, float[] v, int offset);
    public static native void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer v);
    public static native void glUniformMatrix3fv(int location, int count, boolean transpose, float[] v, int offset);
    public static native void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer v);
    public static native void glUniformMatrix4fv(int location, int count, boolean transpose, float[] v, int offset);
    public static void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer v) {
        if (v == null) return;
        float[] tmp = new float[count * 16];
        int p = v.position();
        for (int i = 0; i < tmp.length; ++i) tmp[i] = v.get(p + i);
        glUniformMatrix4fv(location, count, transpose, tmp, 0);
    }
    public static native void glUseProgram(int program);
    public static native void glValidateProgram(int program);
    // ---- glVertexAttrib{1,2,3,4}f{,v}: 12 methods, all empty bodies until
    // v1.0.836, all declared native by AOSP.
    //
    // This is the same failure class as glDrawElements(...,Buffer) in
    // v1.0.834 and it is just as invisible. A CONSTANT vertex attribute (one
    // not fed by an enabled glVertexAttribPointer array) gets its value from
    // exactly these calls; drop them and the attribute keeps whatever it held
    // before, which for a fresh context is the GL default (0,0,0,1). So the
    // geometry draws, glGetError() is clean, the log is clean -- and the mesh
    // comes out black, or at the origin, or invisible, depending on which
    // attribute was dropped. libGDX's ShaderProgram.setAttributef routes here,
    // as does any engine that sets a per-draw constant colour this way.
    //
    // gles-conformance-03 (the "needs native_methods.cpp" note that used to sit
    // here) is CLOSED by this change.
    // See docs/gles20-noop-sweep_2026-07-29.md.
    public static native void glVertexAttrib1f(int index, float x);
    public static native void glVertexAttrib2f(int index, float x, float y);
    public static native void glVertexAttrib3f(int index, float x, float y, float z);
    public static native void glVertexAttrib4f(int index, float x, float y, float z, float w);
    public static native void glVertexAttrib1fv(int index, float[] v, int offset);
    public static native void glVertexAttrib2fv(int index, float[] v, int offset);
    public static native void glVertexAttrib3fv(int index, float[] v, int offset);
    public static native void glVertexAttrib4fv(int index, float[] v, int offset);
    // FloatBuffer overloads delegate onto the array forms, the established
    // pattern for Buffer overloads in this file. Each reads exactly N floats
    // from position() without advancing it.
    public static void glVertexAttrib1fv(int index, FloatBuffer v) { float[] t = macapkTake(v, 1); if (t != null) glVertexAttrib1fv(index, t, 0); }
    public static void glVertexAttrib2fv(int index, FloatBuffer v) { float[] t = macapkTake(v, 2); if (t != null) glVertexAttrib2fv(index, t, 0); }
    public static void glVertexAttrib3fv(int index, FloatBuffer v) { float[] t = macapkTake(v, 3); if (t != null) glVertexAttrib3fv(index, t, 0); }
    public static void glVertexAttrib4fv(int index, FloatBuffer v) { float[] t = macapkTake(v, 4); if (t != null) glVertexAttrib4fv(index, t, 0); }
    // Copy exactly n floats out of a FloatBuffer at its current position
    // WITHOUT advancing it. Returns null when the buffer cannot supply n, so
    // the caller skips the GL call rather than passing a short array.
    private static float[] macapkTake(FloatBuffer v, int n) {
        if (v == null) return null;
        int p = v.position();
        if (v.limit() - p < n) return null;
        float[] t = new float[n];
        for (int i = 0; i < n; ++i) t[i] = v.get(p + i);
        return t;
    }
    public static native void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, Buffer ptr);
    public static native void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int offset);
    public static native void glViewport(int x, int y, int width, int height);

    // --- java.nio.Buffer overloads of the glGet* family (AOSP GLES20 surface). ---
    // Pure-Java delegators onto the existing int[]/float[] variants: read into a
    // temp array, copy back into the buffer preserving its position. glGet reads
    // are tiny (1-4 elements) so the temp allocation is negligible. Added v734.
    // (glGetIntegerv/glGetFloatv/glGetProgramiv/glGetShaderiv/glGetBooleanv Buffer
    //  overloads already exist elsewhere in this class.)
    private static void macapkPutBack(IntBuffer dst, int[] src) {
        int pos = dst.position(); dst.put(src, 0, Math.min(src.length, dst.limit() - pos)); dst.position(pos);
    }
    private static void macapkPutBack(FloatBuffer dst, float[] src) {
        int pos = dst.position(); dst.put(src, 0, Math.min(src.length, dst.limit() - pos)); dst.position(pos);
    }
    public static void glGetVertexAttribiv(int index, int pname, IntBuffer params) {
        int[] t = new int[Math.max(1, params.remaining())]; glGetVertexAttribiv(index, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetVertexAttribfv(int index, int pname, FloatBuffer params) {
        float[] t = new float[Math.max(1, params.remaining())]; glGetVertexAttribfv(index, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetUniformiv(int program, int location, IntBuffer params) {
        int[] t = new int[Math.max(1, params.remaining())]; glGetUniformiv(program, location, t, 0); macapkPutBack(params, t);
    }
    public static void glGetUniformfv(int program, int location, FloatBuffer params) {
        float[] t = new float[Math.max(1, params.remaining())]; glGetUniformfv(program, location, t, 0); macapkPutBack(params, t);
    }
    public static void glGetTexParameteriv(int target, int pname, IntBuffer params) {
        int[] t = new int[Math.max(1, params.remaining())]; glGetTexParameteriv(target, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetTexParameterfv(int target, int pname, FloatBuffer params) {
        float[] t = new float[Math.max(1, params.remaining())]; glGetTexParameterfv(target, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params) {
        int[] t = new int[Math.max(1, params.remaining())]; glGetRenderbufferParameteriv(target, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params) {
        int[] t = new int[Math.max(1, params.remaining())]; glGetFramebufferAttachmentParameteriv(target, attachment, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetBufferParameteriv(int target, int pname, IntBuffer params) {
        int[] t = new int[Math.max(1, params.remaining())]; glGetBufferParameteriv(target, pname, t, 0); macapkPutBack(params, t);
    }
    public static void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision) {
        int[] r = new int[Math.max(1, range.remaining())]; int[] pr = new int[Math.max(1, precision.remaining())];
        glGetShaderPrecisionFormat(shadertype, precisiontype, r, 0, pr, 0); macapkPutBack(range, r); macapkPutBack(precision, pr);
    }
    public static void glGetAttachedShaders(int program, int maxcount, IntBuffer count, IntBuffer shaders) {
        int[] c = new int[Math.max(1, count.remaining())]; int[] sh = new int[Math.max(1, shaders.remaining())];
        glGetAttachedShaders(program, maxcount, c, 0, sh, 0); macapkPutBack(count, c); macapkPutBack(shaders, sh);
    }
    public static void glShaderBinary(int n, IntBuffer shaders, int binaryformat, Buffer binary, int length) {
        // Delegates onto the int[] form, which is a real native as of v1.0.836.
        // (It used to be a no-op "because the int[] variant is a no-op" -- both
        // now reach ANGLE, which raises GL_INVALID_ENUM for its zero supported
        // binary formats, so the caller's source fallback actually triggers.)
        if (shaders == null) return;
        int p = shaders.position(), c = Math.min(n, shaders.limit() - p);
        if (c <= 0) return;
        int[] t = new int[c];
        for (int i = 0; i < c; ++i) t[i] = shaders.get(p + i);
        glShaderBinary(c, t, 0, binaryformat, binary, length);
    }

    // ====== String-returning glGet* overloads (AOSP GLES20, API 8) ===========
    // Added 2026-08-24. AOSP declares all three `native`; they are built here in
    // PURE JAVA on top of the byte[]-out natives already registered above, so no
    // new RegisterNatives row is needed (debug/jni_registration_gate.sh) and the
    // behaviour is identical -- the native side of AOSP's versions does exactly
    // this sequence (query the max length, allocate, call, trim by `length`).
    //
    // ⚠ The scratch buffer is sized from the program's/shader's OWN reported
    // maximum, never from a guess -- the same discipline as macapkComponents /
    // AOSP's getNeededCount above. A too-small buffer here would silently
    // truncate an identifier, which reads as "the uniform does not exist".

    /** AOSP GLES20.glGetActiveAttrib(program, index, size, sizeOffset, type,
     *  typeOffset) -> the attribute's NAME. */
    public static String glGetActiveAttrib(int program, int index,
            int[] size, int sizeOffset, int[] type, int typeOffset) {
        int[] len = new int[1];
        glGetProgramiv(program, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, len, 0);
        int bufsize = len[0] > 0 ? len[0] : 256;
        byte[] name = new byte[bufsize];
        int[] written = new int[1];
        glGetActiveAttrib(program, index, bufsize, written, 0,
                size, sizeOffset, type, typeOffset, name, 0);
        return macapkCString(name, written[0]);
    }

    /** AOSP GLES20.glGetActiveAttrib(program, index, size, type) -- the
     *  NIO-buffer flavour. AOSP writes ONE int into each buffer at its current
     *  position and does NOT advance it. */
    public static String glGetActiveAttrib(int program, int index,
            IntBuffer size, IntBuffer type) {
        int[] sz = new int[1];
        int[] tp = new int[1];
        String n = glGetActiveAttrib(program, index, sz, 0, tp, 0);
        if (size != null) size.put(size.position(), sz[0]);
        if (type != null) type.put(type.position(), tp[0]);
        return n;
    }

    /** AOSP GLES20.glGetActiveUniform(program, index, size, sizeOffset, type,
     *  typeOffset) -> the uniform's NAME. */
    public static String glGetActiveUniform(int program, int index,
            int[] size, int sizeOffset, int[] type, int typeOffset) {
        int[] len = new int[1];
        glGetProgramiv(program, GL_ACTIVE_UNIFORM_MAX_LENGTH, len, 0);
        int bufsize = len[0] > 0 ? len[0] : 256;
        byte[] name = new byte[bufsize];
        int[] written = new int[1];
        glGetActiveUniform(program, index, bufsize, written, 0,
                size, sizeOffset, type, typeOffset, name, 0);
        return macapkCString(name, written[0]);
    }

    /** AOSP GLES20.glGetActiveUniform(program, index, size, type). */
    public static String glGetActiveUniform(int program, int index,
            IntBuffer size, IntBuffer type) {
        int[] sz = new int[1];
        int[] tp = new int[1];
        String n = glGetActiveUniform(program, index, sz, 0, tp, 0);
        if (size != null) size.put(size.position(), sz[0]);
        if (type != null) type.put(type.position(), tp[0]);
        return n;
    }

    /** AOSP GLES20.glGetShaderSource(shader) -> the shader's source string. */
    public static String glGetShaderSource(int shader) {
        int[] len = new int[1];
        glGetShaderiv(shader, GL_SHADER_SOURCE_LENGTH, len, 0);
        int bufsize = len[0] > 0 ? len[0] : 1;
        byte[] src = new byte[bufsize];
        int[] written = new int[1];
        glGetShaderSource(shader, bufsize, written, 0, src, 0);
        return macapkCString(src, written[0]);
    }

    /** GL writes a NUL-terminated string and reports the length WITHOUT the
     *  NUL in `length`. A driver that leaves `length` at 0 (legal when the
     *  object has no name/source) must not produce a garbage String, so fall
     *  back to scanning for the terminator. */
    private static String macapkCString(byte[] buf, int length) {
        int n = length;
        if (n <= 0 || n > buf.length) {
            n = 0;
            while (n < buf.length && buf[n] != 0) n++;
        }
        return new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---- AOSP enum constants (values verbatim from the API-36 signature file) --
    // javac inlines a static-final int into the caller, so a MISSING one cannot
    // throw at runtime -- but it is still part of the class's declared surface: a
    // reflective lookup (engines that map GL enum names to values for logging or
    // for a scripting bridge) sees the difference, and a value we never declared
    // is a value we cannot recognise when an app passes it back in. Generated
    // from the signature file, sorted by name, no hand-typed hex.
    public static final int GL_ALIASED_LINE_WIDTH_RANGE = 33902;
    public static final int GL_ALIASED_POINT_SIZE_RANGE = 33901;
    public static final int GL_ALPHA_BITS = 3413;
    public static final int GL_ARRAY_BUFFER_BINDING = 34964;
    public static final int GL_ATTACHED_SHADERS = 35717;
    public static final int GL_BLEND_COLOR = 32773;
    public static final int GL_BLEND_DST_ALPHA = 32970;
    public static final int GL_BLEND_DST_RGB = 32968;
    public static final int GL_BLEND_EQUATION = 32777;
    public static final int GL_BLEND_EQUATION_ALPHA = 34877;
    public static final int GL_BLEND_EQUATION_RGB = 32777;
    public static final int GL_BLEND_SRC_ALPHA = 32971;
    public static final int GL_BLEND_SRC_RGB = 32969;
    public static final int GL_BLUE_BITS = 3412;
    public static final int GL_BOOL = 35670;
    public static final int GL_BOOL_VEC2 = 35671;
    public static final int GL_BOOL_VEC3 = 35672;
    public static final int GL_BOOL_VEC4 = 35673;
    public static final int GL_BUFFER_SIZE = 34660;
    public static final int GL_BUFFER_USAGE = 34661;
    public static final int GL_COLOR_CLEAR_VALUE = 3106;
    public static final int GL_COLOR_WRITEMASK = 3107;
    public static final int GL_COMPRESSED_TEXTURE_FORMATS = 34467;
    public static final int GL_CONSTANT_ALPHA = 32771;
    public static final int GL_CONSTANT_COLOR = 32769;
    public static final int GL_CULL_FACE_MODE = 2885;
    public static final int GL_CURRENT_VERTEX_ATTRIB = 34342;
    public static final int GL_DECR = 7683;
    public static final int GL_DECR_WRAP = 34056;
    public static final int GL_DELETE_STATUS = 35712;
    public static final int GL_DEPTH_BITS = 3414;
    public static final int GL_DEPTH_CLEAR_VALUE = 2931;
    public static final int GL_DEPTH_COMPONENT = 6402;
    public static final int GL_DEPTH_FUNC = 2932;
    public static final int GL_DEPTH_RANGE = 2928;
    public static final int GL_DEPTH_WRITEMASK = 2930;
    public static final int GL_DONT_CARE = 4352;
    public static final int GL_ELEMENT_ARRAY_BUFFER_BINDING = 34965;
    public static final int GL_FASTEST = 4353;
    public static final int GL_FLOAT_MAT2 = 35674;
    public static final int GL_FLOAT_MAT3 = 35675;
    public static final int GL_FLOAT_MAT4 = 35676;
    public static final int GL_FLOAT_VEC2 = 35664;
    public static final int GL_FLOAT_VEC3 = 35665;
    public static final int GL_FLOAT_VEC4 = 35666;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 36049;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 36048;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE = 36051;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL = 36050;
    public static final int GL_FRAMEBUFFER_BINDING = 36006;
    public static final int GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 36054;
    public static final int GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS = 36057;
    public static final int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 36055;
    public static final int GL_FRAMEBUFFER_UNSUPPORTED = 36061;
    public static final int GL_FRONT_FACE = 2886;
    public static final int GL_FUNC_ADD = 32774;
    public static final int GL_FUNC_REVERSE_SUBTRACT = 32779;
    public static final int GL_FUNC_SUBTRACT = 32778;
    public static final int GL_GENERATE_MIPMAP_HINT = 33170;
    public static final int GL_GREEN_BITS = 3411;
    public static final int GL_HIGH_FLOAT = 36338;
    public static final int GL_HIGH_INT = 36341;
    public static final int GL_IMPLEMENTATION_COLOR_READ_FORMAT = 35739;
    public static final int GL_IMPLEMENTATION_COLOR_READ_TYPE = 35738;
    public static final int GL_INCR = 7682;
    public static final int GL_INCR_WRAP = 34055;
    public static final int GL_INT_VEC2 = 35667;
    public static final int GL_INT_VEC3 = 35668;
    public static final int GL_INT_VEC4 = 35669;
    public static final int GL_INVALID_FRAMEBUFFER_OPERATION = 1286;
    public static final int GL_INVERT = 5386;
    public static final int GL_KEEP = 7680;
    public static final int GL_LINE_WIDTH = 2849;
    public static final int GL_LOW_FLOAT = 36336;
    public static final int GL_LOW_INT = 36339;
    public static final int GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS = 35661;
    public static final int GL_MAX_CUBE_MAP_TEXTURE_SIZE = 34076;
    public static final int GL_MAX_FRAGMENT_UNIFORM_VECTORS = 36349;
    public static final int GL_MAX_RENDERBUFFER_SIZE = 34024;
    public static final int GL_MAX_TEXTURE_SIZE = 3379;
    public static final int GL_MAX_VARYING_VECTORS = 36348;
    public static final int GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS = 35660;
    public static final int GL_MAX_VERTEX_UNIFORM_VECTORS = 36347;
    public static final int GL_MAX_VIEWPORT_DIMS = 3386;
    public static final int GL_MEDIUM_FLOAT = 36337;
    public static final int GL_MEDIUM_INT = 36340;
    public static final int GL_NICEST = 4354;
    public static final int GL_NONE = 0;
    public static final int GL_NUM_COMPRESSED_TEXTURE_FORMATS = 34466;
    public static final int GL_NUM_SHADER_BINARY_FORMATS = 36345;
    public static final int GL_ONE_MINUS_CONSTANT_ALPHA = 32772;
    public static final int GL_ONE_MINUS_CONSTANT_COLOR = 32770;
    public static final int GL_POLYGON_OFFSET_FACTOR = 32824;
    public static final int GL_POLYGON_OFFSET_UNITS = 10752;
    public static final int GL_RED_BITS = 3410;
    public static final int GL_RENDERBUFFER_ALPHA_SIZE = 36179;
    public static final int GL_RENDERBUFFER_BINDING = 36007;
    public static final int GL_RENDERBUFFER_BLUE_SIZE = 36178;
    public static final int GL_RENDERBUFFER_DEPTH_SIZE = 36180;
    public static final int GL_RENDERBUFFER_GREEN_SIZE = 36177;
    public static final int GL_RENDERBUFFER_HEIGHT = 36163;
    public static final int GL_RENDERBUFFER_INTERNAL_FORMAT = 36164;
    public static final int GL_RENDERBUFFER_RED_SIZE = 36176;
    public static final int GL_RENDERBUFFER_STENCIL_SIZE = 36181;
    public static final int GL_RENDERBUFFER_WIDTH = 36162;
    public static final int GL_REPLACE = 7681;
    public static final int GL_RGB565 = 36194;
    public static final int GL_RGB5_A1 = 32855;
    public static final int GL_RGBA4 = 32854;
    public static final int GL_SAMPLER_2D = 35678;
    public static final int GL_SAMPLER_CUBE = 35680;
    public static final int GL_SAMPLES = 32937;
    public static final int GL_SAMPLE_ALPHA_TO_COVERAGE = 32926;
    public static final int GL_SAMPLE_BUFFERS = 32936;
    public static final int GL_SAMPLE_COVERAGE = 32928;
    public static final int GL_SAMPLE_COVERAGE_INVERT = 32939;
    public static final int GL_SAMPLE_COVERAGE_VALUE = 32938;
    public static final int GL_SCISSOR_BOX = 3088;
    public static final int GL_SHADER_BINARY_FORMATS = 36344;
    public static final int GL_SHADER_COMPILER = 36346;
    public static final int GL_SHADER_TYPE = 35663;
    public static final int GL_STENCIL_BACK_FAIL = 34817;
    public static final int GL_STENCIL_BACK_FUNC = 34816;
    public static final int GL_STENCIL_BACK_PASS_DEPTH_FAIL = 34818;
    public static final int GL_STENCIL_BACK_PASS_DEPTH_PASS = 34819;
    public static final int GL_STENCIL_BACK_REF = 36003;
    public static final int GL_STENCIL_BACK_VALUE_MASK = 36004;
    public static final int GL_STENCIL_BACK_WRITEMASK = 36005;
    public static final int GL_STENCIL_BITS = 3415;
    public static final int GL_STENCIL_CLEAR_VALUE = 2961;
    public static final int GL_STENCIL_FAIL = 2964;
    public static final int GL_STENCIL_FUNC = 2962;
    public static final int GL_STENCIL_INDEX = 6401;
    public static final int GL_STENCIL_INDEX8 = 36168;
    public static final int GL_STENCIL_PASS_DEPTH_FAIL = 2965;
    public static final int GL_STENCIL_PASS_DEPTH_PASS = 2966;
    public static final int GL_STENCIL_REF = 2967;
    public static final int GL_STENCIL_VALUE_MASK = 2963;
    public static final int GL_STENCIL_WRITEMASK = 2968;
    public static final int GL_SUBPIXEL_BITS = 3408;
    public static final int GL_TEXTURE = 5890;
    public static final int GL_TEXTURE10 = 33994;
    public static final int GL_TEXTURE11 = 33995;
    public static final int GL_TEXTURE12 = 33996;
    public static final int GL_TEXTURE13 = 33997;
    public static final int GL_TEXTURE14 = 33998;
    public static final int GL_TEXTURE15 = 33999;
    public static final int GL_TEXTURE16 = 34000;
    public static final int GL_TEXTURE17 = 34001;
    public static final int GL_TEXTURE18 = 34002;
    public static final int GL_TEXTURE19 = 34003;
    public static final int GL_TEXTURE2 = 33986;
    public static final int GL_TEXTURE20 = 34004;
    public static final int GL_TEXTURE21 = 34005;
    public static final int GL_TEXTURE22 = 34006;
    public static final int GL_TEXTURE23 = 34007;
    public static final int GL_TEXTURE24 = 34008;
    public static final int GL_TEXTURE25 = 34009;
    public static final int GL_TEXTURE26 = 34010;
    public static final int GL_TEXTURE27 = 34011;
    public static final int GL_TEXTURE28 = 34012;
    public static final int GL_TEXTURE29 = 34013;
    public static final int GL_TEXTURE3 = 33987;
    public static final int GL_TEXTURE30 = 34014;
    public static final int GL_TEXTURE31 = 34015;
    public static final int GL_TEXTURE4 = 33988;
    public static final int GL_TEXTURE5 = 33989;
    public static final int GL_TEXTURE6 = 33990;
    public static final int GL_TEXTURE7 = 33991;
    public static final int GL_TEXTURE8 = 33992;
    public static final int GL_TEXTURE9 = 33993;
    public static final int GL_TEXTURE_BINDING_2D = 32873;
    public static final int GL_TEXTURE_BINDING_CUBE_MAP = 34068;
    public static final int GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 34070;
    public static final int GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 34072;
    public static final int GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 34074;
    public static final int GL_TEXTURE_CUBE_MAP_POSITIVE_X = 34069;
    public static final int GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 34071;
    public static final int GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 34073;
    public static final int GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING = 34975;
    public static final int GL_VERTEX_ATTRIB_ARRAY_ENABLED = 34338;
    public static final int GL_VERTEX_ATTRIB_ARRAY_NORMALIZED = 34922;
    public static final int GL_VERTEX_ATTRIB_ARRAY_POINTER = 34373;
    public static final int GL_VERTEX_ATTRIB_ARRAY_SIZE = 34339;
    public static final int GL_VERTEX_ATTRIB_ARRAY_STRIDE = 34340;
    public static final int GL_VERTEX_ATTRIB_ARRAY_TYPE = 34341;
}
