package apklayer.wayland;

/**
 * Thin wrapper over libapklayer-wl (bridge/src/jni/present.c).
 * Loaded via the dynamic linker (sun.misc Unsafe / System.load on the plain path);
 * only used from the driver main() — never from inside the Robolectric sandbox.
 */
public final class WlWindow {
    static {
        String lib = System.getProperty("apklayer.wl");
        if (lib != null) System.load(lib);
    }

    public static native boolean init(int w, int h);
    public static native int present(int[] argb, int w, int h);
    public static native void pump(int timeoutMs);
    public static native boolean shouldClose();
    /** Dequeues one input event into ev[4] (layout: bridge/src/jni/present.c). */
    public static native boolean nextEvent(int[] ev);
}
