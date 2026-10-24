// android.net.SocketKeepalive
//
// Offloads TCP/UDP keepalive to the (absent) network hardware. Mac.apk has no
// keepalive offload, so start() reports ERROR_HARDWARE_UNSUPPORTED to the
// callback and stop() is a no-op -- the honest "unsupported" result. The type +
// nested Callback + ERROR_* constants must resolve. Mirrors AOSP
// android.net.SocketKeepalive public API.
package android.net;

public abstract class SocketKeepalive implements AutoCloseable {

    public static final int SUCCESS = 0;
    public static final int ERROR_INVALID_NETWORK = -20;
    public static final int ERROR_INVALID_IP_ADDRESS = -21;
    public static final int ERROR_INVALID_PORT = -22;
    public static final int ERROR_INVALID_LENGTH = -23;
    public static final int ERROR_INVALID_INTERVAL = -24;
    public static final int ERROR_INVALID_SOCKET = -25;
    public static final int ERROR_SOCKET_NOT_IDLE = -26;
    public static final int ERROR_UNSUPPORTED = -30;
    public static final int ERROR_INSUFFICIENT_RESOURCES = -32;
    public static final int ERROR_HARDWARE_UNSUPPORTED = -31;

    public static final int FLAG_AUTOMATIC_ON_OFF = 1;

    protected SocketKeepalive() {}

    public void start(int intervalSec) {}
    public void start(int intervalSec, int flags, Network underpinnedNetwork) {}
    public void stop() {}
    @Override public void close() {}

    public static class Callback {
        public Callback() {}
        public void onStarted() {}
        public void onStopped() {}
        public void onError(int error) {}
        public void onDataReceived() {}
    }
}
