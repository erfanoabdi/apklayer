package org.apache.http.impl.client;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
/** 4.3 fluent builder. build() yields a CloseableHttpClient whose transport is not
 *  wired (Mac.apk networking runs over the host JVM); all setters resolve. */
public class HttpClientBuilder {
    public static HttpClientBuilder create() { return new HttpClientBuilder(); }
    protected HttpClientBuilder() {}
    public final HttpClientBuilder useSystemProperties() { return this; }
    public final HttpClientBuilder setSSLSocketFactory(LayeredConnectionSocketFactory f) { return this; }
    public final HttpClientBuilder setRoutePlanner(HttpRoutePlanner p) { return this; }
    public final HttpClientBuilder setMaxConnTotal(int maxConnTotal) { return this; }
    public final HttpClientBuilder setMaxConnPerRoute(int maxConnPerRoute) { return this; }
    public final HttpClientBuilder setDefaultSocketConfig(SocketConfig config) { return this; }
    public final HttpClientBuilder setConnectionManager(HttpClientConnectionManager m) { return this; }
    public final HttpClientBuilder setUserAgent(String userAgent) { return this; }
    public final HttpClientBuilder disableRedirectHandling() { return this; }
    public final HttpClientBuilder disableAutomaticRetries() { return this; }
    public final HttpClientBuilder disableContentCompression() { return this; }
    public final HttpClientBuilder disableCookieManagement() { return this; }
    public CloseableHttpClient build() {
        return new CloseableHttpClient() {
            @Override protected HttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
                    throws IOException, ClientProtocolException {
                throw new IOException("Mac.apk: apache HttpClient transport is not wired; "
                        + "networking runs over the host JVM (java.net / OkHttp) path");
            }
        };
    }
}
