package org.apache.http.conn.params;
import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
public final class ConnRouteParams {
    public static final String DEFAULT_PROXY = "http.route.default-proxy";
    public static final String LOCAL_ADDRESS = "http.route.local-address";
    public static final String FORCED_ROUTE = "http.route.forced-route";
    private ConnRouteParams() {}
    public static HttpHost getDefaultProxy(HttpParams params) {
        return (HttpHost) params.getParameter(DEFAULT_PROXY);
    }
    public static void setDefaultProxy(HttpParams params, HttpHost proxy) {
        params.setParameter(DEFAULT_PROXY, proxy);
    }
}
