// javax.microedition.khronos.opengles.GL11 -- the GLES 1.1 binding. AOSP:
// frameworks/base/opengl/java/javax/microedition/khronos/opengles/GL11.java
//
// Declared for the same reason GL10 is (see that file): apps reach these
// through the GL handle GLSurfaceView.Renderer receives, and the classic
// engine idiom is `if (gl instanceof GL11) { ...VBO path... }`. A missing
// interface is a NoClassDefFoundError at class preparation, before any method
// is called -- so the type has to exist even for the branch that is not taken.
//
// ⚠ NOTHING IN THIS TREE IMPLEMENTS IT YET. android.opengl.GLSurfaceView's
// StubGL10 implements GL10 only, so `gl instanceof GL11` answers false and
// engines take their GL10 path -- correct, and the same thing a GLES-1.0-only
// device does. An app that casts UNCONDITIONALLY still gets a
// ClassCastException; closing that means widening StubGL10, which lives in
// android/opengl/.
// TRANSCRIBED MECHANICALLY from AOSP core/api/current.txt (android16),
// so the method set and the constant VALUES are exactly the platform's.
// Parameter names are positional (a0, a1, ...) because the signature file
// does not carry them; they are not part of any binary contract.
package javax.microedition.khronos.opengles;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface GL11 extends GL10 {
    void glBindBuffer(int a0, int a1);
    void glBufferData(int a0, int a1, Buffer a2, int a3);
    void glBufferSubData(int a0, int a1, int a2, Buffer a3);
    void glClipPlanef(int a0, float[] a1, int a2);
    void glClipPlanef(int a0, FloatBuffer a1);
    void glClipPlanex(int a0, int[] a1, int a2);
    void glClipPlanex(int a0, IntBuffer a1);
    void glColor4ub(byte a0, byte a1, byte a2, byte a3);
    void glColorPointer(int a0, int a1, int a2, int a3);
    void glDeleteBuffers(int a0, int[] a1, int a2);
    void glDeleteBuffers(int a0, IntBuffer a1);
    void glDrawElements(int a0, int a1, int a2, int a3);
    void glGenBuffers(int a0, int[] a1, int a2);
    void glGenBuffers(int a0, IntBuffer a1);
    void glGetBooleanv(int a0, boolean[] a1, int a2);
    void glGetBooleanv(int a0, IntBuffer a1);
    void glGetBufferParameteriv(int a0, int a1, int[] a2, int a3);
    void glGetBufferParameteriv(int a0, int a1, IntBuffer a2);
    void glGetClipPlanef(int a0, float[] a1, int a2);
    void glGetClipPlanef(int a0, FloatBuffer a1);
    void glGetClipPlanex(int a0, int[] a1, int a2);
    void glGetClipPlanex(int a0, IntBuffer a1);
    void glGetFixedv(int a0, int[] a1, int a2);
    void glGetFixedv(int a0, IntBuffer a1);
    void glGetFloatv(int a0, float[] a1, int a2);
    void glGetFloatv(int a0, FloatBuffer a1);
    void glGetLightfv(int a0, int a1, float[] a2, int a3);
    void glGetLightfv(int a0, int a1, FloatBuffer a2);
    void glGetLightxv(int a0, int a1, int[] a2, int a3);
    void glGetLightxv(int a0, int a1, IntBuffer a2);
    void glGetMaterialfv(int a0, int a1, float[] a2, int a3);
    void glGetMaterialfv(int a0, int a1, FloatBuffer a2);
    void glGetMaterialxv(int a0, int a1, int[] a2, int a3);
    void glGetMaterialxv(int a0, int a1, IntBuffer a2);
    void glGetPointerv(int a0, Buffer[] a1);
    void glGetTexEnviv(int a0, int a1, int[] a2, int a3);
    void glGetTexEnviv(int a0, int a1, IntBuffer a2);
    void glGetTexEnvxv(int a0, int a1, int[] a2, int a3);
    void glGetTexEnvxv(int a0, int a1, IntBuffer a2);
    void glGetTexParameterfv(int a0, int a1, float[] a2, int a3);
    void glGetTexParameterfv(int a0, int a1, FloatBuffer a2);
    void glGetTexParameteriv(int a0, int a1, int[] a2, int a3);
    void glGetTexParameteriv(int a0, int a1, IntBuffer a2);
    void glGetTexParameterxv(int a0, int a1, int[] a2, int a3);
    void glGetTexParameterxv(int a0, int a1, IntBuffer a2);
    boolean glIsBuffer(int a0);
    boolean glIsEnabled(int a0);
    boolean glIsTexture(int a0);
    void glNormalPointer(int a0, int a1, int a2);
    void glPointParameterf(int a0, float a1);
    void glPointParameterfv(int a0, float[] a1, int a2);
    void glPointParameterfv(int a0, FloatBuffer a1);
    void glPointParameterx(int a0, int a1);
    void glPointParameterxv(int a0, int[] a1, int a2);
    void glPointParameterxv(int a0, IntBuffer a1);
    void glPointSizePointerOES(int a0, int a1, Buffer a2);
    void glTexCoordPointer(int a0, int a1, int a2, int a3);
    void glTexEnvi(int a0, int a1, int a2);
    void glTexEnviv(int a0, int a1, int[] a2, int a3);
    void glTexEnviv(int a0, int a1, IntBuffer a2);
    void glTexParameterfv(int a0, int a1, float[] a2, int a3);
    void glTexParameterfv(int a0, int a1, FloatBuffer a2);
    void glTexParameteri(int a0, int a1, int a2);
    void glTexParameteriv(int a0, int a1, int[] a2, int a3);
    void glTexParameteriv(int a0, int a1, IntBuffer a2);
    void glTexParameterxv(int a0, int a1, int[] a2, int a3);
    void glTexParameterxv(int a0, int a1, IntBuffer a2);
    void glVertexPointer(int a0, int a1, int a2, int a3);
    int GL_ACTIVE_TEXTURE = 0x84E0;
    int GL_ADD_SIGNED = 0x8574;
    int GL_ALPHA_SCALE = 0x0D1C;
    int GL_ALPHA_TEST_FUNC = 0x0BC1;
    int GL_ALPHA_TEST_REF = 0x0BC2;
    int GL_ARRAY_BUFFER = 0x8892;
    int GL_ARRAY_BUFFER_BINDING = 0x8894;
    int GL_BLEND_DST = 0x0BE0;
    int GL_BLEND_SRC = 0x0BE1;
    int GL_BUFFER_ACCESS = 0x88BB;
    int GL_BUFFER_SIZE = 0x8764;
    int GL_BUFFER_USAGE = 0x8765;
    int GL_CLIENT_ACTIVE_TEXTURE = 0x84E1;
    int GL_CLIP_PLANE0 = 0x3000;
    int GL_CLIP_PLANE1 = 0x3001;
    int GL_CLIP_PLANE2 = 0x3002;
    int GL_CLIP_PLANE3 = 0x3003;
    int GL_CLIP_PLANE4 = 0x3004;
    int GL_CLIP_PLANE5 = 0x3005;
    int GL_COLOR_ARRAY_BUFFER_BINDING = 0x8898;
    int GL_COLOR_ARRAY_POINTER = 0x8090;
    int GL_COLOR_ARRAY_SIZE = 0x8081;
    int GL_COLOR_ARRAY_STRIDE = 0x8083;
    int GL_COLOR_ARRAY_TYPE = 0x8082;
    int GL_COLOR_CLEAR_VALUE = 0x0C22;
    int GL_COLOR_WRITEMASK = 0x0C23;
    int GL_COMBINE = 0x8570;
    int GL_COMBINE_ALPHA = 0x8572;
    int GL_COMBINE_RGB = 0x8571;
    int GL_CONSTANT = 0x8576;
    int GL_COORD_REPLACE_OES = 0x8862;
    int GL_CULL_FACE_MODE = 0x0B45;
    int GL_CURRENT_COLOR = 0x0B00;
    int GL_CURRENT_NORMAL = 0x0B02;
    int GL_CURRENT_TEXTURE_COORDS = 0x0B03;
    int GL_DEPTH_CLEAR_VALUE = 0x0B73;
    int GL_DEPTH_FUNC = 0x0B74;
    int GL_DEPTH_RANGE = 0x0B70;
    int GL_DEPTH_WRITEMASK = 0x0B72;
    int GL_DOT3_RGB = 0x86AE;
    int GL_DOT3_RGBA = 0x86AF;
    int GL_DYNAMIC_DRAW = 0x88E8;
    int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    int GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895;
    int GL_FRONT_FACE = 0x0B46;
    int GL_GENERATE_MIPMAP = 0x8191;
    int GL_GENERATE_MIPMAP_HINT = 0x8192;
    int GL_INTERPOLATE = 0x8575;
    int GL_LINE_WIDTH = 0x0B21;
    int GL_LOGIC_OP_MODE = 0x0BF0;
    int GL_MATRIX_MODE = 0x0BA0;
    int GL_MAX_CLIP_PLANES = 0x0D32;
    int GL_MODELVIEW_MATRIX = 0x0BA6;
    int GL_MODELVIEW_MATRIX_FLOAT_AS_INT_BITS_OES = 0x898D;
    int GL_MODELVIEW_STACK_DEPTH = 0x0BA3;
    int GL_NORMAL_ARRAY_BUFFER_BINDING = 0x8897;
    int GL_NORMAL_ARRAY_POINTER = 0x808F;
    int GL_NORMAL_ARRAY_STRIDE = 0x807F;
    int GL_NORMAL_ARRAY_TYPE = 0x807E;
    int GL_OPERAND0_ALPHA = 0x8598;
    int GL_OPERAND0_RGB = 0x8590;
    int GL_OPERAND1_ALPHA = 0x8599;
    int GL_OPERAND1_RGB = 0x8591;
    int GL_OPERAND2_ALPHA = 0x859A;
    int GL_OPERAND2_RGB = 0x8592;
    int GL_POINT_DISTANCE_ATTENUATION = 0x8129;
    int GL_POINT_FADE_THRESHOLD_SIZE = 0x8128;
    int GL_POINT_SIZE = 0x0B11;
    int GL_POINT_SIZE_ARRAY_BUFFER_BINDING_OES = 0x8B9F;
    int GL_POINT_SIZE_ARRAY_OES = 0x8B9C;
    int GL_POINT_SIZE_ARRAY_POINTER_OES = 0x898C;
    int GL_POINT_SIZE_ARRAY_STRIDE_OES = 0x898B;
    int GL_POINT_SIZE_ARRAY_TYPE_OES = 0x898A;
    int GL_POINT_SIZE_MAX = 0x8127;
    int GL_POINT_SIZE_MIN = 0x8126;
    int GL_POINT_SPRITE_OES = 0x8861;
    int GL_POLYGON_OFFSET_FACTOR = 0x8038;
    int GL_POLYGON_OFFSET_UNITS = 0x2A00;
    int GL_PREVIOUS = 0x8578;
    int GL_PRIMARY_COLOR = 0x8577;
    int GL_PROJECTION_MATRIX = 0x0BA7;
    int GL_PROJECTION_MATRIX_FLOAT_AS_INT_BITS_OES = 0x898E;
    int GL_PROJECTION_STACK_DEPTH = 0x0BA4;
    int GL_RGB_SCALE = 0x8573;
    int GL_SAMPLES = 0x80A9;
    int GL_SAMPLE_BUFFERS = 0x80A8;
    int GL_SAMPLE_COVERAGE_INVERT = 0x80AB;
    int GL_SAMPLE_COVERAGE_VALUE = 0x80AA;
    int GL_SCISSOR_BOX = 0x0C10;
    int GL_SHADE_MODEL = 0x0B54;
    int GL_SRC0_ALPHA = 0x8588;
    int GL_SRC0_RGB = 0x8580;
    int GL_SRC1_ALPHA = 0x8589;
    int GL_SRC1_RGB = 0x8581;
    int GL_SRC2_ALPHA = 0x858A;
    int GL_SRC2_RGB = 0x8582;
    int GL_STATIC_DRAW = 0x88E4;
    int GL_STENCIL_CLEAR_VALUE = 0x0B91;
    int GL_STENCIL_FAIL = 0x0B94;
    int GL_STENCIL_FUNC = 0x0B92;
    int GL_STENCIL_PASS_DEPTH_FAIL = 0x0B95;
    int GL_STENCIL_PASS_DEPTH_PASS = 0x0B96;
    int GL_STENCIL_REF = 0x0B97;
    int GL_STENCIL_VALUE_MASK = 0x0B93;
    int GL_STENCIL_WRITEMASK = 0x0B98;
    int GL_SUBTRACT = 0x84E7;
    int GL_TEXTURE_BINDING_2D = 0x8069;
    int GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING = 0x889A;
    int GL_TEXTURE_COORD_ARRAY_POINTER = 0x8092;
    int GL_TEXTURE_COORD_ARRAY_SIZE = 0x8088;
    int GL_TEXTURE_COORD_ARRAY_STRIDE = 0x808A;
    int GL_TEXTURE_COORD_ARRAY_TYPE = 0x8089;
    int GL_TEXTURE_MATRIX = 0x0BA8;
    int GL_TEXTURE_MATRIX_FLOAT_AS_INT_BITS_OES = 0x898F;
    int GL_TEXTURE_STACK_DEPTH = 0x0BA5;
    int GL_VERTEX_ARRAY_BUFFER_BINDING = 0x8896;
    int GL_VERTEX_ARRAY_POINTER = 0x808E;
    int GL_VERTEX_ARRAY_SIZE = 0x807A;
    int GL_VERTEX_ARRAY_STRIDE = 0x807C;
    int GL_VERTEX_ARRAY_TYPE = 0x807B;
    int GL_VIEWPORT = 0x0BA2;
    int GL_WRITE_ONLY = 0x88B9;
}
