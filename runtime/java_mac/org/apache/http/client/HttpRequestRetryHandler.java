package org.apache.http.client;
import java.io.IOException;
import org.apache.http.protocol.HttpContext;
/** Strategy deciding whether a failed request is worth retrying. */
public interface HttpRequestRetryHandler {
    boolean retryRequest(IOException exception, int executionCount, HttpContext context);
}
