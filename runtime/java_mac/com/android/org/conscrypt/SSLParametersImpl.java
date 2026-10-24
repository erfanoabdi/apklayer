// com.android.org.conscrypt.SSLParametersImpl -- 2026-07-23, input-latency lane.
//
// Root cause this exists for (byte-verified against the actual APK, not
// guessed): Subway Surfers' Mintegral ad SDK bundles a shaded, older OkHttp
// fork (com.mbridge.msdk.thrid.okhttp -- "thrid" is their own typo for
// "third"). Its Platform.findPlatform() equivalent
// (com.mbridge.msdk.thrid.okhttp.internal.platform.g$a(), decompiled from
// ~/Documents/android/data/data/com.kiloo.subwaysurf_3.62.0/classes.jar) is:
//
//   private static g a() {
//       g p = platform.a.h(); if (p != null) return p;
//       g p2 = platform.b.h(); if (p2 != null) return p2;
//       throw new NullPointerException("No platform found on Android");
//   }
//
// and it is only reached when g.f() -- literally
// `"Dalvik".equals(System.getProperty("java.vm.name"))` -- returns true.
// Boot.java deliberately sets java.vm.name=Dalvik (see the comment there) so
// libGDX's SharedLibraryLoader takes its correct Android native-lib-loading
// path; that is a real, load-bearing, positive claim "this process is
// Android." Mintegral's g.f()==true path takes it at its word and tries ONLY
// two Android-specific providers -- platform.a.h() and platform.b.h(),
// decompiled from the same jar -- each of which does, in order:
//   Class.forName("com.android.org.conscrypt.SSLParametersImpl")
//   catch ClassNotFoundException -> (b.h() only) try
//     Class.forName("org.apache.harmony.xnet.provider.jsse.SSLParametersImpl")
// and returns null if that Class.forName fails. Mainline/current OkHttp's
// real Platform.findPlatform() always has a Platform("JVM") fallback for
// exactly this case; Mintegral's older, shaded fork does not -- both
// providers returning null falls straight into the NullPointerException
// above, thrown from a static initializer, which poisons the class for the
// rest of the process (NoClassDefFoundError on every subsequent use) and
// crashes a background thread pool ("FATAL EXCEPTION: CommonThreadPool").
//
// com.android.org.conscrypt.SSLParametersImpl is AOSP's OWN internal,
// jarjar-relocated copy of Conscrypt bundled into every real Android's boot
// classpath (external/conscrypt in AOSP, relocated under com.android.org.*
// specifically so it never collides with an app's own bundled copy of the
// standalone org.conscrypt.* library) -- it is NOT something an app ships,
// it is something the platform always provides. So a real Android device at
// API 29+ (checked via Build.VERSION.SDK_INT, decompiled from the same
// b.i()) always has this exact class loadable, which is exactly the signal
// Mintegral's code is (over-confidently) relying on.
//
// THIS IS A DIFFERENT RISK CATEGORY than android.security.MacApkConscryptProvider's
// documented "do NOT ship org.conscrypt.* classes" decision (read that file's
// header before touching this one again) -- that warning is about the
// STANDALONE org.conscrypt library apps bundle and cast real sockets to
// (org.conscrypt.Conscrypt$Version / OkHttp's ConscryptPlatform.isSupported),
// where shipping a partial impostor risks a library actively TRYING to use
// Conscrypt-specific socket behavior and failing on a bad cast deep in a
// connection. This class is different on both ends:
//   - Mintegral's code never instantiates or casts anything to this class --
//     h() only needs Class.forName(...) to succeed (verified: the decompiled
//     bytecode passes the resolved Class straight into `new a(cls)`/`new
//     b(cls,...)` as a stored field, never an instance).
//   - The two potential real Method invocations this class's presence
//     enables (setUseSessionTickets/setHostname/getAlpnSelectedProtocol/
//     setAlpnProtocols, wrapped by their own internal class `f`) are looked
//     up via `actualSocket.getClass().getMethod(name, paramTypes)` -- i.e.
//     reflection against the REAL socket object's REAL runtime class at
//     invocation time, not against this class -- so on a real (non-
//     conscrypt-backed) Mac.apk SSLSocket that lookup fails closed
//     (NoSuchMethodException -> caught -> null -> the optional-call path
//     no-ops) exactly the way it already does on any real Android OEM build
//     whose vendor swapped in a differently-shaped TLS stack, which is
//     precisely the vendor-fragmentation case OkHttp's reflection-based
//     Android platform detection was designed to tolerate.
// So providing this class only fixes the DETECTION phase (stops the
// NullPointerException); it does not, and does not need to, make Mac.apk's
// actual sockets behave like real Conscrypt sockets.
//
// Method surface is exactly what platform.b.h()'s reflection asks for
// (decompiled com/mbridge/msdk/thrid/okhttp/internal/platform/b.class) --
// not copied from upstream google/conscrypt's CURRENT master source, which
// has since been refactored to setApplicationProtocols(String[])/
// getApplicationProtocol(): Mintegral's fork targets the OLDER API shape
// (setHostname/getAlpnSelectedProtocol byte[]/setAlpnProtocols byte[]) that
// shipped in AOSP conscrypt for API 21-28, which is the actual, historically
// real shape of this class on real devices at those levels; it is honest to
// provide it, not a fabrication.
package com.android.org.conscrypt;

public final class SSLParametersImpl {
    private boolean useSessionTickets;
    private String hostname;
    private byte[] alpnProtocols;

    // Real AOSP's SSLParametersImpl is never constructed directly by app/SDK
    // code (it backs the platform's own SSLSocket internals); nothing in the
    // reflection path above instantiates it either (see the class header),
    // so a plain public constructor is enough for Class.forName's caller to
    // have SOMETHING loadable, with no real Android construction contract to
    // reproduce.
    public SSLParametersImpl() { }

    public void setUseSessionTickets(boolean useSessionTickets) {
        this.useSessionTickets = useSessionTickets;
    }
    public boolean getUseSessionTickets() { return useSessionTickets; }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    public String getHostname() { return hostname; }

    public void setAlpnProtocols(byte[] alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
    }
    /** Real Conscrypt returns the ALPN protocol the handshake actually
     *  negotiated (null until a handshake completes and negotiates one). No
     *  handshake ever touches an instance of THIS class (see the class
     *  header -- callers reflect on the real socket's own class), so null
     *  ("nothing negotiated") is the only honest answer this stand-in can
     *  give; it is also AOSP's own answer before/without ALPN completion. */
    public byte[] getAlpnSelectedProtocol() { return null; }
}
