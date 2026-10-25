// This device's Google Play Services stance -- ONE answer, read by every surface
// that gets asked. Do not re-derive either answer anywhere else.
//
// WHY THIS CLASS EXISTS. Mac.apk used to answer the Play-Services question in
// four contradictory ways at once, and which contradiction an app heard came
// down to a classloader flag:
//   getInstallerPackageName()            -> "com.android.vending"  (a store installed me)
//   getPackageInfo("com.android.vending")-> NameNotFoundException  (no such store)
//   our GoogleApiAvailabilityLight shadow-> SUCCESS or SERVICE_MISSING (an env flag)
//   the app's OWN bundled client         -> SERVICE_INVALID(9), derived from the above
// A real device is never in that state. Measured consequence (2026-08-01, four
// live Crossy Road runs): the app renders IF AND ONLY IF the verdict it receives
// is SUCCESS, and MACAPK_GMS_CHILD_FIRST merely selected which of our
// contradictory answers reached it. So the flag was load-bearing for the wrong
// reason. See docs/gms-device-oracle-parent-first_2026-08-01.md.
//
// THE TWO ANSWERS ARE DELIBERATELY SEPARATE, because they are different
// questions with different blast radii, and coupling them is what caused the
// v1.0.574 regression in the first place:
//   availability()        -- "is there an implementation of the Play-Services
//                            CLIENT API behind these classes?"  YES: Mac.apk
//                            ships one in-process.
//   gmsPackageInstalled() -- "is there an installed com.google.android.gms
//                            PACKAGE?"  NO: our provider is part of the
//                            platform, not a separate APK.
// Keeping them independent is what lets the availability answer be SUCCESS
// while Firebase Cloud Messaging stays honest: FCM's gate is
// checkPermission("com.google.android.c2dm.permission.SEND",
// "com.google.android.gms") == DENIED, which firebase-android-sdk's
// Metadata.isGmscorePresent() reads directly and which never consults
// availability. That DENIED is what self-terminates the unbounded
// "Rpc: No response" registration loop that once hung Minecraft Bedrock.
package com.macapk.loader;

public final class PlayServices {
    private PlayServices() {}

    // ConnectionResult.SUCCESS / SERVICE_MISSING as raw ints, on purpose: the
    // loader jar must not take a link dependency on the gms shadow (the gms
    // classes can be child-first, this class never is).
    private static final int SUCCESS         = 0;
    private static final int SERVICE_MISSING = 1;

    /** The client-API version this device's provider implements. Apps compare
     *  their bundled com.google.android.gms.version meta-data against this; a
     *  high value means "new enough", so no SERVICE_VERSION_UPDATE_REQUIRED. */
    public static final int VERSION_CODE = 244400000;

    // MACAPK_GMS_HONEST_MISSING=1/true: answer the fully-absent contract on the
    // availability question too. A/B diagnosis only -- nothing needs it set.
    private static final boolean FULLY_ABSENT =
            "1".equals(System.getenv("MACAPK_GMS_HONEST_MISSING"))
            || "true".equalsIgnoreCase(System.getenv("MACAPK_GMS_HONEST_MISSING"));

    // MACAPK_GMS_HONEST_MISSING=0/false: the legacy full spoof, where the gms
    // package is treated as installed AND permission-holding. Preserved only so
    // the pre-v1.0.574 behavior stays reachable for A/B. Setting it re-opens the
    // FCM registration loop; that is the point of it being a diagnostic.
    private static final boolean LEGACY_SPOOF =
            "0".equals(System.getenv("MACAPK_GMS_HONEST_MISSING"))
            || "false".equalsIgnoreCase(System.getenv("MACAPK_GMS_HONEST_MISSING"));

    /**
     * "Does this device have Google Play Services?"
     *
     * SUCCESS. Mac.apk implements the Play-Services client API in-process
     * (com/google/android/gms/**, gms.tasks, gms.common.api, and the real
     * play.core.assetpacks local-delivery impl) the same way it implements the
     * rest of android.* -- as part of the platform rather than as a separate
     * signed APK. This is the same answer microG's client library gives, and
     * for the same reason: availability cannot know in advance which specific
     * feature a caller will want, so it reports the provider present and lets
     * each individual SERVICE fail honestly at its own call site. A device
     * whose provider is present but whose Google backends are unreachable is a
     * shape real devices genuinely have.
     *
     * This is NOT a Play-certified spoof: we ship no Google signing
     * certificate, we still answer getPackageInfo("com.google.android.gms")
     * honestly (absent), we still deny the c2dm permission, and any app that
     * verifies GMS's signature still correctly finds it is not Google-signed.
     */
    public static int availability() {
        return FULLY_ABSENT ? SERVICE_MISSING : SUCCESS;
    }

    /**
     * "Is there an installed com.google.android.gms PACKAGE?" -- NO.
     * Independent of {@link #availability()} on purpose; see the class header.
     */
    public static boolean gmsPackageInstalled() {
        return LEGACY_SPOOF;
    }

    // ======================================================================
    // THE GMS LAYER (v1.0.1171+): Mac.apk's own macOS-native Play services.
    // ======================================================================
    //
    // Everything above answers "what do we tell an app about a Google stack we
    // do NOT have". This section is different in kind: it is the switch for a
    // stack we DO have -- a real signed-in Google account, a real checkin
    // android_id, and real calls to Google's servers on the user's behalf
    // (com.macapk.gms.*). When it is on, some answers above stop being
    // hypothetical.
    //
    // ⛔ THE SCOPE LINE IS UNCHANGED AND IS NOT A FLAG. Turning this on does not
    // turn on anything CLAUDE.md forbids. We present a com.android.vending
    // signed by US and never by Google, so an app that byte-compares the store's
    // signing certificate against Google's embedded copy still correctly finds
    // it is not Google's -- that app gets the honest absent answer forever, and
    // that is a principled ceiling, not a TODO. Play Integrity / SafetyNet /
    // DroidGuard are likewise untouched: this device is REGISTERED (checkin),
    // never CERTIFIED (attestation). Nothing here mints a licence; a Google-
    // signed JWS is relayed verbatim or the call fails honestly.
    //
    // ---- WHY vending IS A SEPARATE QUESTION FROM gms -----------------------
    // On a real device com.android.vending (the Play Store) and
    // com.google.android.gms (Play Services) are two packages, installed and
    // updated separately, and the Play-Licensing service lives in the STORE.
    // Keeping the two answers apart is what lets the licensing vertical land
    // without touching the tuned GMS-absent contract at all: the c2dm DENIED
    // that self-terminates Firebase's registration loop (and once hung Minecraft
    // Bedrock and Crossy Road) is a fact about com.google.android.gms, and this
    // flag does not move it. See docs/gms-device-oracle-parent-first_2026-08-01.md.
    // Coupling them is exactly the mistake that caused the v1.0.574 regression;
    // do not "simplify" these into one boolean.
    //
    // ---- DEFAULT ON (v1.0.1175, owner directive), MACAPK_GMS_PRESENT=0 to
    //      restore the absent contract for A/B ---------------------------------
    // The destination was always ON, and the licensing vertical now DELIVERS: a
    // real Google-signed LICENSED verdict for an owned app, NOT_LICENSED for an
    // unowned one (v1.0.1174, measured). The owner directed the flip.
    //
    // ⚠ WHY THIS IS SAFER THAN THE GENERAL "GMS present" FLIP THE EARNED-DEFAULT
    // RULE WARNED ABOUT. That warning was about com.google.android.gms and the
    // availability path -- pushing the 14 tuned corpus apps into a GMS-present
    // code path that then hangs on FCM. This flag does NOT touch that:
    // gmsPackageInstalled() stays NO (below), so the c2dm DENIED that
    // self-terminates Firebase's registration loop is unchanged, and
    // availability() was already SUCCESS. What flips ON is narrower: the
    // com.android.vending PACKAGE + its licensing service resolve, and the
    // AccountManager returns the owner's real com.google account. Both are what
    // a real device with the Play Store and a signed-in account genuinely does.
    //
    // ⚠ WHAT DEFAULT-ON EXPOSES, stated plainly: every guest app can now see the
    // owner's Google account (AccountManager.getAccountsByType) and request
    // tokens for it -- exactly as on a device with an account added. The Play
    // Store IDENTITY, however, is NOT lent to those requests: only the licensing
    // scope borrows it (GoogleAuthClient.applyPlayStoreIdentity is scope-gated),
    // so a guest asking for an arbitrary scope presents our own honest identity
    // and is refused by Google if that scope is gated. The concession stays
    // narrow.
    //
    // NOTE on verification: the games in scripts/sweep.sh do not exercise GMS,
    // so a clean sweep there proves the game paths did not regress but is NOT
    // the GMS gate. The GMS-sensitive canary is Crossy Road (and the tuned
    // corpus in docs/gms-device-oracle-parent-first); check it on the ON path.
    private static final boolean GMS_LAYER =
            !"0".equals(System.getenv("MACAPK_GMS_PRESENT"))
            && !"false".equalsIgnoreCase(System.getenv("MACAPK_GMS_PRESENT"));

    /**
     * Is Mac.apk's own GMS layer enabled? THE single gate for every new
     * GMS-layer code path. Every branch added for it reads
     * {@code if (PlayServices.gmsLayerEnabled())}, the same shape as the
     * existing {@code GMS_HONEST_MISSING &&} guards, so "off" is provably the
     * old behaviour rather than approximately it.
     */
    public static boolean gmsLayerEnabled() { return GMS_LAYER; }

    // ---- THE FCM / PUSH GATE (v1.0.1175), DEFAULT OFF -----------------------
    // Separate from GMS_LAYER on purpose, and default OFF for a measured reason.
    // Turning this on makes com.google.android.gms answer as an INSTALLED
    // package that HOLDS the c2dm SEND permission and RESOLVES a register
    // service -- which is exactly the state that, WITHOUT a service that
    // actually replies, re-opens the unbounded Firebase "Rpc: No response" loop
    // that hung Minecraft Bedrock and Crossy Road. So it must flip only
    // together with a live com.macapk.gms.PushRegisterService, and only after
    // the corpus is re-swept in this mode. The token half can be proven with
    // this OFF (the CLI drives GcmRegisterClient directly); the flag is what an
    // in-process guest app's getToken() rides on.
    //
    // ⚠ FLIPPING THIS ON MOVES com.google.android.gms FROM ABSENT TO PRESENT.
    // Every consumer of gmsPackageInstalled() (the c2dm DENIED, the FCM shadow)
    // must be checked. It is deliberately a THIRD answer, independent of both
    // availability() and gmsPackageInstalled(): "there is a push backend behind
    // the gms package". Default-on is earned by the MCS receive channel working
    // end to end plus a clean re-sweep, exactly like the vending flip was.
    // ---- DEFAULT ON (v1.0.1179). MACAPK_GMS_FCM=0 restores the old contract --
    // Earned exactly the way the vending flip was, and the gate was met on every
    // limb rather than on the sweep alone:
    //   * MACAPK_GMS_FCM=1 protected sweep: PASS=13 REGRESS=0 XFAIL=1, and NOT
    //     vacuous -- crossy and subway loaded their OWN bundled GmsRpc and
    //     reached this seam, so the canaries walked the Firebase path.
    //   * Registration: Subway Surfers, unmodified, got a real 142-char token
    //     from Google through the bound-Messenger seam in the live runtime.
    //   * Delivery: a push injected at the dispatcher travelled broadcast ->
    //     the app's FirebaseInstanceIdReceiver -> resolveService(MESSAGING_EVENT)
    //     -> com.helpshift.fcmunity.HelpshiftCustomFCMService.onMessageReceived,
    //     which is the hop that was structurally broken until v1.0.1177.
    //   * The "Rpc: No response" hang this gate was built around did not recur.
    //
    // ⚠ WHAT IS STILL NOT PROVEN, so the next reader does not over-read this:
    // no push has arrived from GOOGLE. The MCS socket authenticates and the
    // delivery path works, but DataMessageStanza parsing of a REAL server frame
    // is untested and needs a Firebase send credential we do not have. If that
    // parse is wrong, push does not arrive -- which is what the flag being off
    // did anyway, so it is not a regression, just not yet a benefit.
    private static final boolean GMS_FCM =
            !"0".equals(System.getenv("MACAPK_GMS_FCM"))
            && !"false".equalsIgnoreCase(System.getenv("MACAPK_GMS_FCM"));

    /** Is the in-process FCM push backend enabled? Gates the com.google.android.gms
     *  package presence + c2dm permission + register-service resolution. */
    public static boolean pushBackendEnabled() { return GMS_FCM; }

    /**
     * "Is there an installed com.android.vending PACKAGE?"
     *
     * YES when the layer is on: we host a real Play-Licensing service in-process
     * that talks to Google's real servers with the user's own account. This is
     * the same shape of claim as {@link #availability()} -- the provider is
     * present, and individual calls fail honestly at their own call sites (no
     * account -> ERROR_CONTACTING_SERVER, exactly as an offline device answers).
     *
     * It is deliberately NOT tied to whether an account is currently signed in.
     * A device with the Play Store installed and no account added still has the
     * package installed, and an app that probes presence before prompting for
     * sign-in needs that answer to be about the package, not the session.
     */
    public static boolean vendingPresent() { return GMS_LAYER; }
}
