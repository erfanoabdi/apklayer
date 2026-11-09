package hello.arm64;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Calls an arm64-only JNI library (hello.c): arithmetic, a call into Java, a native thread calling Java; times the first and a warm call. */
public class Main extends Activity {
    private static native String cpu();
    private static native double work(int n);
    private static native int callJava(int v);
    private static native int threadCall(int v);
    private static native int gl();
    private static native long field();
    private static native int field(long v);
    private static native int fault();
    private static native int errnos();
    private static native int ownStack();

    static int twice(int v) { return 2 * v; }

    private static void log(String s) { System.out.println("[hello-arm64] " + s); }

    /** Berberis's code cache is memfd-backed (exec_region): its mappings in /proc/self/maps show translation ran in this process. */
    private static long memfdMappings() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            return r.lines().filter(l -> l.contains("memfd:")).count();
        } catch (java.io.IOException e) {
            return -1;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        long t0 = System.nanoTime();
        System.loadLibrary("hello");
        long t1 = System.nanoTime();
        String cpu = cpu();
        long t2 = System.nanoTime();
        double first = work(1000);
        long t3 = System.nanoTime();
        double warm = work(1000);
        long t4 = System.nanoTime();
        double big = work(10_000_000);
        long t5 = System.nanoTime();
        int call = callJava(21), thread = threadCall(50);
        long t6 = System.nanoTime();
        log("load " + (t1 - t0) / 1000 + " us, cpu " + cpu + " (" + (t2 - t1) / 1000 + " us)");
        log("work(1000) first " + (t3 - t2) / 1000 + " us, warm " + (t4 - t3) / 1000 + " us, work(1e7) " + (t5 - t4) / 1000 + " us = " + big);
        log("callJava " + call + ", threadCall " + thread + " (" + (t6 - t5) / 1000 + " us)");
        log("translated code mappings " + memfdMappings());
        boolean overloads = field(5) == 1 && field() == 5;  // each overload bound to its own long-named function
        log("overloaded natives " + (overloads ? "ok" : "MIXED"));
        int errno = errnos();  // open ENOENT (2) * 100 + proxied clock_gettime EINVAL (22)
        log("errno " + (errno == 222 ? "ok" : "WRONG " + errno));
        int fault = fault();  // a guest null read its own SIGSEGV handler recovers from: the JVM lives on
        log("guest fault " + (fault == 1 ? "recovered" : "FAIL " + fault));
        int stack = ownStack();  // pthread_getattr_np(pthread_self()) of this JVM thread: its own guest stack
        log("own stack " + (stack == 1 ? "ok" : "WRONG"));
        boolean ok = "aarch64".equals(cpu) && first == warm && call == 42 && thread == 100 && overloads && errno == 222 && fault == 1 && stack == 1;
        log(ok ? "PASS" : "FAIL first=" + first + " warm=" + warm);
        long t7 = System.nanoTime();
        int px = gl();  // EGL pbuffer cleared to (1, 0.5, 0, 1): 0xff0080ff, read back as RGBA bytes
        int green = (px >> 8) & 0xff;  // drivers round 0.5 differently (ANV: 0x82)
        log("GL " + ((px & 0xff00ff) == 0x0000ff && green > 0x70 && green < 0x90 ? "PASS" : "FAIL") + " pixel 0x" + Integer.toHexString(px)
                + " (" + (System.nanoTime() - t7) / 1000 + " us)");
        TextView v = new TextView(this);
        v.setText("arm64 JNI: " + (ok ? "PASS" : "FAIL"));
        setContentView(v);
    }
}
