package apklayer.host;

import java.lang.reflect.Field;

/** Diagnostic (-Dapklayer.geckodiag=true): prints what GeckoView hands to Gecko's native main (its InitInfo) from a watcher thread. */
final class HostGeckoDiag {
    private HostGeckoDiag() {}

    /** On the main thread: what the app is told about connectivity (Gecko goes offline without a network). */
    static void probeNetwork() {
        if (!Boolean.getBoolean("apklayer.geckodiag")) return;
        android.net.ConnectivityManager cm = org.robolectric.RuntimeEnvironment.getApplication().getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        android.net.Network net = cm.getActiveNetwork();
        android.net.NetworkCapabilities caps = net == null ? null : cm.getNetworkCapabilities(net);
        System.out.println("[apklayer] diag network connected=" + (info != null && info.isConnected()) + " available=" + (info != null && info.isAvailable()) + " type=" + (info == null ? null : info.getType()) + " state=" + (info == null ? null : info.getState()) + " active=" + net + " caps=" + caps);
    }

    /** On the main thread, every 10 s: the SurfaceViews of the top activity, their size, visibility and whether their surface is valid. */
    static void dumpSurfaces(android.app.Activity top) {
        if (!Boolean.getBoolean("apklayer.geckodiag")) return;
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.postDelayed(new Runnable() {
            int n;
            @Override public void run() {
                StringBuilder sb = new StringBuilder("[apklayer] diag surfaces:");
                walk(top.getWindow().getDecorView(), sb, 0);
                System.out.println(sb);
                if (++n < 8) main.postDelayed(this, 10000);
            }
        }, 30000);
    }

    private static void walk(android.view.View v, StringBuilder sb, int depth) {
        if (v instanceof android.view.SurfaceView sv)
            sb.append("\n    ").append(v.getClass().getName()).append(' ').append(v.getWidth()).append('x').append(v.getHeight()).append(" vis=").append(v.getVisibility()).append(" shown=").append(v.isShown())
              .append(" valid=").append(sv.getHolder().getSurface().isValid()).append(" attached=").append(v.isAttachedToWindow());
        else if (v instanceof android.view.TextureView tv) sb.append("\n    TextureView ").append(v.getWidth()).append('x').append(v.getHeight()).append(" available=").append(tv.isAvailable());
        if (v instanceof android.view.ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), sb, depth + 1);
    }

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
