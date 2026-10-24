package org.apache.http.impl.client;
import java.io.Closeable;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
/** 4.3 base client. Mac.apk wires no apache transport, so doExecute throws; apps
 *  that need real networking use the host-JVM path. The full API links. */
public abstract class CloseableHttpClient implements HttpClient, Closeable {
    protected abstract HttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException, ClientProtocolException;
    private static HttpHost determineTarget(HttpUriRequest request) {
        java.net.URI uri = request != null ? request.getURI() : null;
        if (uri != null && uri.isAbsolute()) return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        return null;
    }
    @Override public HttpResponse execute(HttpUriRequest request) throws IOException, ClientProtocolException {
        return doExecute(determineTarget(request), request, null);
    }
    @Override public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return doExecute(determineTarget(request), request, context);
    }
    @Override public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException, ClientProtocolException {
        return doExecute(target, request, null);
    }
    @Override public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return doExecute(target, request, context);
    }
    @Override public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler) throws IOException, ClientProtocolException {
        return execute(request, handler, null);
    }
    @Override public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler, HttpContext context) throws IOException, ClientProtocolException {
        return execute(determineTarget(request), request, handler, context);
    }
    @Override public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> handler) throws IOException, ClientProtocolException {
        return execute(target, request, handler, null);
    }
    @Override public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> handler, HttpContext context) throws IOException, ClientProtocolException {
        return handler.handleResponse(doExecute(target, request, context));
    }
    @Override public HttpParams getParams() { return null; }
    @Override public ClientConnectionManager getConnectionManager() { return null; }
    @Override public void close() throws IOException {}
}
