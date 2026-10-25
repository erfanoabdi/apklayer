// com.macapk.gms.PlayLicenseClient -- asks Google whether this account owns an app.
//
// ⛔ THE ONE RULE THIS FILE EXISTS TO KEEP: WE NEVER MINT A VERDICT.
// A Play licence answer is an RS256 JWS signed by Google, which the app verifies
// against a public key compiled into its own binary. We relay that token BYTE
// FOR BYTE or we report a failure. There is no code path here that constructs a
// licence, and there must never be one -- not "for testing", not behind a flag.
// Forging one is not merely against policy, it is cryptographically impossible
// without Google's private key, and any code that pretended otherwise would be
// lying to the owner rather than to Google.
//
// Consequently: NO ACCOUNT means ERROR_CONTACTING_SERVER, never NOT_LICENSED and
// never LICENSED. Those are verdicts about ownership; "I could not ask" is not a
// verdict, and the LVL defines a distinct local code for exactly that state.
//
// ---- WHAT WE SEND, AND THE IDENTITY QUESTION -------------------------------
// microG sends `User-Agent: Android-Finsky/30.6.18-...` and
// `X-DFE-Client-Id: am-google` -- the Play Store client's own identifiers. We
// default to OUR OWN User-Agent, for the same reason GoogleAuthClient presents
// our own certificate: if Google will serve an honest client we do not need to
// dress up as one, and if it will not, that refusal is a fact the owner should
// see rather than a thing to quietly work around. MACAPK_GMS_FINSKY_UA=1
// switches to the Finsky string so the difference can be MEASURED; it is a
// deliberate act by whoever sets it, not a default.
//
// X-PS-RH (a gzipped, base64url'd RequestHeader protobuf carrying timestamps,
// locality and device metadata) is NOT sent. Whether the endpoint requires it is
// unknown -- microG always sends it, so its absence has never been tested. It
// is ~150 lines of nested protobuf to construct and it carries no answer we
// need, so the honest order is: try without, and build it only if Google says
// it is required. If a request fails in a way that names it, that is the signal.
//
// PROTOCOL REFERENCE (read-only, never vendored): microG GmsCore @157c9d86,
//   vending-app/src/main/java/com/android/vending/licensing/LicenseChecker.kt
//   vending-app/src/main/proto/{GooglePlayResponse,LicenseResult}.proto
// Full tabulation: docs/gms-licensing-wire_2026-09-05.md
package com.macapk.gms;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayLicenseClient {

    // Google's own server codes, relayed verbatim on the v1 path.
    public static final int LICENSED                 = 0;
    public static final int NOT_LICENSED             = 1;
    public static final int LICENSED_OLD_KEY         = 2;
    // LOCAL codes -- the LVL's own, produced by the client, never by Google.
    // ERROR_CONTACTING_SERVER is the "I could not ask" answer, which classic LVL
    // treats as RETRY rather than as a verdict.
    public static final int ERROR_CONTACTING_SERVER    = 0x101;
    public static final int ERROR_INVALID_PACKAGE_NAME = 0x102;
    public static final int ERROR_NON_MATCHING_UID     = 0x103;

    private static final String BASE = "https://play-fe.googleapis.com/fdfe/apps/";

    // GoogleApiResponse.payload = 1; Payload.licenseV2Response = 173,
    // licenseV1Response = 76; LicenseCheckV2Response.license = 1; AppLicense.jwt = 1.
    private static final int F_PAYLOAD = 1, F_V2_RESPONSE = 173, F_V1_RESPONSE = 76;
    private static final int F_LICENSE = 1, F_JWT = 1;
    private static final int F_V1_RESULT = 1, F_V1_SIGNED_DATA = 2, F_V1_SIGNATURE = 3;

    private PlayLicenseClient() {}

    /** One licence answer. `jwt` is Google's signed token on the v2 path;
     *  `signedData`/`signature` are the v1 pair. All three are relayed untouched
     *  or absent -- never synthesised. */
    public static final class Result {
        public final int responseCode;
        public final String jwt;          // v2
        public final String signedData;   // v1
        public final String signature;    // v1
        public final String detail;       // why, when it is not a verdict

        Result(int code, String jwt, String signedData, String signature, String detail) {
            this.responseCode = code; this.jwt = jwt;
            this.signedData = signedData; this.signature = signature; this.detail = detail;
        }
        static Result error(int code, String detail) { return new Result(code, null, null, null, detail); }

        public boolean licensed() { return responseCode == LICENSED; }

        @Override public String toString() {
            return name(responseCode)
                 + (jwt != null ? " (jwt, " + jwt.length() + " chars)" : "")
                 + (signedData != null ? " (v1 signedData, " + signedData.length() + " chars)" : "")
                 + (detail != null ? " -- " + detail : "");
        }
    }

    public static String name(int code) {
        switch (code) {
            case LICENSED:                 return "LICENSED";
            case NOT_LICENSED:             return "NOT_LICENSED";
            case LICENSED_OLD_KEY:         return "LICENSED_OLD_KEY";
            case ERROR_CONTACTING_SERVER:  return "ERROR_CONTACTING_SERVER";
            case ERROR_INVALID_PACKAGE_NAME: return "ERROR_INVALID_PACKAGE_NAME";
            case ERROR_NON_MATCHING_UID:   return "ERROR_NON_MATCHING_UID";
            default:                       return "code " + code + " (0x" + Integer.toHexString(code) + ")";
        }
    }

    // ===================================================================

    /** The v2 (JWT) check -- what modern clients, including pairip, use. */
    public static Result check(String packageName, int versionCode) {
        return checkV2(packageName, versionCode);
    }

    public static Result checkV2(String packageName, int versionCode) {
        Prep p = prepare(packageName);
        if (p.error != null) return p.error;
        String url = BASE + "checkLicenseServerFallback?pkgn=" + enc(packageName)
                   + "&vc=" + versionCode;
        try {
            GmsHttp.Response r = GmsHttp.get(url, headers(p.token, p.androidId));
            GmsHttp.log("licenseV2", url, r);
            if (!r.ok()) {
                return Result.error(ERROR_CONTACTING_SERVER,
                        "HTTP " + r.status + " " + brief(r));
            }
            String jwt = Protobuf.pathString(r.body, F_PAYLOAD, F_V2_RESPONSE, F_LICENSE, F_JWT);
            if (jwt == null || jwt.isEmpty()) {
                // microG LicenseChecker.kt:158 -- "field present and non-empty
                // <-> user has license". An absent jwt on a 200 IS the negative
                // answer; it is not a parse failure.
                return new Result(NOT_LICENSED, null, null, null,
                        "no licence token in the response (" + r.body.length + " bytes)");
            }
            return new Result(LICENSED, jwt, null, null, null);
        } catch (IOException e) {
            return Result.error(ERROR_CONTACTING_SERVER, "network: " + e);
        }
    }

    /** The v1 (signedData + signature) check, for classic LVL clients. */
    public static Result checkV1(String packageName, int versionCode, long nonce) {
        Prep p = prepare(packageName);
        if (p.error != null) return p.error;
        String url = BASE + "checkLicense?pkgn=" + enc(packageName)
                   + "&vc=" + versionCode + "&nnc=" + nonce;
        try {
            GmsHttp.Response r = GmsHttp.get(url, headers(p.token, p.androidId));
            GmsHttp.log("licenseV1", url, r);
            if (!r.ok()) {
                return Result.error(ERROR_CONTACTING_SERVER,
                        "HTTP " + r.status + " " + brief(r));
            }
            byte[] v1 = Protobuf.path(r.body, F_PAYLOAD, F_V1_RESPONSE);
            if (v1 == null) {
                return new Result(NOT_LICENSED, null, null, null,
                        "no v1 licence response in the payload");
            }
            // Google's own result code, relayed verbatim -- we do not reinterpret
            // it. LICENSED_OLD_KEY and the server-failure codes mean specific
            // things to the app's LVL policy that we have no business flattening.
            int code = (int) Protobuf.scalar(v1, F_V1_RESULT, NOT_LICENSED);
            String signedData = Protobuf.pathString(v1, F_V1_SIGNED_DATA);
            String signature  = Protobuf.pathString(v1, F_V1_SIGNATURE);
            if (signedData == null || signature == null) {
                return new Result(NOT_LICENSED, null, null, null,
                        "v1 response had code " + code + " but no signed data");
            }
            return new Result(code, null, signedData, signature, null);
        } catch (IOException e) {
            return Result.error(ERROR_CONTACTING_SERVER, "network: " + e);
        }
    }

    // ===================================================================

    private static final class Prep {
        String token; long androidId; Result error;
    }

    /** The two things every licence call needs, or the honest reason we cannot
     *  make the call. Note the ordering: an absent ACCOUNT and an absent DEVICE
     *  REGISTRATION are different failures and are reported as such, because
     *  "sign in" and "checkin failed" need different fixes. */
    private static Prep prepare(String packageName) {
        Prep p = new Prep();
        if (packageName == null || packageName.isEmpty()) {
            p.error = Result.error(ERROR_INVALID_PACKAGE_NAME, "no package name");
            return p;
        }
        String account = GmsStore.primaryAccount();
        if (account == null) account = promptForAccount();
        if (account == null) {
            p.error = Result.error(ERROR_CONTACTING_SERVER,
                    "no Google account is signed in -- cannot ask Google about ownership");
            return p;
        }
        p.androidId = CheckinClient.ensureAndroidId();
        if (p.androidId == 0) {
            p.error = Result.error(ERROR_CONTACTING_SERVER,
                    "this device is not registered with Google (checkin failed)");
            return p;
        }
        p.token = GmsStore.authToken(account, GoogleAuthenticator.SCOPE_GOOGLEPLAY);
        if (p.token == null) {
            p.token = GoogleAuthClient.fetchScopedToken(account,
                    GoogleAuthenticator.SCOPE_GOOGLEPLAY);
        }
        if (p.token == null) {
            p.error = Result.error(ERROR_CONTACTING_SERVER,
                    "could not get a googleplay-scoped token: " + GoogleAuthClient.lastError());
        }
        return p;
    }

    /** Whether this process has already put a sign-in window in front of the
     *  user. One ask per launch, no matter how many times an app re-checks. */
    private static final java.util.concurrent.atomic.AtomicBoolean sAsked =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * No account yet, and something needs one. Offer to sign in.
     *
     * This is the moment Android handles with its "add an account" flow: an app
     * asks the Play Store to verify a purchase, there is no account, and the
     * platform -- not the app -- asks the user for one. We do the same, in the
     * platform, so an app never has to know an account was missing.
     *
     * ⚠ FOUR GUARDS, and each one exists because its absence is a real failure:
     *   1. `available()` -- a headless run (debug/gms.sh, a DEX gate, a sweep)
     *      has no window, so it must fall straight through to the honest error.
     *   2. `active()` -- a second licence check arriving while the user is still
     *      typing must not stack a second window.
     *   3. `sAsked` -- an app that re-checks its licence on a timer would
     *      otherwise re-prompt forever after a cancel. A user who closed the
     *      window said no; asking again is nagging, and on this path it would
     *      also block a background thread each time.
     *   4. NOT ON THE MAIN THREAD -- AppKit runs the window on the main thread,
     *      so blocking it there is a deadlock. LicensingService already
     *      dispatches to a daemon thread, but this method is reachable from the
     *      CLI too, and a guard is cheaper than a rule nobody can see.
     *
     * @return the account that was signed in, or null (cancelled/unavailable).
     */
    private static String promptForAccount() {
        if (!NativeSignIn.available()) return null;
        if (NativeSignIn.active()) return null;
        if (onMainThread()) {
            log("not prompting for sign-in: this is the main thread, and the"
                + " sign-in window needs it");
            return null;
        }
        if (!sAsked.compareAndSet(false, true)) return null;
        log("no Google account, and a licence check needs one -- opening sign-in");
        String email = NativeSignIn.signInInteractive();
        if (email == null) {
            log("sign-in was cancelled or failed"
                + (GoogleAuthClient.lastError() == null
                    ? "" : ": " + GoogleAuthClient.lastError()));
        }
        return email;
    }

    private static boolean onMainThread() {
        try {
            android.os.Looper m = android.os.Looper.getMainLooper();
            return m != null && m.getThread() == Thread.currentThread();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void log(String m) {
        try { com.macapk.loader.NativeLog.i("Mac.apk.GMS", "licence: " + m); }
        catch (Throwable t) { System.err.println("[licence] " + m); }
    }

    private static Map<String, String> headers(String token, long androidId) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + token);
        // Lower-case hex, unsigned, no padding -- microG extensions.kt:108.
        h.put("X-DFE-Device-Id", Long.toHexString(androidId));
        h.put("Accept-Language", locale());
        h.put("Connection", "Keep-Alive");
        h.put("User-Agent", userAgent());
        // X-DFE-Client-Id names the calling Play client. microG sends
        // "am-google"; ours says what we are. Same reasoning as the User-Agent.
        h.put("X-DFE-Client-Id", finskyUa() ? "am-google" : "mac-apk");
        return h;
    }

    /** microG's Finsky UA, only when explicitly asked for. The default says what
     *  we actually are. */
    private static String userAgent() {
        if (finskyUa()) {
            return "Android-Finsky/30.6.18-21%20%5B0%5D%20%5BPR%5D%20450795914"
                 + " (api=3,versionCode=83061810,sdk=" + sdk()
                 + ",device=" + android.os.Build.DEVICE
                 + ",hardware=" + android.os.Build.HARDWARE
                 + ",product=" + android.os.Build.PRODUCT
                 + ",platformVersionRelease=" + release()
                 + ",model=" + enc(android.os.Build.MODEL)
                 + ",buildId=" + android.os.Build.ID
                 + ",isWideScreen=0"
                 + ",supportedAbis=" + String.join(";", abis()) + ")";
        }
        return GmsHttp.USER_AGENT;
    }

    private static boolean finskyUa() {
        String v = System.getenv("MACAPK_GMS_FINSKY_UA");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private static String brief(GmsHttp.Response r) {
        String t = r.text();
        if (t.isEmpty()) return "(empty body)";
        t = t.replace('\n', ' ').trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    private static String enc(String s) { return GoogleAuthClient.uriEncode(s); }

    private static String locale() {
        try {
            java.util.Locale l = java.util.Locale.getDefault();
            String c = l.getCountry();
            return (c == null || c.isEmpty()) ? l.getLanguage() : l.getLanguage() + "-" + c;
        } catch (Throwable t) { return "en-US"; }
    }

    private static int sdk() {
        try { return android.os.Build.VERSION.SDK_INT; } catch (Throwable t) { return 30; }
    }

    private static String release() {
        try { return android.os.Build.VERSION.RELEASE; } catch (Throwable t) { return "11"; }
    }

    private static String[] abis() {
        try {
            String[] a = android.os.Build.SUPPORTED_ABIS;
            if (a != null && a.length > 0) return a;
        } catch (Throwable ignored) {}
        return new String[] { "arm64-v8a" };
    }
}
