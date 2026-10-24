package org.apache.http.client.methods;
import java.net.URI;
public class HttpHead extends HttpRequestBase {
    public static final String METHOD_NAME = "HEAD";
    public HttpHead() {}
    public HttpHead(URI uri)    { setURI(uri); }
    public HttpHead(String uri) { setURI(URI.create(uri)); }
    @Override public String getMethod() { return METHOD_NAME; }
}
