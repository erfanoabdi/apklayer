package hello.acttarget;

import android.app.Activity;
import android.os.Bundle;

public class PrivateActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        System.out.println("[act-t] PRIVATE STARTED");
    }
}
