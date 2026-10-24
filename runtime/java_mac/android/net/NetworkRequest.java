// android.net.NetworkRequest -- an immutable description of the network an
// app wants (a set of capabilities + transports). Apps build one with the
// nested Builder and hand it to ConnectivityManager.registerNetworkCallback /
// requestNetwork.
//
// Why this matters for Mac.apk (v1.0.406): Chromium Cronet's
// NetworkChangeNotifierAutoDetect.<init> (Reddit's HTTP stack) does
//   new NetworkRequest.Builder()
//       .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
//       .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
//       .build()
// during cronet's CronetLibraryLoader.initializeOnInitThread(). The prior stub
// had no Builder, so this threw NoClassDefFoundError: android/net/NetworkRequest$Builder,
// which aborted initializeOnInitThread BEFORE it called the native
// cronetInitOnInitThread() that constructs base::SingleThreadTaskExecutor. With
// no executor, the init thread had no SingleThreadTaskRunner::CurrentDefaultHandle,
// so a later cronet task's base::SingleThreadTaskRunner::GetCurrentDefault()
// CHECK-failed (brk #0 -> EXC_BREAKPOINT) and killed the process.
//
// AOSP surface mirrored: frameworks/base/.../android/net/NetworkRequest.java +
// NetworkRequest.Builder (addCapability/removeCapability/addTransportType/
// removeTransportType/build). We keep the capability/transport bitmasks so
// hasCapability/hasTransport can answer truthfully if anything inspects the
// built request; nothing in Mac.apk routes on it (we model one always-on WIFI
// network), but storing it is AOSP-faithful and cheap.
package android.net;

public class NetworkRequest {
    // Bitmask of NetworkCapabilities.NET_CAPABILITY_* requested.
    final long capabilities;
    // Bitmask of NetworkCapabilities.TRANSPORT_* requested (0 == "any").
    final long transports;
    // The transport-specific specifier this request was built with, or null.
    final NetworkSpecifier networkSpecifier;

    public NetworkRequest() { this.capabilities = 0L; this.transports = 0L; this.networkSpecifier = null; }
    NetworkRequest(long capabilities, long transports) {
        this(capabilities, transports, null);
    }
    NetworkRequest(long capabilities, long transports, NetworkSpecifier networkSpecifier) {
        this.capabilities = capabilities;
        this.transports = transports;
        this.networkSpecifier = networkSpecifier;
    }

    public boolean hasCapability(int capability) {
        return (capabilities & (1L << capability)) != 0;
    }
    public boolean hasTransport(int transportType) {
        return (transports & (1L << transportType)) != 0;
    }

    /** AOSP getCapabilities(): every NET_CAPABILITY_* bit this request asked
     *  for (via Builder.addCapability), ascending. Same bit-iteration shape
     *  as NetworkCapabilities.getCapabilities(), but over THIS request's own
     *  `capabilities` mask -- a request can ask for any combination the
     *  caller built, unlike NetworkCapabilities' fixed "healthy network"
     *  list, so we genuinely walk the bits rather than return a constant. */
    public int[] getCapabilities() {
        int[] out = new int[Long.bitCount(capabilities)];
        int idx = 0;
        for (int cap = 0; cap < 64; cap++) {
            if ((capabilities & (1L << cap)) != 0) out[idx++] = cap;
        }
        return out;
    }

    /** AOSP getTransportTypes(): every TRANSPORT_* bit this request asked
     *  for (via Builder.addTransportType), ascending. */
    public int[] getTransportTypes() {
        int[] out = new int[Long.bitCount(transports)];
        int idx = 0;
        for (int t = 0; t < 64; t++) {
            if ((transports & (1L << t)) != 0) out[idx++] = t;
        }
        return out;
    }

    /** AOSP getNetworkSpecifier(): the specifier set on the builder, or null. */
    public NetworkSpecifier getNetworkSpecifier() { return networkSpecifier; }

    /** AOSP canBeSatisfiedBy(other): true if a network with `other`
     *  capabilities would satisfy THIS request -- i.e. `other` has every
     *  capability and transport this request demands. Connectivity-aware
     *  libraries call this to test a candidate network against a saved
     *  request. Mac.apk's single network advertises the healthy WIFI set, so
     *  this answers honestly against the request's bitmasks. */
    public boolean canBeSatisfiedBy(NetworkCapabilities other) {
        if (other == null) return false;
        for (int cap = 0; cap < 64; cap++) {
            if ((capabilities & (1L << cap)) != 0 && !other.hasCapability(cap)) {
                return false;
            }
        }
        // transports == 0 means "any transport is fine".
        if (transports == 0L) return true;
        for (int t = 0; t < 64; t++) {
            if ((transports & (1L << t)) != 0 && other.hasTransport(t)) {
                return true;
            }
        }
        return false;
    }

    @Override public String toString() {
        return "NetworkRequest [ Capabilities: 0x" + Long.toHexString(capabilities)
            + " Transports: 0x" + Long.toHexString(transports) + " ]";
    }

    /** AOSP NetworkRequest.Builder -- fluent capability/transport accumulator.
     *  Every mutator returns this (the AOSP contract) so chained calls compile
     *  and run; build() snapshots the accumulated masks into an immutable
     *  NetworkRequest. */
    public static class Builder {
        private long capabilities = 0L;
        private long transports = 0L;
        private NetworkSpecifier networkSpecifier = null;

        public Builder() {}

        public Builder addCapability(int capability) {
            capabilities |= (1L << capability);
            return this;
        }
        public Builder removeCapability(int capability) {
            capabilities &= ~(1L << capability);
            return this;
        }
        public Builder addTransportType(int transportType) {
            transports |= (1L << transportType);
            return this;
        }
        public Builder removeTransportType(int transportType) {
            transports &= ~(1L << transportType);
            return this;
        }
        public Builder clearCapabilities() {
            capabilities = 0L;
            return this;
        }

        /** AOSP setNetworkSpecifier(NetworkSpecifier): attach a transport
         *  specifier (e.g. a WifiNetworkSpecifier) to the request. We store it
         *  so getNetworkSpecifier round-trips; Mac.apk routes on the single
         *  host network regardless. */
        public Builder setNetworkSpecifier(NetworkSpecifier specifier) {
            this.networkSpecifier = specifier;
            return this;
        }

        /** AOSP setNetworkSpecifier(String) (deprecated): legacy string form.
         *  AOSP wraps it in a StringNetworkSpecifier; we keep a small concrete
         *  wrapper so the value is preserved without referencing an absent
         *  type. Null/empty clears the specifier. */
        public Builder setNetworkSpecifier(String specifier) {
            this.networkSpecifier = (specifier == null || specifier.isEmpty())
                ? null : new StringNetworkSpecifier(specifier);
            return this;
        }

        /** AOSP setIncludeOtherUidNetworks(boolean): whether to match networks
         *  restricted to other UIDs. No per-UID networks here; no-op accepting
         *  the flag so the call compiles and chains. */
        public Builder setIncludeOtherUidNetworks(boolean include) {
            return this;
        }

        public NetworkRequest build() {
            return new NetworkRequest(capabilities, transports, networkSpecifier);
        }
    }

    /** Concrete NetworkSpecifier holding a legacy string, mirroring AOSP's
     *  (hidden) StringNetworkSpecifier so the deprecated
     *  Builder.setNetworkSpecifier(String) preserves its value. */
    static final class StringNetworkSpecifier extends NetworkSpecifier {
        final String specifier;
        StringNetworkSpecifier(String specifier) { this.specifier = specifier; }
        @Override public String toString() { return specifier; }
        @Override public boolean equals(Object o) {
            return (o instanceof StringNetworkSpecifier)
                && specifier.equals(((StringNetworkSpecifier) o).specifier);
        }
        @Override public int hashCode() { return specifier.hashCode(); }
    }
}
