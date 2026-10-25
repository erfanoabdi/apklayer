package org.apache.http.client.methods;
import java.net.URI;
public class HttpTrace extends HttpRequestBase {
    public static final String METHOD_NAME = "TRACE";
    public HttpTrace() {}
    public HttpTrace(URI uri)    { setURI(uri); }
    public HttpTrace(String uri) { setURI(URI.create(uri)); }
    @Override public String getMethod() { return METHOD_NAME; }
}
