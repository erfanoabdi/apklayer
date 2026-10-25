package org.apache.http.client.methods;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpMessage;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
public abstract class HttpRequestBase implements HttpUriRequest, AbortableHttpRequest {
    protected URI uri;
    protected HttpParams params = new BasicHttpParams();
    public HttpRequestBase() {}
    public abstract String getMethod();
    @Override public URI getURI() { return uri; }
    public void setURI(URI uri) { this.uri = uri; }
    @Override public ProtocolVersion getProtocolVersion() { return null; }
    @Override public RequestLine getRequestLine() { return null; }
    @Override public void abort() {}
    @Override public boolean isAborted() { return false; }
    @Override public void setConnectionRequest(org.apache.http.conn.ClientConnectionRequest req) {}
    @Override public void setReleaseTrigger(org.apache.http.conn.ConnectionReleaseTrigger t) {}
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
