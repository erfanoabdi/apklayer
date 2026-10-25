// javax.microedition.khronos.opengles.GL11Ext -- the OES extension surface
// that accompanies GL11 (draw-texture, matrix palette, tex2d matrix). AOSP:
// frameworks/base/opengl/java/javax/microedition/khronos/opengles/GL11Ext.java
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
import java.nio.ShortBuffer;

public interface GL11Ext extends GL {
    void glCurrentPaletteMatrixOES(int a0);
    void glDrawTexfOES(float a0, float a1, float a2, float a3, float a4);
    void glDrawTexfvOES(float[] a0, int a1);
    void glDrawTexfvOES(FloatBuffer a0);
    void glDrawTexiOES(int a0, int a1, int a2, int a3, int a4);
    void glDrawTexivOES(int[] a0, int a1);
    void glDrawTexivOES(IntBuffer a0);
    void glDrawTexsOES(short a0, short a1, short a2, short a3, short a4);
    void glDrawTexsvOES(short[] a0, int a1);
    void glDrawTexsvOES(ShortBuffer a0);
    void glDrawTexxOES(int a0, int a1, int a2, int a3, int a4);
    void glDrawTexxvOES(int[] a0, int a1);
    void glDrawTexxvOES(IntBuffer a0);
    void glEnable(int a0);
    void glEnableClientState(int a0);
    void glLoadPaletteFromModelViewMatrixOES();
    void glMatrixIndexPointerOES(int a0, int a1, int a2, Buffer a3);
    void glMatrixIndexPointerOES(int a0, int a1, int a2, int a3);
    void glTexParameterfv(int a0, int a1, float[] a2, int a3);
    void glWeightPointerOES(int a0, int a1, int a2, Buffer a3);
    void glWeightPointerOES(int a0, int a1, int a2, int a3);
    int GL_MATRIX_INDEX_ARRAY_BUFFER_BINDING_OES = 0x8B9E;
    int GL_MATRIX_INDEX_ARRAY_OES = 0x8844;
    int GL_MATRIX_INDEX_ARRAY_POINTER_OES = 0x8849;
    int GL_MATRIX_INDEX_ARRAY_SIZE_OES = 0x8846;
    int GL_MATRIX_INDEX_ARRAY_STRIDE_OES = 0x8848;
    int GL_MATRIX_INDEX_ARRAY_TYPE_OES = 0x8847;
    int GL_MATRIX_PALETTE_OES = 0x8840;
    int GL_MAX_PALETTE_MATRICES_OES = 0x8842;
    int GL_MAX_VERTEX_UNITS_OES = 0x86A4;
    int GL_TEXTURE_CROP_RECT_OES = 0x8B9D;
    int GL_WEIGHT_ARRAY_BUFFER_BINDING_OES = 0x889E;
    int GL_WEIGHT_ARRAY_OES = 0x86AD;
    int GL_WEIGHT_ARRAY_POINTER_OES = 0x86AC;
    int GL_WEIGHT_ARRAY_SIZE_OES = 0x86AB;
    int GL_WEIGHT_ARRAY_STRIDE_OES = 0x86AA;
    int GL_WEIGHT_ARRAY_TYPE_OES = 0x86A9;
}
