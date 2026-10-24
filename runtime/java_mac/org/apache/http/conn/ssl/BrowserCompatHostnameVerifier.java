// org.apache.http.conn.ssl.BrowserCompatHostnameVerifier -- the default
// verifier: matches the way browsers do, allowing a wildcard to cross dots
// (strictWithSubDomains = false). Apache HttpClient 4.x (Apache License 2.0).
package org.apache.http.conn.ssl;

import javax.net.ssl.SSLException;

/** @deprecated Use {@link javax.net.ssl.HostnameVerifier}. */
@Deprecated
public class BrowserCompatHostnameVerifier extends AbstractVerifier {

    @Override
    public final void verify(final String host, final String[] cns, final String[] subjectAlts)
            throws SSLException {
        verify(host, cns, subjectAlts, false);
    }

    @Override
    public final String toString() {
        return "BROWSER_COMPATIBLE";
    }
}
