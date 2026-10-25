package org.apache.http.params;
/**
 * Typed accessors over the {@link CoreConnectionPNames} keys.
 *
 * AOSP declares this as implementing CoreConnectionPNames
 * ("public final class HttpConnectionParams implements
 * org.apache.http.params.CoreConnectionPNames"), which is how app code reaches
 * the key constants as HttpConnectionParams.SO_TIMEOUT. Without the interface
 * on the class, that reference does not resolve.
 *
 * @deprecated Use the constants on the HttpClient configuration classes.
 */
@Deprecated
public final class HttpConnectionParams implements CoreConnectionPNames {
    private HttpConnectionParams() {}
    public static int  getConnectionTimeout(HttpParams params) { return params.getIntParameter(CONNECTION_TIMEOUT, 0); }
    public static void setConnectionTimeout(HttpParams params, int t) { params.setIntParameter(CONNECTION_TIMEOUT, t); }
    public static int  getSoTimeout(HttpParams params) { return params.getIntParameter(SO_TIMEOUT, 0); }
    public static void setSoTimeout(HttpParams params, int t) { params.setIntParameter(SO_TIMEOUT, t); }
    public static boolean getTcpNoDelay(HttpParams params) { return params.getBooleanParameter(TCP_NODELAY, true); }
    public static void setTcpNoDelay(HttpParams params, boolean v) { params.setBooleanParameter(TCP_NODELAY, v); }
    public static int  getSocketBufferSize(HttpParams params) { return params.getIntParameter(SOCKET_BUFFER_SIZE, -1); }
    public static void setSocketBufferSize(HttpParams params, int s) { params.setIntParameter(SOCKET_BUFFER_SIZE, s); }
    public static int  getLinger(HttpParams params) { return params.getIntParameter(SO_LINGER, -1); }
    public static void setLinger(HttpParams params, int v) { params.setIntParameter(SO_LINGER, v); }
    public static boolean isStaleCheckingEnabled(HttpParams params) { return params.getBooleanParameter(STALE_CONNECTION_CHECK, true); }
    public static void setStaleCheckingEnabled(HttpParams params, boolean v) { params.setBooleanParameter(STALE_CONNECTION_CHECK, v); }
}
