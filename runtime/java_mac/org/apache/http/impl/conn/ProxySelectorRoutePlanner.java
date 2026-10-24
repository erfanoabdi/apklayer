package org.apache.http.impl.conn;
import java.net.ProxySelector;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.protocol.HttpContext;
public class ProxySelectorRoutePlanner implements HttpRoutePlanner {
    private final SchemeRegistry schemeRegistry;
    private ProxySelector proxySelector;
    public ProxySelectorRoutePlanner(SchemeRegistry schemeRegistry, ProxySelector proxySelector) {
        this.schemeRegistry = schemeRegistry; this.proxySelector = proxySelector;
    }
    public void setProxySelector(ProxySelector proxySelector) { this.proxySelector = proxySelector; }
    @Override public HttpRoute determineRoute(HttpHost target, HttpRequest request, HttpContext context) throws HttpException {
        return new HttpRoute(target);
    }
}
