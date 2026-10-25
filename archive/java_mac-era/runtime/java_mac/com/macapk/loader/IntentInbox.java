// The cross-app start RECEIVER (stage 3b, v1.0.1652): this bundle's intent
// inbox, drained at boot (the oldest arrival becomes the root intent), on a
// doorbell ring, on unhide and on every app activation.
//
// docs/cross-app-launch-and-implicit-start-design_2026-09-26.md section 7.
// Exists only when this runtime's android root is <X>.app/Contents/android: a
// bare dev run has no inbox and every method here is a no-op.
//
// Ordering: the doorbell is bound BEFORE the boot drain, so a sender either
// rang before the bind (its connect failed, and its envelope was already on
// disk for the drain) or after it (the ring drains it). No window loses one.
//
// The envelope is DATA. Here every component rule is re-checked against OUR
// OWN manifest -- target package is ours, component declared, enabled and
// exported -- so a hand-written file still cannot reach a non-exported
// activity. Every file looked at is deleted, valid or not.
package com.macapk.loader;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IntentInbox {
    private static final String TAG = "Mac.apk.XAPP";

    /** MACAPK_XAPP_RECEIVE (default ON). =0: no doorbell, no drain, no
     *  activation observer (the native side reads the same variable). */
    static final boolean XAPP_RECEIVE = !"0".equals(System.getenv("MACAPK_XAPP_RECEIVE"));
    /** A device never delivers a start late: an envelope older than this is
     *  discarded, so an inbox left by a target that died mid-start cannot open
     *  the app on a stale screen next week. */
    static final long TTL_MS = parseLong(System.getenv("MACAPK_XAPP_TTL_MS"), 30_000L);

    private static volatile File sInbox;          // null outside a bundle
    private static volatile String sOwnPkg;
    private static volatile PackageManager sPm;

    private IntentInbox() {}

    /**
     * Boot, before the root start: bind the doorbell, then drain. Returns the
     * oldest valid arrival (to become the ROOT intent) or null; later arrivals
     * are delivered warm, after the root, in order.
     */
    static Intent bootTakeRoot(String ownPkg, PackageManager pm) {
        if (!XAPP_RECEIVE) return null;
        String root = SharedStorage.androidRoot();
        if (root == null) return null;
        File rootF = new File(root);
        File contents = rootF.getParentFile();
        File bundle = contents != null ? contents.getParentFile() : null;
        if (!"android".equals(rootF.getName()) || contents == null
                || !"Contents".equals(contents.getName()) || bundle == null
                || !bundle.getName().endsWith(".app")) {
            return null;   // a dev run: no bundle, no inbox
        }
        sOwnPkg = ownPkg;
        sPm = pm;
        sInbox = new File(rootF, ".macapk/intents");
        // A ring also drains the URLs a second launch of this bundle handed over
        // (HostUrlIntake.HANDOFF_DIR), as an activation does.
        CrossAppDoorbell.serve(bundle.getPath(), () -> {
            HostUrlIntake.postDrain();
            postDrain();
        });
        List<IntentEnvelope> arrivals = readAll();
        if (arrivals.isEmpty()) return null;
        IntentEnvelope first = arrivals.remove(0);
        NativeLog.i(TAG, "[XAPP] recv " + first.targetClass + " from " + first.senderPackage
                + " -> ROOT (cold, age " + (System.currentTimeMillis() - first.createdAtMillis) + " ms)");
        noteReferrer(first);
        for (IntentEnvelope e : arrivals) deliverLater(e);
        return first.intent;
    }

    /** JNI: the app became active (cocoa_bridge.mm's
     *  NSApplicationDidBecomeActiveNotification observer). */
    public static void macapkOnActivated() {
        // LaunchServices activates the app it hands a URL to, so this is also
        // the backstop for a host URL whose own notify raced Boot.
        HostUrlIntake.postDrain();
        postDrain();
    }

    /** Called when an emptied hidden task comes back: the oldest arrival, if
     *  any, becomes the new root instead of the launcher. */
    static Intent takeForRelaunch() {
        if (!XAPP_RECEIVE || sInbox == null) return null;
        List<IntentEnvelope> arrivals = readAll();
        if (arrivals.isEmpty()) return null;
        IntentEnvelope first = arrivals.remove(0);
        noteReferrer(first);
        NativeLog.i(TAG, "[XAPP] recv " + first.targetClass + " from " + first.senderPackage
                + " -> ROOT of the emptied task");
        for (IntentEnvelope e : arrivals) deliverLater(e);
        return first.intent;
    }

    static void postDrain() {
        if (!XAPP_RECEIVE || sInbox == null) return;
        try {
            ActivityHost.macapkPostOnOwner(IntentInbox::drainNow);
        } catch (Throwable t) {
            NativeLog.w(TAG, "drain post failed: " + t);
        }
    }

    /** On the owner looper: deliver every waiting arrival, oldest first. */
    static void drainNow() {
        if (sInbox == null) return;
        for (IntentEnvelope e : readAll()) deliverNow(e);
    }

    private static void deliverLater(IntentEnvelope e) {
        ActivityHost.macapkPostOnOwner(() -> deliverNow(e));
    }

    private static void deliverNow(IntentEnvelope e) {
        noteReferrer(e);
        try {
            ActivityHost.macapkDeliverCrossApp(e.intent, e.senderPackage);
        } catch (Throwable t) {
            NativeLog.w(TAG, "[XAPP] delivery of " + e.targetClass + " threw: " + t);
        }
    }

    /** Record the sender for Activity.getReferrer() (stage 3c), in
     *  ActivityHost's side table -- never in the intent's extras, which the
     *  app can see. */
    private static void noteReferrer(IntentEnvelope e) {
        ActivityHost.macapkNoteReferrer(e.intent, e.senderPackage != null ? e.senderPackage : "");
    }

    /** Read, validate and DELETE every envelope; the valid ones, oldest first. */
    private static List<IntentEnvelope> readAll() {
        List<IntentEnvelope> out = new ArrayList<>();
        File inbox = sInbox;
        if (inbox == null) return out;
        File[] files = inbox.listFiles((d, n) -> n.endsWith(IntentEnvelope.SUFFIX) && !n.startsWith("."));
        if (files == null || files.length == 0) return out;
        Arrays.sort(files);   // <epochMillis>-<nanoTime>-<rand>: FIFO by name
        for (File f : files) {
            String[] why = new String[1];
            IntentEnvelope e = IntentEnvelope.read(f, TTL_MS, why);
            try { java.nio.file.Files.deleteIfExists(f.toPath()); } catch (Throwable ignored) {}
            if (e == null) {
                NativeLog.w(TAG, "[XAPP] discarded " + f.getName() + ": " + why[0]);
                continue;
            }
            String bad = checkTarget(e);
            if (bad != null) {
                NativeLog.w(TAG, "[XAPP] refused " + e.targetClass + " from " + e.senderPackage + ": " + bad);
                continue;
            }
            e.intent.setExtrasClassLoader(ActivityHost.macapkAppClassLoader());   // ActivityThread :3958
            out.add(e);
        }
        return out;
    }

    /** null when the arrival may start here, else why not. */
    private static String checkTarget(IntentEnvelope e) {
        if (sOwnPkg == null || !sOwnPkg.equals(e.targetPackage)) return "not our package (" + e.targetPackage + ")";
        ComponentName cn = e.intent.getComponent();
        if (cn == null || !sOwnPkg.equals(cn.getPackageName()) || !e.targetClass.equals(cn.getClassName())) {
            return "component mismatch " + cn;
        }
        try {
            ActivityInfo ai = sPm != null ? sPm.getActivityInfo(cn, 0) : null;
            if (ai == null) return "not declared";
            if (!ai.exported) return "not exported";
            if (!ai.enabled) return "disabled";
        } catch (PackageManager.NameNotFoundException nnf) {
            return "not declared";
        } catch (Throwable t) {
            return "unresolvable: " + t;
        }
        return null;
    }

    private static long parseLong(String s, long dflt) {
        try { return s == null || s.isEmpty() ? dflt : Long.parseLong(s); }
        catch (NumberFormatException e) { return dflt; }
    }
}
