package org.apache.http.conn.scheme;
public final class Scheme {
    private final String name;
    private final int defaultPort;
    public Scheme(String name, SocketFactory factory, int port) {
        this.name = name; this.defaultPort = port;
    }
    public Scheme(String name, int port, Object factory) {
        this.name = name; this.defaultPort = port;
    }
    public String getName() { return name; }
    public int getDefaultPort() { return defaultPort; }
}
