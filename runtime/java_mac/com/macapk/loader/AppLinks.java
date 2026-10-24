package com.macapk.loader;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Android App Links on Mac.apk: the verification a device's domain verifier
 * runs, and the part of a device browser that hands a verified link to its app.
 *
 * VERIFICATION is AOSP's (DomainVerificationService + the verifier agent): for
 * every host in an `android:autoVerify="true"` filter that takes VIEW +
 * BROWSABLE + DEFAULT on http/https, fetch
 * `https://<host>/.well-known/assetlinks.json` (HTTPS, no redirects) and accept
 * the host when a `delegate_permission/common.handle_all_urls` statement names
 * this package and the SHA-256 of one of the app's REAL signing certificates
 * (read from the APK, MacApkPackageManager.readApkSignatures). A wildcard host
 * `*.example.com` is verified against `example.com`, as AOSP does. Nothing is
 * assumed: a host whose file is missing, redirected or does not list the cert
 * stays DOMAIN_STATE_NONE. DomainVerificationManager reports the result.
 *
 * THE BROWSER HALF. On a device the app opens its sign-in page in the browser,
 * and when the page finally navigates to the app's own verified link (an OAuth
 * redirect_uri such as Epic's https://store.epicgames.com/authorize), the
 * browser hands that navigation to the app instead of loading it. Safari knows
 * nothing about Android App Links, so it loads the address as a web page (the
 * Epic Games Store's 404). So an open whose `redirect_uri` is one of the app's
 * verified links goes to a Mac.apk sign-in window (applink_window.mm) that
 * behaves like that browser: the navigation to the redirect is cancelled and
 * delivered to the app through the same queue as a URL LaunchServices handed
 * us (HostUrlIntake). Every other open still goes to the Mac's browser.
 *
 * MACAPK_APP_LINKS=0 turns both halves off (the pre-change behaviour: no
 * verification, every open goes to the Mac's browser).
 * docs/android-app-links-signin-window_2026-10-19.md.
 */
public final class AppLinks {
    private AppLinks() {}

    private static final String TAG = "Mac.apk.AppLinks";
    static final boolean ON = !"0".equals(System.getenv("MACAPK_APP_LINKS"));
    static final String RELATION = "delegate_permission/common.handle_all_urls";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 512 * 1024;

    private static volatile String sPkg;
    private static volatile PackageManager sPm;
    /** host (as declared, lower case) -> DOMAIN_STATE_*; filled by verifyAsync. */
    private static volatile Map<String, Integer> sStates = Collections.emptyMap();
    private static final CountDownLatch sDone = new CountDownLatch(1);

    /** The hosts this manifest asks to verify: autoVerify filters that take
     *  VIEW + BROWSABLE + DEFAULT on http/https only (AOSP ignores autoVerify on a
     *  filter with any other scheme). Lower case, declaration order. */
    static List<String> autoVerifyHosts(Manifest m) {
        Set<String> out = new LinkedHashSet<>();
        if (m == null || m.activities == null) return new ArrayList<>(out);
        for (Manifest.ActivityEntry a : m.activities.values()) {
            if (a == null || !a.enabled || !a.exported) continue;
            for (Manifest.IntentFilterEntry f : a.filters) {
                if (!f.autoVerify) continue;
                if (!f.actions.contains(Intent.ACTION_VIEW)) continue;
                if (!f.categories.contains(Intent.CATEGORY_BROWSABLE)) continue;
                if (!f.categories.contains(Intent.CATEGORY_DEFAULT)) continue;
                List<String> schemes = MacApkPackageManager.macapkResolvedDataValues(f.schemes);
                boolean web = !schemes.isEmpty();
                for (String s : schemes) {
                    if (!"http".equalsIgnoreCase(s) && !"https".equalsIgnoreCase(s)) web = false;
                }
                if (!web) continue;
                for (String h : MacApkPackageManager.macapkResolvedDataValues(f.hosts)) {
                    if (h != null && !h.isEmpty()) out.add(h.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** The domain a declared host is verified against ("*.a.com" -> "a.com"). */
    static String verifyDomain(String host) {
        return host.startsWith("*.") ? host.substring(2) : host;
    }

    /** "AB:CD:..." upper-case hex, the assetlinks.json fingerprint spelling. */
    static String fingerprint(byte[] cert) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(cert);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", d[i] & 0xff));
        }
        return sb.toString();
    }

    /** Does this assetlinks.json grant `pkg` (signed by any of `fps`) the
     *  handle_all_urls relation? Statement shape per
     *  developers.google.com/digital-asset-links/v1/getting-started. */
    static boolean grants(String json, String pkg, Set<String> fps) {
        org.json.JSONArray arr;
        try {
            arr = new org.json.JSONArray(json);
        } catch (Throwable t) {
            return false;
        }
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject st = arr.optJSONObject(i);
            if (st == null) continue;
            org.json.JSONArray rel = st.optJSONArray("relation");
            boolean all = false;
            for (int r = 0; rel != null && r < rel.length(); r++) {
                if (RELATION.equals(rel.optString(r))) all = true;
            }
            if (!all) continue;
            org.json.JSONObject t = st.optJSONObject("target");
            if (t == null || !"android_app".equals(t.optString("namespace"))) continue;
            if (!pkg.equals(t.optString("package_name"))) continue;
            org.json.JSONArray f = t.optJSONArray("sha256_cert_fingerprints");
            for (int k = 0; f != null && k < f.length(); k++) {
                if (fps.contains(f.optString(k).toUpperCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    /** GET https://<domain>/.well-known/assetlinks.json; null on any failure,
     *  a redirect or a non-200 answer (the verifier does not follow redirects). */
    static String fetchAssetLinks(String domain) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://" + domain + "/.well-known/assetlinks.json")
                    .openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                    if (bo.size() > MAX_BYTES) return null;
                }
                return new String(bo.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Boot: verify this app's autoVerify hosts off the boot path. */
    static void verifyAsync(final String pkg, final Manifest manifest, final PackageManager pm) {
        sPkg = pkg;
        sPm = pm;
        final List<String> hosts = ON ? autoVerifyHosts(manifest) : Collections.<String>emptyList();
        if (hosts.isEmpty()) {
            sDone.countDown();
            return;
        }
        Thread t = new Thread(() -> {
            Map<String, Integer> states = new HashMap<>();
            try {
                Set<String> fps = new HashSet<>();
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                if (pi != null && pi.signatures != null) {
                    for (Signature s : pi.signatures) fps.add(fingerprint(s.toByteArray()));
                }
                Map<String, Boolean> byDomain = new HashMap<>();
                for (String h : hosts) {
                    String d = verifyDomain(h);
                    Boolean ok = byDomain.get(d);
                    if (ok == null) {
                        String json = fps.isEmpty() ? null : fetchAssetLinks(d);
                        ok = json != null && grants(json, pkg, fps);
                        byDomain.put(d, ok);
                    }
                    states.put(h, ok
                            ? android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                            : android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE);
                }
                NativeLog.i(TAG, "[applinks] " + pkg + ": " + states);
            } catch (Throwable th) {
                NativeLog.w(TAG, "[applinks] verification failed: " + th);
            } finally {
                sStates = Collections.unmodifiableMap(states);
                sDone.countDown();
            }
        }, "macapk-applinks");
        t.setDaemon(true);
        t.start();
    }

    /** DomainVerificationManager's answer for our own package: null when it
     *  declares no autoVerify host (AOSP's "does not declare any domains"). */
    public static Map<String, Integer> stateMapFor(String pkg) {
        if (!ON || pkg == null || !pkg.equals(sPkg)) return null;
        awaitVerification(3000);
        Map<String, Integer> s = sStates;
        return s.isEmpty() ? null : s;
    }

    static void awaitVerification(long ms) {
        try { sDone.await(ms, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
    }

    /** Is `host` covered by a VERIFIED declared host (exact or wildcard)? */
    static boolean hostVerified(String host, Map<String, Integer> states) {
        if (host == null || states == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : states.entrySet()) {
            if (e.getValue() == null || e.getValue()
                    != android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED) {
                continue;
            }
            String h = e.getKey();
            if (h.equals(host)) return true;
            if (h.startsWith("*.") && (host.endsWith(h.substring(1)) || host.equals(h.substring(2)))) {
                return true;
            }
        }
        return false;
    }

    /** The `redirect_uri` query parameter of `url`, decoded, or null. */
    static String redirectUriOf(String url) {
        try {
            String q = new URI(url).getRawQuery();
            if (q == null) return null;
            for (String kv : q.split("&")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                if (!"redirect_uri".equals(kv.substring(0, eq))) continue;
                return URLDecoder.decode(kv.substring(eq + 1), "UTF-8");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** scheme://host/path of `url` with no query or fragment, or null. */
    static String returnPrefix(String url) {
        try {
            URI u = new URI(url);
            String scheme = u.getScheme();
            if (scheme == null || u.getHost() == null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) return null;
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            String port = u.getPort() > 0 ? ":" + u.getPort() : "";
            return scheme + "://" + u.getHost().toLowerCase(Locale.ROOT) + port + path;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The sign-in window decision for a host open of `url`: the return prefix
     * (scheme://host/path of its redirect_uri) when that redirect is one of this
     * app's VERIFIED App Links and resolves to its own exported BROWSABLE
     * activity, else null (open in the Mac's browser as before).
     */
    static String signInReturnPrefix(String url, Map<String, Integer> states, String pkg,
                                     PackageManager pm) {
        String redirect = redirectUriOf(url);
        if (redirect == null) return null;
        if (!isWebScheme(redirect)) {
            // Resolve the way a browser builds the intent: scheme lower-cased
            // first. Epic's EOS SDK sends redirect_uri=eos.xyza7891U64M...://
            // while the app's filter declares eos.xyza7891u64m..., and filter
            // schemes match exactly (IntentFilter.matchData), so the raw form
            // never resolved and the sign-in fell back to Safari.
            boolean own = pm != null && HostUrlIntake.resolveOwn(
                    HostUrlIntake.browserIntentFor(lowerScheme(redirect)), pkg, pm) != null;
            return schemeReturnPrefix(url, redirect, own);
        }
        String prefix = returnPrefix(redirect);
        if (prefix == null) return null;
        String host;
        try {
            host = new URI(redirect).getHost();
        } catch (Throwable t) {
            return null;
        }
        if (!hostVerified(host, states)) return null;
        if (pm != null && HostUrlIntake.resolveOwn(HostUrlIntake.browserIntentFor(lowerScheme(redirect)), pkg, pm) == null) {
            return null;
        }
        return prefix;
    }

    /** MACAPK_SIGNIN_WINDOW_SCHEMES (default ON; owner's choice, 2026-10-19): a
     *  sign-in whose redirect_uri is one of the app's OWN link schemes (Rocket
     *  League's EOS login returns at eos.<client id>://epic/auth) also opens in
     *  the sign-in window. On a device that login runs in a Custom Tab, which
     *  closes when the app takes the redirect back; in Safari the tab stayed
     *  open. Trade-off the owner accepted: the window has no Safari saved
     *  passwords or passkeys. =0: those logins go to the Mac's browser again. */
    static final boolean SCHEMES_ON = !"0".equals(System.getenv("MACAPK_SIGNIN_WINDOW_SCHEMES"));

    /** Google refuses OAuth in embedded browsers ("disallowed_useragent",
     *  developers.google.com/identity/protocols/oauth2/policies#embedded-webviews),
     *  so a Google account sign-in stays in the Mac's browser. */
    static boolean embeddedRefused(String url) {
        try {
            String h = new URI(url).getHost();
            return h != null && "accounts.google.com".equalsIgnoreCase(h);
        } catch (Throwable t) {
            return true;
        }
    }

    /** `url` with its scheme lower-cased (RFC 3986 3.1: schemes are
     *  case-insensitive, and a browser canonicalises them -- Chromium's GURL --
     *  before it builds the VIEW intent). */
    static String lowerScheme(String url) {
        if (url == null) return null;
        int colon = url.indexOf(':');
        return colon <= 0 ? url : url.substring(0, colon).toLowerCase(Locale.ROOT) + url.substring(colon);
    }

    static boolean isWebScheme(String url) {
        return url.regionMatches(true, 0, "https:", 0, 6) || url.regionMatches(true, 0, "http:", 0, 5);
    }

    /** The return prefix for a sign-in that redirects to an app link SCHEME:
     *  the redirect_uri up to its query / fragment, scheme lower-cased (a
     *  browser canonicalises it before it makes the intent). Null when the
     *  scheme case is off, the redirect does not come back to this app
     *  (`redirectIsOwn`, its exported BROWSABLE activity), or the provider
     *  refuses embedded sign-in. */
    static String schemeReturnPrefix(String signInUrl, String redirect, boolean redirectIsOwn) {
        if (!SCHEMES_ON || !redirectIsOwn || redirect == null || embeddedRefused(signInUrl)) return null;
        int colon = redirect.indexOf(':');
        if (colon <= 0) return null;
        int cut = redirect.length();
        int q = redirect.indexOf('?');
        int h = redirect.indexOf('#');
        if (q >= 0) cut = Math.min(cut, q);
        if (h >= 0) cut = Math.min(cut, h);
        String p = redirect.substring(0, colon).toLowerCase(Locale.ROOT) + redirect.substring(colon, cut);
        return p.length() > colon + 1 ? p : null;
    }

    /** ActivityHost.openHost: open `url` in the sign-in window when it is a
     *  sign-in that returns to one of our verified links. True when it did. */
    static boolean maybeOpenSignInWindow(String url) {
        if (!ON || url == null) return false;
        if (!url.regionMatches(true, 0, "https:", 0, 6) && !url.regionMatches(true, 0, "http:", 0, 5)) {
            return false;
        }
        if (redirectUriOf(url) == null) return false;
        String redirect = redirectUriOf(url);
        if (redirect != null && isWebScheme(redirect)) awaitVerification(5000);
        String prefix = signInReturnPrefix(url, sStates, sPkg, sPm);
        if (prefix == null) return false;
        boolean ok;
        try {
            ok = NativeCocoa.openAppLinkWindow(url, prefix);
        } catch (Throwable t) {
            NativeLog.w(TAG, "[applinks] sign-in window unavailable: " + t);
            return false;
        }
        NativeLog.i(TAG, "[applinks] sign-in opened in the Mac.apk window; returns to "
                + prefix + (isWebScheme(prefix) ? " (a verified App Link of " : " (a link scheme of ")
                + sPkg + ") = " + ok);
        return ok;
    }
}
