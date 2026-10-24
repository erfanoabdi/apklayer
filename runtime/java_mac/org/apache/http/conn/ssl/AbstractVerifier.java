// org.apache.http.conn.ssl.AbstractVerifier -- the shared hostname-matching
// logic behind AllowAll / BrowserCompat / Strict X509HostnameVerifier.
//
// THIS IS SECURITY CODE. It decides whether the certificate a server
// presented actually belongs to the host we asked for, so it is transcribed
// from the version Android's org.apache.http.legacy ships (Apache HttpClient
// 4.0-line AbstractVerifier, Apache License 2.0) rather than re-derived. The
// rules that look arbitrary are not:
//
//   * only cns[0] is considered. Firefox, wget, curl and the Sun JSSE all
//     ignore every CN after the first; matching a later one would accept
//     certificates those clients reject.
//   * a wildcard needs "*." AND at least two dots, so "*.com" cannot match
//     anything.
//   * acceptableCountryWildcard rejects "*.co.uk" / "*.com.au" style
//     second-level-domain wildcards via the BAD_COUNTRY_2LDS table.
//   * wildcards never apply when the host is a literal IP address.
//   * in STRICT mode a wildcard may not cross a dot: "*.foo.com" must not
//     match "a.b.foo.com".
//
// Later HttpClient (4.4+) rewrote this on top of DefaultHostnameVerifier /
// SubjectName / InetAddressUtils. We deliberately mirror the OLDER,
// self-contained version -- it is the one android.jar exposes, and pulling in
// three more @hide-adjacent helper classes to reproduce a deprecated API would
// add surface without changing any answer.
package org.apache.http.conn.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/** @deprecated Use {@link javax.net.ssl.HostnameVerifier}. */
@Deprecated
public abstract class AbstractVerifier implements X509HostnameVerifier {

    /**
     * Second-level domains that are effectively public suffixes, so that
     * "*.co.uk" is not allowed to match every UK site. Kept sorted because
     * {@link #acceptableCountryWildcard} binary-searches it.
     */
    private static final String[] BAD_COUNTRY_2LDS =
            { "ac", "co", "com", "ed", "edu", "go", "gouv", "gov", "info",
              "lg", "ne", "net", "or", "org" };

    static {
        // Cheap insurance against someone adding an entry out of order: a
        // mis-sorted array makes binarySearch miss, which would SILENTLY
        // start accepting "*.co.uk".
        Arrays.sort(BAD_COUNTRY_2LDS);
    }

    public AbstractVerifier() {
        super();
    }

    @Override
    public final void verify(String host, SSLSocket ssl) throws IOException {
        if (host == null) {
            throw new NullPointerException("host to verify is null");
        }
        SSLSession session = ssl.getSession();
        if (session == null) {
            // Upstream comment: this only happens under IBM 1.4.x when
            // spurious certificates show up in the chain. Touching the input
            // stream, then forcing a handshake, surfaces the real error
            // instead of a bare NPE.
            InputStream in = ssl.getInputStream();
            in.available();
            session = ssl.getSession();
            if (session == null) {
                ssl.startHandshake();
                session = ssl.getSession();
            }
        }
        Certificate[] certs = session.getPeerCertificates();
        X509Certificate x509 = (X509Certificate) certs[0];
        verify(host, x509);
    }

    @Override
    public final boolean verify(String host, SSLSession session) {
        try {
            Certificate[] certs = session.getPeerCertificates();
            X509Certificate x509 = (X509Certificate) certs[0];
            verify(host, x509);
            return true;
        } catch (SSLException e) {
            return false;
        }
    }

    @Override
    public final void verify(String host, X509Certificate cert) throws SSLException {
        String[] cns = getCNs(cert);
        String[] subjectAlts = getDNSSubjectAlts(cert);
        verify(host, cns, subjectAlts);
    }

    public final void verify(final String host, final String[] cns,
                             final String[] subjectAlts,
                             final boolean strictWithSubDomains) throws SSLException {

        // Only the FIRST CN is considered -- see the class header.
        LinkedList<String> names = new LinkedList<String>();
        if (cns != null && cns.length > 0 && cns[0] != null) {
            names.add(cns[0]);
        }
        if (subjectAlts != null) {
            for (String subjectAlt : subjectAlts) {
                if (subjectAlt != null) {
                    names.add(subjectAlt);
                }
            }
        }

        if (names.isEmpty()) {
            throw new SSLException("Certificate for <" + host
                    + "> doesn't contain CN or DNS subjectAlt");
        }

        // Collected for the error message, so a failure names what WAS offered.
        StringBuilder buf = new StringBuilder();

        // The host may be compared case-insensitively; the certificate's names
        // are lower-cased for the same reason.
        final String hostName = host.trim().toLowerCase(Locale.ENGLISH);
        boolean match = false;
        for (Iterator<String> it = names.iterator(); it.hasNext();) {
            String cn = it.next().toLowerCase(Locale.ENGLISH);
            buf.append(" <").append(cn).append('>');
            if (it.hasNext()) {
                buf.append(" OR");
            }

            // Wildcards need "*." plus at least one more dot, must not be a
            // country 2LD wildcard, and never apply to an IP literal.
            boolean doWildcard = cn.startsWith("*.")
                    && cn.indexOf('.', 2) >= 0
                    && acceptableCountryWildcard(cn)
                    && !isIPAddress(host);

            if (doWildcard) {
                match = hostName.endsWith(cn.substring(1));
                if (match && strictWithSubDomains) {
                    // STRICT: "*.foo.com" must not match "a.b.foo.com".
                    match = countDots(hostName) == countDots(cn);
                }
            } else {
                match = hostName.equals(cn);
            }
            if (match) {
                break;
            }
        }
        if (!match) {
            throw new SSLException("hostname in certificate didn't match: <"
                    + host + "> !=" + buf);
        }
    }

    /**
     * False for "*.co.uk"-shaped names: a wildcard directly under a
     * country-code public suffix would match every site in that country.
     */
    public static boolean acceptableCountryWildcard(final String cn) {
        int cnLen = cn.length();
        if (cnLen >= 7 && cnLen <= 9) {
            // A '.' in the third-from-last position means a two-letter TLD.
            if (cn.charAt(cnLen - 3) == '.') {
                // Trim the leading "*." and the trailing ".XX".
                String s = cn.substring(2, cnLen - 3);
                return Arrays.binarySearch(BAD_COUNTRY_2LDS, s) < 0;
            }
        }
        return true;
    }

    /**
     * The CN values from the certificate's subject DN, or null if there are
     * none. Uses getSubjectX500Principal().toString() -- upstream's choice --
     * because getName() escapes non-ASCII in a way that breaks IDN matching.
     */
    public static String[] getCNs(X509Certificate cert) {
        LinkedList<String> cnList = new LinkedList<String>();
        String subjectPrincipal = cert.getSubjectX500Principal().toString();
        java.util.StringTokenizer st = new java.util.StringTokenizer(subjectPrincipal, ",");
        while (st.hasMoreTokens()) {
            String tok = st.nextToken().trim();
            if (tok.length() > 3) {
                if (tok.substring(0, 3).equalsIgnoreCase("CN=")) {
                    cnList.add(tok.substring(3));
                }
            }
        }
        if (!cnList.isEmpty()) {
            return cnList.toArray(new String[cnList.size()]);
        }
        return null;
    }

    /**
     * The dNSName entries of the certificate's subjectAltName extension, or
     * null if there are none. Type 2 is dNSName per RFC 5280.
     */
    public static String[] getDNSSubjectAlts(X509Certificate cert) {
        LinkedList<String> subjectAltList = new LinkedList<String>();
        Collection<List<?>> c = null;
        try {
            c = cert.getSubjectAlternativeNames();
        } catch (CertificateParsingException ignored) {
            // Upstream swallows this too: an unparseable extension must not
            // be louder than "no alt names", or a malformed certificate would
            // throw where it should simply fail to match.
        }
        if (c != null) {
            for (List<?> list : c) {
                if (list == null || list.size() < 2) {
                    continue;
                }
                int type = ((Integer) list.get(0)).intValue();
                if (type == 2) {                       // dNSName
                    Object value = list.get(1);
                    if (value instanceof String) {
                        subjectAltList.add((String) value);
                    }
                }
            }
        }
        if (!subjectAltList.isEmpty()) {
            return subjectAltList.toArray(new String[subjectAltList.size()]);
        }
        return null;
    }

    /** Counts the '.' characters in a string. */
    public static int countDots(final String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') {
                count++;
            }
        }
        return count;
    }

    /**
     * Is the host a literal IP address rather than a name? Wildcards must not
     * apply to one. A conservative check is the SAFE direction here: treating
     * a name as an IP only disables wildcard matching.
     */
    private static boolean isIPAddress(final String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            return true;                                // IPv6 literal
        }
        // IPv4 dotted quad: four 1..3-digit components.
        int parts = 0;
        int digits = 0;
        for (int i = 0; i < host.length(); i++) {
            char ch = host.charAt(i);
            if (ch == '.') {
                if (digits == 0 || digits > 3) return false;
                parts++;
                digits = 0;
            } else if (ch >= '0' && ch <= '9') {
                digits++;
            } else {
                return false;
            }
        }
        return parts == 3 && digits > 0 && digits <= 3;
    }
}
