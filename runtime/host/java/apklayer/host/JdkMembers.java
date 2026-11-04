package apklayer.host;

/**
 * Bodies of libcore-only members that JdkPatch copies into the java.base class of the same simple name ({@link JdkPatch#TARGETS}): every field and
 * method of a nested class except its constructor, with the nested class renamed to the target. Bodies may only use what the target can reach
 * (java.base API) and no static initializers. Found by apklayer.tools.JdkScan over the framework (android-all) and the corpus apps.
 */
final class JdkMembers {
    private JdkMembers() {}

    static final class Thread {
        /** WhatsApp calls it; libcore throws, JDK 21 removed it. */
        public final synchronized void stop(Throwable cause) { throw new UnsupportedOperationException(); }
    }

    static final class InetAddress {
        /** VpnService.Builder.addAddress/addRoute/addDnsServer, VpnProfile: a numeric address only, never a DNS lookup (libcore: inet_pton). */
        public static java.net.InetAddress parseNumericAddress(String address) {
            if (address == null || address.isEmpty()) return java.net.InetAddress.getLoopbackAddress();
            String literal = address.startsWith("[") && address.endsWith("]") ? address.substring(1, address.length() - 1) : address;
            if (!sun.net.util.IPAddressUtil.isIPv4LiteralAddress(literal) && !sun.net.util.IPAddressUtil.isIPv6LiteralAddress(literal))
                throw new IllegalArgumentException("Not a numeric address: " + address);
            try { return java.net.InetAddress.getByName(literal); }
            catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("Not a numeric address: " + address, e); }
        }

        /** ActivityThread / ConnectivityManager on network changes. The JDK has no call to flush its address cache: entries expire by its own TTL (30 s positive). */
        public static void clearDnsCache() {}
    }

    static final class DateFormat {
        /** The user's 12/24-hour preference, set by ActivityThread (libcore's DateFormat reads it; the JDK's formats ignore it). */
        public static Boolean is24Hour;

        public static final void set24HourTimePref(Boolean is24Hour) { DateFormat.is24Hour = is24Hour; }
    }

    static final class ECParameterSpec {
        /** The framework's Conscrypt (com.android.org.conscrypt.Platform) names curves through these. */
        private String curveName;

        public void setCurveName(String curveName) { this.curveName = curveName; }

        public String getCurveName() { return curveName; }
    }

    static final class ByteBuffer {
        /** MediaCodec hides a queued buffer's ByteBuffer. A JDK buffer cannot be made inaccessible: it stays usable where Android's throws IllegalStateException. */
        public void setAccessible(boolean value) {}

        public boolean isAccessible() { return true; }
    }
}
