package android.net.http;
/** Base HTTP-engine exception. Source: frameworks/base .../net/http/HttpException.java */
public class HttpException extends java.io.IOException {
    public HttpException(String message, Throwable cause) { super(message, cause); }
}
