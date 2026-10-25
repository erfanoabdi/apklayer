package org.apache.http.conn.ssl;
import javax.net.ssl.SSLContext;
/** apache-httpclient 4.3 SSL socket factory. Mac.apk runs networking over the
 *  host JVM, so this holds the context but performs no Apache-managed connect. */
public class SSLConnectionSocketFactory {
    public static final String TLS = "TLS";
    private final SSLContext sslContext;
    public SSLConnectionSocketFactory(SSLContext sslContext) { this.sslContext = sslContext; }
    public SSLContext getSSLContext() { return sslContext; }
}
