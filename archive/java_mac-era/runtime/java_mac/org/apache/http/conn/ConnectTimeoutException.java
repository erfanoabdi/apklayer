package org.apache.http.conn;
import java.io.InterruptedIOException;
/** Signals a connect-phase timeout. apache-httpclient extends
 *  InterruptedIOException so socket-timeout catch blocks also catch it. */
public class ConnectTimeoutException extends InterruptedIOException {
    public ConnectTimeoutException() { super(); }
    public ConnectTimeoutException(String message) { super(message); }
}
