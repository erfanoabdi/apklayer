package hello.acttarget;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("act target (launcher)");
        System.out.println("[act-t] main pid=" + android.os.Process.myPid() + " ui=" + isUiContext());  // relaunch: a launcher start of the running task creates no second one
        android.graphics.Rect max = getWindowManager().getMaximumWindowMetrics().getBounds(), cur = getWindowManager().getCurrentWindowMetrics().getBounds();
        android.graphics.Point real = new android.graphics.Point();
        getDisplay().getRealSize(real);
        // relaunch: the screen (max bounds, real size, ViewConfiguration's drawing cache limit) is the output, not the window
        System.out.println("[act-t] screen max=" + max.width() + "x" + max.height() + " real=" + real.x + "x" + real.y + " window=" + cur.width() + "x" + cur.height()
                + " cache=" + android.view.ViewConfiguration.get(this).getScaledMaximumDrawingCacheSize());
        setContentView(view);
        if (Boolean.getBoolean("hello.stuckexit"))  // exit-deadline.py: a shutdown hook that never returns (the JVM's end must not wait for it)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { System.out.println("[act-t] stuck exit hook"); while (true) android.os.SystemClock.sleep(1000); }));
        if (Boolean.getBoolean("hello.heldshutdown")) {  // exit-deadline.py held: Shutdown.exit waits for this monitor, so SIGTERM's end never reaches a hook
            Object shutdown;
            try { shutdown = Class.forName("java.lang.Shutdown"); } catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
            new Thread(() -> { synchronized (shutdown) { System.out.println("[act-t] holding java.lang.Shutdown"); while (true) android.os.SystemClock.sleep(1000); } }).start();
        }
    }
}
