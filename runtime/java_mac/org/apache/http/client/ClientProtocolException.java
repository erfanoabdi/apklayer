package org.apache.http.client;
import java.io.IOException;
public class ClientProtocolException extends IOException {
    public ClientProtocolException() {}
    public ClientProtocolException(String message) { super(message); }
    public ClientProtocolException(Throwable cause) { initCause(cause); }
    public ClientProtocolException(String message, Throwable cause) { super(message); initCause(cause); }
}
