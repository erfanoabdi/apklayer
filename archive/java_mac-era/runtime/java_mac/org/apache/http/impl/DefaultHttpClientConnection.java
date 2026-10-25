package org.apache.http.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

import org.apache.http.HttpConnection;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

// org.apache.http.impl.DefaultHttpClientConnection -- real AOSP (external/apache-http,
// external/apache-http/src/org/apache/http/impl/{DefaultHttpClientConnection,
// SocketHttpClientConnection,AbstractHttpClientConnection}.java) splits this across a
// 3-class hierarchy: AbstractHttpClientConnection implements the HttpClientConnection
// wire methods (sendRequestHeader/sendRequestEntity/flush/receiveResponseHeader/
// receiveResponseEntity/isResponseAvailable) over a SessionInputBuffer/
// SessionOutputBuffer pair; SocketHttpClientConnection binds those buffers to a real
// java.net.Socket and owns isOpen/close/shutdown/setSocketTimeout/getSocketTimeout/
// isStale; DefaultHttpClientConnection itself only overrides bind() (to apply
// TCP_NODELAY/SO_TIMEOUT/SO_LINGER from HttpParams before delegating) and toString().
//
// This tree carries org.apache.http.HttpConnection (the interface this class already
// declared) but not org.apache.http.HttpClientConnection, and not the
// org.apache.http.message.Basic{Header,HttpResponse,StatusLine} / org.apache.http.entity
// value types AbstractHttpClientConnection needs to hand back a real HttpResponse from
// receiveResponseHeader(). Adding the wire-protocol methods here would mean either (a)
// duplicating the private response/header/entity glue that already lives in
// org.apache.http.impl.client.DefaultHttpClient.MacApkResponse/MacApkHeader/MacApkEntity,
// which is exactly the "parallel code path to harmonize later" the project's no-shortcuts
// rule warns about, or (b) inventing an API shape AOSP does not have. Neither is a
// legitimate fix, so the wire methods are left for a follow-up once
// org.apache.http.message.Basic* / org.apache.http.entity.BasicHttpEntity land as shared
// public classes (tracked in Current_State.md "Per-APK patches" / gap inventory, not here).
//
// What IS fixed here is the SocketHttpClientConnection/HttpConnection-level contract:
// bind() records a real Socket and applies AOSP's TCP tuning, and isOpen/isStale/close/
// shutdown/setSocketTimeout/getSocketTimeout/getMetrics all answer from that real state
// instead of the previous hardcoded false/true/null (a caller doing
// `new DefaultHttpClientConnection(); conn.bind(s, p); if (conn.isOpen())...` previously
// always took the "not open" branch no matter what, and `conn.getMetrics()` NPEd unless
// the caller null-checked first, where real Android returns a live non-null object once
// the connection has actually been bound).
public class DefaultHttpClientConnection implements HttpConnection {
    private volatile boolean open;
    private Socket socket;
    private HttpConnectionMetricsImpl metrics;

    public DefaultHttpClientConnection() {}

    /** AOSP SocketHttpClientConnection.bind(Socket,HttpParams) + the
     *  DefaultHttpClientConnection override that layers TCP_NODELAY / SO_TIMEOUT /
     *  SO_LINGER on top before marking the connection open. Real AOSP also builds a
     *  SessionInputBuffer/SessionOutputBuffer pair here (org.apache.http.impl.io); this
     *  tree keeps the plain Socket instead since nothing yet drives it through the
     *  HttpClientConnection wire methods (see class comment above). */
    public void bind(Socket socket, HttpParams params) throws IOException {
        if (socket == null) throw new IllegalArgumentException("Socket may not be null");
        if (params == null) throw new IllegalArgumentException("HTTP parameters may not be null");
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(params));
        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) socket.setSoLinger(true, linger);
        this.socket = socket;
        this.metrics = new HttpConnectionMetricsImpl();
        this.open = true;
    }

    /** AOSP SocketHttpClientConnection.close(): flush (nothing to flush without a
     *  wire-protocol output buffer here), half-close both directions, then close. A
     *  socket implementation that does not support half-close (SSLSocket historically)
     *  throws UnsupportedOperationException instead of IOException, so both are caught. */
    @Override public void close() throws IOException {
        if (!open) return;
        open = false;
        Socket s = socket;
        if (s == null) return;
        try {
            try { s.shutdownOutput(); } catch (IOException ignore) {}
            try { s.shutdownInput(); } catch (IOException ignore) {}
        } catch (UnsupportedOperationException ignore) {
        }
        s.close();
    }

    @Override public boolean isOpen() { return open; }

    /** AOSP AbstractHttpClientConnection.isStale(): a closed connection is always stale;
     *  otherwise it does a non-blocking peek read through the SessionInputBuffer to
     *  detect a dead peer, relying on the buffer to retain any byte it reads so a real
     *  caller never loses data to the probe. This tree has no such buffering layer (see
     *  class comment), so a destructive peek read here could eat a byte a real
     *  sendRequestHeader/receiveResponseHeader caller was about to read -- instead this
     *  reports staleness from the Socket's own liveness flags, which never consumes a
     *  byte, at the cost of not detecting a peer that dropped the connection without
     *  sending a FIN/RST yet. */
    @Override public boolean isStale() {
        if (!open || socket == null) return true;
        try {
            return socket.isClosed() || !socket.isConnected() || socket.isInputShutdown();
        } catch (Exception ex) {
            return true;
        }
    }

    /** AOSP SocketHttpClientConnection.setSocketTimeout(): AbstractHttpClientConnection
     *  requires the connection be open first (assertOpen()); SocketException from the
     *  underlying setSoTimeout is swallowed the same way AOSP swallows it. */
    @Override public void setSocketTimeout(int timeout) {
        if (!open) throw new IllegalStateException("Connection is not open");
        if (socket == null) return;
        try { socket.setSoTimeout(timeout); } catch (SocketException ignore) {}
    }

    @Override public int getSocketTimeout() {
        if (socket == null) return -1;
        try { return socket.getSoTimeout(); } catch (SocketException ignore) { return -1; }
    }

    /** AOSP SocketHttpClientConnection.shutdown(): an immediate abort, unlike close()
     *  there is no flush and no graceful half-close, just drop the socket now. */
    @Override public void shutdown() throws IOException {
        open = false;
        Socket s = socket;
        if (s != null) s.close();
    }

    @Override public HttpConnectionMetrics getMetrics() { return metrics; }

    /** Local (host-side) and remote (peer-side) endpoint accessors. Real AOSP exposes
     *  these through SocketHttpClientConnection implementing org.apache.http.
     *  HttpInetConnection, which this tree does not carry; declared directly here so a
     *  caller holding a concrete DefaultHttpClientConnection reference (rather than the
     *  missing HttpInetConnection interface type) still resolves them. */
    public InetAddress getLocalAddress() { return socket != null ? socket.getLocalAddress() : null; }
    public int getLocalPort() { return socket != null ? socket.getLocalPort() : -1; }
    public InetAddress getRemoteAddress() { return socket != null ? socket.getInetAddress() : null; }
    public int getRemotePort() { return socket != null ? socket.getPort() : -1; }

    /** AOSP DefaultHttpClientConnection.toString(): identify the peer while open, else
     *  report closed. */
    @Override public String toString() {
        if (open && socket != null) {
            InetAddress remote = socket.getInetAddress();
            if (remote != null) return remote.toString() + ":" + socket.getPort();
        }
        return "closed";
    }

    /** AOSP org.apache.http.impl.HttpConnectionMetricsImpl, minimal form: real AOSP
     *  derives these counts from the SessionInputBuffer/SessionOutputBuffer byte
     *  counters as sendRequestHeader/receiveResponseHeader run. This tree has no wire
     *  methods incrementing them yet (see class comment), so they start and stay at
     *  zero rather than the class not existing at all -- honest given nothing has been
     *  sent through this connection, and it means `conn.getMetrics().getRequestCount()`
     *  returns a real long instead of NPEing on a null getMetrics() result. */
    private static final class HttpConnectionMetricsImpl implements HttpConnectionMetrics {
        private long requestCount;
        private long responseCount;
        private long sentBytes;
        private long receivedBytes;
        @Override public long getRequestCount() { return requestCount; }
        @Override public long getResponseCount() { return responseCount; }
        @Override public long getSentBytesCount() { return sentBytes; }
        @Override public long getReceivedBytesCount() { return receivedBytes; }
        @Override public Object getMetric(String metricName) { return null; }
        @Override public void reset() {
            requestCount = 0;
            responseCount = 0;
            sentBytes = 0;
            receivedBytes = 0;
        }
    }

    public void sendRequestHeader(org.apache.http.HttpRequest request) throws org.apache.http.HttpException, java.io.IOException { }
    public void sendRequestEntity(org.apache.http.HttpEntityEnclosingRequest request) throws org.apache.http.HttpException, java.io.IOException { }
    public org.apache.http.HttpResponse receiveResponseHeader() throws org.apache.http.HttpException, java.io.IOException { return null; }
    public void receiveResponseEntity(org.apache.http.HttpResponse response) throws org.apache.http.HttpException, java.io.IOException { }
    public void flush() throws java.io.IOException { }
}

