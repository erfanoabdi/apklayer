package org.apache.http.conn.params;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.conn.routing.HttpRoute;
public final class ConnPerRouteBean implements ConnPerRoute {
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 2;
    private final Map<HttpRoute, Integer> maxPerHostMap = new HashMap<>();
    private int defaultMax;
    public ConnPerRouteBean(int defaultMax) { this.defaultMax = defaultMax; }
    public ConnPerRouteBean() { this(DEFAULT_MAX_CONNECTIONS_PER_ROUTE); }
    public int getDefaultMax() { return defaultMax; }
    public void setDefaultMaxPerRoute(int max) { this.defaultMax = max; }
    public void setMaxForRoute(HttpRoute route, int max) { maxPerHostMap.put(route, max); }
    @Override public int getMaxForRoute(HttpRoute route) {
        Integer max = maxPerHostMap.get(route);
        return max != null ? max : defaultMax;
    }
}
