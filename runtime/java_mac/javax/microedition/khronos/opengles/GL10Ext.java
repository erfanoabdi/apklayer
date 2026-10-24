// javax.microedition.khronos.opengles.GL10Ext -- the OES extension surface
// that accompanies GL10 (glQueryMatrixxOES only). AOSP:
// frameworks/base/opengl/java/javax/microedition/khronos/opengles/GL10Ext.java
//
// Declared for the same reason GL10 is (see that file): apps reach these
// through the GL handle GLSurfaceView.Renderer receives, and an app that
// declares a GL10Ext-typed field or does `gl instanceof GL10Ext` needs the
// type to LOAD -- a missing interface is a NoClassDefFoundError at class
// preparation, before any of its methods are called.
//
// ⚠ NOTHING IN THIS TREE IMPLEMENTS IT YET. android.opengl.GLSurfaceView's
// StubGL10 implements GL10 only, so `gl instanceof GL10Ext` answers false and
// an unconditional `(GL10Ext) gl` cast still throws ClassCastException --
// which is the same answer a device without the extension gives for the
// instanceof, and a genuine remaining gap for the unconditional cast. Fixing
// the cast means widening StubGL10, which lives in android/opengl/.
// TRANSCRIBED MECHANICALLY from AOSP core/api/current.txt (android16),
// so the method set and the constant VALUES are exactly the platform's.
// Parameter names are positional (a0, a1, ...) because the signature file
// does not carry them; they are not part of any binary contract.
package javax.microedition.khronos.opengles;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface GL10Ext extends GL {
    int glQueryMatrixxOES(int[] a0, int a1, int[] a2, int a3);
    int glQueryMatrixxOES(IntBuffer a0, IntBuffer a1);
}
