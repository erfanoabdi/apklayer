package hello.arm32;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Calls an armeabi-v7a-only JNI library (hello.c) in the 32-bit app process: arithmetic, a call into Java, a native thread calling Java, GL. */
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

    static int twice(int v) { return 2 * v; }

    private static void log(String s) { System.out.println("[hello-arm32] " + s); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String jvm = System.getProperty("os.arch");  // "arm": the process itself is 32-bit
        long t0 = System.nanoTime();
        System.loadLibrary("hello");
        long t1 = System.nanoTime();
        String cpu = cpu();
        double first = work(1000), warm = work(1000);
        long t2 = System.nanoTime();
        double big = work(10_000_000);
        long t3 = System.nanoTime();
        int call = callJava(21), thread = threadCall(50);
        log("jvm " + jvm + ", abi " + android.os.Build.CPU_ABI + ", load " + (t1 - t0) / 1000 + " us, cpu " + cpu);
        log("work(1e7) " + (t3 - t2) / 1000 + " us = " + big + ", callJava " + call + ", threadCall " + thread);
        boolean overloads = field(5) == 1 && field() == 5;
        log("overloaded natives " + (overloads ? "ok" : "MIXED"));
        int errno = errnos();  // open ENOENT (2) * 100 + clock_gettime EINVAL (22)
        log("errno " + (errno == 222 ? "ok" : "WRONG " + errno));
        int fault = fault();  // a null read the library's own SIGSEGV handler recovers from (chained behind the JVM's: libjsig)
        log("native fault " + (fault == 1 ? "recovered" : "FAIL " + fault));
        boolean ok = "arm".equals(jvm) && "arm".equals(cpu) && first == warm && call == 42 && thread == 100 && overloads && errno == 222 && fault == 1;
        log(ok ? "PASS" : "FAIL first=" + first + " warm=" + warm);
        int px = gl();  // EGL pbuffer cleared to (1, 0.5, 0, 1): 0xff0080ff, read back as RGBA bytes
        int green = (px >> 8) & 0xff;
        log("GL " + ((px & 0xff00ff) == 0x0000ff && green > 0x70 && green < 0x90 ? "PASS" : "FAIL") + " pixel 0x" + Integer.toHexString(px));
        TextView v = new TextView(this);
        v.setText("arm32 JNI: " + (ok ? "PASS" : "FAIL"));
        setContentView(v);
    }
}
