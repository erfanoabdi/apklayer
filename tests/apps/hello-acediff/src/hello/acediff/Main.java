package hello.acediff;

import android.app.Activity;
import android.os.Bundle;

/** Differential instruction / runtime-code / signal test (acediff.c): the "[acediff]" lines must equal those of the same code on real arm64 (compare.sh). */
public class Main extends Activity {
    private static native String run();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        for (String line : run().split("\n")) System.out.println("[acediff] " + line);
        System.out.println("[acediff] DONE");
        finish();
    }
}
