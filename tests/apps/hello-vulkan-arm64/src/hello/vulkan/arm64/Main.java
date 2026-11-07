package hello.vulkan.arm64;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** hello.vulkan's Present with an arm64-only library (vkpresent.c): a Vulkan swapchain on a SurfaceView through the native bridge on x86_64. */
public class Main extends Activity {
    private static native int present(android.view.Surface surface, int frames);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                new Thread(() -> System.out.println("[hello-vulkan] PRESENT result " + present(holder.getSurface(), 600))).start();
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        setContentView(view);
    }
}
