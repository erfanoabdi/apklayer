package hello.spawn;

import android.app.Activity;
import android.os.Bundle;

/** Starts a command line, split at commas (-Dhello.cmd, default /usr/bin/true) hello.n (20) times through ProcessBuilder and prints each call's milliseconds and the mean. */
public final class Main extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String cmd = System.getProperty("hello.cmd", "/usr/bin/true");
            int n = Integer.getInteger("hello.n", 20);
            double total = 0;
            try {
                for (int i = 0; i < n; i++) {
                    long t = System.nanoTime();
                    int code = new ProcessBuilder(cmd.split(",")).redirectErrorStream(true).start().waitFor();
                    double ms = (System.nanoTime() - t) / 1e6;
                    total += ms;
                    System.out.printf("[spawn] %d exit=%d %.1f ms%n", i, code, ms);
                }
                System.out.printf("[spawn] MEAN %.1f ms over %d%n[spawn] DONE%n", total / n, n);
            } catch (Throwable e) { System.out.println("[spawn] FAILED " + e); }
            finish();
        }).start();
    }
}
