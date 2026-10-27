package hello.vulkan;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** An Android Vulkan swapchain on a SurfaceView: the native side clears each acquired image and presents it. */
public class Present extends Activity {
    private static native int present(Surface surface, int frames);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("apklayer_vktest");
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                Surface surface = holder.getSurface();
                new Thread(() -> System.out.println("[hello-vulkan] PRESENT result " + present(surface, 600))).start();
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        setContentView(view);
    }
}
