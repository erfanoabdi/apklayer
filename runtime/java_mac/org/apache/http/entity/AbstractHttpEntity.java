package org.apache.http.entity;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
/** Common base for HttpEntity impls: content-type / content-encoding / chunked. */
public abstract class AbstractHttpEntity implements HttpEntity {
    protected Header contentType;
    protected Header contentEncoding;
    protected boolean chunked;
    protected AbstractHttpEntity() {}
    public Header getContentType() { return contentType; }
    public Header getContentEncoding() { return contentEncoding; }
    public boolean isChunked() { return chunked; }
    public void setContentType(Header contentType) { this.contentType = contentType; }
    public void setContentType(String ctString) {
        this.contentType = ctString != null ? new BasicHeader("Content-Type", ctString) : null;
    }
    public void setContentEncoding(Header contentEncoding) { this.contentEncoding = contentEncoding; }
    public void setContentEncoding(String ceString) {
        this.contentEncoding = ceString != null ? new BasicHeader("Content-Encoding", ceString) : null;
    }
    public void setChunked(boolean b) { this.chunked = b; }
    @Override public void consumeContent() throws java.io.IOException {}
}
