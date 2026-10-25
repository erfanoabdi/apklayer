// JNI surface to the EGL / GLES wrapper sitting on top of ANGLE.
// Stubbed for now -- we'll grow into it once the boot path reaches
// android.opengl.GLES20 calls.
package com.macapk.loader;

public final class NativeGles {
    private NativeGles() {}

    public static native boolean init(long metalLayer, int widthPx, int heightPx);
    public static native void beginFrame();
    public static native void endFrame();
}
