// org.apache.http.conn.ssl.StrictHostnameVerifier -- like the browser-compat
// verifier but a wildcard may not cross a dot: "*.foo.com" matches
// "a.foo.com" and NOT "a.b.foo.com" (strictWithSubDomains = true).
// Apache HttpClient 4.x (Apache License 2.0).
package org.apache.http.conn.ssl;

import javax.net.ssl.SSLException;

/** @deprecated Use {@link javax.net.ssl.HostnameVerifier}. */
@Deprecated
public class StrictHostnameVerifier extends AbstractVerifier {

    @Override
    public final void verify(final String host, final String[] cns, final String[] subjectAlts)
            throws SSLException {
        verify(host, cns, subjectAlts, true);
    }

    @Override
    public final String toString() {
        return "STRICT";
    }
}
