// com.macapk.gms.GcmRegisterClient -- GCM/FCM registration (a real token).
//
// WHAT IT DOES. Turns (this device's checkin android_id + securityToken, an
// app package + its signing cert, a sender/project id) into a real Firebase
// registration token, by POSTing android.clients.google.com/c2dm/register3 the
// way the Play Store's GCM client does. NO Google ACCOUNT is needed -- the
// android_id + securityToken pair from checkin IS the credential (Authorization:
// AidLogin). This is the token half of FCM; the receive half (the MCS socket)
// is separate.
//
// ⛔ SCOPE. Registration is REGISTRATION, not certification -- it carries no
// attestation. We present the APP'S OWN real signing certificate (from its APK,
// which MacApkPackageManager already reads), not a forged one. The token Google
// returns is genuinely the app's, minted for this genuinely-registered device.
//
// PROTOCOL REFERENCE (read-only, never vendored): microG GmsCore @157c9d86,
//   play-services-core/.../gcm/RegisterRequest.java + PushRegisterManager.java,
//   cross-checked against Chromium google_apis/gcm/engine/registration_request.cc
//   and the real Firebase SDK's GmsRpc. Full tabulation: docs/gms-fcm-mcs-wire_2026-09-05.md
package com.macapk.gms;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GcmRegisterClient {

    private static final String URL = "https://android.clients.google.com/c2dm/register3";

    /** register3's own time budget, deliberately tighter than GmsHttp's 15s+30s
     *  default. firebase-messaging's MessengerIpcClient gives a bound register
     *  service 30 SECONDS TOTAL before it gives up with "Timed out waiting for
     *  response", and PushRegisterService cannot reply until this POST returns,
     *  so the default 45s worst case guarantees the app times out first and the
     *  clean error Result we build is never seen. 8+15 leaves headroom for the
     *  bind, the Messenger hop and the reply inside the client's window. */
    private static final int REGISTER_CONNECT_MS = 8_000;
    private static final int REGISTER_READ_MS    = 15_000;

    /** The WHOLE register round trip's budget, checkin included.
     *
     *  ⚠ Sizing the two legs separately was NOT enough, and v1.0.1180 claimed
     *  otherwise. register() calls ensureAndroidId() FIRST, so on a cold store
     *  the checkin leg (8+12) and the register3 leg (8+15) run SERIALLY inside
     *  PushRegisterService.handle -- 43s against the ~30s MessengerIpcClient
     *  window the service's header promises to reply within. Shrinking the
     *  constants would not have fixed it either: since v1.0.1180 the MCS thread
     *  also takes CheckinClient's monitor on every connect attempt, so there is
     *  an unbounded LOCK-WAIT term in front of both legs that no constant can
     *  bound. Only a deadline measured across the whole call can.
     *
     *  25s leaves ~5s for the bind, the Messenger hop and the reply inside the
     *  client's 30s. */
    private static final long REGISTER_TOTAL_BUDGET_MS = 25_000L;
    /** Below this there is no point starting the POST; reply fast instead.
     *  Sized so the read timeout can never fall to something a real round trip
     *  cannot meet: a 1s read abandons a registration GOOGLE HAS ALREADY MINTED,
     *  and the app then registers again -- the TOO_MANY_REGISTRATIONS escalation
     *  on this machine's ONE shared device identity that the token cache exists
     *  to avoid. Failing fast is strictly better than minting and discarding. */
    private static final long MIN_POST_MS = 9_000L;

    private GcmRegisterClient() {}

    /** A registration result. `token` is the FCM token on success; `error` is
     *  Google's error CODE (a string like AUTHENTICATION_FAILED) otherwise --
     *  never fabricated, never a placeholder. */
    public static final class Result {
        public final String token;
        public final String error;
        public final long retryAfterSeconds;   // Google's Retry-After, 0 if none

        Result(String token, String error, long retryAfter) {
            this.token = token; this.error = error; this.retryAfterSeconds = retryAfter;
        }
        public boolean ok() { return token != null && !token.isEmpty(); }
        @Override public String toString() {
            return ok() ? "OK token(" + token.length() + " chars)"
                        : "ERROR " + error + (retryAfterSeconds > 0
                            ? " retryAfter=" + retryAfterSeconds + "s" : "");
        }
    }

    /** Build a failed Result carrying the BARE error code.
     *
     *  ⚠ Firebase classifies retriability by EXACT STRING EQUALITY on the code:
     *  GmsRpc.isErrorMessageForRetryableError does
     *  `ERROR_SERVICE_NOT_AVAILABLE.equals(errorMessage)`, and TopicsSubscriber
     *  does the same before deciding to retry or to `throw`. So appending a
     *  parenthesised reason -- which reads as helpful -- silently converts a
     *  RETRIABLE error into a TERMINAL one, and the app abandons the token or
     *  the topic subscription instead of backing off.
     *  docs/gms-fcm-mcs-wire_2026-09-05.md already said this ("The SDK retries
     *  only SERVICE_NOT_AVAILABLE / INTERNAL_SERVER_ERROR; everything else is
     *  terminal") and the code did it anyway, in six places.
     *  The detail belongs in the LOG, never in the wire value. */
    private static Result err(String code, String detail, long retryAfter) {
        if (detail != null && !detail.isEmpty()) log(code + ": " + detail);
        return new Result(null, code, retryAfter);
    }

    /**
     * Register an app for push. `extras` carries the `X-`-prefixed attributes the
     * real Firebase SDK sends (scope, subtype, gmp_app_id, ...); pass null for a
     * bare registration.
     *
     * @param delete true for an unregister (adds delete=1).
     */
    public static Result register(String pkg, String senderId, String certSha1,
                                  int versionCode, int targetSdk,
                                  Map<String, String> extras, boolean delete) {
        // Cheap terminal validation FIRST, before the clock: otherwise a missing
        // sender combined with a slow checkin reports SERVICE_NOT_AVAILABLE
        // (retriable) where INVALID_SENDER (terminal) is the truth, and the app
        // retries a request that can never succeed.
        if (pkg == null || pkg.isEmpty()) return err("INVALID_PARAMETERS", "no package", 0);
        if (senderId == null || senderId.isEmpty()) return new Result(null, "INVALID_SENDER", 0);

        // nanoTime, NOT currentTimeMillis: the wall clock is not monotonic, and a
        // backward NTP step during ensureAndroidId would inflate `remaining` and
        // silently restore the 43s overrun this budget exists to prevent.
        // The clock starts BEFORE ensureAndroidId, because that call can block on
        // both a checkin round trip and CheckinClient's class monitor.
        final long deadlineNs = System.nanoTime() + REGISTER_TOTAL_BUDGET_MS * 1_000_000L;
        long androidId = CheckinClient.ensureAndroidId();
        long securityToken = GmsStore.securityToken();
        if (androidId == 0 || securityToken == 0) {
            return err("PHONE_REGISTRATION_ERROR", "this device is not checked in", 0);
        }
        long remaining = (deadlineNs - System.nanoTime()) / 1_000_000L;
        if (remaining < MIN_POST_MS) {
            // Reply NOW with a shape Firebase understands rather than starting a
            // POST that can only land after the caller has given up. A clean
            // error inside the window beats a correct answer outside it.
            return err("SERVICE_NOT_AVAILABLE", "register budget spent on checkin", 0);
        }
        int connectMs = (int) Math.min(REGISTER_CONNECT_MS, remaining);
        int readMs    = (int) Math.max(1_000L, Math.min(REGISTER_READ_MS, remaining - connectMs));
        Map<String, String> form = new LinkedHashMap<>();
        form.put("app", pkg);
        if (certSha1 != null && !certSha1.isEmpty()) form.put("cert", certSha1);
        if (versionCode > 0) form.put("app_ver", Integer.toString(versionCode));
        form.put("sender", senderId);
        // android_id decimal, SIGNED (microG's Long.toString). If Google answers
        // AUTHENTICATION_FAILED with a negative securityToken, the unsigned
        // spelling is the one retry to try -- see the wire doc's sign note.
        form.put("device", Long.toString(androidId));
        if (delete) form.put("delete", "1");
        if (targetSdk > 0 && !delete) form.put("target_ver", Integer.toString(targetSdk));
        if (extras != null) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                String k = e.getKey(), v = e.getValue();
                if (k == null || v == null || v.isEmpty()) continue;
                if ("sender".equals(k) || "delete".equals(k) || "app".equals(k)) continue;
                form.put(k.startsWith("X-") ? k : "X-" + k, v);
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "AidLogin " + androidId + ":" + securityToken);
        headers.put("User-Agent", "Android-GCM/1.5 (" + android.os.Build.DEVICE
                + " " + android.os.Build.ID + ")");
        headers.put("app", pkg);

        try {
            GmsHttp.Response r = postFormAndroidStyle(URL, form, headers, connectMs, readMs);
            GmsHttp.log("register3", URL, r);
            String body = r.text();
            // Google returns Error= even on HTTP 200 sometimes, so parse the
            // body regardless of status before trusting the code.
            Map<String, String> parsed = GoogleAuthClient.parse(body);
            long retryAfter = parseRetryAfter(r.headers.get("retry-after"));
            String token = parsed.get("token");
            if (token != null && !token.isEmpty()) {
                log("registered " + pkg + " sender=" + senderId + " -> token("
                    + token.length() + " chars)");
                // Remember that this package has push, so a LATER launch starts the
                // MCS channel even though the app will not register again (it caches
                // the token itself). Without this record, lazy channel start would
                // silently stop delivering to every already-registered app.
                if (!delete) GmsStore.markRegistered(pkg);
                return new Result(token, null, retryAfter);
            }
            String error = parsed.get("Error");
            if (error == null && !r.ok()) error = firstNonEmptyLine(body);
            if (error == null) { log("SERVICE_NOT_AVAILABLE: no token, no error in response");
                                 error = "SERVICE_NOT_AVAILABLE"; }
            log("register " + pkg + " failed: " + error);
            return new Result(null, error, retryAfter);
        } catch (IOException e) {
            return err("SERVICE_NOT_AVAILABLE", String.valueOf(e), 0);
        }
    }

    /** Convenience for a plain getToken with the FCM default scope. */
    public static Result register(String pkg, String senderId, String certSha1,
                                  int versionCode, int targetSdk) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("X-scope", "*");
        extras.put("X-subtype", senderId);
        return register(pkg, senderId, certSha1, versionCode, targetSdk, extras, false);
    }

    // ===================================================================

    private static GmsHttp.Response postFormAndroidStyle(String url, Map<String, String> form,
                                                         Map<String, String> headers,
                                                         int connectMs, int readMs)
            throws IOException {
        // Android Uri.encode, NOT URLEncoder -- reuse the auth client's, which
        // implements the correct allowed set (the %20-vs-+ trap).
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (e.getValue() == null) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(GoogleAuthClient.uriEncode(e.getKey()))
              .append('=').append(GoogleAuthClient.uriEncode(e.getValue()));
        }
        Map<String, String> h = new LinkedHashMap<>(headers);
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return GmsHttp.postBytes(url, sb.toString().getBytes("UTF-8"),
                "application/x-www-form-urlencoded", h, false, connectMs, readMs);
    }

    private static long parseRetryAfter(String v) {
        if (v == null || v.isEmpty() || v.indexOf(':') >= 0) return 0;   // date form: ignore
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String firstNonEmptyLine(String s) {
        if (s == null) return null;
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) return t.startsWith("Error=") ? t.substring(6) : t;
        }
        return null;
    }

    static void log(String m) {
        try { com.macapk.loader.NativeLog.i("Mac.apk.GMS", "gcm: " + m); }
        catch (Throwable t) { System.err.println("[gcm] " + m); }
    }
}
