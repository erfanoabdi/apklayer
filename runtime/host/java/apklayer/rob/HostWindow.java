package apklayer.rob;

import java.lang.reflect.Method;

/**
 * Host presenter. Two modes:
 * - shared framebuffer (-Dapklayer.fb=/dev/shm/...): pure Java; a native apklayer-presentd
 *   process maps the file and presents it (works when this JVM's arch differs from the
 *   host's, e.g. x86_64 under Rosetta for Robolectric native graphics).
 * - in-process: reflective bridge to apklayer.wayland.WlWindow. The class bytes are loaded with a PLAIN
 * app classloader (not the sandbox instrumentor), so JNI bindings stay intact; the
 * instance is then exposed to sandbox code (which may see it as Object).
 */
public final class HostWindow {
    private static HostWindow INSTANCE;

    private final Object window;
    private final Method present;
    private final Method shouldClose;
    private final Method pump;

    private HostWindow(Object window, Method present, Method shouldClose, Method pump) {
        this.window = window;
        this.present = present;
        this.shouldClose = shouldClose;
        this.pump = pump;
    }

    // Shared framebuffer layout: see bridge/src/presentd.c.
    private static final int FB_MAGIC = 0x4C4B5041, EV_RING = 64, FB_HDR = 8 + 4 * EV_RING;
    private java.nio.IntBuffer fb;
    private int seq, evRead;
    private Method nextEvent;

    private HostWindow(java.nio.IntBuffer fb) {
        this(null, null, null, null);
        this.fb = fb;
    }

    public static HostWindow get(int w, int h) {
        if (INSTANCE != null) return INSTANCE;
        String fbPath = System.getProperty("apklayer.fb");
        if (fbPath != null) {
            try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                    java.nio.file.Paths.get(fbPath), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                java.nio.IntBuffer fb = ch.map(java.nio.channels.FileChannel.MapMode.READ_WRITE,
                        0, 4L * (FB_HDR + w * h)).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                fb.put(1, w).put(2, h).put(0, FB_MAGIC);
                INSTANCE = new HostWindow(fb);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> fb.put(0, 0)));
                System.out.println("[apklayer-rob] shared framebuffer " + fbPath + " " + w + "x" + h);
                return INSTANCE;
            } catch (Exception e) {
                System.out.println("[apklayer-rob] shared framebuffer failed: " + e);
                return null;
            }
        }
        try {
            // The native lib is loaded by WlWindow's static init inside the plain loader;
            // loading it here too would bind it to the sandbox loader first.
            String cp = System.getProperty("apklayer.hostcp");
            java.net.URL[] urls = { new java.io.File(cp).toURI().toURL() };
            java.net.URLClassLoader plain =
                    new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            Class<?> win = Class.forName("apklayer.wayland.WlWindow", true, plain);
            boolean ok = (Boolean) win.getMethod("init", int.class, int.class)
                    .invoke(null, w, h);
            if (!ok) return null;
            INSTANCE = new HostWindow(win,
                    win.getMethod("present", int[].class, int.class, int.class),
                    win.getMethod("shouldClose"),
                    win.getMethod("pump", int.class));
            INSTANCE.nextEvent = win.getMethod("nextEvent", int[].class);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { win.getMethod("pump", int.class).invoke(null, 0); } catch (Exception ignored) {}
            }));
            return INSTANCE;
        } catch (Throwable t) {
            System.out.println("[apklayer-rob] host window load failed: " + t);
            return null;
        }
    }

    public void present(int[] px, int w, int h) throws Exception {
        if (fb != null) {
            fb.put(FB_HDR, px, 0, w * h).put(3, ++seq);
            return;
        }
        present.invoke(null, px, w, h);
        pump.invoke(null, 0);
    }

    /** Next input event {type, a, b, 0} (see bridge/src/jni/present.c), or false if none. */
    public boolean nextEvent(int[] ev) throws Exception {
        if (fb != null) {
            if (evRead == fb.get(5)) return false;
            fb.get(8 + 4 * (Integer.remainderUnsigned(evRead++, EV_RING)), ev, 0, 4);
            return true;
        }
        return (Boolean) nextEvent.invoke(null, (Object) ev);
    }

    public boolean shouldClose() throws Exception {
        if (fb != null) return fb.get(4) != 0;
        return (Boolean) shouldClose.invoke(null);
    }
}
