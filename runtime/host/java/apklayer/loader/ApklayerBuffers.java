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
 * shortcut: array() is refreshed from the buffer's memory at each call; a write through it reaches the buffer only when the app then
 * moves the position forward over it (position(int), the read-into-array() idiom: Cronet's InputStreamChannel, Okio-style readers):
 * the copy's [old, new) range is written into the memory. Other writes through the array stay in the copy. Upgrade if the JVM gains
 * array pinning for a buffer's lifetime.
 */
public final class ApklayerBuffers {
    private ApklayerBuffers() {}

    private static final class Key extends WeakReference<ByteBuffer> {
        final int hash;
        Key(ByteBuffer buffer, ReferenceQueue<ByteBuffer> queue) { super(buffer, queue); hash = System.identityHashCode(buffer); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) { return o == this || o instanceof Key k && k.get() != null && k.get() == get(); }
    }

    /** A buffer's array copy and the memory as it was when array() last handed it out (what the app changed is where they differ). */
    private static final class Lent {
        final byte[] copy, seen;
        Lent(int capacity) { copy = new byte[capacity]; seen = new byte[capacity]; }
    }

    private static final ReferenceQueue<ByteBuffer> gone = new ReferenceQueue<>();
    private static final Map<Key, Lent> appDirect = new HashMap<>();  // the app's allocateDirect buffers -> their array copy, made on first array()

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
        Lent lent;
        synchronized (ApklayerBuffers.class) { lent = appDirect.computeIfAbsent(new Key(b, null), k -> new Lent(b.capacity())); }
        synchronized (lent) {
            b.duplicate().clear().get(lent.copy);  // the whole capacity, as ART's backing array; the buffer's own position and limit stay (an absolute get stops at the limit: Cronet's partly filled read buffers threw, YouTube's requests failed)
            System.arraycopy(lent.copy, 0, lent.seen, 0, lent.copy.length);
        }
        return lent.copy;
    }

    public static int arrayOffset(ByteBuffer b) { return tagged(b) ? 0 : b.arrayOffset(); }

    /** An app's position(int): bytes it changed through array() in the range it now steps past go into the buffer's memory first. */
    public static java.nio.Buffer position(ByteBuffer b, int position) { return positionByteBuffer(b, position); }

    public static ByteBuffer positionByteBuffer(ByteBuffer b, int position) {
        int old = b.position();
        if (position > old && position <= b.limit() && !b.hasArray() && !b.isReadOnly()) {
            Lent lent;
            synchronized (ApklayerBuffers.class) { lent = appDirect.get(new Key(b, null)); }
            if (lent != null) synchronized (lent) {
                // only bytes the app wrote into the copy since array(): memory written meanwhile by native code or put() is kept
                for (int i = old; i < position; i++)
                    if (lent.copy[i] != lent.seen[i]) { b.put(i, lent.copy[i]); lent.seen[i] = lent.copy[i]; }
            }
        }
        return b.position(position);
    }
}
