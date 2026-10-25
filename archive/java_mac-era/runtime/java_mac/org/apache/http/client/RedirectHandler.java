package org.apache.http.client;
import java.net.URI;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
public interface RedirectHandler {
    boolean isRedirectRequested(HttpResponse response, HttpContext context);
    URI getLocationURI(HttpResponse response, HttpContext context);
}
