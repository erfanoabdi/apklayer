package android.net.http;

import java.util.Collections;
import java.util.List;

/** Metadata about an HTTP response.
 *  Source: frameworks/base .../net/http/UrlResponseInfo.java */
public abstract class UrlResponseInfo {
    public abstract String getUrl();
    public abstract List<String> getUrlChain();
    public abstract int getHttpStatusCode();
    public abstract String getHttpStatusText();
    public abstract HeaderBlock getHeaders();
    public abstract boolean wasCached();
    public abstract String getNegotiatedProtocol();
    public abstract long getReceivedByteCount();

    static UrlResponseInfo make(final String url, final int code, final String text,
            final HeaderBlock headers, final long bytes) {
        return new UrlResponseInfo() {
            @Override public String getUrl() { return url; }
            @Override public List<String> getUrlChain() { return Collections.singletonList(url); }
            @Override public int getHttpStatusCode() { return code; }
            @Override public String getHttpStatusText() { return text; }
            @Override public HeaderBlock getHeaders() { return headers; }
            @Override public boolean wasCached() { return false; }
            @Override public String getNegotiatedProtocol() { return "http/1.1"; }
            @Override public long getReceivedByteCount() { return bytes; }
        };
    }
}
