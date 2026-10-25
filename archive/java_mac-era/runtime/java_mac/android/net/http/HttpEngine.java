package android.net.http;

import android.content.Context;

import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;

/**
 * The platform HTTP engine (android.net.http, API 34 -- the AOSP-shipped Cronet).
 * openConnection() returns a real java.net URLConnection; newUrlRequestBuilder
 * builds a real UrlRequest (see that class). Most apps bundle their own Cronet,
 * so this is the fallback for the few that use the platform engine.
 *
 * Source: frameworks/base .../net/http/HttpEngine.java
 */
public class HttpEngine {

    HttpEngine() { }

    public static String getVersionString() { return "Mac.apk-platform-http/1"; }

    public void shutdown() { }

    public URLConnection openConnection(URL url) throws java.io.IOException {
        return url.openConnection();
    }

    public java.net.URLStreamHandlerFactory createUrlStreamHandlerFactory() {
        return new java.net.URLStreamHandlerFactory() {
            @Override public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
                return null;  // fall back to the JVM's default handlers
            }
        };
    }

    public void bindToNetwork(android.net.Network network) { }

    public UrlRequest.Builder newUrlRequestBuilder(String url, Executor executor,
            UrlRequest.Callback callback) {
        return new UrlRequest.Builder(url, executor, callback);
    }

    public BidirectionalStream.Builder newBidirectionalStreamBuilder(String url,
            Executor executor, BidirectionalStream.Callback callback) {
        return new BidirectionalStream.Builder();
    }

    public static final class Builder {
        public Builder(Context context) { }
        public String getDefaultUserAgent() { return "Mac.apk-platform-http/1"; }
        public Builder setUserAgent(String userAgent) { return this; }
        public Builder setEnablePublicKeyPinningBypassForLocalTrustAnchors(boolean value) { return this; }
        public Builder setEnableHttpCache(int cacheMode, long maxSize) { return this; }
        public Builder setStoragePath(String value) { return this; }
        public Builder setEnableQuic(boolean value) { return this; }
        public Builder setEnableHttp2(boolean value) { return this; }
        public Builder setEnableBrotli(boolean value) { return this; }
        public Builder addQuicHint(String host, int port, int alternatePort) { return this; }
        public Builder addPublicKeyPins(String hostName, java.util.Set<byte[]> pinsSha256,
                boolean includeSubdomains, java.time.Instant expirationInstant) { return this; }
        public Builder setConnectionMigrationOptions(ConnectionMigrationOptions options) { return this; }
        public Builder setDnsOptions(DnsOptions options) { return this; }
        public Builder setQuicOptions(QuicOptions options) { return this; }
        public HttpEngine build() { return new HttpEngine(); }
    }
}
