package apklayer.host;

import org.robolectric.annotation.internal.DoNotInstrument;

/** The converter's target for {@code ClassLoader.getSystemClassLoader()} in an app: Android's is a PathClassLoader (the app's), the JVM's is the JDK's. */
@DoNotInstrument
public final class GuestLoaders {
    private GuestLoaders() {}

    /** Runtime.maxMemory as ART reports it: the growth limit of the device's heap (dalvik.vm.heapgrowthlimit, "256m"), the host's own when that is smaller. */
    public static long maxMemory(Runtime runtime) {
        long host = runtime.maxMemory();
        String limit = android.os.SystemProperties.get("dalvik.vm.heapgrowthlimit", "");
        try {
            if (limit.length() < 2) return host;
            long unit = switch (Character.toLowerCase(limit.charAt(limit.length() - 1))) { case 'k' -> 1L << 10; case 'm' -> 1L << 20; case 'g' -> 1L << 30; default -> 0; };
            long bytes = unit == 0 ? Long.parseLong(limit) : Long.parseLong(limit.substring(0, limit.length() - 1)) * unit;
            return Math.min(host, bytes);
        } catch (NumberFormatException bad) { return host; }
    }

    public static ClassLoader getSystemClassLoader() {
        ClassLoader app = AppClassLoader.app;
        return app != null ? app : ClassLoader.getSystemClassLoader();
    }
}
