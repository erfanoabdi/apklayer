// dalvik.system.LazyDexSource -- on-demand DEX->JVM-bytecode conversion.
//
// WHY (measured): the install pipeline converts EVERY class in the APK up
// front, but most are never loaded. `-verbose:class` on a real boot:
//   Calculator  1,674 of 3,620 converted classes ever defined  (46%)
//   GD Lite     3,467 of 22,982                                (15%)
// So 54-85% of the conversion work is discarded, and the waste GROWS with app
// size -- backwards, since large apps already pay the longest install.
//
// This drives conversion from the JVM's own classloading instead: the parsed
// DEX is held resident and one class is translated when something actually
// asks for it. Parsing is the cheap half (measured 0.26s for Candy Crush's
// 28MB of dex against ~15s to translate it), so keeping it warm and skipping
// the unused 85% is the whole trade.
//
// v763: this used to drive a `python -m enjarify.server` SUBPROCESS over a
// pipe, with a hand-rolled length-prefixed protocol that could not use a
// BufferedReader (it would swallow the binary body) and a resident Python
// interpreter for the life of the app. Mac.apk's own converter runs IN THIS
// JVM, so all of that is gone: no process, no pipe, no framing, no Python, and
// -- the part that actually matters -- it is by construction the SAME code the
// install path runs, so a lazily converted class cannot drift from the eager
// jar. That equivalence used to need a dedicated harness gate to check.
package dalvik.system;

import com.macapk.dex.DexConverter;
import com.macapk.dex.Main;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class LazyDexSource implements AutoCloseable {

    /** Sentinel for "no such class", so a negative lookup is cached too: a
     *  classloader asks for the same absent class repeatedly while walking the
     *  delegation chain. */
    private static final byte[] ABSENT = new byte[0];

    private final DexConverter.Session session;
    private final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();

    private LazyDexSource(DexConverter.Session session) {
        this.session = session;
    }

    /**
     * Open a converter over {@code apkOrDex}, or return null if it has no DEX.
     * Never throws: a failure here must degrade to the eager jar, not break
     * class loading.
     */
    public static LazyDexSource start(File apkOrDex) {
        try {
            long t0 = System.nanoTime();
            List<byte[]> dexes = Main.readDexes(apkOrDex.toPath());
            if (dexes.isEmpty()) return null;
            long t1 = System.nanoTime();
            DexConverter.Session s = DexConverter.open(dexes.toArray(new byte[0][]));
            long t2 = System.nanoTime();
            System.err.println("[macapk] lazy dex: " + dexes.size() + " dex, "
                    + s.classNames().size() + " classes available on demand"
                    + " (read " + ((t1 - t0) / 1_000_000) + " ms, parse+index "
                    + ((t2 - t1) / 1_000_000) + " ms)");
            // Warm the cross-class plans off the classloading path. The
            // InitRepointPlan scan walks every method in the session and cost
            // a measured 22 s IN-PROCESS on TikTok (411k classes) -- paid,
            // before this, inside the app's very first findClass. On a
            // background daemon thread it overlaps the app's native/resource
            // startup instead; classBytes still blocks on the plan if a class
            // is requested before the scan finishes (Session.repointPlan is
            // the single synchronized resolver either way).
            Thread warm = new Thread(() -> {
                long w0 = System.nanoTime();
                try {
                    s.prepare();
                    System.err.println("[macapk] lazy dex: cross-class plans"
                            + " ready in " + ((System.nanoTime() - w0) / 1_000_000)
                            + " ms (background)");
                } catch (Throwable t) {
                    System.err.println("[macapk] lazy dex: plan warm failed: " + t);
                }
            }, "macapk-lazydex-warm");
            warm.setDaemon(true);
            warm.start();
            return new LazyDexSource(s);
        } catch (Throwable t) {
            System.err.println("[macapk] lazy dex unavailable: " + t);
            return null;
        }
    }

    /** Class bytes for an INTERNAL name ("com/example/Foo"), or null if this
     *  DEX has no such class or that one class failed to translate. */
    public byte[] classBytes(String internalName) {
        byte[] hit = cache.get(internalName);
        if (hit != null) return hit == ABSENT ? null : hit;
        byte[] got = convert(internalName);
        cache.put(internalName, got == null ? ABSENT : got);
        return got;
    }

    // One at a time: Session records per-class failures in a plain map, and
    // class loading can be concurrent. Conversion is a fraction of a
    // millisecond per class, so the lock is not worth removing.
    private long convertedCount = 0;
    private long convertedNanos = 0;
    private final long openedAtNanos = System.nanoTime();

    private synchronized byte[] convert(String internalName) {
        try {
            long c0 = System.nanoTime();
            byte[] r = session.classBytes(internalName);
            long dt = System.nanoTime() - c0;
            convertedNanos += dt;
            convertedCount++;
            // Milestone telemetry: the first call carries InitRepointPlan's
            // whole-session build, so it is named; after that a line every
            // 2000 classes keeps the cost visible without spamming.
            if (convertedCount == 1) {
                System.err.println("[macapk] lazy dex: first convert "
                        + (dt / 1_000_000) + " ms (includes the one-time"
                        + " InitRepointPlan scan)");
            } else if (convertedCount % 2000 == 0 || convertedCount == 100
                       || convertedCount == 500 || convertedCount == 1000) {
                System.err.println("[macapk] lazy dex: " + convertedCount
                        + " classes converted, " + (convertedNanos / 1_000_000)
                        + " ms converting, +"
                        + ((System.nanoTime() - openedAtNanos) / 1_000_000)
                        + " ms since open");
            }
            if (dt > 500_000_000L) {
                // A single class costing >0.5s is exactly the kind of outlier
                // that explains a slow first launch; name it.
                System.err.println("[macapk] lazy dex: SLOW convert "
                        + internalName + " took " + (dt / 1_000_000) + " ms");
            }
            return r;
        } catch (Throwable t) {
            // A single bad class must not break class loading; the caller
            // falls back exactly as it would for any other classpath miss.
            // But NEVER silently: this Throwable is the difference between
            // "the dex does not define X" and "we failed to convert X", and
            // swallowing it turned a converter OOM into a phantom
            // ClassNotFoundException with nothing in the log (measured on
            // TikTok: a 1.45 MB class died here after 17 s and the only
            // symptom was NoClassDefFoundError three frames later).
            System.err.println("[macapk] lazy dex: convert FAILED for "
                    + internalName + ": " + t);
            t.printStackTrace();
            return null;
        }
    }

    @Override
    public void close() {
        cache.clear();
    }
}
