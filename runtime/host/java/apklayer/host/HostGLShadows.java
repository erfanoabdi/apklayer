package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Select original device GL JNI; static JNI caches finish after native registration. Every android.opengl class with natives calls Android's
 * JNI (runtime_shim.c registers them): Robolectric's ShadowEGL14 fakes EGL (eglMakeCurrent made nothing current, so a SurfaceTexture consumer's
 * updateTexImage failed and its GLES calls crashed), and an unshadowed class's natives are no-ops.
 */
public final class HostGLShadows {
    private HostGLShadows() {}
    @Implements(className = "android.graphics.SurfaceTexture", callNativeMethodsByDefault = true)
    public static class SurfaceTexture {}
    @Implements(className = "android.graphics.TextureLayer", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static class TextureLayer {}
    @Implements(className = "android.opengl.GLES20", callNativeMethodsByDefault = true)
    public static class GLES20 {
        @Implementation protected static void __staticInitializer__() {}
    }
    @Implements(className = "android.opengl.GLUtils", callNativeMethodsByDefault = true)
    public static class GLUtils {}
    @Implements(className = "android.opengl.EGL14", callNativeMethodsByDefault = true)
    public static class EGL14 {}  // its static initializer's _nativeClassInit caches the EGL object classes and EGL_NO_* the natives return
    @Implements(className = "android.opengl.EGL15", callNativeMethodsByDefault = true)
    public static class EGL15 {}
    @Implements(className = "android.opengl.EGLExt", callNativeMethodsByDefault = true)
    public static class EGLExt {}
    @Implements(className = "android.opengl.GLES10", callNativeMethodsByDefault = true)
    public static class GLES10 {}
    @Implements(className = "android.opengl.GLES10Ext", callNativeMethodsByDefault = true)
    public static class GLES10Ext {}
    @Implements(className = "android.opengl.GLES11", callNativeMethodsByDefault = true)
    public static class GLES11 {}
    @Implements(className = "android.opengl.GLES11Ext", callNativeMethodsByDefault = true)
    public static class GLES11Ext {}
    @Implements(className = "android.opengl.GLES30", callNativeMethodsByDefault = true)
    public static class GLES30 {}
    @Implements(className = "android.opengl.GLES31", callNativeMethodsByDefault = true)
    public static class GLES31 {}
    @Implements(className = "android.opengl.GLES31Ext", callNativeMethodsByDefault = true)
    public static class GLES31Ext {}
    @Implements(className = "android.opengl.GLES32", callNativeMethodsByDefault = true)
    public static class GLES32 {}
}
