package org.apache.http.conn;
import java.util.concurrent.TimeUnit;
public interface ClientConnectionRequest {
    Object getConnection(long timeout, TimeUnit tunit) throws InterruptedException;
    void abortRequest();
}
