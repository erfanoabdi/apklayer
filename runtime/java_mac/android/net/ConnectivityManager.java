// android.net.ConnectivityManager -- "what network do we have?" query service.
//
// ⚠ THIS CLASS USED TO ANSWER FROM A CONSTANT. Its header said, in as many
// words, that it claimed "always WIFI, always connected" so analytics SDKs
// would stop polling. That is the one shape of answer this framework is not
// supposed to give: a Mac has real networking, so the truthful answer is
// reachable, and CLAUDE.md's decision ladder puts "route it to the host" ahead
// of everything else. It now asks macOS -- see MacApkHostNetwork for the probe
// order (default route, hardware-port table, scutil) and its fallbacks.
//
// WHAT CHANGED, precisely:
//   * connected/disconnected is now the presence of a real DEFAULT ROUTE. On a
//     host that is online this is the same answer as before, so the common
//     case is unchanged; the delta is that a genuinely offline Mac now reports
//     offline instead of lying. That state is not new to the framework -- it
//     is exactly what MACAPK_OFFLINE already produced, and a previous session
//     verified it is AOSP-faithful (getActiveNetworkInfo returns null when
//     there is no default network).
//   * the network TYPE follows the real transport, so a wired Mac reports
//     ethernet rather than claiming Wi-Fi.
//
// ⚠ NOT MEASURED against the app fleet (this lane cannot launch apps).
// MACAPK_NET_HOST_TRUTH=0 restores the old constant answer so the change can
// be A/B'd in one run.
//
// STILL AN APPROXIMATION, and named so it is not mistaken for truth:
// NET_CAPABILITY_VALIDATED is inferred from "there is a default route", not
// from an actual reachability probe, and isActiveNetworkMetered() is still a
// flat false (macOS does surface per-interface Low Data Mode; we do not read
// it yet).
package android.net;

import android.content.Context;

public class ConnectivityManager {
    public static final int TYPE_NONE     = -1;
    public static final int TYPE_MOBILE   = 0;
    public static final int TYPE_WIFI     = 1;
    /** AOSP values; needed now that the type follows the host's transport. */
    public static final int TYPE_ETHERNET = 9;
    public static final int TYPE_VPN      = 17;

    // AOSP getRestrictBackgroundStatus() return values (Data Saver state).
    public static final int RESTRICT_BACKGROUND_STATUS_DISABLED  = 1;
    public static final int RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2;
    public static final int RESTRICT_BACKGROUND_STATUS_ENABLED   = 3;

    // AOSP broadcast actions apps register receivers for.
    public static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
    public static final String EXTRA_NETWORK = "android.net.extra.NETWORK";
    public static final String EXTRA_NETWORK_REQUEST = "android.net.extra.NETWORK_REQUEST";
    public static final String EXTRA_NO_CONNECTIVITY = "noConnectivity";
    public static final String EXTRA_EXTRA_INFO = "extraInfo";
    public static final String EXTRA_REASON = "reason";

    // AOSP requestNetwork(...): "no timeout" sentinel for the int-timeout
    // overloads (the value the no-timeout overload uses internally).
    private static final int NO_TIMEOUT = 0;

    // v1.0.609 OFFLINE POSTURE (opt-in, default OFF). MACAPK_OFFLINE=1 makes the
    // framework report a de-Googled device that ALSO has no network -- an
    // airplane-mode device, REGARDLESS of what the host actually has. This is
    // AOSP-faithful (getActiveNetworkInfo returns null when there is no default
    // network) and is the RELIABLE fix for apps that hang in a racy ONLINE-boot
    // handshake on a "connected" device: e.g. Minecraft Bedrock's android_main
    // posts a RakPeer/Xbox setup functor to a guest thread-pool and blocks on a
    // RakNet barrier for its completion, and that post has an intermittent
    // lost-wakeup -> MCPE hangs at 54% loading. With no network, MCPE's
    // NetworkMonitor takes the OFFLINE branch, never posts that functor, and
    // reaches its menu deterministically (single-player Minecraft is fully
    // offline). The gate is still needed even now that the default answer is
    // truthful, because the dev machine IS online and the flag is how a session
    // forces the offline posture anyway.

    /** True when this host has no usable network -- or when MACAPK_OFFLINE
     *  forces that posture. Not a constant: it re-reads the host (behind a
     *  short TTL cache) so an app sees the machine it is actually running on. */
    private static boolean offline() {
        return MacApkHostNetwork.forcedOffline() || !MacApkHostNetwork.get().connected;
    }

    /** The legacy int network TYPE matching the host's real transport. */
    private static int activeType() {
        switch (MacApkHostNetwork.get().transport) {
            case NetworkCapabilities.TRANSPORT_ETHERNET: return TYPE_ETHERNET;
            case NetworkCapabilities.TRANSPORT_VPN:      return TYPE_VPN;
            default:                                     return TYPE_WIFI;
        }
    }

    public ConnectivityManager() {}

    public NetworkInfo getActiveNetworkInfo() {
        if (offline()) return null;   // AOSP returns null when there is no default network
        return new NetworkInfo(activeType(), NetworkInfo.State.CONNECTED);
    }
    public NetworkInfo[] getAllNetworkInfo() {
        if (offline()) return new NetworkInfo[0];
        return new NetworkInfo[] { getActiveNetworkInfo() };
    }
    public boolean isActiveNetworkMetered() { return false; }
    public boolean isDefaultNetworkActive() { return !offline(); }
    /** Pre-ICS API. AdMob/analytics SDKs still call it on older targetSdk apps. */
    public boolean getBackgroundDataSetting() { return true; }

    /** API-21+ network registration. Apps subclass NetworkCallback and
     *  pass an instance to registerDefaultNetworkCallback / requestNetwork
     *  to learn about network availability changes. Mac.apk claims a
     *  single always-on WIFI network, so we just call onAvailable
     *  synchronously when the callback registers and never fire
     *  onLost/onCapabilitiesChanged. HCR 1.68's
     *  ConnectivityListener.onCreate hits NoClassDefFoundError on this
     *  class otherwise -- the listener is constructed during
     *  Application onCreate's "is the network up?" probe. */
    public static class NetworkCallback {
        // AOSP NetworkCallback(FLAG_INCLUDE_LOCATION_INFO): opt the callback's
        // NetworkCapabilities into carrying location-sensitive fields. We have
        // no location info to redact either way; the constant must exist so
        // apps that pass it to the int-ctor compile and bind.
        public static final int FLAG_INCLUDE_LOCATION_INFO = 1;
        public NetworkCallback() {}
        public NetworkCallback(int flags) {}
        public void onAvailable(Network network) {}
        public void onLosing(Network network, int maxMsToLive) {}
        public void onLost(Network network) {}
        public void onUnavailable() {}
        public void onCapabilitiesChanged(Network network, NetworkCapabilities cap) {}
        // AOSP signature is (Network, LinkProperties). The prior Object typing
        // produced a DIFFERENT method descriptor, so an app's
        // onLinkPropertiesChanged(Network, LinkProperties) override did NOT
        // actually override ours (it added an overload the framework never
        // calls), and a super.onLinkPropertiesChanged(net, lp) call with a real
        // LinkProperties would NoSuchMethodError. Matching the exact AOSP type
        // fixes the override binding. LinkProperties is now a real class.
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {}
        public void onBlockedStatusChanged(Network network, boolean blocked) {}
    }

    /** API-21+ "the default network just became active (traffic resumed after
     *  idle)" callback. Chromium cronet's org.chromium.net.NetworkActiveNotifier
     *  implements this and registers via addDefaultNetworkActiveListener; the
     *  class must exist or NetworkActiveNotifier fails to load (jni_zero CHECK).
     *  Mac.apk's single network is always active, so we never need to fire the
     *  "became active" edge (cronet treats it as a radio-wake optimization
     *  hint, not a correctness signal). */
    public interface OnNetworkActiveListener {
        void onNetworkActive();
    }
    public void addDefaultNetworkActiveListener(OnNetworkActiveListener l) {}
    public void removeDefaultNetworkActiveListener(OnNetworkActiveListener l) {}

    public Network getActiveNetwork() { return offline() ? null : new Network(); }
    public Network[] getAllNetworks() {
        return offline() ? new Network[0] : new Network[] { getActiveNetwork() };
    }
    public NetworkInfo getNetworkInfo(Network n) {
        if (offline()) return new NetworkInfo(TYPE_NONE, NetworkInfo.State.DISCONNECTED);
        return new NetworkInfo(activeType(), NetworkInfo.State.CONNECTED);
    }
    /** Deprecated pre-Lollipop int-typed overload (superseded by
     *  getNetworkInfo(Network), but never removed -- AOSP still routes it
     *  straight through to the service). AOSP returns null when networkType
     *  is "not supported by the device"; Mac.apk only ever presents
     *  TYPE_WIFI, so any other type is unsupported here. Large numbers of
     *  pre-2016-vintage ad/analytics SDKs bundled in current APKs still call
     *  this directly instead of the Network-typed API. */
    @Deprecated
    public NetworkInfo getNetworkInfo(int networkType) {
        // No cellular radio exists on a Mac, so TYPE_MOBILE is genuinely an
        // unsupported type and AOSP's answer for that is null. The two types
        // this host can present (Wi-Fi, Ethernet) answer CONNECTED for whichever
        // one actually carries the default route and DISCONNECTED for the other
        // -- which is what AOSP reports for a supported type that is down.
        if (networkType != TYPE_WIFI && networkType != TYPE_ETHERNET) return null;
        if (offline()) return new NetworkInfo(TYPE_NONE, NetworkInfo.State.DISCONNECTED);
        if (networkType != activeType()) {
            return new NetworkInfo(networkType, NetworkInfo.State.DISCONNECTED);
        }
        return new NetworkInfo(networkType, NetworkInfo.State.CONNECTED);
    }
    public NetworkCapabilities getNetworkCapabilities(Network n) {
        return offline() ? null : new NetworkCapabilities();
    }

    // ---- Network-state edge delivery -------------------------------------
    //
    // AOSP keeps a registered callback live until unregisterNetworkCallback:
    // onAvailable/onLost fire every time the default network comes and goes
    // (ConnectivityManager.CallbackHandler). Before this, we delivered the
    // CURRENT state once at registration and never fired again -- so an app
    // that launched offline, or lost Wi-Fi mid-run, and waits for onAvailable
    // to retry waited FOREVER even after the host reconnected. That is a hang
    // with no error anywhere, which is the worst shape a gap can take.
    //
    // The polling APIs already tracked flaps through MacApkHostNetwork's
    // 5s-TTL host probe; only the callbacks were frozen. So the pump below is
    // a diff over that same snapshot, not a new source of truth.

    private static final java.util.List<Reg> sRegs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static Thread sPump;
    private static boolean sLastOffline;
    private static int sLastTransport = -1;

    /** One live registration. AOSP retains the request and the handler; we keep
     *  both so unregister can remove exactly this one and edges land on the
     *  thread the app asked for. */
    private static final class Reg {
        final NetworkCallback cb; final android.os.Handler handler;
        /** The connectivity state this callback was told at
         *  registration. If the pump's FIRST edge for this callback would report
         *  that same state, the callback has already been told and replaying it
         *  would deliver onAvailable twice with no onLost between -- which breaks
         *  the contract for anything counting transitions. Comparing STATE is
         *  exact; comparing tick numbers is not, because registration and the
         *  pump run concurrently and a tick boundary does not say which side of
         *  the transition the registration landed on. */
        final boolean bornOffline;
        volatile boolean sawEdge;
        Reg(NetworkCallback cb, android.os.Handler handler, boolean bornOffline) {
            this.cb = cb; this.handler = handler; this.bornOffline = bornOffline;
        }
    }

    /** AOSP posts every callback through a Handler, so delivery never precedes
     *  register() returning: the app's handler when it passed one, otherwise
     *  the singleton "ConnectivityThread" looper (ConnectivityManager
     *  .getDefaultHandler(): {@code new CallbackHandler(ConnectivityThread
     *  .getInstanceLooper())}; ConnectivityThread is a HandlerThread named
     *  "ConnectivityThread", packages/modules/Connectivity framework/src/
     *  android/net/ConnectivityThread.java). Every registration carries a
     *  handler, so the initial availability sequence and later edges all land
     *  through it, in order.
     *
     *  This used to run the INITIAL delivery synchronously on the registering
     *  thread. Asphalt Legends (Gameloft JPal, 2026-10-05) registers in
     *  onCreate BEFORE super.onCreate, and its onAvailable does
     *  runOnUiThread(native OnConnectivityChanged): delivered inline on the
     *  UI thread, that native call ran before GameActivity.onCreate had loaded
     *  the game's library, the UnsatisfiedLinkError aborted MainActivity
     *  .onCreate, and the game never created its surface (black screen). On
     *  Android the callback arrives on ConnectivityThread and the runnable is
     *  posted to the main queue, behind the rest of onCreate. */
    private static void deliver(Reg r, Runnable work) {
        try { r.handler.post(work); return; } catch (Throwable ignored) { }
        try { work.run(); } catch (Throwable ignored) { }
    }

    private static android.os.Handler sDefaultHandler;

    /** AOSP ConnectivityManager.getDefaultHandler(): one lazily started
     *  "ConnectivityThread" looper shared by every callback registered without
     *  a handler. Daemon, like the pump, so it never holds the process up. */
    private static synchronized android.os.Handler getDefaultHandler() {
        if (sDefaultHandler == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("ConnectivityThread");
            t.setDaemon(true);
            t.start();
            sDefaultHandler = new android.os.Handler(t.getLooper());
        }
        return sDefaultHandler;
    }

    /** Started on the first registration -- an app that never registers pays
     *  nothing. Daemon, so it can never hold the process up at exit. */
    private static synchronized void startPump() {
        if (sPump != null) return;
        sLastOffline = offline();
        sLastTransport = sLastOffline ? -1 : MacApkHostNetwork.get().transport;
        sPump = new Thread(new Runnable() { @Override public void run() {
            for (;;) {
                try { Thread.sleep(2000L); } catch (InterruptedException e) { return; }
                if (sRegs.isEmpty()) continue;
                boolean nowOffline;
                int nowTransport;
                try {
                    nowOffline = offline();
                    nowTransport = nowOffline ? -1 : MacApkHostNetwork.get().transport;
                } catch (Throwable t) { continue; }
                boolean lost    = nowOffline && !sLastOffline;
                boolean gained  = !nowOffline && sLastOffline;
                // A transport change with no connectivity gap is AOSP's
                // onCapabilitiesChanged, not a new network.
                boolean changed = !nowOffline && !sLastOffline && nowTransport != sLastTransport;
                sLastOffline = nowOffline; sLastTransport = nowTransport;
                if (!lost && !gained && !changed) continue;
                for (final Reg r : sRegs) {
                    // Suppress only the FIRST edge, and only when it reports the
                    // state this callback was already told at registration.
                    if (!r.sawEdge) {
                        r.sawEdge = true;
                        if (nowOffline == r.bornOffline) continue;
                    }
                    if (lost) {
                        deliver(r, new Runnable() { @Override public void run() { r.cb.onLost(new Network()); } });
                    } else {
                        final Network n = new Network();
                        final NetworkCapabilities cap = new NetworkCapabilities();
                        // A regained network is a NEW availability: the same
                        // four-callback sequence as registration (see
                        // registerDefaultNetworkCallback for the AOSP citation
                        // and why onBlockedStatusChanged must be among them).
                        if (gained) deliver(r, new Runnable() { @Override public void run() {
                            r.cb.onAvailable(n); r.cb.onCapabilitiesChanged(n, cap);
                            r.cb.onLinkPropertiesChanged(n, new LinkProperties());
                            r.cb.onBlockedStatusChanged(n, false); } });
                        else        deliver(r, new Runnable() { @Override public void run() {
                            r.cb.onCapabilitiesChanged(n, cap); } });
                    }
                }
                com.macapk.loader.NativeLog.i("Connectivity", "network edge: "
                    + (lost ? "LOST" : gained ? "AVAILABLE" : "CAPABILITIES")
                    + " -> " + sRegs.size() + " callback(s)");
            }
        } }, "macapk-connectivity");
        sPump.setDaemon(true);
        sPump.start();
    }

    /** App registers a callback to learn about default network changes. The
     *  current state is posted to the callback's handler right away (AOSP does
     *  the same for an already-up network) and the registration then stays
     *  LIVE: the pump above fires onAvailable/onLost as the host's
     *  connectivity actually changes, until unregisterNetworkCallback. */
    public void registerDefaultNetworkCallback(NetworkCallback cb) {
        registerDefaultNetworkCallback(cb, getDefaultHandler());
    }
    public void registerDefaultNetworkCallback(final NetworkCallback cb, android.os.Handler handler) {
        if (cb == null) return;
        if (handler == null) handler = getDefaultHandler();
        // AOSP throws IllegalArgumentException("NetworkCallback was already
        // registered") rather than double-registering; without this check the
        // same callback receives every edge twice, forever.
        for (Reg r : sRegs) if (r.cb == cb) return;
        final boolean bornOffline = offline();
        Reg reg = new Reg(cb, handler, bornOffline);
        sRegs.add(reg);
        startPump();
        if (bornOffline) {
            // No default network -> deliver the "no network" edge so a monitor
            // waiting on connectivity resolves to offline instead of hanging.
            deliver(reg, new Runnable() { @Override public void run() {
                cb.onUnavailable();
                cb.onLost(new Network()); } });
            return;
        }
        // AOSP delivers FOUR callbacks for an already-up network, in this
        // order, and libraries key on the last one. NetworkCallback.onAvailable
        // javadoc (packages/modules/Connectivity framework/src/android/net/
        // ConnectivityManager.java, main): "Starting with O, this will always
        // immediately be followed by a call to onCapabilitiesChanged(Network,
        // NetworkCapabilities) then by a call to onLinkPropertiesChanged(
        // Network, LinkProperties), and a call to onBlockedStatusChanged(
        // Network, boolean)". ConnectivityService drives them through the
        // hidden 4-arg onAvailable(network, caps, linkProperties, blocked).
        //
        // onBlockedStatusChanged is LOAD-BEARING, not decoration: WorkManager
        // 2.10+'s SharedNetworkCallback (work-runtime .../impl/constraints/
        // WorkConstraintsTracker.kt, dispatchOnConstraintState) refuses to
        // emit any constraint state until BOTH capabilitiesInitialized and
        // isBlocked != null -- "Not dispatching constraint state yet:
        // isBlocked=null, capabilitiesInitialized=true" -- so with only the
        // first two edges every NetworkType.CONNECTED work request stays
        // unschedulable in-process forever (2026-09-04, Aurora Store). We model
        // a single always-on, never-blocked network, so blocked is false and
        // no later onLost/onLosing fires.
        deliver(reg, new Runnable() { @Override public void run() { deliverAvailable(cb); } });
    }

    /** The AOSP initial-availability sequence for one callback; see
     *  registerDefaultNetworkCallback for the citation. */
    private void deliverAvailable(NetworkCallback cb) {
        Network n = getActiveNetwork();
        cb.onAvailable(n);
        cb.onCapabilitiesChanged(n, getNetworkCapabilities(n));
        cb.onLinkPropertiesChanged(n, getLinkProperties(n));
        cb.onBlockedStatusChanged(n, false);
    }
    // AOSP-typed (NetworkRequest, not Object): the JVM resolves an app's
    // invokevirtual by EXACT descriptor, and real callers -- e.g. Chromium
    // cronet's NetworkChangeNotifierAutoDetect.register() -- emit
    // (Landroid/net/NetworkRequest;Landroid/net/ConnectivityManager$NetworkCallback;...)V.
    // A method declared (Ljava/lang/Object;...) would NOT bind to that site
    // (NoSuchMethodError). v1.0.406: matched the AOSP signature exactly.
    public void registerNetworkCallback(NetworkRequest request, NetworkCallback cb) {
        registerDefaultNetworkCallback(cb);
    }
    public void registerNetworkCallback(NetworkRequest request, NetworkCallback cb, android.os.Handler handler) {
        registerDefaultNetworkCallback(cb, handler);
    }
    public void requestNetwork(NetworkRequest request, NetworkCallback cb) {
        registerDefaultNetworkCallback(cb);
    }
    public void requestNetwork(NetworkRequest request, NetworkCallback cb, android.os.Handler handler) {
        registerDefaultNetworkCallback(cb, handler);
    }
    // AOSP requestNetwork(request, callback, timeoutMs): on a timeout the
    // framework would fire onUnavailable if no matching network appears in
    // time. Ours is always available, so we satisfy the request immediately
    // (onAvailable) and the timeout never elapses. Common SDK pattern:
    // "request a network for N ms, then give up".
    public void requestNetwork(NetworkRequest request, NetworkCallback cb, int timeoutMs) {
        registerDefaultNetworkCallback(cb);
    }
    public void requestNetwork(NetworkRequest request, NetworkCallback cb,
                               android.os.Handler handler, int timeoutMs) {
        registerDefaultNetworkCallback(cb, handler);
    }
    // AOSP requestNetwork(request, PendingIntent): the framework broadcasts
    // the PendingIntent when a matching network appears. We can't faithfully
    // fire a PendingIntent broadcast here without the broadcast plumbing, so
    // this is a deliberate no-op (the app's broadcast never arrives, same as
    // a network that never materializes). The callback-based overloads above
    // are the ones apps actually wait on for connectivity.
    public void requestNetwork(NetworkRequest request, android.app.PendingIntent operation) {}
    public void registerNetworkCallback(NetworkRequest request, android.app.PendingIntent operation) {}
    public void releaseNetworkRequest(android.app.PendingIntent operation) {}

    /** AOSP stops delivering to this callback. Previously a no-op, which was
     *  harmless only because nothing was retained; now that registrations are
     *  live it has to really remove, or a callback outlives its owner and an
     *  edge lands on a dead object. */
    public void unregisterNetworkCallback(NetworkCallback cb) {
        if (cb == null) return;
        for (Reg r : sRegs) if (r.cb == cb) sRegs.remove(r);
    }
    public void unregisterNetworkCallback(android.app.PendingIntent operation) {}

    /** AOSP bindProcessToNetwork(network): routes ALL of this process's
     *  sockets through the given network (null clears the binding). With a
     *  single host network there is nothing to switch to, so the binding is a
     *  no-op that AOSP-faithfully reports success. Apps gate later traffic on
     *  this boolean.
     *  AOSP's body is just "return setProcessDefaultNetwork(network);" (the
     *  instance form exists only to force ConnectivityManager to be
     *  instantiated; frameworks/base android11-release core/java/android/net/
     *  ConnectivityManager.java:4489-4493), so it delegates here too.
     *  Unchanged in the android14 Connectivity module, where the class now
     *  lives (packages/modules/Connectivity framework/src/android/net/
     *  ConnectivityManager.java:5379-5383, read through the LineageOS
     *  lineage-21.0 mirror because googlesource returned 503); the line refs
     *  below give both. */
    public boolean bindProcessToNetwork(Network network) {
        return setProcessDefaultNetwork(network);
    }

    /** Deprecated pre-M form of bindProcessToNetwork, and the one that does the
     *  work in AOSP. STATIC there ("public static boolean
     *  setProcessDefaultNetwork", android11 :4517, android14 module :5407) and in the
     *  SDK-34 android.jar; it was an instance method here, so every app call
     *  (invokestatic) threw IncompatibleClassChangeError. Same answer as
     *  bindProcessToNetwork always gave. Found by debug/apishape_gate.sh
     *  (KNOWN_OPEN, struck 2026-09-26). */
    @Deprecated public static boolean setProcessDefaultNetwork(Network network) { return true; }

    /** AOSP getBoundNetworkForProcess(): the network this process is bound to
     *  via bindProcessToNetwork, or null if unbound. We never actually bind
     *  (single host network), so report "unbound" = null, matching the
     *  default-network behavior. AOSP's body is "return
     *  getProcessDefaultNetwork();" (android11 :4555-4559, android14 module
     *  :5445-5449). */
    public Network getBoundNetworkForProcess() { return getProcessDefaultNetwork(); }

    /** STATIC in AOSP ("public static Network getProcessDefaultNetwork()",
     *  android11 :4572, android14 module :5462) and the SDK-34 android.jar; it was an
     *  instance method here (ICCE at every app call). Same answer as
     *  getBoundNetworkForProcess always gave. */
    @Deprecated public static Network getProcessDefaultNetwork() { return null; }

    /** AOSP getRestrictBackgroundStatus(): the Data Saver state for this app.
     *  macOS has no Data Saver, so background data is never restricted ->
     *  DISABLED. Apps check this before deferring background downloads. */
    public int getRestrictBackgroundStatus() { return RESTRICT_BACKGROUND_STATUS_DISABLED; }

    /** AOSP getProxyForNetwork(network): the HTTP proxy configured for the
     *  given network as a ProxyInfo, or null for "no proxy". We surface the
     *  system proxy (http.proxyHost) if one is set, else null. cronet/OkHttp
     *  read this to decide whether to route through a proxy. */
    public ProxyInfo getProxyForNetwork(Network network) {
        return ProxyInfo.macapkSystemProxyOrNull();
    }
    /** AOSP getDefaultProxy(): the proxy for the default network. */
    public ProxyInfo getDefaultProxy() {
        return ProxyInfo.macapkSystemProxyOrNull();
    }

    /** AOSP isNetworkTypeValid: a pure RANGE test, "MIN_NETWORK_TYPE <=
     *  networkType && networkType <= MAX_NETWORK_TYPE" with MIN = TYPE_MOBILE
     *  (0) and MAX = TYPE_TEST (18) (frameworks/base android11-release
     *  core/java/android/net/ConnectivityManager.java:707, 749, 751, 841-843;
     *  identical in the android14 module at :577, 757, 799-801, 1150-1152).
     *  It asks "is this a network-type number", not "can this host have that
     *  transport". STATIC there and in the SDK-34 android.jar; it was an
     *  instance method here, so every app call (invokestatic) threw
     *  IncompatibleClassChangeError and the previous four-type body (WIFI /
     *  MOBILE / ETHERNET / VPN) was never observed. Found by
     *  debug/apishape_gate.sh (KNOWN_OPEN, struck 2026-09-26). */
    @Deprecated
    public static boolean isNetworkTypeValid(int networkType) {
        return MIN_NETWORK_TYPE <= networkType && networkType <= MAX_NETWORK_TYPE;
    }
    /** @hide in AOSP (public there): TYPE_TEST, the highest legacy type. */
    private static final int MAX_NETWORK_TYPE = 18;
    /** private in AOSP: TYPE_MOBILE. */
    private static final int MIN_NETWORK_TYPE = TYPE_MOBILE;

    /** AOSP reportNetworkConnectivity / reportBadNetwork: app-supplied hints
     *  the framework uses to re-validate a network. No validation pipeline
     *  here, so no-op. */
    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {}
    @Deprecated public void reportBadNetwork(Network network) {}

    /** AOSP requestBandwidthUpdate(network): asks the framework to refresh the
     *  cached bandwidth estimate for a network. We report static bandwidth, so
     *  there is nothing to refresh; report "not requested". */
    public boolean requestBandwidthUpdate(Network network) { return false; }

    /** AOSP returns the LinkProperties (DNS servers, domains, private-DNS
     *  state) of a Network, and only returns null when the network does not
     *  exist -- every other method in this class (getActiveNetworkInfo,
     *  getNetworkInfo, getNetworkCapabilities, getActiveNetwork) already
     *  reports the one network we claim as present via the OFFLINE branch;
     *  this method was the one holdout that returned null unconditionally,
     *  disagreeing with the class's own "always connected unless OFFLINE"
     *  model. cronet's AndroidNetworkLibrary.getDnsStatus (the one verified
     *  caller) tolerates null and a real object equally; both fall back to
     *  the no-DNS NetworkState branch since LinkProperties.getDnsServers()
     *  still reports empty (no per-network DNS config surfaced yet). */
    public LinkProperties getLinkProperties(Network n) {
        return offline() ? null : new LinkProperties();
    }

    public void registerBestMatchingNetworkCallback(android.net.NetworkRequest request,
            NetworkCallback networkCallback, android.os.Handler handler) { }

    /** Mac.apk-only (not AOSP API): the host's Wi-Fi HARDWARE presence, for
     *  PackageManager's FEATURE_WIFI answer. See MacApkHostNetwork.hasWifiHardware. */
    public static boolean macapkHostHasWifiHardware() {
        return MacApkHostNetwork.hasWifiHardware();
    }
}

