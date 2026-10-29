package apklayer.host;

import java.lang.reflect.Field;

/**
 * Starts the sandbox clock at CLOCK_MONOTONIC, as Android's SystemClock.uptimeMillis and System.nanoTime are. Robolectric starts it at
 * 100 ms; native code that compares the Choreographer's frame time with its own monotonic clock (Gecko's vsync: timestamps hours in
 * the past are dropped, nothing ticks) then disagrees with Java.
 */
final class HostClock {
    private HostClock() {}

    static void alignToMonotonic() {
        try {
            Class<?> c = Class.forName("org.robolectric.shadows.ShadowPausedSystemClock");
            long now = System.nanoTime();  // the JVM's: CLOCK_MONOTONIC
            for (String name : new String[] {"currentUptimeNs", "currentRealtimeNs"}) {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.setLong(null, now);
            }
        } catch (ReflectiveOperationException e) {
            System.out.println("[apklayer] clock not aligned: " + e);
        }
    }
}
