package org.apache.http.client.methods;
import java.net.URI;
public class HttpOptions extends HttpRequestBase {
    public static final String METHOD_NAME = "OPTIONS";
    public HttpOptions() {}
    public HttpOptions(URI uri)    { setURI(uri); }
    public HttpOptions(String uri) { setURI(URI.create(uri)); }
    @Override public String getMethod() { return METHOD_NAME; }
}
