// org.apache.http.conn.ssl.AllowAllHostnameVerifier -- accepts ANY hostname.
// Apache HttpClient 4.x (Apache License 2.0); shipped by Android's
// org.apache.http.legacy.
//
// This turns off certificate hostname checking entirely, which is why it is
// deprecated upstream and why nothing in Mac.apk selects it. It exists because
// an app may reference it, and if the app asks for it the app gets exactly what
// a device would give it -- silently substituting a stricter verifier would
// break connections that work on Android.
package org.apache.http.conn.ssl;

/** @deprecated Use {@link javax.net.ssl.HostnameVerifier}. */
@Deprecated
public class AllowAllHostnameVerifier extends AbstractVerifier {

    @Override
    public final void verify(final String host, final String[] cns, final String[] subjectAlts) {
        // Always passes.
    }

    @Override
    public final String toString() {
        return "ALLOW_ALL";
    }
}
