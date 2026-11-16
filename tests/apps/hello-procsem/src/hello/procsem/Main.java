package hello.procsem;

import android.app.Activity;
import android.os.Bundle;

/** Read semantics of the proc/sys files an app reads (procsem.c): the "[procsem]" lines are compared with the same code run as a plain program on a real kernel (compare.sh). */
public class Main extends Activity {
    private static native String run();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        for (String line : run().split("\n")) System.out.println("[procsem] " + line);
        System.out.println("[procsem] DONE");
        finish();
    }
}
