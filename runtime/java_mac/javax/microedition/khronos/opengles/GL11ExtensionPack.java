// javax.microedition.khronos.opengles.GL11ExtensionPack -- the OES Extension
// Pack surface (FBOs, cube maps, blend equation separate, stencil wrap).
// AOSP: frameworks/base/opengl/java/javax/microedition/khronos/opengles/
//       GL11ExtensionPack.java
//
// See GL11 for why the type must exist even where nothing implements it, and
// for the StubGL10 caveat.
// TRANSCRIBED MECHANICALLY from AOSP core/api/current.txt (android16),
// so the method set and the constant VALUES are exactly the platform's.
// Parameter names are positional (a0, a1, ...) because the signature file
// does not carry them; they are not part of any binary contract.
package javax.microedition.khronos.opengles;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface GL11ExtensionPack extends GL {
    void glBindFramebufferOES(int a0, int a1);
    void glBindRenderbufferOES(int a0, int a1);
    void glBindTexture(int a0, int a1);
    void glBlendEquation(int a0);
    void glBlendEquationSeparate(int a0, int a1);
    void glBlendFuncSeparate(int a0, int a1, int a2, int a3);
    int glCheckFramebufferStatusOES(int a0);
    void glCompressedTexImage2D(int a0, int a1, int a2, int a3, int a4, int a5, int a6, Buffer a7);
    void glCopyTexImage2D(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7);
    void glDeleteFramebuffersOES(int a0, int[] a1, int a2);
    void glDeleteFramebuffersOES(int a0, IntBuffer a1);
    void glDeleteRenderbuffersOES(int a0, int[] a1, int a2);
    void glDeleteRenderbuffersOES(int a0, IntBuffer a1);
    void glEnable(int a0);
    void glFramebufferRenderbufferOES(int a0, int a1, int a2, int a3);
    void glFramebufferTexture2DOES(int a0, int a1, int a2, int a3, int a4);
    void glGenFramebuffersOES(int a0, int[] a1, int a2);
    void glGenFramebuffersOES(int a0, IntBuffer a1);
    void glGenRenderbuffersOES(int a0, int[] a1, int a2);
    void glGenRenderbuffersOES(int a0, IntBuffer a1);
    void glGenerateMipmapOES(int a0);
    void glGetFramebufferAttachmentParameterivOES(int a0, int a1, int a2, int[] a3, int a4);
    void glGetFramebufferAttachmentParameterivOES(int a0, int a1, int a2, IntBuffer a3);
    void glGetIntegerv(int a0, int[] a1, int a2);
    void glGetIntegerv(int a0, IntBuffer a1);
    void glGetRenderbufferParameterivOES(int a0, int a1, int[] a2, int a3);
    void glGetRenderbufferParameterivOES(int a0, int a1, IntBuffer a2);
    void glGetTexGenfv(int a0, int a1, float[] a2, int a3);
    void glGetTexGenfv(int a0, int a1, FloatBuffer a2);
    void glGetTexGeniv(int a0, int a1, int[] a2, int a3);
    void glGetTexGeniv(int a0, int a1, IntBuffer a2);
    void glGetTexGenxv(int a0, int a1, int[] a2, int a3);
    void glGetTexGenxv(int a0, int a1, IntBuffer a2);
    boolean glIsFramebufferOES(int a0);
    boolean glIsRenderbufferOES(int a0);
    void glRenderbufferStorageOES(int a0, int a1, int a2, int a3);
    void glStencilOp(int a0, int a1, int a2);
    void glTexEnvf(int a0, int a1, float a2);
    void glTexEnvfv(int a0, int a1, float[] a2, int a3);
    void glTexEnvfv(int a0, int a1, FloatBuffer a2);
    void glTexEnvx(int a0, int a1, int a2);
    void glTexEnvxv(int a0, int a1, int[] a2, int a3);
    void glTexEnvxv(int a0, int a1, IntBuffer a2);
    void glTexGenf(int a0, int a1, float a2);
    void glTexGenfv(int a0, int a1, float[] a2, int a3);
    void glTexGenfv(int a0, int a1, FloatBuffer a2);
    void glTexGeni(int a0, int a1, int a2);
    void glTexGeniv(int a0, int a1, int[] a2, int a3);
    void glTexGeniv(int a0, int a1, IntBuffer a2);
    void glTexGenx(int a0, int a1, int a2);
    void glTexGenxv(int a0, int a1, int[] a2, int a3);
    void glTexGenxv(int a0, int a1, IntBuffer a2);
    void glTexParameterf(int a0, int a1, float a2);
    int GL_BLEND_DST_ALPHA = 0x80CA;
    int GL_BLEND_DST_RGB = 0x80C8;
    int GL_BLEND_EQUATION = 0x8009;
    int GL_BLEND_EQUATION_ALPHA = 0x883D;
    int GL_BLEND_EQUATION_RGB = 0x8009;
    int GL_BLEND_SRC_ALPHA = 0x80CB;
    int GL_BLEND_SRC_RGB = 0x80C9;
    int GL_COLOR_ATTACHMENT0_OES = 0x8CE0;
    int GL_COLOR_ATTACHMENT10_OES = 0x8CEA;
    int GL_COLOR_ATTACHMENT11_OES = 0x8CEB;
    int GL_COLOR_ATTACHMENT12_OES = 0x8CEC;
    int GL_COLOR_ATTACHMENT13_OES = 0x8CED;
    int GL_COLOR_ATTACHMENT14_OES = 0x8CEE;
    int GL_COLOR_ATTACHMENT15_OES = 0x8CEF;
    int GL_COLOR_ATTACHMENT1_OES = 0x8CE1;
    int GL_COLOR_ATTACHMENT2_OES = 0x8CE2;
    int GL_COLOR_ATTACHMENT3_OES = 0x8CE3;
    int GL_COLOR_ATTACHMENT4_OES = 0x8CE4;
    int GL_COLOR_ATTACHMENT5_OES = 0x8CE5;
    int GL_COLOR_ATTACHMENT6_OES = 0x8CE6;
    int GL_COLOR_ATTACHMENT7_OES = 0x8CE7;
    int GL_COLOR_ATTACHMENT8_OES = 0x8CE8;
    int GL_COLOR_ATTACHMENT9_OES = 0x8CE9;
    int GL_DECR_WRAP = 0x8508;
    int GL_DEPTH_ATTACHMENT_OES = 0x8D00;
    int GL_DEPTH_COMPONENT = 0x1902;
    int GL_DEPTH_COMPONENT16 = 0x81A5;
    int GL_DEPTH_COMPONENT24 = 0x81A6;
    int GL_DEPTH_COMPONENT32 = 0x81A7;
    int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_OES = 0x8CD1;
    int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE_OES = 0x8CD0;
    int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE_OES = 0x8CD3;
    int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL_OES = 0x8CD2;
    int GL_FRAMEBUFFER_BINDING_OES = 0x8CA6;
    int GL_FRAMEBUFFER_COMPLETE_OES = 0x8CD5;
    int GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT_OES = 0x8CD6;
    int GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_OES = 0x8CD9;
    int GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER_OES = 0x8CDB;
    int GL_FRAMEBUFFER_INCOMPLETE_FORMATS_OES = 0x8CDA;
    int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT_OES = 0x8CD7;
    int GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER_OES = 0x8CDC;
    int GL_FRAMEBUFFER_OES = 0x8D40;
    int GL_FRAMEBUFFER_UNSUPPORTED_OES = 0x8CDD;
    int GL_FUNC_ADD = 0x8006;
    int GL_FUNC_REVERSE_SUBTRACT = 0x800B;
    int GL_FUNC_SUBTRACT = 0x800A;
    int GL_INCR_WRAP = 0x8507;
    int GL_INVALID_FRAMEBUFFER_OPERATION_OES = 0x0506;
    int GL_MAX_COLOR_ATTACHMENTS_OES = 0x8CDF;
    int GL_MAX_CUBE_MAP_TEXTURE_SIZE = 0x851C;
    int GL_MAX_RENDERBUFFER_SIZE_OES = 0x84E8;
    int GL_MIRRORED_REPEAT = 0x8370;
    int GL_NORMAL_MAP = 0x8511;
    int GL_REFLECTION_MAP = 0x8512;
    int GL_RENDERBUFFER_ALPHA_SIZE_OES = 0x8D53;
    int GL_RENDERBUFFER_BINDING_OES = 0x8CA7;
    int GL_RENDERBUFFER_BLUE_SIZE_OES = 0x8D52;
    int GL_RENDERBUFFER_DEPTH_SIZE_OES = 0x8D54;
    int GL_RENDERBUFFER_GREEN_SIZE_OES = 0x8D51;
    int GL_RENDERBUFFER_HEIGHT_OES = 0x8D43;
    int GL_RENDERBUFFER_INTERNAL_FORMAT_OES = 0x8D44;
    int GL_RENDERBUFFER_OES = 0x8D41;
    int GL_RENDERBUFFER_RED_SIZE_OES = 0x8D50;
    int GL_RENDERBUFFER_STENCIL_SIZE_OES = 0x8D55;
    int GL_RENDERBUFFER_WIDTH_OES = 0x8D42;
    int GL_RGB565_OES = 0x8D62;
    int GL_RGB5_A1 = 0x8057;
    int GL_RGB8 = 0x8051;
    int GL_RGBA4 = 0x8056;
    int GL_RGBA8 = 0x8058;
    int GL_STENCIL_ATTACHMENT_OES = 0x8D20;
    int GL_STENCIL_INDEX = 0x1901;
    int GL_STENCIL_INDEX1_OES = 0x8D46;
    int GL_STENCIL_INDEX4_OES = 0x8D47;
    int GL_STENCIL_INDEX8_OES = 0x8D48;
    int GL_STR = -1;
    int GL_TEXTURE_BINDING_CUBE_MAP = 0x8514;
    int GL_TEXTURE_CUBE_MAP = 0x8513;
    int GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 0x8516;
    int GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 0x8518;
    int GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 0x851A;
    int GL_TEXTURE_CUBE_MAP_POSITIVE_X = 0x8515;
    int GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 0x8517;
    int GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 0x8519;
    int GL_TEXTURE_GEN_MODE = 0x2500;
    int GL_TEXTURE_GEN_STR = 0x8D60;
}
