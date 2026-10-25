package android.webkit;

import java.io.InputStream;
import java.util.Map;

// android.webkit.WebResourceResponse (AOSP API-11; status/headers ctor API-21).
// Returned from WebViewClient.shouldInterceptRequest to serve a response for a
// resource the page requested. Now actually STORES its fields (was no-op
// getters) and adds the API-21 status-code/headers surface. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebResourceResponse.java
public class WebResourceResponse {

    private String mimeType;
    private String encoding;
    private int statusCode;
    private String reasonPhrase;
    private Map<String, String> responseHeaders;
    private InputStream inputStream;

    // Retained (not in AOSP) so any existing in-tree caller using the no-arg
    // form keeps compiling; additive only.
    public WebResourceResponse() {}

    public WebResourceResponse(String mimeType, String encoding, InputStream data) {
        this.mimeType = mimeType;
        this.encoding = encoding;
        this.inputStream = data;
    }

    public WebResourceResponse(String mimeType, String encoding, int statusCode,
                               String reasonPhrase, Map<String, String> responseHeaders,
                               InputStream data) {
        this.mimeType = mimeType;
        this.encoding = encoding;
        setStatusCodeAndReasonPhrase(statusCode, reasonPhrase);
        this.responseHeaders = responseHeaders;
        this.inputStream = data;
    }

    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getMimeType() { return mimeType; }

    public void setEncoding(String encoding) { this.encoding = encoding; }

    public String getEncoding() { return encoding; }

    public void setStatusCodeAndReasonPhrase(int statusCode, String reasonPhrase) {
        // AOSP validates the range/emptiness; mirror those guards so apps that
        // depend on the IllegalArgumentException contract behave identically.
        if (statusCode < 100) {
            throw new IllegalArgumentException("statusCode can't be less than 100.");
        }
        if (statusCode > 599) {
            throw new IllegalArgumentException("statusCode can't be greater than 599.");
        }
        if (statusCode > 299 && statusCode < 400) {
            throw new IllegalArgumentException("statusCode can't be in the [300, 399] range.");
        }
        if (reasonPhrase == null) {
            throw new IllegalArgumentException("reasonPhrase can't be null.");
        }
        if (reasonPhrase.trim().isEmpty()) {
            throw new IllegalArgumentException("reasonPhrase can't be empty.");
        }
        for (int i = 0; i < reasonPhrase.length(); i++) {
            int c = reasonPhrase.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException(
                        "reasonPhrase can't contain non-ASCII characters.");
            }
        }
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public int getStatusCode() { return statusCode; }

    public String getReasonPhrase() { return reasonPhrase; }

    public void setResponseHeaders(Map<String, String> headers) {
        this.responseHeaders = headers;
    }

    public Map<String, String> getResponseHeaders() { return responseHeaders; }

    public void setData(InputStream data) { this.inputStream = data; }

    public InputStream getData() { return inputStream; }
}
