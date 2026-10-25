package org.apache.http.impl.conn;
import java.net.ProxySelector;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.protocol.HttpContext;
public class SystemDefaultRoutePlanner implements HttpRoutePlanner {
    private final ProxySelector proxySelector;
    public SystemDefaultRoutePlanner(ProxySelector proxySelector) { this.proxySelector = proxySelector; }
    @Override public HttpRoute determineRoute(HttpHost target, HttpRequest request, HttpContext context) throws HttpException {
        return new HttpRoute(target);
    }
}
