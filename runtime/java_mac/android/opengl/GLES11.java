package android.opengl;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
public class GLES11 extends GLES10 {
    protected GLES11() {}
    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_STATIC_DRAW = 0x88E4;

    // ================= AOSP GLES 1.1 entry points shared with GLES 2.0 =======
    // Same contract, and the same limitation, as the GLES10 block -- see the
    // long comment there before adding to or removing from this list.
    public static void glBindBuffer(int target, int buffer) { GLES20.glBindBuffer(target, buffer); }
    public static void glBufferData(int target, int size, Buffer data, int usage) { GLES20.glBufferData(target, size, data, usage); }
    public static void glBufferSubData(int target, int offset, int size, Buffer data) { GLES20.glBufferSubData(target, offset, size, data); }
    public static void glDeleteBuffers(int n, IntBuffer buffers) { GLES20.glDeleteBuffers(n, buffers); }
    public static void glDeleteBuffers(int n, int[] buffers, int offset) { GLES20.glDeleteBuffers(n, buffers, offset); }
    public static void glDrawElements(int mode, int count, int type, int offset) { GLES20.glDrawElements(mode, count, type, offset); }
    public static void glGenBuffers(int n, IntBuffer buffers) { GLES20.glGenBuffers(n, buffers); }
    public static void glGenBuffers(int n, int[] buffers, int offset) { GLES20.glGenBuffers(n, buffers, offset); }
    public static void glGetBooleanv(int pname, IntBuffer params) { GLES20.glGetBooleanv(pname, params); }
    public static void glGetBooleanv(int pname, boolean[] params, int offset) { GLES20.glGetBooleanv(pname, params, offset); }
    public static void glGetBufferParameteriv(int target, int pname, IntBuffer params) { GLES20.glGetBufferParameteriv(target, pname, params); }
    public static void glGetBufferParameteriv(int target, int pname, int[] params, int offset) { GLES20.glGetBufferParameteriv(target, pname, params, offset); }
    public static void glGetFloatv(int pname, FloatBuffer params) { GLES20.glGetFloatv(pname, params); }
    public static void glGetFloatv(int pname, float[] params, int offset) { GLES20.glGetFloatv(pname, params, offset); }
    public static void glGetTexParameterfv(int target, int pname, FloatBuffer params) { GLES20.glGetTexParameterfv(target, pname, params); }
    public static void glGetTexParameterfv(int target, int pname, float[] params, int offset) { GLES20.glGetTexParameterfv(target, pname, params, offset); }
    public static void glGetTexParameteriv(int target, int pname, IntBuffer params) { GLES20.glGetTexParameteriv(target, pname, params); }
    public static void glGetTexParameteriv(int target, int pname, int[] params, int offset) { GLES20.glGetTexParameteriv(target, pname, params, offset); }
    public static boolean glIsBuffer(int buffer) { return GLES20.glIsBuffer(buffer); }
    public static boolean glIsEnabled(int cap) { return GLES20.glIsEnabled(cap); }
    public static boolean glIsTexture(int texture) { return GLES20.glIsTexture(texture); }
    public static void glTexParameterfv(int target, int pname, FloatBuffer params) { GLES20.glTexParameterfv(target, pname, params); }
    public static void glTexParameterfv(int target, int pname, float[] params, int offset) { GLES20.glTexParameterfv(target, pname, params, offset); }
    public static void glTexParameteri(int target, int pname, int param) { GLES20.glTexParameteri(target, pname, param); }
    public static void glTexParameteriv(int target, int pname, IntBuffer params) { GLES20.glTexParameteriv(target, pname, params); }
    public static void glTexParameteriv(int target, int pname, int[] params, int offset) { GLES20.glTexParameteriv(target, pname, params, offset); }

    // ---- AOSP enum constants (values verbatim from the API-36 signature file) --
    // javac inlines a static-final int into the caller, so a MISSING one cannot
    // throw at runtime -- but it is still part of the class's declared surface: a
    // reflective lookup (engines that map GL enum names to values for logging or
    // for a scripting bridge) sees the difference, and a value we never declared
    // is a value we cannot recognise when an app passes it back in. Generated
    // from the signature file, sorted by name, no hand-typed hex.
    public static final int GL_ACTIVE_TEXTURE = 34016;
    public static final int GL_ADD_SIGNED = 34164;
    public static final int GL_ALPHA_SCALE = 3356;
    public static final int GL_ALPHA_TEST_FUNC = 3009;
    public static final int GL_ALPHA_TEST_REF = 3010;
    public static final int GL_ARRAY_BUFFER_BINDING = 34964;
    public static final int GL_BLEND_DST = 3040;
    public static final int GL_BLEND_SRC = 3041;
    public static final int GL_BUFFER_ACCESS = 35003;
    public static final int GL_BUFFER_SIZE = 34660;
    public static final int GL_BUFFER_USAGE = 34661;
    public static final int GL_CLIENT_ACTIVE_TEXTURE = 34017;
    public static final int GL_CLIP_PLANE0 = 12288;
    public static final int GL_CLIP_PLANE1 = 12289;
    public static final int GL_CLIP_PLANE2 = 12290;
    public static final int GL_CLIP_PLANE3 = 12291;
    public static final int GL_CLIP_PLANE4 = 12292;
    public static final int GL_CLIP_PLANE5 = 12293;
    public static final int GL_COLOR_ARRAY_BUFFER_BINDING = 34968;
    public static final int GL_COLOR_ARRAY_POINTER = 32912;
    public static final int GL_COLOR_ARRAY_SIZE = 32897;
    public static final int GL_COLOR_ARRAY_STRIDE = 32899;
    public static final int GL_COLOR_ARRAY_TYPE = 32898;
    public static final int GL_COLOR_CLEAR_VALUE = 3106;
    public static final int GL_COLOR_WRITEMASK = 3107;
    public static final int GL_COMBINE = 34160;
    public static final int GL_COMBINE_ALPHA = 34162;
    public static final int GL_COMBINE_RGB = 34161;
    public static final int GL_CONSTANT = 34166;
    public static final int GL_COORD_REPLACE_OES = 34914;
    public static final int GL_CULL_FACE_MODE = 2885;
    public static final int GL_CURRENT_COLOR = 2816;
    public static final int GL_CURRENT_NORMAL = 2818;
    public static final int GL_CURRENT_TEXTURE_COORDS = 2819;
    public static final int GL_DEPTH_CLEAR_VALUE = 2931;
    public static final int GL_DEPTH_FUNC = 2932;
    public static final int GL_DEPTH_RANGE = 2928;
    public static final int GL_DEPTH_WRITEMASK = 2930;
    public static final int GL_DOT3_RGB = 34478;
    public static final int GL_DOT3_RGBA = 34479;
    public static final int GL_DYNAMIC_DRAW = 35048;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 34963;
    public static final int GL_ELEMENT_ARRAY_BUFFER_BINDING = 34965;
    public static final int GL_FRONT_FACE = 2886;
    public static final int GL_GENERATE_MIPMAP = 33169;
    public static final int GL_GENERATE_MIPMAP_HINT = 33170;
    public static final int GL_INTERPOLATE = 34165;
    public static final int GL_LINE_WIDTH = 2849;
    public static final int GL_LOGIC_OP_MODE = 3056;
    public static final int GL_MATRIX_MODE = 2976;
    public static final int GL_MAX_CLIP_PLANES = 3378;
    public static final int GL_MODELVIEW_MATRIX = 2982;
    public static final int GL_MODELVIEW_MATRIX_FLOAT_AS_INT_BITS_OES = 35213;
    public static final int GL_MODELVIEW_STACK_DEPTH = 2979;
    public static final int GL_NORMAL_ARRAY_BUFFER_BINDING = 34967;
    public static final int GL_NORMAL_ARRAY_POINTER = 32911;
    public static final int GL_NORMAL_ARRAY_STRIDE = 32895;
    public static final int GL_NORMAL_ARRAY_TYPE = 32894;
    public static final int GL_OPERAND0_ALPHA = 34200;
    public static final int GL_OPERAND0_RGB = 34192;
    public static final int GL_OPERAND1_ALPHA = 34201;
    public static final int GL_OPERAND1_RGB = 34193;
    public static final int GL_OPERAND2_ALPHA = 34202;
    public static final int GL_OPERAND2_RGB = 34194;
    public static final int GL_POINT_DISTANCE_ATTENUATION = 33065;
    public static final int GL_POINT_FADE_THRESHOLD_SIZE = 33064;
    public static final int GL_POINT_SIZE = 2833;
    public static final int GL_POINT_SIZE_ARRAY_BUFFER_BINDING_OES = 35743;
    public static final int GL_POINT_SIZE_ARRAY_OES = 35740;
    public static final int GL_POINT_SIZE_ARRAY_POINTER_OES = 35212;
    public static final int GL_POINT_SIZE_ARRAY_STRIDE_OES = 35211;
    public static final int GL_POINT_SIZE_ARRAY_TYPE_OES = 35210;
    public static final int GL_POINT_SIZE_MAX = 33063;
    public static final int GL_POINT_SIZE_MIN = 33062;
    public static final int GL_POINT_SPRITE_OES = 34913;
    public static final int GL_POLYGON_OFFSET_FACTOR = 32824;
    public static final int GL_POLYGON_OFFSET_UNITS = 10752;
    public static final int GL_PREVIOUS = 34168;
    public static final int GL_PRIMARY_COLOR = 34167;
    public static final int GL_PROJECTION_MATRIX = 2983;
    public static final int GL_PROJECTION_MATRIX_FLOAT_AS_INT_BITS_OES = 35214;
    public static final int GL_PROJECTION_STACK_DEPTH = 2980;
    public static final int GL_RGB_SCALE = 34163;
    public static final int GL_SAMPLES = 32937;
    public static final int GL_SAMPLE_BUFFERS = 32936;
    public static final int GL_SAMPLE_COVERAGE_INVERT = 32939;
    public static final int GL_SAMPLE_COVERAGE_VALUE = 32938;
    public static final int GL_SCISSOR_BOX = 3088;
    public static final int GL_SHADE_MODEL = 2900;
    public static final int GL_SRC0_ALPHA = 34184;
    public static final int GL_SRC0_RGB = 34176;
    public static final int GL_SRC1_ALPHA = 34185;
    public static final int GL_SRC1_RGB = 34177;
    public static final int GL_SRC2_ALPHA = 34186;
    public static final int GL_SRC2_RGB = 34178;
    public static final int GL_STENCIL_CLEAR_VALUE = 2961;
    public static final int GL_STENCIL_FAIL = 2964;
    public static final int GL_STENCIL_FUNC = 2962;
    public static final int GL_STENCIL_PASS_DEPTH_FAIL = 2965;
    public static final int GL_STENCIL_PASS_DEPTH_PASS = 2966;
    public static final int GL_STENCIL_REF = 2967;
    public static final int GL_STENCIL_VALUE_MASK = 2963;
    public static final int GL_STENCIL_WRITEMASK = 2968;
    public static final int GL_SUBTRACT = 34023;
    public static final int GL_TEXTURE_BINDING_2D = 32873;
    public static final int GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING = 34970;
    public static final int GL_TEXTURE_COORD_ARRAY_POINTER = 32914;
    public static final int GL_TEXTURE_COORD_ARRAY_SIZE = 32904;
    public static final int GL_TEXTURE_COORD_ARRAY_STRIDE = 32906;
    public static final int GL_TEXTURE_COORD_ARRAY_TYPE = 32905;
    public static final int GL_TEXTURE_MATRIX = 2984;
    public static final int GL_TEXTURE_MATRIX_FLOAT_AS_INT_BITS_OES = 35215;
    public static final int GL_TEXTURE_STACK_DEPTH = 2981;
    public static final int GL_VERTEX_ARRAY_BUFFER_BINDING = 34966;
    public static final int GL_VERTEX_ARRAY_POINTER = 32910;
    public static final int GL_VERTEX_ARRAY_SIZE = 32890;
    public static final int GL_VERTEX_ARRAY_STRIDE = 32892;
    public static final int GL_VERTEX_ARRAY_TYPE = 32891;
    public static final int GL_VIEWPORT = 2978;
    public static final int GL_WRITE_ONLY = 35001;

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
    public static native void glClipPlanef(int a0, java.nio.FloatBuffer a1);
    public static native void glClipPlanef(int a0, float[] a1, int a2);
    public static native void glClipPlanex(int a0, java.nio.IntBuffer a1);
    public static native void glClipPlanex(int a0, int[] a1, int a2);
    public static native void glColor4ub(byte a0, byte a1, byte a2, byte a3);
    public static native void glColorPointer(int a0, int a1, int a2, int a3);
    public static native void glGetClipPlanef(int a0, java.nio.FloatBuffer a1);
    public static native void glGetClipPlanef(int a0, float[] a1, int a2);
    public static native void glGetClipPlanex(int a0, java.nio.IntBuffer a1);
    public static native void glGetClipPlanex(int a0, int[] a1, int a2);
    public static native void glGetFixedv(int a0, java.nio.IntBuffer a1);
    public static native void glGetFixedv(int a0, int[] a1, int a2);
    public static native void glGetLightfv(int a0, int a1, java.nio.FloatBuffer a2);
    public static native void glGetLightfv(int a0, int a1, float[] a2, int a3);
    public static native void glGetLightxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glGetLightxv(int a0, int a1, int[] a2, int a3);
    public static native void glGetMaterialfv(int a0, int a1, java.nio.FloatBuffer a2);
    public static native void glGetMaterialfv(int a0, int a1, float[] a2, int a3);
    public static native void glGetMaterialxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glGetMaterialxv(int a0, int a1, int[] a2, int a3);
    public static native void glGetTexEnvfv(int a0, int a1, java.nio.FloatBuffer a2);
    public static native void glGetTexEnvfv(int a0, int a1, float[] a2, int a3);
    public static native void glGetTexEnviv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glGetTexEnviv(int a0, int a1, int[] a2, int a3);
    public static native void glGetTexEnvxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glGetTexEnvxv(int a0, int a1, int[] a2, int a3);
    public static native void glGetTexParameterxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glGetTexParameterxv(int a0, int a1, int[] a2, int a3);
    public static native void glNormalPointer(int a0, int a1, int a2);
    public static native void glPointParameterf(int a0, float a1);
    public static native void glPointParameterfv(int a0, java.nio.FloatBuffer a1);
    public static native void glPointParameterfv(int a0, float[] a1, int a2);
    public static native void glPointParameterx(int a0, int a1);
    public static native void glPointParameterxv(int a0, java.nio.IntBuffer a1);
    public static native void glPointParameterxv(int a0, int[] a1, int a2);
    public static native void glPointSizePointerOES(int a0, int a1, java.nio.Buffer a2);
    public static native void glTexCoordPointer(int a0, int a1, int a2, int a3);
    public static native void glTexEnvi(int a0, int a1, int a2);
    public static native void glTexEnviv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glTexEnviv(int a0, int a1, int[] a2, int a3);
    public static native void glTexParameterxv(int a0, int a1, java.nio.IntBuffer a2);
    public static native void glTexParameterxv(int a0, int a1, int[] a2, int a3);
    public static native void glVertexPointer(int a0, int a1, int a2, int a3);
}
