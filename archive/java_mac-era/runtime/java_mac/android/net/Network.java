// android.net.Network -- opaque handle for a specific network. API-21+
// APIs return / accept this; ConnectivityManager.NetworkCallback's
// onAvailable receives one. We just provide a stable identity; apps
// don't typically inspect this object beyond passing it back to other
// ConnectivityManager methods.
package android.net;

public class Network {
    private final int netId;
    public Network() { this.netId = 1; }
    Network(int netId) { this.netId = netId; }

    public int  describeContents() { return 0; }
    @Override public boolean equals(Object o) {
        return (o instanceof Network) && ((Network) o).netId == netId;
    }
    @Override public int hashCode() { return netId; }
    @Override public String toString() { return "Network:" + netId; }

    /** Returns netId as a long; used by some apps to key network state
     *  in their own caches. Stable for the lifetime of this Network. */
    public long getNetworkHandle() { return netId; }

    /** AOSP: bind a socket so its traffic goes via THIS network. Mac.apk has a
     *  single host network, so binding is a no-op (traffic already goes the one
     *  way out). cronet's NetworkChangeNotifierAutoDetect only calls this on the
     *  VPN-filter branch, which our "non-VPN WIFI" capabilities never trigger;
     *  the no-op exists defensively so that branch can't NoSuchMethodError. */
    public void bindSocket(java.net.Socket socket) {}
    public void bindSocket(java.net.DatagramSocket socket) {}
    /** AOSP also binds a raw FileDescriptor's traffic to this Network.
     *  Single-host network => no-op, mirroring the Socket/DatagramSocket
     *  overloads above. */
    public void bindSocket(java.io.FileDescriptor fd) {}

    /** AOSP Network.openConnection(URL): opens a URLConnection whose traffic
     *  is routed via THIS network (per-network sockets + DNS). Mac.apk models
     *  a single host network, so routing is implicit: we delegate straight to
     *  the JDK's URL.openConnection(). This makes the common "fetch over the
     *  network I was handed in onAvailable()" pattern work instead of CNF/
     *  NoSuchMethodError. AOSP:
     *  packages/modules/Connectivity/.../android/net/Network.java. */
    public java.net.URLConnection openConnection(java.net.URL url) throws java.io.IOException {
        if (url == null) throw new IllegalArgumentException("url");
        return url.openConnection();
    }

    /** AOSP Network.openConnection(URL, Proxy): same as above but with an
     *  explicit proxy. Delegates to the JDK overload. */
    public java.net.URLConnection openConnection(java.net.URL url, java.net.Proxy proxy)
            throws java.io.IOException {
        if (url == null) throw new IllegalArgumentException("url");
        if (proxy == null) throw new IllegalArgumentException("proxy");
        return url.openConnection(proxy);
    }

    /** AOSP Network.getSocketFactory(): returns a SocketFactory whose sockets
     *  are bound to THIS network. With a single host network, the default TLS
     *  socket factory already routes correctly, so we hand back the JDK
     *  default SSLSocketFactory (a SocketFactory) -- OkHttp/HttpsURLConnection
     *  that call network.getSocketFactory() then create TLS sockets get a
     *  working factory. */
    public javax.net.SocketFactory getSocketFactory() {
        return javax.net.ssl.SSLSocketFactory.getDefault();
    }

    /** AOSP Network.getByName(host): like InetAddress.getByName but resolved
     *  on THIS network's DNS. Single host network => delegate to the JDK
     *  resolver. */
    public java.net.InetAddress getByName(String host) throws java.net.UnknownHostException {
        return java.net.InetAddress.getByName(host);
    }

    /** AOSP Network.getAllByName(host): all addresses for a host on this
     *  network. Delegates to the JDK resolver. */
    public java.net.InetAddress[] getAllByName(String host) throws java.net.UnknownHostException {
        return java.net.InetAddress.getAllByName(host);
    }

    /** AOSP static Network.fromNetworkHandle(networkHandle): reconstruct a
     *  Network from the opaque long returned by getNetworkHandle(). AOSP
     *  rejects 0 (the "no network" sentinel) with IllegalArgumentException and
     *  XORs out a per-version handle salt; we don't salt, so the handle IS the
     *  netId. Apps round-trip handles through Bundles / native code and call
     *  this to rebuild the object. */
    public static Network fromNetworkHandle(long networkHandle) {
        if (networkHandle == 0) {
            throw new IllegalArgumentException("networkHandle 0 is not a valid network handle");
        }
        return new Network((int) networkHandle);
    }
}
