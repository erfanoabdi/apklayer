package android.net.http;
/** Legacy response cache (superseded by HttpEngine). Inert here. */
public final class HttpResponseCache extends java.net.ResponseCache implements java.io.Closeable {
    private HttpResponseCache() {}
    public static HttpResponseCache getInstalled() { return null; }
    public static HttpResponseCache install(java.io.File directory, long maxSize) throws java.io.IOException { return new HttpResponseCache(); }
    public long size() { return 0; }
    public long maxSize() { return 0; }
    public int getHitCount() { return 0; }
    public int getNetworkCount() { return 0; }
    public int getRequestCount() { return 0; }
    public void flush() {}
    public void delete() throws java.io.IOException {}
    @Override public void close() {}
    @Override public java.net.CacheResponse get(java.net.URI uri, String rqstMethod, java.util.Map<String,java.util.List<String>> rqstHeaders) { return null; }
    @Override public java.net.CacheRequest put(java.net.URI uri, java.net.URLConnection conn) { return null; }
}
