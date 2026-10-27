package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Select original device GL JNI; static JNI caches finish after native registration. */
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
}
