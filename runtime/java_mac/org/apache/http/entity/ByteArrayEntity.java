package org.apache.http.entity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class ByteArrayEntity extends AbstractHttpEntity {
    protected final byte[] content;
    public ByteArrayEntity(byte[] b) { this.content = b != null ? b : new byte[0]; }
    @Override public boolean isRepeatable() { return true; }
    @Override public long getContentLength() { return content.length; }
    @Override public InputStream getContent() { return new ByteArrayInputStream(content); }
    @Override public void writeTo(OutputStream outstream) throws IOException { outstream.write(content); }
    @Override public boolean isStreaming() { return false; }
}
