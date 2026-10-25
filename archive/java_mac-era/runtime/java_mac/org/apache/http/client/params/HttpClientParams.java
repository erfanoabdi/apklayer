package org.apache.http.client.params;
import org.apache.http.params.HttpParams;
public final class HttpClientParams {
    private HttpClientParams() {}
    public static boolean isRedirecting(HttpParams params) { return params.getBooleanParameter("http.protocol.handle-redirects", true); }
    public static void setRedirecting(HttpParams params, boolean v) { params.setBooleanParameter("http.protocol.handle-redirects", v); }
    public static String getCookiePolicy(HttpParams params) { return (String) params.getParameter("http.protocol.cookie-policy"); }
    public static void setCookiePolicy(HttpParams params, String v) { params.setParameter("http.protocol.cookie-policy", v); }
    public static boolean isAuthenticating(HttpParams params) { return params.getBooleanParameter("http.protocol.handle-authentication", true); }
    public static void setAuthenticating(HttpParams params, boolean v) { params.setBooleanParameter("http.protocol.handle-authentication", v); }
}
