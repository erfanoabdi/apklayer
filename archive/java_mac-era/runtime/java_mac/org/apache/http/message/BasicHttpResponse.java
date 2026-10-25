package org.apache.http.message;
import java.util.Locale;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
public class BasicHttpResponse implements HttpResponse {
    private StatusLine statusline;
    private HttpEntity entity;
    private Locale locale = Locale.getDefault();
    private HttpParams params = new BasicHttpParams();
    public BasicHttpResponse(StatusLine statusline) { this.statusline = statusline; }
    public BasicHttpResponse(ProtocolVersion ver, int code, String reason) {
        this.statusline = new BasicStatusLine(ver, code, reason);
    }
    @Override public StatusLine getStatusLine() { return statusline; }
    @Override public void setStatusLine(StatusLine statusline) { this.statusline = statusline; }
    @Override public void setStatusLine(ProtocolVersion ver, int code) { this.statusline = new BasicStatusLine(ver, code, null); }
    @Override public void setStatusLine(ProtocolVersion ver, int code, String reason) { this.statusline = new BasicStatusLine(ver, code, reason); }
    @Override public void setStatusCode(int code) {
        ProtocolVersion ver = statusline != null ? statusline.getProtocolVersion() : null;
        this.statusline = new BasicStatusLine(ver, code, null);
    }
    @Override public void setReasonPhrase(String reason) {
        this.statusline = new BasicStatusLine(statusline.getProtocolVersion(), statusline.getStatusCode(), reason);
    }
    @Override public HttpEntity getEntity() { return entity; }
    @Override public void setEntity(HttpEntity entity) { this.entity = entity; }
    @Override public Locale getLocale() { return locale; }
    @Override public void setLocale(Locale loc) { this.locale = loc; }
    @Override public ProtocolVersion getProtocolVersion() { return statusline != null ? statusline.getProtocolVersion() : null; }
    @Override public boolean containsHeader(String name) { return false; }
    @Override public Header[] getHeaders(String name) { return new Header[0]; }
    @Override public Header getFirstHeader(String name) { return null; }
    @Override public Header getLastHeader(String name) { return null; }
    @Override public Header[] getAllHeaders() { return new Header[0]; }
    @Override public void addHeader(Header header) {}
    @Override public void addHeader(String name, String value) {}
    @Override public void setHeader(Header header) {}
    @Override public void setHeader(String name, String value) {}
    @Override public void setHeaders(Header[] headers) {}
    @Override public void removeHeader(Header header) {}
    @Override public void removeHeaders(String name) {}
    @Override public HeaderIterator headerIterator() { return null; }
    @Override public HeaderIterator headerIterator(String name) { return null; }
    @Override public HttpParams getParams() { return params; }
    @Override public void setParams(HttpParams params) { this.params = params; }
}
