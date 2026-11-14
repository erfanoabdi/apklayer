package hello.actrestart;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.widget.TextView;

/** Ends its own process after the 5 s the restart idiom (start, then kill) is excused (ShadowHostProcess.killProcess). */
public class Second extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("second");
        setContentView(view);
        new Handler().postDelayed(() -> Process.killProcess(Process.myPid()), 6000);
    }
}
