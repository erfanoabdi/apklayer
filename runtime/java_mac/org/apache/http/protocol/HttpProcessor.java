package org.apache.http.protocol;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
public interface HttpProcessor {
    void process(HttpRequest request, HttpContext context) throws HttpException, IOException;
    void process(HttpResponse response, HttpContext context) throws HttpException, IOException;
}
