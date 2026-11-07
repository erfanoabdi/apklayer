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
        setContentView(view);
    }
}
