package android.net.http;
public abstract class QuicException extends NetworkException {
    protected QuicException(String message, Throwable cause) { super(message, cause); }
}
