// apklayer.loader.ApklayerBuffers (the converter's ByteBuffer redirect): array() of an app's direct buffer whose limit is below its capacity (a partly
// filled Cronet read buffer: YouTube's requests failed with IndexOutOfBoundsException), and the write-back through position(). Run by meson (suite bionic).
import java.nio.ByteBuffer;
import apklayer.loader.ApklayerBuffers;
public class ApklayerBuffersTest {
    public static void main(String[] a) {
        ByteBuffer b = ApklayerBuffers.allocateDirect(8192);
        for (int i = 0; i < 8035; i++) b.put((byte) i);
        b.flip();  // limit 8035 < capacity 8192: a response chunk
        if (!ApklayerBuffers.hasArray(b)) throw new AssertionError("no array");
        byte[] arr = ApklayerBuffers.array(b);
        if (arr.length != 8192) throw new AssertionError("array length " + arr.length);
        for (int i = 0; i < 8035; i++) if (arr[i] != (byte) i) throw new AssertionError("byte " + i);
        arr[10] = 42; ApklayerBuffers.position(b, 20);  // the read-into-array idiom writes back
        if (b.get(10) != 42) throw new AssertionError("write-back");
        System.out.println("PASS");
    }
}
