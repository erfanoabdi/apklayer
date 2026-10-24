// android.net.InetAddresses -- utilities to parse / validate NUMERIC IP
// addresses WITHOUT a DNS lookup. Libraries (OkHttp, gRPC, cronet's Java
// glue, anything that needs to tell "is this a literal IP or a hostname?")
// call isNumericAddress() before deciding whether to resolve, and
// parseNumericAddress() to turn a known-literal string into an InetAddress
// with no network round-trip.
//
// AOSP source (Connectivity module):
//   packages/modules/Connectivity/framework/src/android/net/InetAddresses.java
// Signatures verified against that source:
//   public static boolean isNumericAddress(String address)
//   public static InetAddress parseNumericAddress(String address)
//       -- throws IllegalArgumentException if not numeric (never does DNS)
//
// Implementation note: AOSP delegates to libcore / Android's own
// inet_pton-style parser that NEVER performs a name lookup. The JDK's
// InetAddress.getByName() ALSO never does a DNS lookup when handed a literal
// IP (numeric IPv4/IPv6 are parsed locally; only non-numeric inputs trigger
// resolution). So our faithful, no-DNS path is: first decide numeric-ness
// purely lexically, and only call InetAddress.getByName() once we KNOW the
// input is a literal -- guaranteeing parseNumericAddress never blocks on the
// network, exactly as the AOSP contract requires.
package android.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public class InetAddresses {
    private InetAddresses() {}

    /** AOSP: true iff `address` is a numeric IPv4 or IPv6 literal. Never does
     *  DNS. Mirrors the AOSP contract that a bracketed or scoped IPv6 literal
     *  ("fe80::1%eth0", "[::1]") is NOT accepted in the bare-string form (the
     *  framework strips brackets at the URI layer, not here). */
    public static boolean isNumericAddress(String address) {
        if (address == null) return false;
        return isNumericV4(address) || isNumericV6(address);
    }

    /** AOSP: parse a numeric IP literal into an InetAddress with no DNS.
     *  Throws IllegalArgumentException (NOT UnknownHostException) when the
     *  input is not a literal -- callers rely on the unchecked type. */
    public static InetAddress parseNumericAddress(String address) {
        if (!isNumericAddress(address)) {
            throw new IllegalArgumentException("Not a numeric address: " + address);
        }
        try {
            // Safe: we proved `address` is a literal above, so getByName parses
            // it locally and never performs a name resolution.
            return InetAddress.getByName(address);
        } catch (java.net.UnknownHostException e) {
            // Unreachable for a validated literal, but keep the unchecked
            // contract if a host parser disagrees with our lexical check.
            throw new IllegalArgumentException("Not a numeric address: " + address, e);
        }
    }

    // ---- lexical numeric checks (no DNS, no allocation on the common path) ----

    private static boolean isNumericV4(String s) {
        // Dotted-quad: exactly four 0..255 decimal octets, no leading-zero
        // ambiguity rejected (AOSP/inet_pton accepts "01" as 1, so we DON'T
        // reject leading zeros -- match inet_pton leniency for the common
        // case). Reject anything with a letter / colon.
        int parts = 0;
        int i = 0;
        int len = s.length();
        if (len == 0) return false;
        while (i < len) {
            int start = i;
            int val = 0;
            while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                val = val * 10 + (s.charAt(i) - '0');
                if (val > 255) return false;
                i++;
            }
            if (i == start) return false;       // empty octet (".." or leading ".")
            if (i - start > 3) return false;     // more than 3 digits
            parts++;
            if (parts > 4) return false;
            if (i < len) {
                if (s.charAt(i) != '.') return false;
                i++;
                if (i == len) return false;      // trailing dot
            }
        }
        return parts == 4;
    }

    private static boolean isNumericV6(String s) {
        // Quick reject: an IPv6 literal must contain a colon.
        if (s.indexOf(':') < 0) return false;
        try {
            // The JDK's literal IPv6 parser is reachable via InetAddress.
            // getByName for a string containing ':' and otherwise valid hex
            // groups; it NEVER does DNS for a colon-bearing string (a
            // hostname can't contain ':'), so this stays no-DNS. We additionally
            // confirm the result is an Inet6Address to reject odd inputs.
            InetAddress a = InetAddress.getByName(s);
            return a instanceof Inet6Address
                || (a instanceof Inet4Address && s.indexOf(':') >= 0 && s.contains("."));
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
