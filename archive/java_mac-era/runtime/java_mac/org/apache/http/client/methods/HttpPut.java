package org.apache.http.client.methods;
import java.net.URI;
public class HttpPut extends HttpRequestBase {
    public static final String METHOD_NAME = "PUT";
    public HttpPut() {}
    public HttpPut(URI uri)    { setURI(uri); }
    public HttpPut(String uri) { setURI(URI.create(uri)); }
    @Override public String getMethod() { return METHOD_NAME; }
}
