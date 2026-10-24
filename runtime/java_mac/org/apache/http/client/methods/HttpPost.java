package org.apache.http.client.methods;
import java.net.URI;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
public class HttpPost extends HttpRequestBase implements HttpEntityEnclosingRequest {
    public static final String METHOD_NAME = "POST";
    private HttpEntity entity;
    public HttpPost() {}
    public HttpPost(URI uri)    { setURI(uri); }
    public HttpPost(String uri) { setURI(URI.create(uri)); }
    @Override public String getMethod() { return METHOD_NAME; }
    @Override public boolean expectContinue() { return false; }
    @Override public HttpEntity getEntity() { return entity; }
    @Override public void setEntity(HttpEntity entity) { this.entity = entity; }
}
