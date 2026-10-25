package org.apache.http.params;
import org.apache.http.ProtocolVersion;
public final class HttpProtocolParams {
    private HttpProtocolParams() {}
    public static ProtocolVersion getVersion(HttpParams params) {
        Object v = params.getParameter("http.protocol.version");
        return v instanceof ProtocolVersion ? (ProtocolVersion) v : null;
    }
    public static void setVersion(HttpParams params, ProtocolVersion v) { params.setParameter("http.protocol.version", v); }
    public static String getContentCharset(HttpParams params) {
        Object v = params.getParameter("http.protocol.content-charset");
        return v instanceof String ? (String) v : "ISO-8859-1";
    }
    public static void setContentCharset(HttpParams params, String s) { params.setParameter("http.protocol.content-charset", s); }
    public static String getHttpElementCharset(HttpParams params) {
        Object v = params.getParameter("http.protocol.element-charset");
        return v instanceof String ? (String) v : "US-ASCII";
    }
    public static void setHttpElementCharset(HttpParams params, String s) { params.setParameter("http.protocol.element-charset", s); }
    public static String getUserAgent(HttpParams params) {
        Object v = params.getParameter("http.useragent");
        return v instanceof String ? (String) v : null;
    }
    public static void setUserAgent(HttpParams params, String s) { params.setParameter("http.useragent", s); }
    public static boolean useExpectContinue(HttpParams params) { return params.getBooleanParameter("http.protocol.expect-continue", false); }
    public static void setUseExpectContinue(HttpParams params, boolean v) { params.setBooleanParameter("http.protocol.expect-continue", v); }
}
