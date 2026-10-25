package org.apache.http.conn.routing;
import org.apache.http.HttpHost;
public final class HttpRoute {
    private final HttpHost target;
    public HttpRoute(HttpHost target) { this.target = target; }
    public HttpHost getTargetHost() { return target; }
    public HttpHost getProxyHost() { return null; }
    public int getHopCount() { return 1; }
    public boolean isSecure() { return false; }

    public boolean isTunnelled() { return false; }
}

