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
    }
}
