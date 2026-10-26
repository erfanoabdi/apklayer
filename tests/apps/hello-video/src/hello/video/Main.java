package hello.video;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** P5.2 sample: plays /tmp/apklayer-test.webm (colour bars) into a SurfaceView with MediaPlayer. */
public class Main extends Activity implements SurfaceHolder.Callback {
    private MediaPlayer mp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SurfaceView v = new SurfaceView(this);
        v.getHolder().addCallback(this);
        setContentView(v);
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        try {
            mp = new MediaPlayer();
            mp.setDataSource("/tmp/apklayer-test.webm");
            mp.setDisplay(h);
            mp.setLooping(true);
            mp.setOnPreparedListener(m -> {
                System.out.println("[hello-video] prepared, duration=" + m.getDuration());
                m.start();
            });
            mp.prepareAsync();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}

    @Override public void surfaceDestroyed(SurfaceHolder h) { if (mp != null) mp.release(); }
}
