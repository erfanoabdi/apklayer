package hello.actcaller;

import android.app.Activity;
import android.os.Bundle;

/** An up target that is never in the task: navigateUpTo must not start it. */
public class UpOther extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        System.out.println("[act] FAIL up: navigateUpTo started a parent that was not in the task");
    }
}
