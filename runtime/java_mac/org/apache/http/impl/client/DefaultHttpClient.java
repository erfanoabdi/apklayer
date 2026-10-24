// DefaultHttpClient -- Android shipped the Apache HttpClient inside the
// framework through API 22, and still ships it as the optional
// org.apache.http.legacy library from API 28 on. On a real device it is a
// fully functional network client: execute(...) opens a real socket, writes
// the request, and hands back an HttpResponse carrying the server's real
// status line, headers and body.
//
// Every execute(...) overload here used to throw
// ClientProtocolException("network disabled in Mac.apk"). That was written for
// one 2013 game whose only use of HttpClient was optional analytics, and it
// generalized into a lie the guest cannot detect: the app is told the network
// failed while every other path in the same process (HttpURLConnection, OkHttp,
// TLS through the host JSSE) works, so anything gated on a successful response
// -- leaderboards, remote config, receipt validation, cloud save -- is
// permanently dead while the app's error handling reports a transient offline
// blip.
//
// Transport is the host JDK's java.net.HttpURLConnection. That is the same
// "borrow the host stack instead of reimplementing a socket layer" approach the
// runtime already takes for TLS, and it buys real HTTP/1.1 framing, proxies,
// keep-alive, redirect handling and the host trust store. What this class owns
// is the Apache-shaped contract layered on top: request/response translation,
// the AbstractHttpClient execute()/ResponseHandler semantics, and the parameter
// defaults DefaultHttpClient.createHttpParams() installs.
//
// AOSP reference (external/apache-http -- the exact code a device runs):
//   impl/client/AbstractHttpClient.java  execute(...) L467-530, determineTarget
//                                        L495-507, and the ResponseHandler
//                                        consumeContent /
//                                        UndeclaredThrowableException contract
//                                        L647-700
//   impl/client/DefaultHttpClient.java   createHttpParams()
//   protocol/ExecutionContext.java       the http.* context attribute names
package org.apache.http.impl.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;

public class DefaultHttpClient implements HttpClient {

    // ExecutionContext attribute names, verbatim from
    // external/apache-http/src/org/apache/http/protocol/ExecutionContext.java
    // L50-55. The class itself is not part of this tree's Apache surface, but
    // the names are what callers pass to context.getAttribute(...), so the
    // values have to match exactly.
    private static final String CTX_TARGET_HOST = "http.target_host";
    private static final String CTX_REQUEST     = "http.request";
    private static final String CTX_RESPONSE    = "http.response";
    private static final String CTX_REQ_SENT    = "http.request_sent";

    private HttpParams params;
    private ClientConnectionManager mgr;

    public DefaultHttpClient() { this(null, null); }
    public DefaultHttpClient(HttpParams params) { this(null, params); }
    public DefaultHttpClient(ClientConnectionManager mgr, HttpParams params) {
        this.mgr = mgr;
        // Left null on purpose when the caller supplied none: AbstractHttpClient
        // builds the defaults lazily in getParams(), so a subclass overriding
        // createHttpParams() gets to run against a fully constructed object.
        this.params = params;
    }

    /** AOSP DefaultHttpClient.createHttpParams(). Apps read these back off
     *  getParams() and branch on them, so the defaults have to be the ones a
     *  device installs: HTTP/1.1, ISO-8859-1 element charset, expect-continue
     *  OFF (an explicit Android change -- asking first slows the common case
     *  and yields 417s behind an HTTP/1.0 proxy, AOSP b/2471595), and the
     *  User-Agent Android actually emits. AOSP builds that agent from
     *  VersionInfo.loadVersionInfo("org.apache.http.client", ...), and the
     *  platform tree ships no version.properties for that package, so the
     *  release resolves to VersionInfo.UNAVAILABLE -- which is why real
     *  devices are seen on the wire as "Apache-HttpClient/UNAVAILABLE
     *  (java 1.4)". Note the timeouts are deliberately absent: Apache's
     *  default connection/socket timeout is 0 (wait forever), and an app that
     *  wants a bound sets one through HttpConnectionParams. */
    protected HttpParams createHttpParams() {
        HttpParams p = new BasicHttpParams();
        HttpProtocolParams.setVersion(p, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(p, "ISO-8859-1");
        HttpProtocolParams.setUseExpectContinue(p, false);
        HttpProtocolParams.setUserAgent(p, "Apache-HttpClient/UNAVAILABLE (java 1.4)");
        return p;
    }

    @Override public synchronized HttpParams getParams() {
        if (params == null) params = createHttpParams();
        return params;
    }
    public synchronized void setParams(HttpParams p) { this.params = p; }

    /** AOSP never hands back a null manager: DefaultHttpClient builds one in
     *  createClientConnectionManager() if the caller did not supply one. The
     *  single most common HttpClient teardown idiom in Android code is
     *  `client.getConnectionManager().shutdown()`, which NPEs against a null.
     *  The registry carries the two schemes AOSP registers by default so an
     *  app that inspects getSchemeRegistry() sees the real ports. Our
     *  transport does not draw sockets from this pool (HttpURLConnection owns
     *  its own keep-alive cache), so the manager is here for the lifecycle and
     *  scheme-registry contract only. */
    @Override public synchronized ClientConnectionManager getConnectionManager() {
        if (mgr == null) {
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
            mgr = new ThreadSafeClientConnManager(getParams(), registry);
        }
        return mgr;
    }

    // ---------------------------------------------------------------------
    // HttpResponse-returning overloads. Shapes mirror AbstractHttpClient:
    // everything funnels into execute(target, request, context), and the
    // target is derived from an absolute request URI when the caller did not
    // name one.
    // ---------------------------------------------------------------------

    @Override public HttpResponse execute(HttpUriRequest request)
            throws IOException, ClientProtocolException {
        return execute(request, (HttpContext) null);
    }

    @Override public HttpResponse execute(HttpUriRequest request, HttpContext context)
            throws IOException, ClientProtocolException {
        if (request == null) throw new IllegalArgumentException("Request must not be null.");
        return execute(determineTarget(request), request, context);
    }

    @Override public HttpResponse execute(HttpHost target, HttpRequest request)
            throws IOException, ClientProtocolException {
        return execute(target, request, (HttpContext) null);
    }

    @Override public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException, ClientProtocolException {
        if (request == null) throw new IllegalArgumentException("Request must not be null.");
        return doExecute(target, request, context);
    }

    // ---------------------------------------------------------------------
    // ResponseHandler overloads. AbstractHttpClient L647-700 is precise about
    // this and callers depend on it: the handler runs once, the entity is
    // consumed whether the handler returned or threw, Error/RuntimeException/
    // IOException propagate unwrapped, anything else is wrapped in
    // UndeclaredThrowableException.
    // ---------------------------------------------------------------------

    @Override public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler)
            throws IOException, ClientProtocolException {
        return execute(request, handler, null);
    }

    @Override public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler,
                                   HttpContext context)
            throws IOException, ClientProtocolException {
        return execute(determineTarget(request), request, handler, context);
    }

    @Override public <T> T execute(HttpHost target, HttpRequest request,
                                   ResponseHandler<? extends T> handler)
            throws IOException, ClientProtocolException {
        return execute(target, request, handler, null);
    }

    @Override public <T> T execute(HttpHost target, HttpRequest request,
                                   ResponseHandler<? extends T> handler, HttpContext context)
            throws IOException, ClientProtocolException {
        if (handler == null) {
            throw new IllegalArgumentException("Response handler must not be null.");
        }
        HttpResponse response = execute(target, request, context);
        T result;
        try {
            result = handler.handleResponse(response);
        } catch (Throwable t) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                // The original throwable is the important one; a failure while
                // draining must not mask it.
                try { entity.consumeContent(); } catch (Throwable ignored) { }
            }
            if (t instanceof Error) throw (Error) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof IOException) throw (IOException) t;
            throw new UndeclaredThrowableException(t);
        }
        // Handling succeeded: make sure the body is fully consumed so the
        // connection can go back to the keep-alive cache. This one is allowed
        // to reach the caller.
        HttpEntity entity = response.getEntity();
        if (entity != null) entity.consumeContent();
        return result;
    }

    // ---------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------

    /** AbstractHttpClient.determineTarget: a target only falls out of the
     *  request when its URI is absolute; a relative URI leaves it null and the
     *  route planner is expected to have a default (we surface that as a
     *  ClientProtocolException instead of a null-route NPE). */
    private static HttpHost determineTarget(HttpUriRequest request) {
        if (request == null) throw new IllegalArgumentException("Request must not be null.");
        URI requestURI = request.getURI();
        if (requestURI != null && requestURI.isAbsolute()) {
            return new HttpHost(requestURI.getHost(), requestURI.getPort(), requestURI.getScheme());
        }
        return null;
    }

    private HttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {
        final URI uri = resolveUri(target, request);
        final String method = resolveMethod(request);
        // ClientParamsStack order: a value set on the request wins over the
        // same value set on the client (AbstractHttpClient.determineParams).
        final HttpParams effective = new ParamsStack(getParams(), safeParams(request));

        if (context != null) {
            context.setAttribute(CTX_TARGET_HOST,
                    target != null ? target : hostOf(uri));
            context.setAttribute(CTX_REQUEST, request);
            context.setAttribute(CTX_REQ_SENT, Boolean.FALSE);
        }

        URLConnection raw;
        try {
            raw = uri.toURL().openConnection();
        } catch (IllegalArgumentException | java.net.MalformedURLException e) {
            throw new ClientProtocolException("cannot open " + uri, e);
        }
        if (!(raw instanceof HttpURLConnection)) {
            throw new ClientProtocolException("unsupported scheme for " + uri);
        }
        final HttpURLConnection conn = (HttpURLConnection) raw;

        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            // The host stack only speaks the RFC 2616 method set. Say so
            // rather than silently sending some other verb.
            throw new ClientProtocolException("unsupported HTTP method " + method, e);
        }
        // 0 means "wait forever" in both APIs, so these pass straight through.
        conn.setConnectTimeout(Math.max(0, HttpConnectionParams.getConnectionTimeout(effective)));
        conn.setReadTimeout(Math.max(0, HttpConnectionParams.getSoTimeout(effective)));
        // DefaultRequestDirector L929-949 follows 301/302/303/307 for any
        // method and re-issues the follow-up as a GET, which is what the host
        // stack does for 301/302/303 too. The one residual difference is a 307
        // answering a POST: Apache would follow it (as a GET), the host stack
        // hands the 307 back to the caller.
        conn.setInstanceFollowRedirects(HttpClientParams.isRedirecting(effective));
        // Apache never reads from a ResponseCache; an app that installed an
        // HttpResponseCache for its HttpURLConnection traffic must not have it
        // silently start serving this client's requests too.
        conn.setUseCaches(false);

        boolean requestSetUserAgent = false;
        Header[] requestHeaders = request.getAllHeaders();
        if (requestHeaders != null) {
            for (Header h : requestHeaders) {
                if (h == null || h.getName() == null) continue;
                if ("User-Agent".equalsIgnoreCase(h.getName())) requestSetUserAgent = true;
                // addRequestProperty, not set: Apache keeps every value of a
                // repeated header. A handful of names (Host, Content-Length,
                // Connection) are owned by the host stack and are dropped here
                // the same way they are dropped by any HttpURLConnection user.
                conn.addRequestProperty(h.getName(), h.getValue());
            }
        }

        // RequestUserAgent only fills in the agent when the request did not
        // carry one. The flag is what decides, not getRequestProperty: the
        // host stack seeds its own "Java/<version>" agent at construction, so
        // asking the connection would always report one already set and the
        // app would go out on the wire as a JVM instead of as Android.
        String ua = HttpProtocolParams.getUserAgent(effective);
        if (ua != null && !requestSetUserAgent) conn.setRequestProperty("User-Agent", ua);

        HttpEntity outgoing = null;
        if (request instanceof HttpEntityEnclosingRequest) {
            outgoing = ((HttpEntityEnclosingRequest) request).getEntity();
        }
        if (outgoing != null) {
            // Apache's RequestContent interceptor derives these from the
            // entity when the caller did not set them explicitly.
            if (conn.getRequestProperty("Content-Type") == null
                    && outgoing.getContentType() != null) {
                conn.setRequestProperty("Content-Type", outgoing.getContentType().getValue());
            }
            if (conn.getRequestProperty("Content-Encoding") == null
                    && outgoing.getContentEncoding() != null) {
                conn.setRequestProperty("Content-Encoding", outgoing.getContentEncoding().getValue());
            }
            long len = outgoing.getContentLength();
            if (len >= 0 && !outgoing.isChunked()) {
                conn.setFixedLengthStreamingMode(len);
            } else {
                conn.setChunkedStreamingMode(0);
            }
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            try {
                outgoing.writeTo(os);
                os.flush();
            } finally {
                os.close();
            }
        }

        final int code = conn.getResponseCode();
        if (context != null) context.setAttribute(CTX_REQ_SENT, Boolean.TRUE);

        final MacApkResponse response = new MacApkResponse(
                new MacApkStatusLine(protocolVersionOf(conn), code, conn.getResponseMessage()));
        for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
            // Key null is the status line itself, which is not a header.
            if (e.getKey() == null || e.getValue() == null) continue;
            for (String v : e.getValue()) response.addHeader(new MacApkHeader(e.getKey(), v));
        }

        InputStream body;
        try {
            body = conn.getInputStream();
        } catch (IOException notOk) {
            // 4xx/5xx: the body lives on the error stream. Apache returns the
            // response with its body either way and lets the caller read the
            // status code, so an error status must not surface as an
            // exception here.
            body = conn.getErrorStream();
        }
        if (body == null) body = new ByteArrayInputStream(new byte[0]);
        response.setEntity(new MacApkEntity(body,
                conn.getContentLengthLong(),
                conn.getContentType(),
                conn.getContentEncoding(),
                "chunked".equalsIgnoreCase(conn.getHeaderField("Transfer-Encoding"))));

        if (context != null) context.setAttribute(CTX_RESPONSE, response);
        return response;
    }

    private static HttpParams safeParams(HttpRequest request) {
        try { return request.getParams(); } catch (RuntimeException ignored) { return null; }
    }

    private static HttpHost hostOf(URI uri) {
        return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    }

    /** The absolute URI to hit. Apache carries it either on an HttpUriRequest
     *  or in the request line; a relative one is resolved against the target
     *  host the caller named. */
    private static URI resolveUri(HttpHost target, HttpRequest request) throws ClientProtocolException {
        String raw = null;
        if (request instanceof HttpUriRequest) {
            URI u = ((HttpUriRequest) request).getURI();
            if (u != null) raw = u.toString();
        }
        if (raw == null) {
            RequestLine rl = request.getRequestLine();
            if (rl != null) raw = rl.getUri();
        }
        if (raw == null || raw.isEmpty()) {
            throw new ClientProtocolException(
                    "request carries neither a URI nor a request line: "
                    + request.getClass().getName());
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new ClientProtocolException("invalid request URI: " + raw, e);
        }
        if (uri.isAbsolute()) return uri;
        if (target == null) {
            throw new ClientProtocolException("relative request URI with no target host: " + raw);
        }
        try {
            return new URI(target.toURI()).resolve(uri);
        } catch (URISyntaxException e) {
            throw new ClientProtocolException("invalid target host: " + target, e);
        }
    }

    private static String resolveMethod(HttpRequest request) throws ClientProtocolException {
        if (request instanceof HttpUriRequest) {
            String m = ((HttpUriRequest) request).getMethod();
            if (m != null && !m.isEmpty()) return m;
        }
        RequestLine rl = request.getRequestLine();
        if (rl != null && rl.getMethod() != null && !rl.getMethod().isEmpty()) {
            return rl.getMethod();
        }
        throw new ClientProtocolException(
                "request exposes no HTTP method: " + request.getClass().getName());
    }

    /** The wire version the server answered with. HttpURLConnection exposes
     *  the status line as header field 0 (the one with a null key), which is
     *  the only place the version survives. */
    private static ProtocolVersion protocolVersionOf(HttpURLConnection conn) {
        String statusLine = conn.getHeaderFieldKey(0) == null ? conn.getHeaderField(0) : null;
        if (statusLine != null && statusLine.startsWith("HTTP/")) {
            int slash = statusLine.indexOf('/');
            int space = statusLine.indexOf(' ', slash);
            String ver = space > 0 ? statusLine.substring(slash + 1, space)
                                   : statusLine.substring(slash + 1);
            int dot = ver.indexOf('.');
            if (dot > 0) {
                try {
                    return new HttpVersion(Integer.parseInt(ver.substring(0, dot).trim()),
                                           Integer.parseInt(ver.substring(dot + 1).trim()));
                } catch (NumberFormatException ignored) { }
            }
        }
        return HttpVersion.HTTP_1_1;
    }

    // ---------------------------------------------------------------------
    // Apache-shaped value types. These are the org.apache.http.message /
    // org.apache.http.entity Basic* classes' observable behavior; they live
    // here (private) because this tree does not carry those classes, and every
    // caller reaches them through the HttpResponse / Header / HttpEntity
    // interfaces anyway.
    // ---------------------------------------------------------------------

    private static final class ParamsStack implements HttpParams {
        private final HttpParams client;
        private final HttpParams request;
        ParamsStack(HttpParams client, HttpParams request) {
            this.client = client;
            this.request = request;
        }
        @Override public Object getParameter(String name) {
            Object v = request != null ? request.getParameter(name) : null;
            if (v == null && client != null) v = client.getParameter(name);
            return v;
        }
        // ClientParamsStack is a read-only view; AOSP throws on mutation
        // rather than silently writing into one of the stacked sets.
        @Override public HttpParams setParameter(String name, Object value) {
            throw new UnsupportedOperationException("Setting parameters in a stack is not supported.");
        }
        @Override public boolean removeParameter(String name) {
            throw new UnsupportedOperationException("Removing parameters in a stack is not supported.");
        }
        @Override public HttpParams copy() { return this; }
        @Override public long getLongParameter(String name, long def) {
            Object v = getParameter(name); return v instanceof Long ? (Long) v : def;
        }
        @Override public HttpParams setLongParameter(String name, long v) { return setParameter(name, v); }
        @Override public int getIntParameter(String name, int def) {
            Object v = getParameter(name); return v instanceof Integer ? (Integer) v : def;
        }
        @Override public HttpParams setIntParameter(String name, int v) { return setParameter(name, v); }
        @Override public double getDoubleParameter(String name, double def) {
            Object v = getParameter(name); return v instanceof Double ? (Double) v : def;
        }
        @Override public HttpParams setDoubleParameter(String name, double v) { return setParameter(name, v); }
        @Override public boolean getBooleanParameter(String name, boolean def) {
            Object v = getParameter(name); return v instanceof Boolean ? (Boolean) v : def;
        }
        @Override public HttpParams setBooleanParameter(String name, boolean v) { return setParameter(name, v); }
        @Override public boolean isParameterTrue(String name)  { return getBooleanParameter(name, false); }
        @Override public boolean isParameterFalse(String name) { return !getBooleanParameter(name, true); }
    }

    private static final class MacApkStatusLine implements StatusLine {
        private final ProtocolVersion ver;
        private final int code;
        private final String reason;
        MacApkStatusLine(ProtocolVersion ver, int code, String reason) {
            this.ver = ver != null ? ver : HttpVersion.HTTP_1_1;
            this.code = code;
            this.reason = reason;
        }
        @Override public ProtocolVersion getProtocolVersion() { return ver; }
        @Override public int getStatusCode()   { return code; }
        @Override public String getReasonPhrase() { return reason; }
        @Override public String toString() {
            return ver + " " + code + (reason != null ? " " + reason : "");
        }
    }

    private static final class MacApkHeader implements Header {
        private final String name;
        private final String value;
        MacApkHeader(String name, String value) { this.name = name; this.value = value; }
        @Override public String getName()  { return name; }
        @Override public String getValue() { return value; }
        /** Apache parses a header value into comma-separated elements, each
         *  with semicolon-separated name=value parameters (RFC 2616 section
         *  2.1 / 3.6). Cookie, Content-Type and Cache-Control handling all
         *  read this, so returning an empty array would quietly strip
         *  information the caller stored. Quoted strings hide both separators. */
        @Override public HeaderElement[] getElements() {
            if (value == null) return new HeaderElement[0];
            List<HeaderElement> out = new ArrayList<>();
            for (String chunk : splitUnquoted(value, ',')) {
                String s = chunk.trim();
                if (s.isEmpty()) continue;
                List<String> parts = splitUnquoted(s, ';');
                NameValuePair head = parsePair(parts.get(0));
                List<NameValuePair> ps = new ArrayList<>();
                for (int i = 1; i < parts.size(); i++) {
                    String p = parts.get(i).trim();
                    if (!p.isEmpty()) ps.add(parsePair(p));
                }
                out.add(new MacApkHeaderElement(head.getName(), head.getValue(),
                        ps.toArray(new NameValuePair[0])));
            }
            return out.toArray(new HeaderElement[0]);
        }
        private static NameValuePair parsePair(String s) {
            String t = s.trim();
            int eq = t.indexOf('=');
            if (eq < 0) return new BasicNameValuePair(t, null);
            String n = t.substring(0, eq).trim();
            String v = t.substring(eq + 1).trim();
            if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
                v = v.substring(1, v.length() - 1);
            }
            return new BasicNameValuePair(n, v);
        }
        private static List<String> splitUnquoted(String s, char sep) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            boolean escaped = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (escaped)            { cur.append(c); escaped = false; continue; }
                if (quoted && c == '\\') { cur.append(c); escaped = true;  continue; }
                if (c == '"')           { quoted = !quoted; cur.append(c); continue; }
                if (c == sep && !quoted) { out.add(cur.toString()); cur.setLength(0); continue; }
                cur.append(c);
            }
            out.add(cur.toString());
            return out;
        }
        @Override public String toString() { return name + ": " + value; }
    }

    private static final class MacApkHeaderElement implements HeaderElement {
        private final String name;
        private final String value;
        private final NameValuePair[] params;
        MacApkHeaderElement(String name, String value, NameValuePair[] params) {
            this.name = name; this.value = value; this.params = params;
        }
        @Override public String getName()  { return name; }
        @Override public String getValue() { return value; }
        @Override public NameValuePair[] getParameters() { return params.clone(); }
        @Override public NameValuePair getParameterByName(String n) {
            for (NameValuePair p : params) if (p.getName().equalsIgnoreCase(n)) return p;
            return null;
        }
        @Override public int getParameterCount() { return params.length; }
        @Override public NameValuePair getParameter(int index) { return params[index]; }
    }

    private static final class MacApkHeaderIterator implements HeaderIterator {
        private final List<Header> headers;
        private final String name;
        private int pos;
        MacApkHeaderIterator(List<Header> headers, String name) {
            this.headers = headers;
            this.name = name;
            this.pos = advance(0);
        }
        private int advance(int from) {
            for (int i = from; i < headers.size(); i++) {
                if (name == null || name.equalsIgnoreCase(headers.get(i).getName())) return i;
            }
            return -1;
        }
        @Override public boolean hasNext() { return pos >= 0; }
        @Override public Header nextHeader() {
            if (pos < 0) throw new NoSuchElementException("No more headers available");
            Header h = headers.get(pos);
            pos = advance(pos + 1);
            return h;
        }
        @Override public Object next() { return nextHeader(); }
    }

    /** Streaming response body. Not repeatable, exactly like Apache's
     *  BasicHttpEntity over a live socket: the bytes are gone once read. */
    private static final class MacApkEntity implements HttpEntity {
        private final InputStream content;
        private final long length;
        private final Header contentType;
        private final Header contentEncoding;
        private final boolean chunked;
        private boolean consumed;
        MacApkEntity(InputStream content, long length, String contentType,
                     String contentEncoding, boolean chunked) {
            this.content = content;
            this.length = length;
            this.contentType = contentType != null ? new MacApkHeader("Content-Type", contentType) : null;
            this.contentEncoding =
                    contentEncoding != null ? new MacApkHeader("Content-Encoding", contentEncoding) : null;
            this.chunked = chunked;
        }
        @Override public boolean isRepeatable() { return false; }
        @Override public boolean isChunked()    { return chunked; }
        @Override public long getContentLength() { return length; }
        @Override public Header getContentType() { return contentType; }
        @Override public Header getContentEncoding() { return contentEncoding; }
        @Override public InputStream getContent() throws IOException, IllegalStateException {
            if (consumed) throw new IllegalStateException("Content has been consumed");
            return content;
        }
        @Override public void writeTo(OutputStream outstream) throws IOException {
            if (outstream == null) throw new IllegalArgumentException("Output stream may not be null");
            byte[] buf = new byte[4096];
            int n;
            while ((n = content.read(buf)) != -1) outstream.write(buf, 0, n);
        }
        @Override public boolean isStreaming() { return !consumed; }
        /** Apache's contract: drain what is left, then release. Draining
         *  rather than slamming the socket shut is what lets the host stack
         *  put the connection back in its keep-alive cache. */
        @Override public void consumeContent() throws IOException {
            if (consumed) return;
            consumed = true;
            try {
                byte[] buf = new byte[4096];
                while (content.read(buf) != -1) { /* drain */ }
            } catch (IOException ignored) {
                // A truncated body is not worth failing the caller over; the
                // close below still releases the connection.
            } finally {
                content.close();
            }
        }
    }

    private static final class MacApkResponse implements HttpResponse {
        private final List<Header> headers = new ArrayList<>();
        private StatusLine statusline;
        private HttpEntity entity;
        private Locale locale = Locale.getDefault();
        private HttpParams params = new BasicHttpParams();

        MacApkResponse(StatusLine statusline) { this.statusline = statusline; }

        @Override public StatusLine getStatusLine() { return statusline; }
        @Override public void setStatusLine(StatusLine statusline) { this.statusline = statusline; }
        @Override public void setStatusLine(ProtocolVersion ver, int code) {
            this.statusline = new MacApkStatusLine(ver, code, null);
        }
        @Override public void setStatusLine(ProtocolVersion ver, int code, String reason) {
            this.statusline = new MacApkStatusLine(ver, code, reason);
        }
        @Override public void setStatusCode(int code) {
            ProtocolVersion ver = statusline != null ? statusline.getProtocolVersion()
                                                    : HttpVersion.HTTP_1_1;
            this.statusline = new MacApkStatusLine(ver, code, null);
        }
        @Override public void setReasonPhrase(String reason) {
            ProtocolVersion ver = statusline != null ? statusline.getProtocolVersion()
                                                    : HttpVersion.HTTP_1_1;
            int code = statusline != null ? statusline.getStatusCode() : 0;
            this.statusline = new MacApkStatusLine(ver, code, reason);
        }
        @Override public HttpEntity getEntity() { return entity; }
        @Override public void setEntity(HttpEntity entity) { this.entity = entity; }
        @Override public Locale getLocale() { return locale; }
        @Override public void setLocale(Locale loc) { this.locale = loc; }

        @Override public ProtocolVersion getProtocolVersion() {
            return statusline != null ? statusline.getProtocolVersion() : HttpVersion.HTTP_1_1;
        }
        @Override public boolean containsHeader(String name) {
            for (Header h : headers) if (h.getName().equalsIgnoreCase(name)) return true;
            return false;
        }
        @Override public Header[] getHeaders(String name) {
            List<Header> out = new ArrayList<>();
            for (Header h : headers) if (h.getName().equalsIgnoreCase(name)) out.add(h);
            return out.toArray(new Header[0]);
        }
        @Override public Header getFirstHeader(String name) {
            for (Header h : headers) if (h.getName().equalsIgnoreCase(name)) return h;
            return null;
        }
        @Override public Header getLastHeader(String name) {
            for (int i = headers.size() - 1; i >= 0; i--) {
                if (headers.get(i).getName().equalsIgnoreCase(name)) return headers.get(i);
            }
            return null;
        }
        @Override public Header[] getAllHeaders() { return headers.toArray(new Header[0]); }
        @Override public void addHeader(Header header) { if (header != null) headers.add(header); }
        @Override public void addHeader(String name, String value) {
            headers.add(new MacApkHeader(name, value));
        }
        /** setHeader replaces the FIRST match in place and drops the rest, so
         *  header order survives a rewrite (HeaderGroup.updateHeader). */
        @Override public void setHeader(Header header) {
            if (header == null) return;
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).getName().equalsIgnoreCase(header.getName())) {
                    headers.set(i, header);
                    for (int j = headers.size() - 1; j > i; j--) {
                        if (headers.get(j).getName().equalsIgnoreCase(header.getName())) {
                            headers.remove(j);
                        }
                    }
                    return;
                }
            }
            headers.add(header);
        }
        @Override public void setHeader(String name, String value) {
            setHeader(new MacApkHeader(name, value));
        }
        @Override public void setHeaders(Header[] hs) {
            headers.clear();
            if (hs != null) for (Header h : hs) if (h != null) headers.add(h);
        }
        @Override public void removeHeader(Header header) { headers.remove(header); }
        @Override public void removeHeaders(String name) {
            headers.removeIf(h -> h.getName().equalsIgnoreCase(name));
        }
        @Override public HeaderIterator headerIterator() {
            return new MacApkHeaderIterator(headers, null);
        }
        @Override public HeaderIterator headerIterator(String name) {
            return new MacApkHeaderIterator(headers, name);
        }
        @Override public HttpParams getParams() { return params; }
        @Override public void setParams(HttpParams params) { this.params = params; }
    }

    public void setRoutePlanner(org.apache.http.conn.routing.HttpRoutePlanner routePlanner) { }
    public void setHttpRequestRetryHandler(org.apache.http.client.HttpRequestRetryHandler retryHandler) { }
}

