package org.apache.http.client;

/** Signals a non-2xx HTTP response, carrying the status code that caused it.
 *
 *  Part of the {@code org.apache.http.legacy} optional shared library; mirrors
 *  Apache HttpClient 4.x. Extends ClientProtocolException (and so IOException),
 *  which is what lets callers treat it as an ordinary transport failure.
 */
public class HttpResponseException extends ClientProtocolException {

    private static final long serialVersionUID = -7186627969477257933L;

    private final int statusCode;

    public HttpResponseException(final int statusCode, final String s) {
        super(s);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return this.statusCode;
    }
}
