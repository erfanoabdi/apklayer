package apklayer.host;

import java.lang.reflect.Field;

/** Diagnostic (-Dapklayer.geckodiag=true): prints what GeckoView hands to Gecko's native main (its InitInfo) from a watcher thread. */
final class HostGeckoDiag {
    private HostGeckoDiag() {}

    static void start() {
        if (!Boolean.getBoolean("apklayer.geckodiag")) return;
        Thread t = new Thread(() -> {
            for (int n = 0; n < 4000; n++) {
                for (Thread th : Thread.getAllStackTraces().keySet()) {
                    if (!th.getClass().getName().equals("org.mozilla.gecko.GeckoThread")) continue;
                    try {
                        Field f = th.getClass().getDeclaredField("mInitInfo"); f.setAccessible(true);
                        Object info = f.get(th);
                        if (info == null) continue;
                        StringBuilder sb = new StringBuilder("[apklayer] GeckoThread InitInfo:");
                        for (Field g : info.getClass().getDeclaredFields()) {
                            g.setAccessible(true);
                            Object v = g.get(info);
                            sb.append("\n    ").append(g.getName()).append(" = ").append(v instanceof Object[] a ? java.util.Arrays.toString(a) : v instanceof int[] i ? java.util.Arrays.toString(i) : String.valueOf(v));
                        }
                        System.out.println(sb);
                        return;
                    } catch (ReflectiveOperationException ignored) { /* not ready */ }
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { return; }
            }
        }, "apklayer-geckodiag");
        t.setDaemon(true);
        t.start();
    }
}
