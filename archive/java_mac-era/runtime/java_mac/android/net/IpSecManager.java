// android.net.IpSecManager
//
// Manages IPsec security associations + transforms. Mac.apk has no IPsec
// subsystem, so SPI allocation fails with the documented
// SpiUnavailableException and UDP-encap socket allocation fails with
// ResourceUnavailableException -- the honest "no IPsec" result rather than a
// missing type. The nested exception/resource types must resolve. Mirrors
// AOSP android.net.IpSecManager public API.
package android.net;

import android.content.Context;

import java.net.InetAddress;

public class IpSecManager {

    public static final int INVALID_SECURITY_PARAMETER_INDEX = 0;
    public static final int DIRECTION_IN = 0;
    public static final int DIRECTION_OUT = 1;

    public IpSecManager() {}
    public IpSecManager(Context context) {}

    public SecurityParameterIndex allocateSecurityParameterIndex(InetAddress destinationAddress)
            throws ResourceUnavailableException, SpiUnavailableException {
        throw new ResourceUnavailableException("IPsec not supported");
    }

    public SecurityParameterIndex allocateSecurityParameterIndex(InetAddress destinationAddress,
            int requestedSpi) throws ResourceUnavailableException, SpiUnavailableException {
        throw new ResourceUnavailableException("IPsec not supported");
    }

    public UdpEncapsulationSocket openUdpEncapsulationSocket()
            throws java.io.IOException, ResourceUnavailableException {
        throw new ResourceUnavailableException("IPsec not supported");
    }

    public UdpEncapsulationSocket openUdpEncapsulationSocket(int port)
            throws java.io.IOException, ResourceUnavailableException {
        throw new ResourceUnavailableException("IPsec not supported");
    }

    public void applyTransportModeTransform(java.net.Socket socket, int direction,
            Object transform) throws java.io.IOException {}
    public void removeTransportModeTransforms(java.net.Socket socket) throws java.io.IOException {}

    public static final class SecurityParameterIndex implements AutoCloseable {
        private SecurityParameterIndex() {}
        public int getSpi() { return INVALID_SECURITY_PARAMETER_INDEX; }
        @Override public void close() {}
    }

    public static final class UdpEncapsulationSocket implements AutoCloseable {
        private UdpEncapsulationSocket() {}
        public java.io.FileDescriptor getFileDescriptor() { return null; }
        public int getPort() { return 0; }
        @Override public void close() {}
    }

    // AOSP: `extends android.util.AndroidException`.
    // frameworks/base/core/java/android/net/IpSecManager.java
    public static final class SpiUnavailableException extends android.util.AndroidException {
        private final int mSpi;
        public SpiUnavailableException(String msg, int spi) { super(msg); mSpi = spi; }
        public int getSpi() { return mSpi; }
    }

    // AOSP: `extends android.util.AndroidException`.
    public static final class ResourceUnavailableException extends android.util.AndroidException {
        public ResourceUnavailableException(String msg) { super(msg); }
    }
}
