package android.net.http;
/** A network-layer HTTP error. Source: frameworks/base .../net/http/NetworkException.java */
public abstract class NetworkException extends HttpException {
    public static final int ERROR_HOSTNAME_NOT_RESOLVED = 1;
    public static final int ERROR_INTERNET_DISCONNECTED = 2;
    public static final int ERROR_CONNECTION_TIMED_OUT = 4;
    public static final int ERROR_CONNECTION_REFUSED = 5;
    public static final int ERROR_OTHER = 11;
    public NetworkException(String message, Throwable cause) { super(message, cause); }
    public abstract int getErrorCode();
    public abstract boolean isImmediatelyRetryable();
}
