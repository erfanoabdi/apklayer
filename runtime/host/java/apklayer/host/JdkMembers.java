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

    /** ART's Executable.artMethod (Android 13), filled by ApklayerArt (JdkArt) at the end of the Method/Constructor constructors. */
    static final class Executable {
        private long artMethod;
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

    static final class DatagramSocket {
        /** VpnService.protect(DatagramSocket) and apps that pass a UDP socket's descriptor to native code. The JDK's socket wraps its implementation
         *  (delegate(), a channel's DatagramSocketAdaptor since JDK 15); the descriptor is that channel's (DatagramChannelImpl.getFD). Reflection
         *  here runs in java.base (the body is copied there), where setAccessible on java.base's own members is allowed. */
        public java.io.FileDescriptor getFileDescriptor$() {
            try {
                java.net.DatagramSocket socket = (java.net.DatagramSocket) (Object) this;
                if (socket.getChannel() == null) {
                    java.lang.reflect.Method delegate = java.net.DatagramSocket.class.getDeclaredMethod("delegate");
                    delegate.setAccessible(true);
                    socket = (java.net.DatagramSocket) delegate.invoke(socket);
                }
                java.nio.channels.DatagramChannel channel = socket.getChannel();
                // shortcut: a socket over a custom DatagramSocketImpl (the protected constructor) has no channel and stays unsupported; upgrade when an app needs one
                if (channel == null) throw new UnsupportedOperationException("no descriptor for a " + socket.getClass().getName());
                java.lang.reflect.Method fd = channel.getClass().getMethod("getFD");
                fd.setAccessible(true);
                return (java.io.FileDescriptor) fd.invoke(channel);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** getCurrentSpi: AndroidKeyStoreProvider.getKeyStoreOperationHandle (BiometricPrompt/FingerprintManager CryptoObject) reads the provider
     *  object behind an initialized primitive; null before init, as libcore's. The private field is read in java.base (the body is copied there). */
    static final class Cipher {
        public javax.crypto.CipherSpi getCurrentSpi() {
            try { java.lang.reflect.Field f = javax.crypto.Cipher.class.getDeclaredField("spi"); f.setAccessible(true); return (javax.crypto.CipherSpi) f.get(this); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }

    static final class Mac {
        public javax.crypto.MacSpi getCurrentSpi() {
            try { java.lang.reflect.Field f = javax.crypto.Mac.class.getDeclaredField("spi"); f.setAccessible(true); return (javax.crypto.MacSpi) f.get(this); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }

    static final class KeyAgreement {
        public javax.crypto.KeyAgreementSpi getCurrentSpi() {
            try { java.lang.reflect.Field f = javax.crypto.KeyAgreement.class.getDeclaredField("spi"); f.setAccessible(true); return (javax.crypto.KeyAgreementSpi) f.get(this); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }

    static final class Signature {
        /** getInstance's Signature is a Signature$Delegate around the provider's SPI; any other (a provider's own subclass) has none (libcore's base: null). */
        public java.security.SignatureSpi getCurrentSpi() {
            if (!getClass().getName().equals("java.security.Signature$Delegate")) return null;
            try { java.lang.reflect.Field f = getClass().getDeclaredField("sigSpi"); f.setAccessible(true); return (java.security.SignatureSpi) f.get(this); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }

    static final class ByteBuffer {
        /** MediaCodec hides a queued buffer's ByteBuffer. A JDK buffer cannot be made inaccessible: it stays usable where Android's throws IllegalStateException. */
        public void setAccessible(boolean value) {}

        public boolean isAccessible() { return true; }
    }
}
