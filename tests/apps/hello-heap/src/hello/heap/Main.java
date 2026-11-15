package hello.heap;

import android.app.Activity;
import android.os.Bundle;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** Holds hello.heapmb MB live (1 MB arrays), then churns garbage for 10 s on top of it; prints the heap limit, live size, GC counts and times. */
public final class Main extends Activity {
    static volatile byte[] sink;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int mb = Integer.getInteger("hello.heapmb", 700);
        Runtime rt = Runtime.getRuntime();
        System.out.println("[heap] max " + rt.maxMemory() / (1 << 20) + " MB, want live " + mb + " MB");
        List<byte[]> live = new ArrayList<>();
        long t0 = System.nanoTime();
        try {
            for (int i = 0; i < mb; i++) { byte[] b = new byte[1 << 20]; b[0] = (byte) i; live.add(b); }
        } catch (OutOfMemoryError e) { System.out.println("[heap] FAIL OutOfMemoryError at " + live.size() + " MB"); finish(); return; }
        System.out.println("[heap] live " + live.size() + " MB in " + (System.nanoTime() - t0) / 1000000 + " ms, heap now " + rt.totalMemory() / (1 << 20) + " MB");
        long churned = 0, until = System.nanoTime() + 10_000_000_000L, worst = 0;
        while (System.nanoTime() < until) {
            long s = System.nanoTime();
            sink = new byte[1 << 20]; sink[0] = 1; churned++;  // sink: the allocation is not optimised away
            worst = Math.max(worst, System.nanoTime() - s);
        }
        long count = 0, ms = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += gc.getCollectionCount(); ms += gc.getCollectionTime();
            System.out.println("[heap] gc " + gc.getName() + " " + gc.getCollectionCount() + " collections, " + gc.getCollectionTime() + " ms");
        }
        System.out.println("[heap] churned " + churned + " MB, worst allocation " + worst / 1000000 + " ms, heap " + rt.totalMemory() / (1 << 20) + " MB, live kept " + live.size());
        System.out.println("[heap] PASS gcs=" + count + " gc_ms=" + ms);
        finish();
    }
}
