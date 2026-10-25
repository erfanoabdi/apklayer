package android.media;
import java.io.Closeable;
public abstract class MediaDataSource implements Closeable {
    public abstract int readAt(long position, byte[] buffer, int offset, int size)
        throws java.io.IOException;
    public abstract long getSize() throws java.io.IOException;
}
