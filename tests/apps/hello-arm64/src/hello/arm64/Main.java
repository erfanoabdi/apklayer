package hello.arm64;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Calls an arm64-only JNI library (hello.c): arithmetic, a call into Java, a native thread calling Java; times the first and a warm call. */
public class Main extends Activity {
    private static native String cpu();
    private static native int artMaps();
    private static native double work(int n);
    private static native int callJava(int v);
    private static native int threadCall(int v);
    private static native int gl();
    private static native long field();
    private static native int field(long v);
    private static native int fault();
    private static native int errnos();
    private static native int ownStack();
    private static native int sigqueue();
    private static native int auxv();
    private static native int vdsoVerdef();
    private static native int missingGlFunctions();
    private static native int ignoreTerm();
    private static native int uid();
    private static native int ownerUid(String path);
    private static native int procView();
    private static native String layout(String pkg);
    private static native String cpuProfile();
    private static native String fidelity();
    private static native String oldHandlers();
    private static native String probeEnv();
    private static native String zipDiff();
    private static String getPrimaryCpuAbi(android.content.pm.ApplicationInfo ai) { return field(ai, "primaryCpuAbi"); }
    private static String getSecondaryCpuAbi(android.content.pm.ApplicationInfo ai) { return field(ai, "secondaryCpuAbi"); }
    private static String field(Object o, String n) {
        try { return String.valueOf(o.getClass().getField(n).get(o)); } catch (ReflectiveOperationException e) { return "n/a " + e; }
    }

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
        if (Boolean.getBoolean("hello.artmaps")) {
            int nativeBits = artMaps();
            boolean javaMaps = true;
            for (String path : new String[]{"/proc/self/maps", "/proc/thread-self/maps"}) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) {
                    String maps = r.lines().collect(java.util.stream.Collectors.joining("\n"));
                    javaMaps &= maps.contains("/apex/com.android.art/lib64/libart.so") && !maps.contains("libjvm.so")
                            && !maps.contains("libjava.so") && !maps.contains("/lib/modules") && !maps.contains(".jsa");
                } catch (java.io.IOException e) { throw new AssertionError(e); }
            }
            boolean ok = javaMaps && nativeBits == 255 && "Dalvik".equals(System.getProperty("java.vm.name"));
            log("ART maps " + (ok ? "PASS" : "FAIL") + " java=" + javaMaps + " nativeBits=" + nativeBits);
            TextView text = new TextView(this); text.setText("ART maps: " + ok); setContentView(text);
            return;
        }
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
        android.content.pm.ApplicationInfo ai = getApplicationInfo();
        log("abi SUPPORTED_ABIS " + java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS) + " SUPPORTED_64 " + java.util.Arrays.toString(android.os.Build.SUPPORTED_64_BIT_ABIS)
                + " CPU_ABI " + android.os.Build.CPU_ABI + " primaryCpuAbi " + getPrimaryCpuAbi(ai) + " secondaryCpuAbi " + getSecondaryCpuAbi(ai)
                + " libdir " + ai.nativeLibraryDir + " is64 " + android.os.Process.is64Bit());
        log("abi consistent " + (android.os.Build.CPU_ABI.equals(getPrimaryCpuAbi(ai)) && android.os.Build.SUPPORTED_ABIS[0].equals(android.os.Build.CPU_ABI) ? "ok" : "MIXED"));
        log("env MOZ_LOG " + probeEnv());
        String handlers = oldHandlers();  // the previous actions sigaction reports are SIG_DFL, SIG_IGN or code (a chained crash handler calls them)
        log("old handlers " + (handlers.isEmpty() ? "ok" : "WRONG" + handlers));
        int stack = ownStack();  // pthread_getattr_np(pthread_self()) of this JVM thread: its own guest stack
        log("own stack " + (stack == 1 ? "ok" : "WRONG"));
        int sigq = sigqueue();  // pthread_sigqueue to this thread: the handler gets the sigval
        log("sigqueue " + (sigq == 1 ? "ok" : "WRONG"));
        int auxv = auxv();  // /proc/self/auxv's hwcaps are getauxval's (the guest's, not the host's)
        log("auxv " + (auxv == 1 ? "ok" : "WRONG"));
        int verdef = vdsoVerdef();  // the guest vdso defines LINUX_2.6.39 (Go's vdso parser dereferences its verdef)
        log("vdso verdef " + (verdef == 1 ? "ok" : "WRONG"));
        log("missing host GL functions called " + missingGlFunctions() + " ok");  // returns (not an abort) from functions the host library lacks
        // the app's uid as the arm64 code sees it, and as the owner of its own files: Android's (Process.myUid), not the desktop user's
        int uid = uid(), owner = ownerUid(getFilesDir().getPath()), app = android.os.Process.myUid();
        boolean ids = uid == app && owner == app;
        log("uid " + (ids ? "ok " + uid : "WRONG getuid " + uid + ", files owner " + owner + ", app " + app));
        // /proc as an app sees it (binary_translation 0016, 0017; hidepid-like): own ids in status, other apps' and the desktop's pids not there
        int proc = procView();
        log("proc view " + (proc == 15 ? "ok" : "WRONG bits " + proc));
        if (System.getProperty("hello.layout") != null || new java.io.File(getFilesDir(), "layout").exists())  // the filesystem-layout audit: one line per finding
            for (String line : (layout(getPackageName()) + fidelity()).split("\n")) log("layout " + line);
        log("profile " + android.os.Build.MANUFACTURER + " / " + android.os.Build.MODEL + " / " + android.os.Build.DEVICE + " / " + android.os.Build.BOARD + " / "
                + android.os.Build.HARDWARE + " / " + android.os.Build.FINGERPRINT);
        log("profile cpu " + cpuProfile());
        String zd = zipDiff();
        for (String line : zd.split("\n")) log("zipdiff " + line);  // Fennec x86 part 8: identical lines on arm64 and under the translator
        boolean ok = "aarch64".equals(cpu) && first == warm && call == 42 && thread == 100 && overloads && errno == 222 && fault == 1 && stack == 1 && sigq == 1
                && auxv == 1 && verdef == 1 && handlers.isEmpty() && ids && proc == 15
                && zd.contains("found 700 wrong 0") && zd.contains("greprefs@0") && zd.contains("siglongjmp=7");
        log(ok ? "PASS" : "FAIL first=" + first + " warm=" + warm);
        long t7 = System.nanoTime();
        int px = gl();  // EGL pbuffer cleared to (1, 0.5, 0, 1): 0xff0080ff, read back as RGBA bytes
        int green = (px >> 8) & 0xff;  // drivers round 0.5 differently (ANV: 0x82)
        log("GL " + ((px & 0xff00ff) == 0x0000ff && green > 0x70 && green < 0x90 ? "PASS" : "FAIL") + " pixel 0x" + Integer.toHexString(px)
                + " (" + (System.nanoTime() - t7) / 1000 + " us)");
        if (Boolean.getBoolean("hello.ignoreterm")) log("SIGTERM ignored and caught by the library: " + (ignoreTerm() == 1 ? "ok" : "FAIL"));  // exit-deadline.py native
        TextView v = new TextView(this);
        v.setText("arm64 JNI: " + (ok ? "PASS" : "FAIL"));
        setContentView(v);
    }
}
