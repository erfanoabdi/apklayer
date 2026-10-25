package org.apache.http.conn;
import java.util.concurrent.TimeUnit;
/** apache-httpclient 4.3 connection manager. Inert on Mac.apk. */
public interface HttpClientConnectionManager {
    void closeIdleConnections(long idletime, TimeUnit tunit);
    void closeExpiredConnections();
    void shutdown();
}
