package org.apache.http.impl.client;
import java.io.IOException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
public class DefaultHttpRequestRetryHandler implements HttpRequestRetryHandler {
    private final int retryCount;
    private final boolean requestSentRetryEnabled;
    public DefaultHttpRequestRetryHandler() { this(3, false); }
    public DefaultHttpRequestRetryHandler(int retryCount, boolean requestSentRetryEnabled) {
        this.retryCount = retryCount; this.requestSentRetryEnabled = requestSentRetryEnabled;
    }
    public int getRetryCount() { return retryCount; }
    public boolean isRequestSentRetryEnabled() { return requestSentRetryEnabled; }
    @Override public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        return executionCount <= retryCount;
    }
}
