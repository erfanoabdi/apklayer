package android.net.http;
import java.io.Closeable;
import java.nio.ByteBuffer;
/** Provides request-body bytes for an upload. Source: frameworks/base .../net/http/UploadDataProvider.java */
public abstract class UploadDataProvider implements Closeable {
    public abstract long getLength() throws java.io.IOException;
    public abstract void read(UploadDataSink uploadDataSink, ByteBuffer byteBuffer)
            throws java.io.IOException;
    public abstract void rewind(UploadDataSink uploadDataSink) throws java.io.IOException;
    @Override public void close() throws java.io.IOException { }
}
