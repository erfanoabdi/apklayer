package org.apache.http.client.methods;
import java.net.URI;
public class HttpGet extends HttpRequestBase {
    public static final String METHOD_NAME = "GET";
    public HttpGet() {}
    public HttpGet(URI uri)    { setURI(uri); }
    public HttpGet(String uri) { setURI(URI.create(uri)); }
    @Override public String getMethod() { return METHOD_NAME; }
}
