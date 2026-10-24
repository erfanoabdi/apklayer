package org.apache.http.conn;
import java.util.concurrent.TimeUnit;
import org.apache.http.conn.scheme.SchemeRegistry;
public interface ClientConnectionManager {
    SchemeRegistry getSchemeRegistry();
    ClientConnectionRequest requestConnection(Object route, Object state);
    void releaseConnection(Object conn, long validDuration, TimeUnit timeUnit);
    void closeIdleConnections(long idletime, TimeUnit tunit);
    void closeExpiredConnections();
    void shutdown();
}
