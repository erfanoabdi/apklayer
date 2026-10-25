package org.apache.http;
import java.io.IOException;
public interface HttpConnection {
    void close() throws IOException;
    boolean isOpen();
    boolean isStale();
    void setSocketTimeout(int timeout);
    int  getSocketTimeout();
    void shutdown() throws IOException;
    HttpConnectionMetrics getMetrics();
}
