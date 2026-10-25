package org.apache.http.impl.conn.tsccm;
import java.util.concurrent.TimeUnit;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.params.HttpParams;
public class ThreadSafeClientConnManager implements ClientConnectionManager {
    private final SchemeRegistry registry;
    public ThreadSafeClientConnManager(HttpParams params, SchemeRegistry registry) { this.registry = registry; }
    public ThreadSafeClientConnManager(SchemeRegistry registry) { this.registry = registry; }
    public ThreadSafeClientConnManager() { this(new SchemeRegistry()); }
    @Override public SchemeRegistry getSchemeRegistry() { return registry; }
    @Override public ClientConnectionRequest requestConnection(Object route, Object state) { return null; }
    @Override public void releaseConnection(Object conn, long validDuration, TimeUnit unit) {}
    @Override public void closeIdleConnections(long idletime, TimeUnit tunit) {}
    @Override public void closeExpiredConnections() {}
    @Override public void shutdown() {}
}
