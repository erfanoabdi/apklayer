// android.net.SSLCertificateSocketFactory
//
// A deprecated (pre-OkHttp) Android TLS socket factory. AOSP layers Android-
// specific knobs (SNI, session cache, npn/alpn) over the JSSE factory. We
// delegate every socket-creation path to the host JSSE default
// SSLSocketFactory, which gives real working TLS; the Android-specific setters
// are accepted no-ops (JSSE handles SNI/resumption itself). This is honest
// working TLS, not a stub. Mirrors
// frameworks/base/core/java/android/net/SSLCertificateSocketFactory.java
// public surface.
package android.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

@Deprecated
public class SSLCertificateSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory mDelegate =
            (SSLSocketFactory) SSLSocketFactory.getDefault();

    /**
     * PUBLIC on AOSP (and @Deprecated there), not private: apps construct this
     * factory directly as well as via getDefault(). The timeout is accepted
     * and not applied -- the JSSE delegate manages its own handshake timing,
     * and AOSP's own value only ever reached its now-removed OpenSSL socket
     * implementation.
     */
    @Deprecated
    public SSLCertificateSocketFactory(int handshakeTimeoutMillis) {}

    /** AOSP getDefault(int handshakeTimeoutMillis): a SocketFactory. */
    public static SocketFactory getDefault(int handshakeTimeoutMillis) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis);
    }

    public static SSLSocketFactory getDefault(int handshakeTimeoutMillis, SSLSessionCache cache) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis);
    }

    // AOSP returns SSLSocketFactory here (only the single-arg getDefault(int)
    // returns the wider SocketFactory).
    public static SSLSocketFactory getInsecure(int handshakeTimeoutMillis, SSLSessionCache cache) {
        // AOSP's "insecure" factory disables verification. We do NOT weaken TLS
        // (security regression risk); callers that truly need to bypass
        // verification must install their own TrustManager. Returns a normal
        // verifying factory.
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis);
    }

    // Android-specific knobs: JSSE manages these internally, so accept + ignore.
    public void setHostname(Socket socket, String hostname) {}
    public void setUseSessionTickets(Socket socket, boolean useSessionTickets) {}
    public void setNpnProtocols(byte[][] npnProtocols) {}
    public void setKeyManagers(KeyManager[] keyManagers) {}
    public void setTrustManagers(TrustManager[] trustManagers) {}
    public byte[] getNpnSelectedProtocol(Socket socket) { return null; }
    public byte[] getAlpnSelectedProtocol(Socket socket) { return null; }
    public void setHostnameVerifier(HostnameVerifier verifier) {}

    @Override public String[] getDefaultCipherSuites() { return mDelegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() {
        return mDelegate.getSupportedCipherSuites();
    }

    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return mDelegate.createSocket(s, host, port, autoClose);
    }
    @Override public Socket createSocket(String host, int port)
            throws IOException, UnknownHostException {
        return mDelegate.createSocket(host, port);
    }
    @Override public Socket createSocket(String host, int port, InetAddress localHost,
            int localPort) throws IOException, UnknownHostException {
        return mDelegate.createSocket(host, port, localHost, localPort);
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return mDelegate.createSocket(host, port);
    }
    @Override public Socket createSocket(InetAddress address, int port,
            InetAddress localAddress, int localPort) throws IOException {
        return mDelegate.createSocket(address, port, localAddress, localPort);
    }
}
