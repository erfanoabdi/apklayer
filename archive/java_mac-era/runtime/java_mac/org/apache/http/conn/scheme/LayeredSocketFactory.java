// org.apache.http.conn.scheme.LayeredSocketFactory -- a SocketFactory that can
// wrap an ALREADY CONNECTED plain socket in a secure layer. This is what makes
// CONNECT-tunnelled HTTPS-through-a-proxy possible: the plain socket is
// connected to the proxy first, then TLS is layered on top of it for the
// target host.
//
// Apache HttpClient 4.x, org/apache/http/conn/scheme/LayeredSocketFactory.java
// (Apache License 2.0).
package org.apache.http.conn.scheme;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/** @deprecated Use {@link org.apache.http.conn.socket.LayeredConnectionSocketFactory}. */
@Deprecated
public interface LayeredSocketFactory extends SocketFactory {
    /**
     * @param socket    an already-connected socket
     * @param host      the target host name (used for certificate verification)
     * @param port      the target port
     * @param autoClose whether closing the returned socket closes {@code socket}
     */
    Socket createSocket(Socket socket, String host, int port, boolean autoClose)
            throws IOException, UnknownHostException;
}
