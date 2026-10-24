package org.apache.http.conn.scheme;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import org.apache.http.params.HttpParams;
public final class PlainSocketFactory implements SocketFactory {
    private static final PlainSocketFactory DEFAULT = new PlainSocketFactory();
    public static PlainSocketFactory getSocketFactory() { return DEFAULT; }
    public PlainSocketFactory() {}
    @Override public Socket createSocket() throws IOException { return new Socket(); }
    @Override public Socket connectSocket(Socket sock, String host, int port, InetAddress localAddress, int localPort, HttpParams params)
            throws IOException, UnknownHostException { return sock != null ? sock : new Socket(); }
    @Override public boolean isSecure(Socket sock) { return false; }
}
