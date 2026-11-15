package hello.runtime;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** An app's direct ByteBuffer has an array on Android; bytes read into it reach the buffer once the position moves past them. */
public final class BufferChecks {
    private BufferChecks() {}

    public static void run() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        if (!buffer.hasArray()) throw new AssertionError("direct buffer without an array");
        buffer.put((byte) 7);  // memory written directly, before the array is taken
        // Cronet's InputStreamChannel: read into array() at arrayOffset + position, then advance the position
        InputStream in = new ByteArrayInputStream(new byte[] {10, 20, 30, 40});
        int n = in.read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        buffer.position(buffer.position() + n);
        buffer.flip();
        byte[] got = new byte[buffer.remaining()];
        buffer.get(got);
        if (got.length != 5 || got[0] != 7 || got[1] != 10 || got[2] != 20 || got[3] != 30 || got[4] != 40)
            throw new AssertionError("bytes read into array() did not reach the buffer: " + java.util.Arrays.toString(got));
        // bytes put into the memory after array() are kept when the position steps over them
        buffer.clear();
        byte[] array = buffer.array();
        buffer.put(0, (byte) 99);
        array[1] = 55;
        buffer.position(2);
        if (buffer.get(0) != 99 || buffer.get(1) != 55) throw new AssertionError("write-back overwrote newer memory: " + buffer.get(0) + "," + buffer.get(1));
    }
}
