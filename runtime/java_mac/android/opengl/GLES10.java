// android.opengl.GLES10 -- legacy fixed-function GLES1.0 binding. We
// expose the constants AndEngine + apps reference as type tokens; the
// methods are no-ops for now (no app in our corpus uses real GLES1
// rendering on top of v2).
package android.opengl;

import java.nio.Buffer;
import java.nio.IntBuffer;

public class GLES10 {
    protected GLES10() {}
    public static final int GL_DEPTH_BUFFER_BIT   = 0x0100;
    public static final int GL_COLOR_BUFFER_BIT   = 0x4000;
    public static final int GL_TRIANGLES = 4;
    public static final int GL_TRIANGLE_STRIP = 5;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_BLEND      = 0xBE2;
    public static final int GL_FLOAT      = 0x1406;
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_UNSIGNED_SHORT = 0x1403;
    public static final int GL_RGB        = 0x1907;
    public static final int GL_RGBA       = 0x1908;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_LINEAR     = 0x2601;
    public static final int GL_NEAREST    = 0x2600;
    // These three were empty bodies until v1.0.836, so a genuine GLES1 app
    // never cleared and never set a viewport -- a black window with a clean
    // log, the same silent shape as the GLES20 no-ops
    // (docs/gles20-noop-sweep_2026-07-29.md). glClearColor / glClear /
    // glViewport carry IDENTICAL semantics in GLES1 and GLES2, so delegating
    // to the GLES20 natives is exact, not an approximation. Pure Java: no
    // RegisterNatives entry, so nothing here can fail to bind.
    public static void glClearColor(float r, float g, float b, float a) { GLES20.glClearColor(r, g, b, a); }
    public static void glClear(int mask) { GLES20.glClear(mask); }
    public static void glViewport(int x, int y, int w, int h) { GLES20.glViewport(x, y, w, h); }

    // ================= AOSP GLES 1.0 entry points shared with GLES 2.0 =======
    // Added 2026-08-24. EVERY method below has IDENTICAL name, signature and
    // semantics in the GLES 1.0 and GLES 2.0 specifications, so forwarding to
    // the already-registered GLES20 native is a DELEGATION, not an
    // approximation -- the same reasoning the glClear / glClearColor /
    // glViewport trio above already carries (v1.0.836).
    //
    // Pure Java: no `native` declaration, so nothing here can miss a
    // RegisterNatives row (debug/jni_registration_gate.sh).
    //
    // ⚠ WHAT THIS DOES **NOT** GIVE YOU. The GLES1 FIXED-FUNCTION pipeline
    // (glMatrixMode/glLoadIdentity/glRotatef, glVertexPointer/glColorPointer,
    // glLight*/glMaterial*/glFog*/glTexEnv*, every `x` fixed-point variant) has
    // no GLES2 equivalent and is NOT here -- an app that uses it still hits
    // NoSuchMethodError at the first such call. ANGLE's libGLESv2.dylib DOES
    // export the whole GLES1 entry-point set (verified: glMatrixMode,
    // glLoadIdentity, glAlphaFunc, glFogf, glLightf, glTexEnvf, glVertexPointer,
    // glOrthof, glPushMatrix, glRotatef, glClearDepthx, glColor4x are all
    // defined in thirdparty/angle/out/Release/libGLESv2.dylib), so the correct
    // fix is a native bridge to those -- plus an EGL context created with
    // EGL_CONTEXT_CLIENT_VERSION 1, because ANGLE rejects GLES1 entry points on
    // an ES2 context. Both halves live in runtime/src/, which this lane does not
    // own. When they land, DELETE the delegations below in the same change so
    // there is one dispatch path rather than two.
    //
    // Note also that a state query with an ES1-only pname (GL_MODELVIEW_MATRIX,
    // GL_TEXTURE_2D as an enable target, ...) will raise GL_INVALID_ENUM on the
    // ES2 context. That is an honest failure the app can see through
    // glGetError(), not a fabricated answer.
    public static void glActiveTexture(int texture) { GLES20.glActiveTexture(texture); }
    public static void glBindTexture(int target, int texture) { GLES20.glBindTexture(target, texture); }
    public static void glBlendFunc(int sfactor, int dfactor) { GLES20.glBlendFunc(sfactor, dfactor); }
    public static void glClearDepthf(float depth) { GLES20.glClearDepthf(depth); }
    public static void glClearStencil(int s) { GLES20.glClearStencil(s); }
    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) { GLES20.glColorMask(red, green, blue, alpha); }
    public static void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data) { GLES20.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data); }
    public static void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data) { GLES20.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data); }
    public static void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) { GLES20.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border); }
    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) { GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height); }
    public static void glCullFace(int mode) { GLES20.glCullFace(mode); }
    public static void glDeleteTextures(int n, IntBuffer textures) { GLES20.glDeleteTextures(n, textures); }
    public static void glDeleteTextures(int n, int[] textures, int offset) { GLES20.glDeleteTextures(n, textures, offset); }
    public static void glDepthFunc(int func) { GLES20.glDepthFunc(func); }
    public static void glDepthMask(boolean flag) { GLES20.glDepthMask(flag); }
    public static void glDepthRangef(float zNear, float zFar) { GLES20.glDepthRangef(zNear, zFar); }
    public static void glDisable(int cap) { GLES20.glDisable(cap); }
    public static void glDrawArrays(int mode, int first, int count) { GLES20.glDrawArrays(mode, first, count); }
    public static void glDrawElements(int mode, int count, int type, Buffer indices) { GLES20.glDrawElements(mode, count, type, indices); }
    public static void glEnable(int cap) { GLES20.glEnable(cap); }
    public static void glFinish() { GLES20.glFinish(); }
    public static void glFlush() { GLES20.glFlush(); }
    public static void glFrontFace(int mode) { GLES20.glFrontFace(mode); }
    public static void glGenTextures(int n, IntBuffer textures) { GLES20.glGenTextures(n, textures); }
    public static void glGenTextures(int n, int[] textures, int offset) { GLES20.glGenTextures(n, textures, offset); }
    public static int glGetError() { return GLES20.glGetError(); }
    public static void glGetIntegerv(int pname, IntBuffer params) { GLES20.glGetIntegerv(pname, params); }
    public static void glGetIntegerv(int pname, int[] params, int offset) { GLES20.glGetIntegerv(pname, params, offset); }
    public static String glGetString(int name) { return GLES20.glGetString(name); }
    public static void glHint(int target, int mode) { GLES20.glHint(target, mode); }
    public static void glLineWidth(float width) { GLES20.glLineWidth(width); }
    public static void glPixelStorei(int pname, int param) { GLES20.glPixelStorei(pname, param); }
    public static void glPolygonOffset(float factor, float units) { GLES20.glPolygonOffset(factor, units); }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels) { GLES20.glReadPixels(x, y, width, height, format, type, pixels); }
    public static void glSampleCoverage(float value, boolean invert) { GLES20.glSampleCoverage(value, invert); }
    public static void glScissor(int x, int y, int width, int height) { GLES20.glScissor(x, y, width, height); }
    public static void glStencilFunc(int func, int ref, int mask) { GLES20.glStencilFunc(func, ref, mask); }
    public static void glStencilMask(int mask) { GLES20.glStencilMask(mask); }
    public static void glStencilOp(int fail, int zfail, int zpass) { GLES20.glStencilOp(fail, zfail, zpass); }
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels) { GLES20.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels); }
    public static void glTexParameterf(int target, int pname, float param) { GLES20.glTexParameterf(target, pname, param); }
    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels) { GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels); }

    // ---- GLfixed variants of entry points GLES 2.0 also has -----------------
    // OpenGL ES 1.1 spec section 2.1.2: a GLfixed value x denotes the real
    // number x / 2^16 (16.16 fixed point). So each of these is EXACTLY its
    // float sibling with the argument divided by 65536 -- a unit conversion,
    // not an approximation. `x` variants whose parameter is an ENUM rather than
    // a number (glTexParameterx, glAlphaFuncx's func, ...) are deliberately NOT
    // here: scaling an enum would be silently wrong. See the block comment
    // further down about the rest of the GLES1 surface.
    private static final float MACAPK_FIXED_ONE = 65536.0f;

    public static void glClearColorx(int red, int green, int blue, int alpha) {
        GLES20.glClearColor(red / MACAPK_FIXED_ONE, green / MACAPK_FIXED_ONE,
                            blue / MACAPK_FIXED_ONE, alpha / MACAPK_FIXED_ONE);
    }
    public static void glClearDepthx(int depth) {
        GLES20.glClearDepthf(depth / MACAPK_FIXED_ONE);
    }
    public static void glDepthRangex(int zNear, int zFar) {
        GLES20.glDepthRangef(zNear / MACAPK_FIXED_ONE, zFar / MACAPK_FIXED_ONE);
    }
    public static void glLineWidthx(int width) {
        GLES20.glLineWidth(width / MACAPK_FIXED_ONE);
    }
    public static void glPolygonOffsetx(int factor, int units) {
        GLES20.glPolygonOffset(factor / MACAPK_FIXED_ONE, units / MACAPK_FIXED_ONE);
    }
    public static void glSampleCoveragex(int value, boolean invert) {
        GLES20.glSampleCoverage(value / MACAPK_FIXED_ONE, invert);
    }

    // ---- AOSP enum constants (values verbatim from the API-36 signature file) --
    // javac inlines a static-final int into the caller, so a MISSING one cannot
    // throw at runtime -- but it is still part of the class's declared surface: a
    // reflective lookup (engines that map GL enum names to values for logging or
    // for a scripting bridge) sees the difference, and a value we never declared
    // is a value we cannot recognise when an app passes it back in. Generated
    // from the signature file, sorted by name, no hand-typed hex.
    public static final int GL_ADD = 260;
    public static final int GL_ALIASED_LINE_WIDTH_RANGE = 33902;
    public static final int GL_ALIASED_POINT_SIZE_RANGE = 33901;
    public static final int GL_ALPHA = 6406;
    public static final int GL_ALPHA_BITS = 3413;
    public static final int GL_ALPHA_TEST = 3008;
    public static final int GL_ALWAYS = 519;
    public static final int GL_AMBIENT = 4608;
    public static final int GL_AMBIENT_AND_DIFFUSE = 5634;
    public static final int GL_AND = 5377;
    public static final int GL_AND_INVERTED = 5380;
    public static final int GL_AND_REVERSE = 5378;
    public static final int GL_BACK = 1029;
    public static final int GL_BLUE_BITS = 3412;
    public static final int GL_BYTE = 5120;
    public static final int GL_CCW = 2305;
    public static final int GL_CLAMP_TO_EDGE = 33071;
    public static final int GL_CLEAR = 5376;
    public static final int GL_COLOR_ARRAY = 32886;
    public static final int GL_COLOR_LOGIC_OP = 3058;
    public static final int GL_COLOR_MATERIAL = 2903;
    public static final int GL_COMPRESSED_TEXTURE_FORMATS = 34467;
    public static final int GL_CONSTANT_ATTENUATION = 4615;
    public static final int GL_COPY = 5379;
    public static final int GL_COPY_INVERTED = 5388;
    public static final int GL_CULL_FACE = 2884;
    public static final int GL_CW = 2304;
    public static final int GL_DECAL = 8449;
    public static final int GL_DECR = 7683;
    public static final int GL_DEPTH_BITS = 3414;
    public static final int GL_DEPTH_TEST = 2929;
    public static final int GL_DIFFUSE = 4609;
    public static final int GL_DITHER = 3024;
    public static final int GL_DONT_CARE = 4352;
    public static final int GL_DST_ALPHA = 772;
    public static final int GL_DST_COLOR = 774;
    public static final int GL_EMISSION = 5632;
    public static final int GL_EQUAL = 514;
    public static final int GL_EQUIV = 5385;
    public static final int GL_EXP = 2048;
    public static final int GL_EXP2 = 2049;
    public static final int GL_EXTENSIONS = 7939;
    public static final int GL_FALSE = 0;
    public static final int GL_FASTEST = 4353;
    public static final int GL_FIXED = 5132;
    public static final int GL_FLAT = 7424;
    public static final int GL_FOG = 2912;
    public static final int GL_FOG_COLOR = 2918;
    public static final int GL_FOG_DENSITY = 2914;
    public static final int GL_FOG_END = 2916;
    public static final int GL_FOG_HINT = 3156;
    public static final int GL_FOG_MODE = 2917;
    public static final int GL_FOG_START = 2915;
    public static final int GL_FRONT = 1028;
    public static final int GL_FRONT_AND_BACK = 1032;
    public static final int GL_GEQUAL = 518;
    public static final int GL_GREATER = 516;
    public static final int GL_GREEN_BITS = 3411;
    public static final int GL_IMPLEMENTATION_COLOR_READ_FORMAT_OES = 35739;
    public static final int GL_IMPLEMENTATION_COLOR_READ_TYPE_OES = 35738;
    public static final int GL_INCR = 7682;
    public static final int GL_INVALID_ENUM = 1280;
    public static final int GL_INVALID_OPERATION = 1282;
    public static final int GL_INVALID_VALUE = 1281;
    public static final int GL_INVERT = 5386;
    public static final int GL_KEEP = 7680;
    public static final int GL_LEQUAL = 515;
    public static final int GL_LESS = 513;
    public static final int GL_LIGHT0 = 16384;
    public static final int GL_LIGHT1 = 16385;
    public static final int GL_LIGHT2 = 16386;
    public static final int GL_LIGHT3 = 16387;
    public static final int GL_LIGHT4 = 16388;
    public static final int GL_LIGHT5 = 16389;
    public static final int GL_LIGHT6 = 16390;
    public static final int GL_LIGHT7 = 16391;
    public static final int GL_LIGHTING = 2896;
    public static final int GL_LIGHT_MODEL_AMBIENT = 2899;
    public static final int GL_LIGHT_MODEL_TWO_SIDE = 2898;
    public static final int GL_LINEAR_ATTENUATION = 4616;
    public static final int GL_LINEAR_MIPMAP_LINEAR = 9987;
    public static final int GL_LINEAR_MIPMAP_NEAREST = 9985;
    public static final int GL_LINES = 1;
    public static final int GL_LINE_LOOP = 2;
    public static final int GL_LINE_SMOOTH = 2848;
    public static final int GL_LINE_SMOOTH_HINT = 3154;
    public static final int GL_LINE_STRIP = 3;
    public static final int GL_LUMINANCE = 6409;
    public static final int GL_LUMINANCE_ALPHA = 6410;
    public static final int GL_MAX_ELEMENTS_INDICES = 33001;
    public static final int GL_MAX_ELEMENTS_VERTICES = 33000;
    public static final int GL_MAX_LIGHTS = 3377;
    public static final int GL_MAX_MODELVIEW_STACK_DEPTH = 3382;
    public static final int GL_MAX_PROJECTION_STACK_DEPTH = 3384;
    public static final int GL_MAX_TEXTURE_SIZE = 3379;
    public static final int GL_MAX_TEXTURE_STACK_DEPTH = 3385;
    public static final int GL_MAX_TEXTURE_UNITS = 34018;
    public static final int GL_MAX_VIEWPORT_DIMS = 3386;
    public static final int GL_MODELVIEW = 5888;
    public static final int GL_MODULATE = 8448;
    public static final int GL_MULTISAMPLE = 32925;
    public static final int GL_NAND = 5390;
    public static final int GL_NEAREST_MIPMAP_LINEAR = 9986;
    public static final int GL_NEAREST_MIPMAP_NEAREST = 9984;
    public static final int GL_NEVER = 512;
    public static final int GL_NICEST = 4354;
    public static final int GL_NOOP = 5381;
    public static final int GL_NOR = 5384;
    public static final int GL_NORMALIZE = 2977;
    public static final int GL_NORMAL_ARRAY = 32885;
    public static final int GL_NOTEQUAL = 517;
    public static final int GL_NO_ERROR = 0;
    public static final int GL_NUM_COMPRESSED_TEXTURE_FORMATS = 34466;
    public static final int GL_ONE = 1;
    public static final int GL_ONE_MINUS_DST_ALPHA = 773;
    public static final int GL_ONE_MINUS_DST_COLOR = 775;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 771;
    public static final int GL_ONE_MINUS_SRC_COLOR = 769;
    public static final int GL_OR = 5383;
    public static final int GL_OR_INVERTED = 5389;
    public static final int GL_OR_REVERSE = 5387;
    public static final int GL_OUT_OF_MEMORY = 1285;
    public static final int GL_PACK_ALIGNMENT = 3333;
    public static final int GL_PALETTE4_R5_G6_B5_OES = 35730;
    public static final int GL_PALETTE4_RGB5_A1_OES = 35732;
    public static final int GL_PALETTE4_RGB8_OES = 35728;
    public static final int GL_PALETTE4_RGBA4_OES = 35731;
    public static final int GL_PALETTE4_RGBA8_OES = 35729;
    public static final int GL_PALETTE8_R5_G6_B5_OES = 35735;
    public static final int GL_PALETTE8_RGB5_A1_OES = 35737;
    public static final int GL_PALETTE8_RGB8_OES = 35733;
    public static final int GL_PALETTE8_RGBA4_OES = 35736;
    public static final int GL_PALETTE8_RGBA8_OES = 35734;
    public static final int GL_PERSPECTIVE_CORRECTION_HINT = 3152;
    public static final int GL_POINTS = 0;
    public static final int GL_POINT_FADE_THRESHOLD_SIZE = 33064;
    public static final int GL_POINT_SIZE = 2833;
    public static final int GL_POINT_SMOOTH = 2832;
    public static final int GL_POINT_SMOOTH_HINT = 3153;
    public static final int GL_POLYGON_OFFSET_FILL = 32823;
    public static final int GL_POLYGON_SMOOTH_HINT = 3155;
    public static final int GL_POSITION = 4611;
    public static final int GL_PROJECTION = 5889;
    public static final int GL_QUADRATIC_ATTENUATION = 4617;
    public static final int GL_RED_BITS = 3410;
    public static final int GL_RENDERER = 7937;
    public static final int GL_REPEAT = 10497;
    public static final int GL_REPLACE = 7681;
    public static final int GL_RESCALE_NORMAL = 32826;
    public static final int GL_SAMPLE_ALPHA_TO_COVERAGE = 32926;
    public static final int GL_SAMPLE_ALPHA_TO_ONE = 32927;
    public static final int GL_SAMPLE_COVERAGE = 32928;
    public static final int GL_SCISSOR_TEST = 3089;
    public static final int GL_SET = 5391;
    public static final int GL_SHININESS = 5633;
    public static final int GL_SHORT = 5122;
    public static final int GL_SMOOTH = 7425;
    public static final int GL_SMOOTH_LINE_WIDTH_RANGE = 2850;
    public static final int GL_SMOOTH_POINT_SIZE_RANGE = 2834;
    public static final int GL_SPECULAR = 4610;
    public static final int GL_SPOT_CUTOFF = 4614;
    public static final int GL_SPOT_DIRECTION = 4612;
    public static final int GL_SPOT_EXPONENT = 4613;
    public static final int GL_SRC_ALPHA = 770;
    public static final int GL_SRC_ALPHA_SATURATE = 776;
    public static final int GL_SRC_COLOR = 768;
    public static final int GL_STACK_OVERFLOW = 1283;
    public static final int GL_STACK_UNDERFLOW = 1284;
    public static final int GL_STENCIL_BITS = 3415;
    public static final int GL_STENCIL_BUFFER_BIT = 1024;
    public static final int GL_STENCIL_TEST = 2960;
    public static final int GL_SUBPIXEL_BITS = 3408;
    public static final int GL_TEXTURE = 5890;
    public static final int GL_TEXTURE0 = 33984;
    public static final int GL_TEXTURE1 = 33985;
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
    public static final int GL_TEXTURE_COORD_ARRAY = 32888;
    public static final int GL_TEXTURE_ENV = 8960;
    public static final int GL_TEXTURE_ENV_COLOR = 8705;
    public static final int GL_TEXTURE_ENV_MODE = 8704;
    public static final int GL_TEXTURE_WRAP_S = 10242;
    public static final int GL_TEXTURE_WRAP_T = 10243;
    public static final int GL_TRIANGLE_FAN = 6;
    public static final int GL_TRUE = 1;
    public static final int GL_UNPACK_ALIGNMENT = 3317;
    public static final int GL_UNSIGNED_SHORT_4_4_4_4 = 32819;
    public static final int GL_UNSIGNED_SHORT_5_5_5_1 = 32820;
    public static final int GL_UNSIGNED_SHORT_5_6_5 = 33635;
    public static final int GL_VENDOR = 7936;
    public static final int GL_VERSION = 7938;
    public static final int GL_VERTEX_ARRAY = 32884;
    public static final int GL_XOR = 5382;
    public static final int GL_ZERO = 0;

    // ---- GLES1 fixed-function natives ------------------------------------
    // Bridged to ANGLE's GLES1 entry points by runtime/src/gles1_bridges.inc
    // (generated by debug/gles_gen/gen_gles1.py). These have NO GLES2
    // equivalent, which is why they could not be emulated in Java.
    // ⚠ The java.nio parameter types are the SPECIFIC buffer classes, not
    // java.nio.Buffer: RegisterNatives matches on name+DESCRIPTOR, so a
    // widened parameter type silently fails to bind and aborts at boot.
    // ⚠ They only DO anything on an ES1 context; main.cpp scans the guest's
    // native libs for ES1 imports and angle_glue.cpp asks for
    // EGL_CONTEXT_CLIENT_VERSION 1 accordingly.
    public static native void glAlphaFunc(int a0, float a1);
    public static native void glAlphaFuncx(int a0, int a1);
    public static native void glClientActiveTexture(int a0);
    public static native void glColor4f(float a0, float a1, float a2, float a3);
    public static native void glColor4x(int a0, int a1, int a2, int a3);
    public static native void glColorPointer(int a0, int a1, int a2, java.nio.Buffer a3);
    public static native void glDisableClientState(int a0);
    public static native void glEnableClientState(int a0);
    public static native void glFogf(int a0, float a1);
    public static native void glFogfv(int a0, java.nio.FloatBuffer a1);
    public static native void glFogfv(int a0, float[] a1, int a2);
    public static native void glFogx(int a0, int a1);
    public static native void glFogxv(int a0, java.nio.IntBuffer a1);
    public static native void glFogxv(int a0, int[] a1, int a2);
    public static native void glFrustumf(float a0, float a1, float a2, float a3, float a4, float a5);
    public static native void glFrustumx(int a0, int a1, int a2, int a3, int a4, int a5);
    public static native void glLightModelf(int a0, float a1);
    public static native void glLightModelfv(int a0, java.nio.FloatBuffer a1);
    public static native void glLightModelfv(int a0, float[] a1, int a2);
    public static native void glLightModelx(int a0, int a1);
    public static native void glLightModelxv(int a0, java.nio.IntBuffer a1);
    public static native void glLightModelxv(int a0, int[] a1, int a2);
    public static native void glLightf(int a0, int a1, float a2);
    public static native void glLightfv(int a0, int a1, java.nio.FloatBuffer a2);
    public static native void glLightfv(int a0, int a1, float[] a2, int a3);
    public static native void glLightx(int a0, int a1, int a2);
    public static native void glLightxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glLightxv(int a0, int a1, int[] a2, int a3);
    public static native void glLoadIdentity();
    public static native void glLoadMatrixf(java.nio.FloatBuffer a0);
    public static native void glLoadMatrixf(float[] a0, int a1);
    public static native void glLoadMatrixx(java.nio.IntBuffer a0);
    public static native void glLoadMatrixx(int[] a0, int a1);
    public static native void glLogicOp(int a0);
    public static native void glMaterialf(int a0, int a1, float a2);
    public static native void glMaterialfv(int a0, int a1, java.nio.FloatBuffer a2);
    public static native void glMaterialfv(int a0, int a1, float[] a2, int a3);
    public static native void glMaterialx(int a0, int a1, int a2);
    public static native void glMaterialxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glMaterialxv(int a0, int a1, int[] a2, int a3);
    public static native void glMatrixMode(int a0);
    public static native void glMultMatrixf(java.nio.FloatBuffer a0);
    public static native void glMultMatrixf(float[] a0, int a1);
    public static native void glMultMatrixx(java.nio.IntBuffer a0);
    public static native void glMultMatrixx(int[] a0, int a1);
    public static native void glMultiTexCoord4f(int a0, float a1, float a2, float a3, float a4);
    public static native void glMultiTexCoord4x(int a0, int a1, int a2, int a3, int a4);
    public static native void glNormal3f(float a0, float a1, float a2);
    public static native void glNormal3x(int a0, int a1, int a2);
    public static native void glNormalPointer(int a0, int a1, java.nio.Buffer a2);
    public static native void glOrthof(float a0, float a1, float a2, float a3, float a4, float a5);
    public static native void glOrthox(int a0, int a1, int a2, int a3, int a4, int a5);
    public static native void glPointSize(float a0);
    public static native void glPointSizex(int a0);
    public static native void glPopMatrix();
    public static native void glPushMatrix();
    public static native void glRotatef(float a0, float a1, float a2, float a3);
    public static native void glRotatex(int a0, int a1, int a2, int a3);
    public static native void glScalef(float a0, float a1, float a2);
    public static native void glScalex(int a0, int a1, int a2);
    public static native void glShadeModel(int a0);
    public static native void glTexCoordPointer(int a0, int a1, int a2, java.nio.Buffer a3);
    public static native void glTexEnvf(int a0, int a1, float a2);
    public static native void glTexEnvfv(int a0, int a1, java.nio.FloatBuffer a2);
    public static native void glTexEnvfv(int a0, int a1, float[] a2, int a3);
    public static native void glTexEnvx(int a0, int a1, int a2);
    public static native void glTexEnvxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glTexEnvxv(int a0, int a1, int[] a2, int a3);
    public static native void glTexParameterx(int a0, int a1, int a2);
    public static native void glTranslatef(float a0, float a1, float a2);
    public static native void glTranslatex(int a0, int a1, int a2);
    public static native void glVertexPointer(int a0, int a1, int a2, java.nio.Buffer a3);
}
