// Host URL intake (MACAPK_URL_SCHEMES, default ON): the RETURN half of an
// Android BROWSABLE link -- a custom-scheme deep link or an OAuth redirect such
// as AppAuth's `net.thunderbird.android:/oauth2redirect?code=...` -- arriving
// from the macOS browser into this app.
//
// docs/host-url-scheme-intake_2026-09-29.md has the contract and the evidence.
//
// On a device two things make that hand-off work, and Mac.apk had neither:
//
//  1. REGISTRATION. PackageManager indexes every exported activity's
//     <intent-filter>, so a browser that meets `myscheme:...` finds the app
//     that declared `<data android:scheme="myscheme"/>` under VIEW + DEFAULT +
//     BROWSABLE. macOS's equivalent index is LaunchServices, keyed by the
//     CFBundleURLTypes of each bundle's Info.plist. Our generated bundles
//     declared none, so Safari answered "macOS doesn't recognize Internet
//     addresses starting with ..." and the sign-in dead-ended in the browser.
//     syncBundleSchemes() keeps that key equal to the manifest's BROWSABLE
//     schemes (the install-time registration, re-checked every launch the way
//     the stub re-checks its other identity keys) and re-registers the bundle.
//
//  2. DELIVERY. The browser starts the matching activity with an intent it
//     has sanitised: ACTION_VIEW + the URL, CATEGORY_BROWSABLE added, the
//     component and selector cleared, FLAG_ACTIVITY_NEW_TASK
//     (Chromium components/external_intents/.../ExternalNavigationHandler.java
//     sanitizeQueryIntentActivitiesIntent :2248-2255 and
//     `targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` :1582). LaunchServices
//     hands the URL to the running (or cold-launched) bundle as a kAEGetURL
//     Apple Event; cocoa_bridge.mm queues it and this class turns it into that
//     same browser-shaped intent, resolves it against OUR OWN manifest only
//     (exported, MATCH_DEFAULT_ONLY -- the browser is another app), and starts
//     it through the cross-app arrival path (ActivityHost.macapkDeliverCrossApp),
//     so a singleTop top gets onNewIntent and anything else is pushed, exactly
//     as for an intent from another Mac.apk app.
//
// Schemes the HOST performs itself (http/https/mailto/tel/geo -- see
// ActivityHost.hostActionFor) and Android-internal pseudo-schemes are never
// claimed: claiming mailto would make the app's own mailto: open, which we hand
// to NSWorkspace, route straight back into the app. The one exception is
// `intent`: an app that receives browser redirects registers it on its own
// (withIntentHandoff, MACAPK_URL_INTENT_HANDOFF) because on macOS the app plays
// the browser's part for it -- never because its manifest declares it.
package com.macapk.loader;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class HostUrlIntake {
    private static final String TAG = "Mac.apk.URL";

    /** MACAPK_URL_SCHEMES (default ON). =0: no Info.plist registration, no
     *  Apple Event handler (the native side reads the same variable), no
     *  delivery -- the pre-change behaviour. */
    static final boolean ON = !"0".equals(System.getenv("MACAPK_URL_SCHEMES"));

    /** The sender recorded for Activity.getReferrer(): the package our PM
     *  already lists as "the browser" (MacApkPackageManager's host row). */
    static final String SENDER = MacApkPackageManager.MACAPK_HOST_PACKAGE;

    /** Info.plist key recording which schemes WE wrote, so a launch whose
     *  manifest has not changed is a single read and never a rewrite. */
    static final String MARKER_KEY = "MacapkUrlSchemes";

    /** Never claimed on the host. The first group is what hostActionFor hands
     *  to macOS itself (claiming it would loop our own opens back into us); the
     *  rest are schemes a browser never hands to an app or that name local
     *  storage, where a LaunchServices claim would hijack the Mac. `intent` stays
     *  here (a manifest that declares it never claims it through hostSchemes);
     *  withIntentHandoff adds it separately, for the browser's part only. */
    static final Set<String> HOST_OWNED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "http", "https", "geo", "mailto", "tel",
            "file", "content", "data", "javascript", "about", "blob", "intent",
            "android-app", "chrome", "ftp", "sms", "smsto", "mms", "mmsto", "ws", "wss")));

    /** Schemes macOS itself (or a system app) owns. A LaunchServices claim can
     *  change which bundle a scheme opens (which claimant wins is LaunchServices'
     *  own decision and NOT a rule to rely on: observed both ways on 2026-09-30,
     *  docs/signin-provider-layer_2026-09-30.md),
     *  so registering an Android app that declares one of these could silently
     *  take it from the Mac's own handler (Passwords owns otpauth, Podcasts pcast/itpc/feed,
     *  Finder smb/afp/nfs/vnc, FaceTime facetime, Messages message). On a device
     *  a second claimant produces a CHOOSER; a silent takeover of the host's
     *  existing handler is not the AOSP analog. Checked BEFORE the live
     *  LaunchServices probe (foreignOwner), so it also holds on a Mac where the
     *  system app is not (yet) registered for the scheme. */
    static final Set<String> OS_RESERVED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "smb", "afp", "nfs", "cifs", "vnc", "ssh", "sftp", "ftps", "ftpes",
            "rtsp", "rtsps", "rtmp", "rtmps", "rtp", "udp", "mmsh",
            "otpauth", "otpauth-migration",
            "itpc", "pcast", "podcast", "podcasts", "feed", "webcal",
            "facetime", "facetime-audio", "message", "news", "nntp", "macappstore")));

    /** Prefix families of the same kind: x-apple-* (System Settings, Reminders,
     *  Messages private-relay ...) and itms* (iTunes/App Store). */
    static boolean osReserved(String lowerScheme) {
        return OS_RESERVED.contains(lowerScheme)
                || lowerScheme.startsWith("x-apple-") || lowerScheme.startsWith("itms");
    }

    static final String LSREGISTER = "/System/Library/Frameworks/CoreServices.framework/Versions/A/"
            + "Frameworks/LaunchServices.framework/Versions/A/Support/lsregister";

    private static volatile String sOwnPkg;
    private static volatile PackageManager sPm;
    private static volatile boolean sBooted;

    private HostUrlIntake() {}

    // ---------------------------------------------------------------- schemes

    /** RFC 3986 section 3.1: scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ). */
    static boolean validScheme(String s) {
        if (s == null || s.isEmpty() || s.length() > 64) return false;
        char c0 = s.charAt(0);
        if (!((c0 >= 'a' && c0 <= 'z') || (c0 >= 'A' && c0 <= 'Z'))) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.';
            if (!ok) return false;
        }
        return true;
    }

    /** The schemes this app would receive from a browser, as the host should
     *  register them: the manifest's VIEW+DEFAULT+BROWSABLE schemes of enabled,
     *  exported activities (MacApkPackageManager.macapkBrowsableSchemes), minus
     *  HOST_OWNED, the OS-reserved schemes (osReserved) and anything that is not
     *  a syntactically valid scheme.
     *  Declaration order; duplicates (case-insensitively, as macOS compares
     *  schemes) dropped. */
    static List<String> hostSchemes(List<String> browsable) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (browsable == null) return out;
        for (String s : browsable) {
            if (!validScheme(s)) continue;
            String low = s.toLowerCase(Locale.ROOT);
            if (HOST_OWNED.contains(low) || osReserved(low) || !seen.add(low)) continue;
            out.add(s);
        }
        return out;
    }

    /** MACAPK_URL_INTENT_HANDOFF (default ON; implies MACAPK_URL_SCHEMES). On a
     *  device an `intent:` URI never reaches an app as DATA: the BROWSER parses it
     *  (Intent.parseUri(URI_INTENT_SCHEME)), sanitises it and starts the activity it
     *  names, restricted to `package=` when present. Firebase Auth's non-GMS redirect
     *  page navigates to exactly such a URI after a browser sign-in
     *  (intent://firebase.auth/#Intent;scheme=genericidp|recaptcha;package=<apn>;...;end;,
     *  docs/firebase-intent-uri-handoff_2026-09-30.md), and macOS has no handler for
     *  it, so the sign-in stopped in Safari. With this gate an app that receives
     *  browser redirects (a non-empty hostSchemes list) ALSO registers `intent` and
     *  plays the browser's part for URIs that name it (or name no package): the
     *  shared-scheme opener rule below then makes the app that opened the browser
     *  the one LaunchServices hands the redirect to. =0: `intent` stays HOST_OWNED
     *  and is never registered or parsed (pre-change). */
    static final boolean INTENT_HANDOFF =
            ON && !"0".equals(System.getenv("MACAPK_URL_INTENT_HANDOFF"));
    static final String INTENT_SCHEME = "intent";

    /** The schemes to register: hostSchemes plus `intent` when this app receives
     *  any browser redirect at all and the gate is on. Pure (gated offline). */
    static List<String> withIntentHandoff(List<String> host, boolean handoff) {
        List<String> out = new ArrayList<>(host == null ? Collections.<String>emptyList() : host);
        if (handoff && !out.isEmpty()) out.add(INTENT_SCHEME);
        return out;
    }

    /** True for an `intent:` URI (scheme compared case-insensitively, RFC 3986). */
    static boolean isIntentUri(String url) {
        return url != null && url.regionMatches(true, 0, INTENT_SCHEME + ":", 0, 7);
    }

    /**
     * The intent a browser starts for a host URL handed to `ownPkg`. A plain URL
     * is browserIntentFor. An `intent:` URI (gate on) is parsed the way the browser
     * parses it -- Intent.parseUri(url, URI_INTENT_SCHEME) -- then sanitised as
     * Chromium's ExternalNavigationHandler.sanitizeQueryIntentActivitiesIntent does
     * before it starts anything from a web page: flags masked to
     * ALLOWED_INTENT_FLAGS, CATEGORY_BROWSABLE added, component cleared, selector
     * dropped (crbug.com/1254422), so a page can reach only BROWSABLE activities
     * through their intent filters. A URI naming a
     * DIFFERENT package is not this app's to handle (null; the browser would have
     * started that package). Null when the URI does not parse. Gate off: every
     * URL, intent: included, takes the pre-change browserIntentFor path.
     *
     * Two more steps Chromium takes before anything is started (ExternalNavigation
     * Handler.java main, fetched 2026-10-01): it parses GURL's CANONICAL spec
     * (`Intent.parseUri(params.getUrl().getSpec(), ...)` :743, scheme lower-cased by
     * the URL parser; Intent.parseUri itself only recognises a lower-case "intent:"),
     * and it removes the fallback extra (`targetIntent.removeExtra(
     * EXTRA_BROWSER_FALLBACK_URL)` :760), so the started activity never sees it.
     */
    static Intent hostIntentFor(String url, String ownPkg, boolean handoff) {
        if (!handoff || !isIntentUri(url)) return browserIntentFor(url);
        Intent i;
        try {
            i = Intent.parseUri(INTENT_SCHEME + url.substring(INTENT_SCHEME.length()),
                    Intent.URI_INTENT_SCHEME);
        } catch (Throwable t) {
            return null;
        }
        i.removeExtra(EXTRA_BROWSER_FALLBACK_URL);
        String named = i.getPackage();
        if (named != null && !named.equals(ownPkg)) return null;
        i.setFlags(i.getFlags() & ALLOWED_INTENT_FLAGS);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.setComponent(null);
        i.setSelector(null);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);   // started from the browser, as browserIntentFor
        return i;
    }

    /** Chromium ExternalNavigationHandler.EXTRA_BROWSER_FALLBACK_URL: an intent: URI's
     *  `S.browser_fallback_url`, consumed by the browser and never delivered. */
    static final String EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url";

    /** The package an intent: URI names (`package=`), or null (none / unparsable).
     *  For the drop log only: it says WHICH app a misrouted redirect was for. */
    static String intentUriPackage(String url) {
        if (!isIntentUri(url)) return null;
        try {
            return Intent.parseUri(INTENT_SCHEME + url.substring(INTENT_SCHEME.length()),
                    Intent.URI_INTENT_SCHEME).getPackage();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Chromium ExternalNavigationHandler.ALLOWED_INTENT_FLAGS (components/external_intents,
     *  fetched 2026-09-30): the only launch flags a web page's intent: URI may carry. */
    static final int ALLOWED_INTENT_FLAGS = Intent.FLAG_EXCLUDE_STOPPED_PACKAGES
            | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
            | Intent.FLAG_ACTIVITY_MATCH_EXTERNAL | Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;

    /** The bundle this runtime was launched from, or null for a dev run: the
     *  android root must be <X>.app/Contents/android (IntentInbox's rule). */
    static File bundleOf(String androidRoot) {
        if (androidRoot == null) return null;
        File rootF = new File(androidRoot);
        File contents = rootF.getParentFile();
        File bundle = contents != null ? contents.getParentFile() : null;
        if (!"android".equals(rootF.getName()) || contents == null
                || !"Contents".equals(contents.getName()) || bundle == null
                || !bundle.getName().endsWith(".app")) {
            return null;
        }
        return bundle;
    }

    /** Result of a sync, for the log and the gate. */
    enum Sync { UNCHANGED, WRITTEN, REMOVED, SKIPPED, FAILED }

    /**
     * Make `bundle`'s Info.plist claim exactly `schemes` (CFBundleURLTypes) and
     * re-register it with LaunchServices when anything changed. A bundle whose
     * app claims nothing and that we never wrote to is left byte-for-byte alone.
     * `lsregister` may be null (the gate's scratch bundles are never
     * registered).
     */
    static Sync syncBundleSchemes(File bundle, String pkg, List<String> schemes, String lsregister) {
        File plist = new File(bundle, "Contents/Info.plist");
        if (!plist.isFile()) return Sync.SKIPPED;
        // Only a Mac.apk-generated bundle of THIS package is ours to edit.
        String owner = plutilRead(plist, "MacapkPackage");
        if (owner == null || !owner.equals(pkg)) return Sync.SKIPPED;
        String want = String.join(" ", schemes);
        String have = plutilRead(plist, MARKER_KEY);
        if (schemes.isEmpty()) {
            if (have == null) return Sync.UNCHANGED;
            boolean ok = run("/usr/bin/plutil", "-remove", "CFBundleURLTypes", plist.getPath()) == 0;
            ok &= run("/usr/bin/plutil", "-remove", MARKER_KEY, plist.getPath()) == 0;
            if (ok && lsregister != null) run(lsregister, "-f", bundle.getPath());
            return ok ? Sync.REMOVED : Sync.FAILED;
        }
        if (want.equals(have) && plutilRead(plist, "CFBundleURLTypes.0.CFBundleURLName") != null) {
            return Sync.UNCHANGED;
        }
        if (run("/usr/bin/plutil", "-replace", "CFBundleURLTypes", "-json",
                urlTypesJson(pkg, schemes), plist.getPath()) != 0) {
            return Sync.FAILED;
        }
        if (run("/usr/bin/plutil", "-replace", MARKER_KEY, "-string", want, plist.getPath()) != 0) {
            return Sync.FAILED;
        }
        if (lsregister != null) run(lsregister, "-f", bundle.getPath());
        return Sync.WRITTEN;
    }

    /** The CFBundleURLTypes value: one entry, named after the package, role
     *  Viewer (the app consumes the URL; it is not an editor of it). */
    static String urlTypesJson(String pkg, List<String> schemes) {
        StringBuilder sb = new StringBuilder("[{\"CFBundleURLName\":\"").append(jsonEsc(pkg))
                .append("\",\"CFBundleTypeRole\":\"Viewer\",\"CFBundleURLSchemes\":[");
        for (int i = 0; i < schemes.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsonEsc(schemes.get(i))).append('"');
        }
        return sb.append("]}]").toString();
    }

    private static String jsonEsc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** `plutil -extract <keypath> raw`, or null when the key is absent. */
    private static String plutilRead(File plist, String keyPath) {
        try {
            Process p = new ProcessBuilder("/usr/bin/plutil", "-extract", keyPath, "raw",
                    "-o", "-", plist.getPath()).redirectErrorStream(false).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            if (p.exitValue() != 0) return null;
            return new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1; }
            return p.exitValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Who does LaunchServices already list as a handler of a scheme? Returns the
     *  handlers' bundle paths (empty = nobody), or null when the answer is
     *  unavailable -- which is NOT "nobody": an unknown answer never claims. The
     *  gate injects its own; production asks NSWorkspace (NativeCocoa). */
    interface HandlerProbe {
        List<String> handlersOf(String scheme);
    }

    static final HandlerProbe HOST_PROBE = scheme -> {
        try {
            String[] a = NativeCocoa.urlSchemeHandlers(scheme);
            return a == null ? null : Arrays.asList(a);
        } catch (Throwable t) {
            return null;   // an older runtime binary, or no native bridge at all
        }
    };

    /** Is this application bundle one of ours (Info.plist carries MacapkPackage)? */
    private static boolean isMacApkBundle(String appPath) {
        return plutilRead(new File(appPath, "Contents/Info.plist"), "MacapkPackage") != null;
    }

    /** The first handler of `scheme` that is NOT a Mac.apk bundle -- a native macOS
     *  app that already owns it -- or null when every listed handler is ours (or
     *  there is none). Our own bundle is ours; another Mac.apk app sharing a
     *  scheme is the SDK-shared case the doc notes, not a takeover of the host. */
    static String foreignOwner(String scheme, List<String> handlers, java.util.Map<String, Boolean> ours) {
        for (String h : handlers) {
            if (h == null || h.isEmpty()) continue;
            Boolean o = ours.get(h);
            if (o == null) { o = isMacApkBundle(h); ours.put(h, o); }
            if (!o) return h;
        }
        return null;
    }

    /** Outcome of the ownership filter: the schemes we may claim, the ones we
     *  yield (scheme -> owning application), and whether the probe was unavailable. */
    static final class Claim {
        final List<String> schemes = new ArrayList<>();
        final java.util.Map<String, String> yielded = new java.util.LinkedHashMap<>();
        boolean unknown;
    }

    static Claim claimable(List<String> schemes, HandlerProbe probe) {
        Claim c = new Claim();
        java.util.Map<String, Boolean> ours = new java.util.HashMap<>();
        for (String s : schemes) {
            List<String> hs = probe == null ? null : probe.handlersOf(s);
            if (hs == null) { c.unknown = true; return c; }
            String foreign = foreignOwner(s, hs, ours);
            if (foreign != null) c.yielded.put(s, foreign);
            else c.schemes.add(s);
        }
        return c;
    }

    /** The registration itself, synchronously: SKIPPED when the gate is off
     *  (the pre-change behaviour -- nothing is ever registered). A scheme a
     *  native macOS app already owns is NOT claimed (issue: a silent takeover of
     *  the host's handler); when the LaunchServices probe is unavailable nothing
     *  is changed at all (a wrong claim is sticky, a missed one is retried on the
     *  next launch). */
    static Sync registerNow(File bundle, String pkg, Manifest manifest, String lsregister,
                            HandlerProbe probe) {
        if (!ON || bundle == null || manifest == null) return Sync.SKIPPED;
        List<String> schemes = withIntentHandoff(
                hostSchemes(MacApkPackageManager.macapkBrowsableSchemes(manifest)), INTENT_HANDOFF);
        if (!schemes.isEmpty()) {
            Claim c = claimable(schemes, probe);
            if (c.unknown) {
                NativeLog.w(TAG, "[URL] LaunchServices handler query unavailable -- claiming nothing this launch");
                return Sync.SKIPPED;
            }
            for (java.util.Map.Entry<String, String> y : c.yielded.entrySet()) {
                NativeLog.i(TAG, "[URL] not claiming " + y.getKey() + ": owned by "
                        + new File(y.getValue()).getName());
            }
            schemes = c.schemes;
        }
        Sync r = syncBundleSchemes(bundle, pkg, schemes, lsregister);
        if (r == Sync.WRITTEN || r == Sync.UNCHANGED) {
            sBundle = bundle;
            sClaimed = Collections.unmodifiableList(new ArrayList<>(schemes));
        }
        return r;
    }

    // ------------------------------------------------- redirect routing (opener)
    //
    // SCOPE, stated precisely (lanes20 review): PROVEN for fbconnect ONLY -- the
    // Facebook SDK's Custom Tab redirect `fbconnect://cct.<pkg>/...`, which the
    // Facebook login page really does navigate to in the browser. genericidp and
    // recaptcha are declared by Firebase Auth in five apps too, but Firebase's own
    // redirect page never opens a genericidp:// or recaptcha:// URL in a browser:
    // its non-GMS branch navigates to
    //     intent://firebase.auth/#Intent;scheme=genericidp|recaptcha;package=<apn>;...;end;
    // which macOS has no handler for. The LaunchServices binding of those two
    // schemes is therefore never consulted. That family is handled by the intent:
    // URI hand-off (MACAPK_URL_INTENT_HANDOFF, hostIntentFor): an app that receives
    // browser redirects also registers `intent`, so this same opener rule makes the
    // app that opened the browser the one LaunchServices hands the intent: URI to,
    // and the app plays the browser's part (parseUri + Chromium's sanitisation,
    // package= must be this app). docs/firebase-intent-uri-handoff_2026-09-30.md.
    // OPEN: S.browser_fallback_url is not followed, and an intent: URI naming a
    // package other than the receiver is dropped rather than forwarded to that
    // package's bundle.
    //
    // The fbconnect problem: several Mac.apk apps bundle the Facebook SDK and so
    // claim the same scheme (fbconnect, host cct.<pkg>). LaunchServices binds a
    // scheme to ONE bundle, and which one is LaunchServices' own choice, not a rule to
    // rely on (observed both ways on 2026-09-30: a rival registered afterwards did not
    // move the owner Mac's default in one run, the newest registration won in clean
    // scratch runs), so the redirect of app A's sign-in reached
    // whichever bundle LaunchServices had picked, whose resolveOwn (scheme-only
    // matching) accepted it and dropped it on the floor: the owner's "Cant make a
    // new account or log in" (Duolingo's redirect cold-launched the installed
    // Subway Surf).
    //
    // A device does not resolve a Custom Tab's redirect by scheme alone: it is a
    // navigation INSIDE the tab the app opened, and Chrome delivers an intent the
    // tab's client can handle to THAT client (CustomTabDelegateFactory
    // isForTrustedCallingApp / setPackageForTrustedCallingApp target the Custom
    // Tab's client package). So the app that opens the host browser takes the
    // shared redirect schemes it claims: the default handler becomes its own
    // bundle, and the redirect comes back to it. Schemes a native macOS app owns
    // are never taken (the same ownership rule as registration), an app-unique
    // scheme needs nothing, and an unavailable probe changes nothing.

    /** MACAPK_URL_REDIRECT_OPENER (default ON; implies MACAPK_URL_SCHEMES). =0:
     *  LaunchServices' own binding of a shared scheme (pre-change). */
    static final boolean REDIRECT_OPENER =
            ON && !"0".equals(System.getenv("MACAPK_URL_REDIRECT_OPENER"));
    /** This app's bundle and the schemes it registered (set by registerNow). */
    private static volatile File sBundle;
    private static volatile List<String> sClaimed = Collections.emptyList();

    /** The schemes among `claimed` that another Mac.apk bundle also handles and no
     *  native macOS application does -- the ones the opener must take so the
     *  redirect comes back to it. Unknown (null) handler lists are skipped. */
    static List<String> redirectSchemesToTake(List<String> claimed, String ownBundle,
                                              HandlerProbe probe, java.util.Map<String, Boolean> ours) {
        List<String> out = new ArrayList<>();
        if (claimed == null || ownBundle == null || probe == null) return out;
        String own = new File(ownBundle).getAbsoluteFile().toPath().normalize().toString();
        ours.put(own, Boolean.TRUE);
        for (String s : claimed) {
            List<String> hs = probe.handlersOf(s);
            if (hs == null) continue;                          // unknown: change nothing
            List<String> norm = new ArrayList<>();
            for (String h : hs) {
                if (h == null || h.isEmpty()) continue;
                norm.add(new File(h).getAbsoluteFile().toPath().normalize().toString());
            }
            if (foreignOwner(s, norm, ours) != null) continue; // a native app owns it: never take it
            for (String h : norm) {
                if (!h.equals(own)) { out.add(s); break; }     // shared with another Mac.apk app
            }
        }
        return out;
    }

    /** MACAPK_URL_REDIRECT_CLAIM_ON_REFUSAL=1 (HARNESS ONLY, default off): when a
     *  run refuses host opens (MACAPK_EXTERNAL_URLS=0) still take the shared redirect
     *  schemes for an open that WOULD have been user-initiated. debug/urlscheme_live.sh
     *  sets it so an unattended run can exercise the claim without a real browser
     *  launch; it is honoured ONLY when externalUrlsOff(), so a production open is
     *  decided exactly by whether the open itself happens. Every other harness run
     *  (drive_app.sh defaults MACAPK_EXTERNAL_URLS=0 against the installed bundle's
     *  android-root) leaves the owner's machine-global LaunchServices default alone. */
    static final boolean CLAIM_ON_REFUSAL =
            "1".equals(System.getenv("MACAPK_URL_REDIRECT_CLAIM_ON_REFUSAL"));

    /** The decision ActivityHost.openHost takes before it claims: is this open one
     *  a person made? Pure, so debug/urlscheme_gate.sh can drive every branch.
     *  - externalOff (MACAPK_EXTERNAL_URLS=0): the open is REFUSED, nothing reaches a
     *    browser, so nothing is claimed -- unless the harness-only claimOnRefusal.
     *  - allowAtLaunch (MACAPK_ALLOW_HOST_URL_AT_LAUNCH=1): every open happens, so
     *    every open claims.
     *  - otherwise the open happens iff the last real input was within graceMs
     *    (lastInputMs == 0: never), which is exactly openHost's own refusal rule, so
     *    a programmatic open (a launch-time ad / rate-us) never steals a scheme. */
    static boolean shouldClaim(boolean allowAtLaunch, long lastInputMs, long nowMs, long graceMs,
                               boolean externalOff, boolean claimOnRefusal) {
        if (externalOff && !claimOnRefusal) return false;
        if (allowAtLaunch) return true;
        if (lastInputMs == 0L) return false;
        return nowMs - lastInputMs <= graceMs;
    }

    /** What claimRedirectSchemes would take: empty when the gate is off. */
    static List<String> redirectPlan(List<String> claimed, String ownBundle, HandlerProbe probe) {
        if (!REDIRECT_OPENER) return new ArrayList<>();
        return redirectSchemesToTake(claimed, ownBundle, probe, new java.util.HashMap<>());
    }

    /** The schemes the last successful registerNow claimed (the redirect candidates). */
    static List<String> claimedSchemes() { return sClaimed; }

    /** The app is about to open `uri` in the host browser (a user-initiated open,
     *  ActivityHost.openHost): take the shared redirect schemes it claims. Off the
     *  caller's thread (LaunchServices queries; the caller is often the UI thread). */
    public static void claimRedirectSchemes(String uri) {
        if (!REDIRECT_OPENER || uri == null) return;
        String low = uri.toLowerCase(Locale.ROOT);
        if (!low.startsWith("https://") && !low.startsWith("http://")) return;
        final File bundle = sBundle;
        final List<String> claimed = sClaimed;
        if (bundle == null || claimed.isEmpty()) return;
        Thread t = new Thread(() -> {
            try {
                // One line per user-initiated browser open, so "the redirect went to another
                // app" is diagnosable from the log: what this bundle claims and what it takes.
                List<String> plan = redirectPlan(claimed, bundle.getPath(), HOST_PROBE);
                NativeLog.i(TAG, "[URL] redirect routing: " + bundle.getName() + " claims " + claimed
                        + ", takes " + plan);
                for (String s : plan) {
                    int r = NativeCocoa.setUrlSchemeDefault(bundle.getPath(), s);
                    if (r == 1) {
                        NativeLog.i(TAG, "[URL] redirect scheme " + s + " -> " + bundle.getName()
                                + " (this app opened the browser; a shared scheme's redirect returns to its opener)");
                    } else if (r < 0) {
                        NativeLog.w(TAG, "[URL] redirect scheme " + s + ": default handler not changed");
                    }
                }
            } catch (Throwable th) {
                NativeLog.w(TAG, "[URL] redirect routing failed: " + th);
            }
        }, "macapk-url-opener");
        t.setDaemon(true);
        t.start();
    }

    /** Boot: register this app's schemes with the host, off the boot path. */
    static void registerAsync(final String pkg, final Manifest manifest) {
        if (!ON || manifest == null) return;
        final File bundle = bundleOf(SharedStorage.androidRoot());
        if (bundle == null) return;   // a dev run: nothing to register
        Thread t = new Thread(() -> {
            try {
                Sync r = registerNow(bundle, pkg, manifest, LSREGISTER, HOST_PROBE);
                NativeLog.i(TAG, "[URL] schemes " + withIntentHandoff(
                        hostSchemes(MacApkPackageManager.macapkBrowsableSchemes(manifest)), INTENT_HANDOFF)
                        + " -> " + bundle.getName() + ": " + r);
            } catch (Throwable th) {
                NativeLog.w(TAG, "[URL] scheme registration failed: " + th);
            }
        }, "macapk-url-schemes");
        t.setDaemon(true);
        t.start();
    }

    // --------------------------------------------------------------- delivery

    /** The intent a browser starts for `url` (Chromium's sanitised shape). */
    static Intent browserIntentFor(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.setComponent(null);
        i.setSelector(null);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** `browser` resolved against OUR OWN exported activities only; a copy
     *  pinned to the chosen component, or null when none matches. */
    static Intent resolveOwn(Intent browser, String ownPkg, PackageManager pm) {
        if (browser == null || ownPkg == null || pm == null) return null;
        List<ResolveInfo> rows;
        try {
            rows = pm.queryIntentActivities(browser, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            return null;
        }
        if (rows == null) return null;
        for (ResolveInfo ri : rows) {
            if (ri == null || ri.activityInfo == null) continue;
            if (!ownPkg.equals(ri.activityInfo.packageName)) continue;
            if (!ri.activityInfo.exported) continue;   // another app cannot reach it
            Intent copy = new Intent(browser);
            copy.setComponent(new ComponentName(ownPkg, ri.activityInfo.name));
            return copy;
        }
        return null;
    }

    /** What a URL looks like in the log: scheme, authority and path, never the
     *  query (an OAuth redirect's query is a one-time authorization code). */
    static String redact(String url) {
        if (url == null) return "null";
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        int cut = q < 0 ? h : (h < 0 ? q : Math.min(q, h));
        return cut < 0 ? url : url.substring(0, cut) + " (+" + (url.length() - cut) + " bytes)";
    }

    // ------------------------------------------- URLs a second launch handed us

    /** Where a second launch of this bundle leaves the URLs LaunchServices sent
     *  IT rather than us (launcher/AppStubMain.swift LaunchUrlHandoff): a runtime
     *  started from the Mac.apk window is not this bundle to LaunchServices, so a
     *  browser redirect launches the bundle's stub, which finds us running and
     *  hands the URL over instead of dropping it. Relative to the android root. */
    static final String HANDOFF_DIR = ".macapk/host-urls";
    static final String HANDOFF_SUFFIX = ".url";
    /** A device never delivers a browser redirect late; same bound as a
     *  cross-app envelope (IntentInbox.TTL_MS). */
    static final long HANDOFF_TTL_MS = IntentInbox.TTL_MS;
    private static final int HANDOFF_MAX_BYTES = 64 * 1024;

    private static volatile File sHandoffDir;   // null outside a bundle

    /** The handoff inbox for an android root shaped <X>.app/Contents/android,
     *  else null (a dev run has no bundle, so nothing can hand it a URL). */
    static File handoffDirFor(String androidRoot) {
        File bundle = bundleOf(androidRoot);
        return bundle == null ? null : new File(androidRoot, HANDOFF_DIR);
    }

    /** Read and DELETE every handed-over URL, oldest first; one older than
     *  `ttlMs` (by the epoch-millis name prefix) or unreadable is dropped. */
    static List<String> takeHandedOver(File dir, long nowMs, long ttlMs) {
        List<String> out = new ArrayList<>();
        if (dir == null) return out;
        File[] files = dir.listFiles((d, n) -> n.endsWith(HANDOFF_SUFFIX) && !n.startsWith("."));
        if (files == null || files.length == 0) return out;
        Arrays.sort(files);   // <epochMillis>-<index>-<rand>.url: FIFO by name
        for (File f : files) {
            String u = null;
            String why = null;
            try {
                long born = Long.parseLong(f.getName().substring(0, f.getName().indexOf('-')));
                if (f.length() > HANDOFF_MAX_BYTES) why = "too large";
                else if (nowMs - born > ttlMs) why = "stale (" + (nowMs - born) + " ms old)";
                else u = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (Throwable t) {
                why = "unreadable: " + t;
            }
            try { java.nio.file.Files.deleteIfExists(f.toPath()); } catch (Throwable ignored) {}
            if (u == null || u.isEmpty()) {
                NativeLog.w(TAG, "[URL] discarded handed-over " + f.getName() + ": "
                        + (why != null ? why : "empty"));
                continue;
            }
            NativeLog.i(TAG, "[URL] handed over by a second launch of this bundle: " + redact(u));
            out.add(u);
        }
        return out;
    }

    private static List<Intent> takeResolved() {
        List<Intent> out = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        try {
            String[] fromEvents = NativeCocoa.takeHostUrls();
            if (fromEvents != null) urls.addAll(Arrays.asList(fromEvents));
        } catch (Throwable t) {
            // no native bridge (an offline harness)
        }
        urls.addAll(takeHandedOver(sHandoffDir, System.currentTimeMillis(), HANDOFF_TTL_MS));
        for (String u : urls) {
            if (u == null || u.isEmpty()) continue;
            Intent in = resolveOwn(hostIntentFor(u, sOwnPkg, INTENT_HANDOFF), sOwnPkg, sPm);
            if (in == null) {
                String named = INTENT_HANDOFF ? intentUriPackage(u) : null;
                if (named != null && !named.equals(sOwnPkg)) {
                    // The misroute the opener rule exists to prevent: another app's
                    // redirect reached this bundle (LaunchServices' intent default).
                    NativeLog.w(TAG, "[URL] recv " + redact(u) + ": intent: URI names package "
                            + named + ", not " + sOwnPkg + " -- dropped");
                } else {
                    NativeLog.w(TAG, "[URL] recv " + redact(u) + ": no exported BROWSABLE activity"
                            + " of " + sOwnPkg + " matches -- dropped");
                }
                continue;
            }
            in.setExtrasClassLoader(ActivityHost.macapkAppClassLoader());
            out.add(in);
        }
        return out;
    }

    /**
     * Boot, when the root intent is chosen: a URL that cold-launched the app
     * (queued by the Apple Event before Java existed) becomes the ROOT, as a
     * browser's NEW_TASK start of a non-running app makes it on a device. Later
     * arrivals are delivered warm, in order. Null when nothing is waiting.
     */
    static Intent bootTakeRoot(String ownPkg, PackageManager pm) {
        if (!ON) return null;
        sOwnPkg = ownPkg;
        sPm = pm;
        sHandoffDir = handoffDirFor(SharedStorage.androidRoot());
        sBooted = true;
        List<Intent> arrivals = takeResolved();
        if (arrivals.isEmpty()) return null;
        Intent first = arrivals.remove(0);
        ActivityHost.macapkNoteReferrer(first, SENDER);
        NativeLog.i(TAG, "[URL] recv " + redact(first.getDataString()) + " -> "
                + first.getComponent().getClassName() + " ROOT (cold)");
        for (final Intent e : arrivals) {
            ActivityHost.macapkPostOnOwner(() -> deliverNow(e));
        }
        return first;
    }

    /** JNI (cocoa_bridge.mm macapk_notify_host_url): a URL was queued. Before
     *  Boot has chosen the root it stays queued for bootTakeRoot. */
    public static void macapkOnHostUrl() {
        postDrain();
    }

    static void postDrain() {
        if (!ON || !sBooted) return;
        try {
            ActivityHost.macapkPostOnOwner(HostUrlIntake::drainNow);
        } catch (Throwable t) {
            NativeLog.w(TAG, "[URL] drain post failed: " + t);
        }
    }

    static void drainNow() {
        for (Intent e : takeResolved()) deliverNow(e);
    }

    private static void deliverNow(Intent e) {
        ActivityHost.macapkNoteReferrer(e, SENDER);
        NativeLog.i(TAG, "[URL] recv " + redact(e.getDataString()) + " -> "
                + e.getComponent().getClassName());
        try {
            ActivityHost.macapkDeliverCrossApp(e, SENDER);
        } catch (Throwable t) {
            NativeLog.w(TAG, "[URL] delivery of " + e.getComponent() + " threw: " + t);
        }
    }
}
