package android.net.http;
/** Deprecated Apache-based HTTP client (removed from the platform long ago).
 *  Type-only handle; use java.net / HttpEngine instead. */
public final class AndroidHttpClient {
    private AndroidHttpClient() {}
    public static AndroidHttpClient newInstance(String userAgent) { return new AndroidHttpClient(); }
    public static AndroidHttpClient newInstance(String userAgent, android.content.Context context) { return new AndroidHttpClient(); }
    public void close() {}
}
