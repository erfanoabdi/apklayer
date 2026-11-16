package hello.atomics;

import android.app.Activity;
import android.os.Bundle;

/** Multi-threaded atomics stress (atomics.c and friends). -Datomics.threads=N -Datomics.iter=N -Datomics.pin=0..3 -Datomics.only=<substring of a test name>. Prints "[atomics] RES ..." lines. */
public class Main extends Activity {
    private static native String run(int threads, long iter, int pin, String only);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        System.loadLibrary("hello");
        String r = run(Integer.getInteger("atomics.threads", 4), Long.getLong("atomics.iter", 20000L), Integer.getInteger("atomics.pin", 0), System.getProperty("atomics.only", ""));
        for (String line : r.split("\n")) System.out.println("[atomics] " + line);
        System.out.println("[atomics] EXIT");
        finish();
    }
}
