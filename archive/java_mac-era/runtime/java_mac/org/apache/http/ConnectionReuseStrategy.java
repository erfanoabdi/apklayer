package org.apache.http;
import org.apache.http.protocol.HttpContext;
public interface ConnectionReuseStrategy {
    boolean keepAlive(HttpResponse response, HttpContext context);
}
