package hello.gl2;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class Main extends Activity implements SurfaceHolder.Callback {
    static { System.loadLibrary("hello"); }
    static native void nativeStart(android.view.Surface surface);
    static native void nativeStop();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SurfaceView v = new SurfaceView(this);
        v.getHolder().addCallback(this);
        setContentView(v);
    }

    @Override public void surfaceCreated(SurfaceHolder h) { nativeStart(h.getSurface()); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) { nativeStop(); }
}
