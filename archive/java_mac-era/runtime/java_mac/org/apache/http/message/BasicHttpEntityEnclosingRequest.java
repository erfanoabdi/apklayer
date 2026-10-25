package org.apache.http.message;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
public class BasicHttpEntityEnclosingRequest implements HttpEntityEnclosingRequest {
    private final String method;
    private final String uri;
    private final ProtocolVersion ver;
    private HttpEntity entity;
    private HttpParams params = new BasicHttpParams();
    public BasicHttpEntityEnclosingRequest(String method, String uri) { this(method, uri, null); }
    public BasicHttpEntityEnclosingRequest(String method, String uri, ProtocolVersion ver) {
        this.method = method; this.uri = uri; this.ver = ver;
    }
    @Override public RequestLine getRequestLine() { return null; }
    @Override public ProtocolVersion getProtocolVersion() { return ver; }
    @Override public boolean expectContinue() { return false; }
    @Override public HttpEntity getEntity()  { return entity; }
    @Override public void setEntity(HttpEntity entity) { this.entity = entity; }
    @Override public boolean containsHeader(String name) { return false; }
    @Override public Header[] getHeaders(String name) { return new Header[0]; }
    @Override public Header getFirstHeader(String name) { return null; }
    @Override public Header getLastHeader(String name) { return null; }
    @Override public Header[] getAllHeaders() { return new Header[0]; }
    @Override public void addHeader(Header header) {}
    @Override public void addHeader(String name, String value) {}
    @Override public void setHeader(Header header) {}
    @Override public void setHeader(String name, String value) {}
    @Override public void setHeaders(Header[] headers) {}
    @Override public void removeHeader(Header header) {}
    @Override public void removeHeaders(String name) {}
    @Override public HeaderIterator headerIterator() { return null; }
    @Override public HeaderIterator headerIterator(String name) { return null; }
    @Override public HttpParams getParams() { return params; }
    @Override public void setParams(HttpParams params) { this.params = params; }
}
