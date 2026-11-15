package hello.glformats;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** glformats (tests/bench/glformats) through a window: the window surface colour-space checks and the format checks, then "glformats: done". */
public class Main extends Activity {
    private static native int run(Surface surface);
    private boolean started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {}
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (started) return;
                started = true;
                Surface surface = holder.getSurface();
                new Thread(() -> {
                    int fails = run(surface);
                    System.out.println("glformats: window done fails=" + fails);
                }, "glformats").start();
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        setContentView(view);
    }
}
