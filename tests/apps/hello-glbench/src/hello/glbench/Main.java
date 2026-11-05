package hello.glbench;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** glbench (tests/bench/glbench) on a SurfaceView: the window modes, then "glbench: done". Intent extras (string): modes (default
 * "window,window-novsync"), scenes (default all), seconds (default 3). */
public class Main extends Activity {
    private static native int run(Surface surface, String mode, String scenes, double seconds);
    private boolean started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        String modes = extra("modes", "window,window-novsync"), scenes = extra("scenes", null);
        double seconds = Double.parseDouble(extra("seconds", "3"));
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {}
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (started) return;
                started = true;
                Surface surface = holder.getSurface();
                new Thread(() -> {
                    for (String mode : modes.split(",")) run(surface, mode, scenes, seconds);
                    System.out.println("glbench: done");
                }, "glbench").start();
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        setContentView(view);
    }

    private String extra(String name, String fallback) {
        String v = getIntent().getStringExtra(name);
        return v != null ? v : fallback;
    }
}
