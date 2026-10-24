package org.apache.http.entity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
public class StringEntity implements HttpEntity {
    private final byte[] content;
    private Header contentType;
    public StringEntity(String s, String charset) throws UnsupportedEncodingException {
        if (charset == null) charset = "UTF-8";
        content = s != null ? s.getBytes(charset) : new byte[0];
    }
    public StringEntity(String s) throws UnsupportedEncodingException { this(s, "UTF-8"); }
    public void setContentType(Header h) { this.contentType = h; }
    @Override public boolean isRepeatable() { return true; }
    @Override public boolean isChunked()    { return false; }
    @Override public long getContentLength() { return content.length; }
    @Override public Header getContentType() { return contentType; }
    @Override public Header getContentEncoding() { return null; }
    @Override public InputStream getContent() { return new ByteArrayInputStream(content); }
    @Override public void writeTo(OutputStream outstream) throws IOException { outstream.write(content); }
    @Override public boolean isStreaming()    { return false; }
    @Override public void consumeContent()    {}

    public void setContentType(String ctString) {
        this.contentType = ctString != null
            ? new org.apache.http.message.BasicHeader("Content-Type", ctString) : null;
    }
}

