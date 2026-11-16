package hello.isa;

import android.app.Activity;
import android.os.Bundle;

/** ISA feature / id register / HWCAP probe (isa.c): the "[isa]" lines are compared with real arm64 hardware by hand (see compare.sh). */
public class Main extends Activity {
    private static native String run();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        for (String line : run().split("\n")) System.out.println("[isa] " + line);
        finish();
    }
}
