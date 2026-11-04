package hello.nativesurface;

import android.app.Service;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Surface;
import java.util.concurrent.CompletableFuture;

/**
 * A SurfaceTexture owned by another process (":st"), as a browser's GPU process owns the one its media process decodes into: SURFACE hands its
 * producer Surface out over binder; every frame-available callback (on this service's GL thread) runs updateTexImage; FRAMES answers how many
 * frames it latched and the timestamp of the last one.
 */
public class TextureService extends Service {
    static final int SURFACE = 1, FRAMES = 2;
    private Handler gl;
    private SurfaceTexture texture;
    private Surface surface;
    private int frames;
    private long timestamp = -1;
    private String error = "";

    @Override public IBinder onBind(Intent intent) {
        HandlerThread thread = new HandlerThread("texture-gl");
        thread.start();
        gl = new Handler(thread.getLooper());
        CompletableFuture<Void> ready = new CompletableFuture<>();
        gl.post(() -> {
            try { setUp(); ready.complete(null); } catch (Throwable failure) { ready.completeExceptionally(failure); }
        });
        ready.join();
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == SURFACE) { surface.writeToParcel(reply, 0); return true; }
                if (code != FRAMES) return false;
                synchronized (TextureService.this) { reply.writeInt(frames); reply.writeLong(timestamp); reply.writeString(error); }
                return true;
            }
        };
    }

    /** A pbuffer-backed ES 2 context current on this thread, the external texture and the SurfaceTexture on it. */
    private void setUp() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("eglInitialize");
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        EGL14.eglChooseConfig(display, new int[] {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0);
        if (count[0] < 1) throw new IllegalStateException("no EGL config");
        EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        EGLSurface pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0], new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) throw new IllegalStateException("eglMakeCurrent 0x" + Integer.toHexString(EGL14.eglGetError()));
        int[] name = new int[1];
        GLES20.glGenTextures(1, name, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, name[0]);
        texture = new SurfaceTexture(name[0]);
        texture.setOnFrameAvailableListener(st -> {
            try {
                st.updateTexImage();
                synchronized (this) { frames++; timestamp = st.getTimestamp(); }
            } catch (RuntimeException failure) { synchronized (this) { error = failure.toString(); } }
        }, gl);
        surface = new Surface(texture);
        if (name[0] == 0) throw new IllegalStateException("glGenTextures made no texture (no GL context current)");
        System.out.println("[hello-native-surface] texture service: SurfaceTexture on texture " + name[0] + " in pid " + android.os.Process.myPid()
                + ", " + GLES20.glGetString(GLES20.GL_VERSION));
    }
}
