package apklayer.rob;

import java.lang.reflect.Method;

/**
 * Reflective bridge to apklayer.wayland.WlWindow. The class bytes are loaded with a PLAIN
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

    public static HostWindow get(int w, int h) {
        if (INSTANCE != null) return INSTANCE;
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
        present.invoke(null, px, w, h);
        pump.invoke(null, 0);
    }

    public boolean shouldClose() throws Exception {
        return (Boolean) shouldClose.invoke(null);
    }
}
