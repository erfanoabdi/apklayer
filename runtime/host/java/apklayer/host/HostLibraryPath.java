package apklayer.host;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Apps that load system libraries by name (SoLoader's "liblog.so") search the directories of System.getenv("LD_LIBRARY_PATH"), on a device the system library
 * directories. On the host that variable belongs to the glibc loader; the Android libraries are added to the copy the app can read, not to the real environment
 * (children of this JVM must not get the Android libc on their glibc path).
 */
final class HostLibraryPath {
    private HostLibraryPath() {}
    @SuppressWarnings("unchecked")
    static void install() {
        String android = System.getenv("BIONIC_LD_LIBRARY_PATH");
        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] library path: android=" + android + " LD=" + System.getenv("LD_LIBRARY_PATH"));
        if (android == null || android.isEmpty()) return;
        try {
            Class<?> environment = Class.forName("java.lang.ProcessEnvironment");
            Field view = environment.getDeclaredField("theUnmodifiableEnvironment");
            view.setAccessible(true);
            Map<String, String> unmodifiable = (Map<String, String>) view.get(null);
            Field inner = unmodifiable.getClass().getDeclaredField("m");
            inner.setAccessible(true);
            Map<String, String> copy = new HashMap<>(unmodifiable);
            String old = copy.get("LD_LIBRARY_PATH");
            copy.put("LD_LIBRARY_PATH", (old == null || old.isEmpty() ? "" : old + ":") + android + ":" + System.getProperty("apklayer.native") + "/android-system-stubs");
            inner.set(unmodifiable, copy);
        } catch (ReflectiveOperationException | RuntimeException failure) { System.out.println("[apklayer] library path not exposed: " + failure); }
    }
}
