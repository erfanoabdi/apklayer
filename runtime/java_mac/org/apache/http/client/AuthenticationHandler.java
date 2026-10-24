package org.apache.http.client;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
public interface AuthenticationHandler {
    boolean isAuthenticationRequested(HttpResponse response, HttpContext context);
    Map<String, Header> getChallenges(HttpResponse response, HttpContext context);
}
