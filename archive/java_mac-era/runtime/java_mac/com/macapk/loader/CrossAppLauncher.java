// The cross-app START sender (stage 3a, v1.0.1652): hand an activity start to
// ANOTHER installed Mac.apk app, the way ActivityTaskManager hands a start to
// another process on a device.
//
// docs/cross-app-launch-and-implicit-start-design_2026-09-26.md sections 5-6.
// Runs synchronously on the CALLER's thread from ActivityHost's start router,
// in ActivityStarter's order: resolution errors (ActivityNotFoundException),
// then permission (SecurityException), then the background-launch policy (a
// silent START_ABORTED). Only then is anything written or opened.
//
// Transport: the envelope reaches the TARGET bundle's inbox on disk BEFORE any
// launch; the doorbell makes a running target drain at once; `/usr/bin/open -a
// <bundle>` (no other argument, no environment) activates a running target or
// cold-starts it through its own stub, whose Boot drains the inbox. Nothing is
// ever exec'd directly and nothing is registered globally.
//
// The trust boundary is the macOS user account (every Mac.apk app runs as this
// user); what this guarantees is narrower and real: we only ever OPEN a bundle
// that the installed-apps registry names AND whose own Info.plist agrees it is
// that package's Mac.apk bundle directly under /Applications or the per-user
// ~/Applications (where the installer puts it for a user who cannot write
// /Applications, GitHub #28).
package com.macapk.loader;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrossAppLauncher {
    private static final String TAG = "Mac.apk.XAPP";

    /** MACAPK_XAPP_START (default ON). =0: a registry-resolved start falls
     *  through to the pre-change path (performLaunch misses and logs). */
    static final boolean XAPP_START = !"0".equals(System.getenv("MACAPK_XAPP_START"));

    /** Where an openable bundle must live: the two folders the installer
     *  generates bundles in (InstallCore.swift Paths.bundleDirs). A parameter
     *  for the gate's scratch root; there is deliberately no env seam that
     *  widens it at runtime. */
    static final String BUNDLES_ROOT = "/Applications";

    /** BUNDLES_ROOT, then the per-user ~/Applications. */
    static String[] bundleRoots() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) return new String[] { BUNDLES_ROOT };
        return new String[] { BUNDLES_ROOT, new File(home, "Applications").getPath() };
    }

    /** validateBundle against each root in turn: null when the bundle is
     *  valid under one of them, else the reason from the first root. */
    static String validateBundleAnyRoot(String[] roots, String bundlePath, String pkg,
                                        String dirName) {
        String first = null;
        for (String r : roots) {
            String why = validateBundle(r, bundlePath, pkg, dirName);
            if (why == null) return null;
            if (first == null) first = why;
        }
        return first;
    }

    private CrossAppLauncher() {}

    /**
     * Start `intent` in the installed app that owns `ai`. Returns normally when
     * the start was handed off OR refused by policy (both are "no exception" on
     * a device). Throws ActivityNotFoundException when the bundle does not
     * really exist, SecurityException for a non-exported / permission-guarded
     * target the caller cannot reach.
     */
    static void start(String callerPkg, PackageManager callerPm, Intent intent,
                      ActivityInfo ai, String[] bundle) {
        final String pkg = ai.packageName;
        final String bundlePath = bundle[0], dirName = bundle[1];
        final ComponentName target = new ComponentName(pkg, ai.name);
        String why = validateBundleAnyRoot(bundleRoots(), bundlePath, pkg, dirName);
        if (why != null) {
            NativeLog.w(TAG, "start " + target.flattenToShortString() + ": bundle "
                    + bundlePath + " refused (" + why + ") -- not installed");
            throw new ActivityNotFoundException("Unable to find explicit activity class "
                    + target.toShortString()
                    + "; have you declared this activity in your AndroidManifest.xml"
                    + ", or does your intent not match its declared <intent-filter>?");
        }
        // ActivityTaskSupervisor.checkStartAnyActivityPermission, same order.
        if (!ai.exported) {
            throw new SecurityException("Permission Denial: starting " + intent
                    + " from " + callerPkg + " not exported from " + pkg);
        }
        if (ai.permission != null && !ai.permission.isEmpty()) {
            boolean held = false;
            try {
                held = callerPm != null
                        && callerPm.checkPermission(ai.permission, callerPkg) == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignored) {}
            if (!held) {
                throw new SecurityException("Permission Denial: starting " + intent
                        + " from " + callerPkg + " requires " + ai.permission);
            }
        }
        if (!ActivityHost.macapkLaunchAllowed("cross-app start " + target.flattenToShortString())) {
            return;   // START_ABORTED: silent, as a blocked background start is
        }
        // The caller's object is never mutated; the envelope carries a copy
        // pinned to the RESOLVED component (an alias stays the alias).
        Intent copy = new Intent(intent);
        copy.setComponent(target);
        byte[] env = IntentEnvelope.encode(callerPkg, pkg, ai.name, copy);
        if (env.length > IntentEnvelope.MAX_BYTES) {
            throw new RuntimeException("Failure from system",
                    new android.os.TransactionTooLargeException("data parcel size " + env.length + " bytes"));
        }
        final File inbox = IntentEnvelope.inboxOfBundle(bundlePath);
        try {
            IntentEnvelope.publish(inbox, env);
        } catch (Throwable t) {
            NativeLog.w(TAG, "start " + target.flattenToShortString() + ": could not write the"
                    + " inbox (" + t + ") -- aborted");
            return;
        }
        // The rest never blocks the caller.
        Thread t = new Thread(() -> {
            String ring = CrossAppDoorbell.ring(bundlePath);
            int rc = -1;
            try {
                Process p = new ProcessBuilder("/usr/bin/open", "-a", bundlePath)
                        .redirectErrorStream(true).start();
                rc = p.waitFor();
            } catch (Throwable e) {
                NativeLog.w(TAG, "open -a " + bundlePath + " failed: " + e);
            }
            NativeLog.i(TAG, "[XAPP] start " + target.flattenToShortString() + " from " + callerPkg
                    + " -> " + bundlePath + " (" + (ring != null ? "warm: doorbell " + ring : "cold")
                    + ", open -a rc=" + rc + ")");
        }, "macapk-xapp-send");
        t.setDaemon(true);
        t.start();
    }

    /** null when `bundlePath` is an openable Mac.apk bundle of `pkg`, else why
     *  not. The SAME guard BundleGenerator.remove applies before it will trash a
     *  bundle, plus identity: canonical parent is `root`, a real `.app`
     *  directory (not a symlink), an Info.plist naming an org.macapk.app.*
     *  bundle whose MacapkPackage is `pkg` and, when the registry row knows it,
     *  whose MacapkDirName is `dirName`. */
    static String validateBundle(String root, String bundlePath, String pkg, String dirName) {
        try {
            if (bundlePath == null) return "no bundle path";
            File b = new File(bundlePath);
            if (Files.isSymbolicLink(b.toPath())) return "symlink";
            if (!b.isDirectory()) return "not a directory";
            if (!b.getName().endsWith(".app")) return "not a .app";
            File canon = b.getCanonicalFile();
            File parent = canon.getParentFile();
            if (parent == null || !parent.getPath().equals(new File(root).getCanonicalPath())) {
                return "not directly under " + root;
            }
            File plist = new File(canon, "Contents/Info.plist");
            if (!plist.isFile()) return "no Info.plist";
            String xml = plistXml(plist);
            if (xml == null) return "Info.plist unreadable";
            String bid = plistString(xml, "CFBundleIdentifier");
            if (bid == null || !bid.startsWith("org.macapk.app.")) return "bundle id " + bid;
            String mp = plistString(xml, "MacapkPackage");
            if (!pkg.equals(mp)) return "MacapkPackage " + mp + " != " + pkg;
            if (dirName != null) {
                String md = plistString(xml, "MacapkDirName");
                if (!dirName.equals(md)) return "MacapkDirName " + md + " != " + dirName;
            }
            return null;
        } catch (Throwable t) {
            return "unreadable: " + t;
        }
    }

    /** The plist as XML text: our BundleGenerator writes XML; a binary plist
     *  (a bundle something else re-saved) is converted by plutil. */
    private static String plistXml(File plist) {
        try {
            byte[] raw = Files.readAllBytes(plist.toPath());
            String s = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            if (s.startsWith("<?xml") || s.contains("<plist")) return s;
            Process p = new ProcessBuilder("/usr/bin/plutil", "-convert", "xml1", "-o", "-",
                    plist.getPath()).redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readAllBytes();
            return p.waitFor() == 0 ? new String(out, java.nio.charset.StandardCharsets.UTF_8) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String plistString(String xml, String key) {
        Matcher m = Pattern.compile("<key>\\s*" + Pattern.quote(key)
                + "\\s*</key>\\s*<string>([^<]*)</string>").matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }
}
