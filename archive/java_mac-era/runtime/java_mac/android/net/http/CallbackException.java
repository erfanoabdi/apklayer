package android.net.http;
public abstract class CallbackException extends HttpException {
    protected CallbackException(String message, Throwable cause) { super(message, cause); }
}
