package org.apache.http.entity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class EntityTemplate extends AbstractHttpEntity {
    private final ContentProducer contentproducer;
    public EntityTemplate(ContentProducer contentproducer) { this.contentproducer = contentproducer; }
    @Override public long getContentLength() { return -1; }
    @Override public InputStream getContent() throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        writeTo(buf);
        return new java.io.ByteArrayInputStream(buf.toByteArray());
    }
    @Override public boolean isRepeatable() { return true; }
    @Override public void writeTo(OutputStream outstream) throws IOException {
        if (contentproducer != null) contentproducer.writeTo(outstream);
    }
    @Override public boolean isStreaming() { return false; }
}
