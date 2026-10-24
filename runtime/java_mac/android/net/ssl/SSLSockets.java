// android.net.ssl.SSLSockets -- AOSP API-29+ static helpers for
// turning on Android's "extra" SSLSocket features (ALPN, session
// tickets, etc.) on the platform's Conscrypt-backed SSL sockets.
// OkHttp's Android10Platform uses isSupportedSocket(socket) to gate
// session-ticket reuse on Android 10+. AOSP source:
//   frameworks/base/core/java/android/net/ssl/SSLSockets.java
//
// ⚠ CORRECTED 2026-08-31: the reasoning below ("reporting not-supported makes
// OkHttp take its standard JDK codepath, which works fine") is WRONG about the
// consequence, and it has been costing every OkHttp app on the fleet HTTP/2.
//
// It conflates "this socket is not Conscrypt" with "this socket cannot do the
// things AOSP's helper turns on". Measured against MCPE's OWN bundled OkHttp
// (javap on its converted classes.jar), Android10SocketAdapter needs exactly
// three things and NOTHING Conscrypt-specific:
//     SSLSockets.setUseSessionTickets(sock, true)   <- ours, a no-op is honest
//     SSLSocket.getSSLParameters().setApplicationProtocols(String[])  <- JDK 9+
//     SSLSocket.getApplicationProtocol()                              <- JDK 9+
// The last two are stock javax.net.ssl that SunJSSE fully implements. But
// Android10SocketAdapter.matchesSocket() is a bare invokestatic of
// isSupportedSocket, so returning false makes Android10Platform skip the adapter
// entirely -- ALPN is never configured and getSelectedProtocol() returns null, so
// OkHttp pins every connection to HTTP/1.1.
//
// It does NOT "fall back to OkHttp's JDK path": we present as Android
// (java.vm.name=Dalvik, see com/macapk/loader/Boot.java), so OkHttp selects
// Android10Platform, walks its adapter list to the end, and finds no adapter at
// all. Measured on a real MCPE run: Http1ExchangeCodec loaded 12x,
// Http2ExchangeCodec 0x, while client.discovery.minecraft-services.net and
// title.mgt.xboxlive.com both negotiate h2 from this host. A second cost falls
// out of the same miss: reaching the LAST adapter forces ConscryptPlatform.
// isSupported, which for any app bundling Conscrypt (MCPE, Signal, Telegram...)
// dlopens BoringSSL and registers 288 natives INSIDE RealConnection.connectTls --
// ~620ms on the connecting thread, per process.
//
// This is a rung-1 violation of the host-first decision ladder: macOS/the JDK
// supports ALPN natively and we were declining to route to it.
//
// THE FIX, and why it is shaped this way. AOSP's semantic is "is this socket the
// PLATFORM's own TLS socket, so the platform extras apply" (on a device the
// platform factory yields Conscrypt, hence true). The faithful Mac.apk analogue
// is "was this socket produced by the platform's TLS stack", i.e. loaded by the
// boot/platform classloader -- true for SunJSSE, false for an app-bundled
// org.conscrypt.* socket, which correctly leaves those to ConscryptSocketAdapter.
//
// GATED, DEFAULT OFF (MACAPK_ALPN=1). The blast radius is every OkHttp app on the
// fleet -- it changes the wire protocol, connection multiplexing and response
// delivery threading -- so it does not flip on until it has been measured. It is
// also a candidate perturbation for the MCPE 54% stall (it changes the transport
// of exactly the request whose completion is the break point), which is a reason
// to A/B it, NOT a reason to believe it. See
// docs/alpn-suppressed-http1-only_2026-08-31.md.
package android.net.ssl;

import javax.net.ssl.SSLSocket;

public class SSLSockets {
    private SSLSockets() {}

    /** MACAPK_ALPN=1 opts in; see the correction at the top of this file. */
    private static final boolean ALPN_ENABLED =
            System.getenv("MACAPK_ALPN") != null;
    private static boolean announced = false;

    /** AOSP: true iff this is the platform's own (Conscrypt) SSLSocket, which is
     *  what gates OkHttp's Android10SocketAdapter -- and therefore ALPN. Our
     *  analogue is "produced by the platform TLS stack", i.e. a boot/platform
     *  classloader socket; an app that ships its own Conscrypt still gets false
     *  here and is handled by ConscryptSocketAdapter, as on a device. */
    public static boolean isSupportedSocket(SSLSocket socket) {
        if (!ALPN_ENABLED || socket == null) return false;
        // A boot/platform-classloader class reports a null ClassLoader.
        boolean platform = socket.getClass().getClassLoader() == null;
        if (platform && !announced) {
            announced = true;
            // Instrument the fix itself: without this line a gate that is ON but
            // never reached is indistinguishable from a gate that is OFF, which
            // has already cost this project a wasted measurement round.
            System.err.println("[alpn] MACAPK_ALPN engaged: serving ALPN for "
                    + "platform SSLSocket " + socket.getClass().getName());
        }
        return platform;
    }

    /** Enables the TLS session-ticket extension. No-op on Mac.apk. */
    public static void setUseSessionTickets(SSLSocket socket, boolean useSessionTickets) {}

    /** AOSP exportKeyingMaterial(socket, label, context, length): RFC 5705
     *  TLS keying-material export, used by channel-binding / token-binding code
     *  (e.g. some auth SDKs). The host JSSE SSLSocket does not expose RFC 5705
     *  export portably, and AOSP itself throws SSLException for a non-Conscrypt
     *  socket. We are signature-faithful and report "unsupported on this
     *  socket" so callers take their non-channel-bound path. The method must
     *  exist (correct descriptor + checked SSLException) or a caller's
     *  invokestatic NoSuchMethodErrors. */
    public static byte[] exportKeyingMaterial(SSLSocket socket, String label,
            byte[] context, int length) throws javax.net.ssl.SSLException {
        throw new javax.net.ssl.SSLException(
            "exportKeyingMaterial is not supported on this SSLSocket");
    }
}
