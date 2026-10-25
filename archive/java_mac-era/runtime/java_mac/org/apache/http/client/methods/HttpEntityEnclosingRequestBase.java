package org.apache.http.client.methods;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
/** Base for request methods that carry an entity (POST/PUT). */
public abstract class HttpEntityEnclosingRequestBase extends HttpRequestBase
        implements HttpEntityEnclosingRequest {
    private HttpEntity entity;
    public HttpEntityEnclosingRequestBase() {}
    @Override public HttpEntity getEntity() { return entity; }
    @Override public void setEntity(HttpEntity entity) { this.entity = entity; }
    @Override public boolean expectContinue() {
        org.apache.http.Header expect = getFirstHeader("Expect");
        return expect != null && "100-continue".equalsIgnoreCase(expect.getValue());
    }
}
