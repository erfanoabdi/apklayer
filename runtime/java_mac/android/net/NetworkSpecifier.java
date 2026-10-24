// android.net.NetworkSpecifier -- the abstract base for transport-specific
// network selectors (WifiNetworkSpecifier, etc.) attached to a NetworkRequest
// via NetworkRequest.Builder.setNetworkSpecifier and returned by
// NetworkCapabilities.getNetworkSpecifier. AOSP declares it abstract with a
// hidden canBeSatisfiedBy; the public surface is just the type itself.
// AOSP: packages/modules/Connectivity/framework/src/android/net/NetworkSpecifier.java
package android.net;

public abstract class NetworkSpecifier {
    public NetworkSpecifier() {}
}
