package org.apache.http.conn.params;
import org.apache.http.params.HttpParams;
/** Connection-manager parameter names + setters. Inert on Mac.apk (no pooled
 *  Apache transport runs) but the parameter contract resolves. */
public final class ConnManagerParams {
    public static final String MAX_TOTAL_CONNECTIONS = "http.conn-manager.max-total";
    public static final String MAX_CONNECTIONS_PER_ROUTE = "http.conn-manager.max-per-route";
    public static final String TIMEOUT = "http.conn-manager.timeout";
    private ConnManagerParams() {}
    public static void setMaxTotalConnections(HttpParams params, int maxTotalConnections) {
        params.setIntParameter(MAX_TOTAL_CONNECTIONS, maxTotalConnections);
    }
    public static int getMaxTotalConnections(HttpParams params) {
        return params.getIntParameter(MAX_TOTAL_CONNECTIONS, 20);
    }
    public static void setMaxConnectionsPerRoute(HttpParams params, ConnPerRoute connPerRoute) {
        params.setParameter(MAX_CONNECTIONS_PER_ROUTE, connPerRoute);
    }
    public static void setTimeout(HttpParams params, long timeout) {
        params.setLongParameter(TIMEOUT, timeout);
    }
    public static long getTimeout(HttpParams params) {
        return params.getLongParameter(TIMEOUT, 0);
    }
}
