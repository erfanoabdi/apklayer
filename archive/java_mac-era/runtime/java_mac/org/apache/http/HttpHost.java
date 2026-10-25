package org.apache.http;
public class HttpHost implements Cloneable {
    public static final String DEFAULT_SCHEME_NAME = "http";
    protected final String hostname;
    protected final String lcHostname;
    protected final int port;
    protected final String schemeName;
    public HttpHost(String hostname, int port, String scheme) {
        this.hostname = hostname == null ? "" : hostname;
        this.lcHostname = this.hostname.toLowerCase();
        this.port = port;
        this.schemeName = scheme != null ? scheme.toLowerCase() : DEFAULT_SCHEME_NAME;
    }
    public HttpHost(String hostname, int port) { this(hostname, port, null); }
    public HttpHost(String hostname)            { this(hostname, -1, null); }
    public HttpHost(HttpHost httphost) {
        this(httphost.hostname, httphost.port, httphost.schemeName);
    }
    public String getHostName()   { return hostname; }
    public int    getPort()       { return port; }
    public String getSchemeName() { return schemeName; }
    public String toURI() {
        StringBuilder b = new StringBuilder();
        b.append(schemeName).append("://").append(hostname);
        if (port != -1) b.append(':').append(port);
        return b.toString();
    }
    public String toHostString() {
        return port != -1 ? hostname + ":" + port : hostname;
    }
    @Override public String toString() { return toURI(); }
    @Override public Object clone() throws CloneNotSupportedException { return super.clone(); }
}
