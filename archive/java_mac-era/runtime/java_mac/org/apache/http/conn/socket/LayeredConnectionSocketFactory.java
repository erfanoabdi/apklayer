package org.apache.http.conn.socket;
import java.io.IOException;
import java.net.Socket;
import org.apache.http.protocol.HttpContext;
/** 4.3 layered (TLS-over-plain) socket factory. Standalone here to avoid pulling
 *  the full ConnectionSocketFactory hierarchy; the type resolves. */
public interface LayeredConnectionSocketFactory {
    Socket createSocket(HttpContext context) throws IOException;
    Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context) throws IOException;
}
