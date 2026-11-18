package hello.anglecanvas;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Exercises Surface.lockCanvas/unlockCanvasAndPost: CPU rendering into a gralloc buffer while ANGLE is selected. */
public final class Main extends Activity implements SurfaceHolder.Callback {
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean drawing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(this);
        setContentView(view);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (drawing) return;
        drawing = true;
        new Thread(() -> drawFrames(holder), "angle-cpu-canvas").start();
    }

    private void drawFrames(SurfaceHolder holder) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xff00ff00);
        for (int frame = 0; frame < 3; frame++) {
            Canvas canvas;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) throw new IllegalStateException("lockCanvas returned null");
                canvas.drawColor(0xffcc2200);
                canvas.drawRect(12, 12, canvas.getWidth() - 12, canvas.getHeight() - 12, paint);
                holder.unlockCanvasAndPost(canvas);
            } catch (Throwable error) {
                System.out.println("angle-cpu-canvas: FAIL frame=" + frame + " " + error);
                main.post(this::finish);
                return;
            }
        }
        System.out.println("angle-cpu-canvas: PASS posted=3");
        main.postDelayed(this::finish, 1200);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {}
}
