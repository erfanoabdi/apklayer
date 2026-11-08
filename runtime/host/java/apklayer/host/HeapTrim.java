package apklayer.host;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;

/**
 * An idle JVM gives its heap back to the system (docs/PLAN.md "app start speed on phones" 8: the always-on phone instance's hub, component hosts and
 * spare): a full GC with HotSpot's heap free ratios lowered for its duration (both are manageable flags), after which SerialGC uncommits the old
 * generation's free tail and sizes the young generation after it (cli/env.cpp: -XX:MinHeapSize lets it go below the initial size, -XX:-ShrinkHeapInSteps
 * shrinks at once). The ratios go back right after, so later growth, and the app a spare becomes, size their heap as before.
 */
final class HeapTrim {
    private HeapTrim() {}

    private static final long PERIOD_MS = 60_000, GROWTH = 16L << 20;
    private static long usedAfter;

    /** One trim now; the heap in use after it, in bytes. */
    static synchronized long now() {
        long t0 = System.nanoTime();
        try {
            HotSpotDiagnosticMXBean vm = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            String min = vm.getVMOption("MinHeapFreeRatio").getValue(), max = vm.getVMOption("MaxHeapFreeRatio").getValue();
            vm.setVMOption("MinHeapFreeRatio", "0");  // min first: max may never go below it
            vm.setVMOption("MaxHeapFreeRatio", "10");
            try { System.gc(); }
            finally { vm.setVMOption("MaxHeapFreeRatio", max); vm.setVMOption("MinHeapFreeRatio", min); }
        } catch (RuntimeException unsupported) {  // another collector, a JVM without the bean: a plain full GC
            System.gc();
        }
        Runtime r = Runtime.getRuntime();
        usedAfter = r.totalMemory() - r.freeMemory();
        System.out.println("[apklayer] heap trim: " + (usedAfter >> 20) + " MB in use, " + (r.totalMemory() >> 20) + " MB committed, " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        return usedAfter;
    }

    /** A daemon thread that trims every minute in which the heap grew by GROWTH since the last trim (an idle process allocates little: no work). */
    static void periodically(String who) {
        Thread t = new Thread(() -> {
            now();
            for (;;) {
                try { Thread.sleep(PERIOD_MS); } catch (InterruptedException stop) { return; }
                Runtime r = Runtime.getRuntime();
                if (r.totalMemory() - r.freeMemory() > usedAfter + GROWTH) now();
            }
        }, "heap-trim " + who);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
