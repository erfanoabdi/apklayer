// Apache HTTP stubs. Pre-API-23 Android shipped the Apache HttpClient
// in framework. Modern Android dropped it; many old APKs (and the
// analytics/ads SDKs they pin) still link against these names. We
// expose interface/class names just enough that the verifier links;
// no real HTTP transport runs through these.
package org.apache.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface HttpEntity {
    boolean isRepeatable();
    boolean isChunked();
    long getContentLength();
    Header getContentType();
    Header getContentEncoding();
    InputStream getContent() throws IOException, IllegalStateException;
    void writeTo(OutputStream outstream) throws IOException;
    boolean isStreaming();
    void consumeContent() throws IOException;
}
