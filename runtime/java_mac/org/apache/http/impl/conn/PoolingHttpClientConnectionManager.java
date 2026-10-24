package org.apache.http.impl.conn;
import java.util.concurrent.TimeUnit;
import org.apache.http.conn.HttpClientConnectionManager;
public class PoolingHttpClientConnectionManager implements HttpClientConnectionManager {
    private int maxTotal = 20, defaultMaxPerRoute = 2, validateAfterInactivity = 2000;
    public PoolingHttpClientConnectionManager() {}
    public PoolingHttpClientConnectionManager(long timeToLive, TimeUnit tunit) {}
    public void setMaxTotal(int max) { this.maxTotal = max; }
    public int getMaxTotal() { return maxTotal; }
    public void setDefaultMaxPerRoute(int max) { this.defaultMaxPerRoute = max; }
    public int getDefaultMaxPerRoute() { return defaultMaxPerRoute; }
    public void setValidateAfterInactivity(int ms) { this.validateAfterInactivity = ms; }
    public int getValidateAfterInactivity() { return validateAfterInactivity; }
    @Override public void closeIdleConnections(long idletime, TimeUnit tunit) {}
    @Override public void closeExpiredConnections() {}
    @Override public void shutdown() {}
}
