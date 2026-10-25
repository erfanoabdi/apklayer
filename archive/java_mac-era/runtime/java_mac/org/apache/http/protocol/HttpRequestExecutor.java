package org.apache.http.protocol;
/** Sends a request and receives a response over a connection. Mac.apk drives no
 *  apache transport, so this is a structural holder. */
public class HttpRequestExecutor {
    public static final int DEFAULT_WAIT_FOR_CONTINUE = 3000;
    private final int waitForContinue;
    public HttpRequestExecutor() { this(DEFAULT_WAIT_FOR_CONTINUE); }
    public HttpRequestExecutor(int waitForContinue) { this.waitForContinue = waitForContinue; }
    public int getWaitForContinue() { return waitForContinue; }
}
