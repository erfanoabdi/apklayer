package apklayer.loader;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Target of the converter's ByteBuffer redirect (dex2jvm HostDefaults.REDIRECT_BUFFER): an app's ByteBuffer.allocateDirect,
 * hasArray, array and arrayOffset calls arrive here, the receiver first. On Android a buffer from allocateDirect has an array
 * (libcore allocates it over a non-movable byte[]); code such as WebRTC's audio capture refuses one without. HotSpot's has none,
 * and its moving collector cannot pin a byte[] under the native address, so the app's own direct buffers report an array that is
 * a copy of their memory. Framework and JDK code keep HotSpot's buffers as they are (only converted app bytecode is routed here).
 * shortcut: array() is refreshed from the buffer's memory at each call; writes through it never reach the buffer (or native code
 * reading its address). Upgrade if the JVM gains array pinning for a buffer's lifetime.
 */
public final class ApklayerBuffers {
    private ApklayerBuffers() {}

    private static final class Key extends WeakReference<ByteBuffer> {
        final int hash;
        Key(ByteBuffer buffer, ReferenceQueue<ByteBuffer> queue) { super(buffer, queue); hash = System.identityHashCode(buffer); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) { return o == this || o instanceof Key k && k.get() != null && k.get() == get(); }
    }

    private static final ReferenceQueue<ByteBuffer> gone = new ReferenceQueue<>();
    private static final Map<Key, byte[]> appDirect = new HashMap<>();  // the app's allocateDirect buffers -> their array copy, made on first array()

    public static synchronized ByteBuffer allocateDirect(int capacity) {
        for (Object k; (k = gone.poll()) != null; ) appDirect.remove(k);
        ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
        appDirect.put(new Key(buffer, gone), null);
        return buffer;
    }

    private static synchronized boolean tagged(ByteBuffer b) { return !b.hasArray() && appDirect.containsKey(new Key(b, null)); }

    public static boolean hasArray(ByteBuffer b) { return b.hasArray() || tagged(b); }

    public static byte[] array(ByteBuffer b) {
        if (!tagged(b)) return b.array();
        byte[] copy;
        synchronized (ApklayerBuffers.class) { copy = appDirect.computeIfAbsent(new Key(b, null), k -> new byte[b.capacity()]); }
        b.get(0, copy);  // absolute bulk get: the position stays
        return copy;
    }

    public static int arrayOffset(ByteBuffer b) { return tagged(b) ? 0 : b.arrayOffset(); }
}
