package org.apache.http.entity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class BasicHttpEntity extends AbstractHttpEntity {
    private InputStream content;
    private long length = -1;
    public BasicHttpEntity() {}
    @Override public long getContentLength() { return length; }
    public void setContentLength(long len) { this.length = len; }
    @Override public InputStream getContent() throws IllegalStateException {
        if (content == null) throw new IllegalStateException("Content has not been provided");
        return content;
    }
    public void setContent(InputStream instream) { this.content = instream; }
    @Override public boolean isRepeatable() { return false; }
    @Override public boolean isStreaming() { return content != null; }
    @Override public void writeTo(OutputStream outstream) throws IOException {
        InputStream in = getContent();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) outstream.write(buf, 0, n);
    }
}
