package com.macapk.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keyboard-to-screen-coordinate bindings, so a touch-only Android game is
 * playable on a Mac keyboard. Owner request 2026-07-28; design notes in
 * docs/keybind-profiles-design_2026-07-28.md.
 *
 * <p>Coordinates are stored NORMALISED to [0,1], never as raw pixels. The window
 * is resizable and the runtime composes at several different drawable sizes
 * (3244x1796 and 3244x1860 both appear depending on whether window chrome is
 * included), so a pixel binding breaks the moment anything changes. Normalised
 * coordinates are resolution-, DPI- and resize-independent for free.
 *
 * <p>The reference frame is the CONTENT rect -- the surface with the status-bar
 * inset excluded -- which is the same space {@code TouchDispatcher} converts a
 * real click into. Binding against the raw window instead would drift as the
 * window shape changes, because cocos2d-x and friends letterbox.
 *
 * <p>This class owns the MODEL and its persistence only. Injection lives in
 * {@link TouchDispatcher}, which already holds the pointer state and the
 * coordinate transform a real click goes through.
 */
public final class KeyBindProfiles {

    private KeyBindProfiles() { }

    /** One key mapped to one point, with a human label for the editor UI. */
    public static final class Binding {
        public final int    keyCode;   // Android KEYCODE_* (KeyEvent constants)
        public final float  nx, ny;    // normalised [0,1] within the CONTENT rect
        public final String label;

        public Binding(int keyCode, float nx, float ny, String label) {
            this.keyCode = keyCode;
            this.nx = nx;
            this.ny = ny;
            this.label = label == null ? "" : label;
        }
    }

    /** A named set of bindings, optionally auto-selected for one package. */
    public static final class Profile {
        public final String        name;
        public final String        packageName;   // may be null: applies to anything
        public final List<Binding> bindings;

        public Profile(String name, String packageName, List<Binding> bindings) {
            this.name        = name;
            this.packageName = packageName;
            this.bindings    = bindings == null
                ? Collections.<Binding>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(bindings));
        }

        /** The binding for a key code, or null. Linear scan: a profile has a
         *  handful of entries, and this runs once per key event. */
        public Binding forKey(int keyCode) {
            for (Binding b : bindings) if (b.keyCode == keyCode) return b;
            return null;
        }
    }

    // ---- storage -----------------------------------------------------------
    //
    // Alongside the Swift launcher's own state, which already lives at
    // ~/Library/Application Support/Mac.apk/installed_apps.json -- one location
    // for user-level Mac.apk state rather than inventing a second.

    private static File profilesDir() {
        String home = System.getProperty("user.home");
        return new File(home + "/Library/Application Support/Mac.apk/keybinds");
    }

    private static File profileFile(String name) {
        return new File(profilesDir(), sanitize(name) + ".json");
    }

    /** Profile names double as FILE names, so keep them to a safe alphabet
     *  rather than trusting whatever the editor UI passed in. */
    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length() && sb.length() < 64; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ')
                sb.append(c);
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? "profile" : s;
    }

    /** Every saved profile name, sorted. Never null. */
    public static List<String> list() {
        List<String> out = new ArrayList<>();
        File[] files = profilesDir().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".json")) out.add(n.substring(0, n.length() - 5));
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Load one profile by name, or null if it is missing or unreadable. A
     *  corrupt file must not take the app down, so this swallows and logs. */
    public static Profile load(String name) {
        File f = profileFile(name);
        if (!f.isFile()) return null;
        try {
            String text = new String(Files.readAllBytes(f.toPath()),
                                     StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(text);
            JSONArray arr = root.optJSONArray("bindings");
            List<Binding> bs = new ArrayList<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                bs.add(new Binding(
                    o.optInt("keyCode", 0),
                    (float) o.optDouble("x", 0.0),
                    (float) o.optDouble("y", 0.0),
                    o.optString("label", "")));
            }
            return new Profile(root.optString("name", name),
                               root.has("package") ? root.optString("package") : null,
                               bs);
        } catch (Throwable t) {
            NativeLog.w("Mac.apk.KeyBind", "load(" + name + ") failed: " + t);
            return null;
        }
    }

    /** Persist a profile. Returns false (and logs) rather than throwing. */
    public static boolean save(Profile p) {
        if (p == null || p.name == null) return false;
        try {
            File dir = profilesDir();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                NativeLog.w("Mac.apk.KeyBind", "cannot create " + dir);
                return false;
            }
            JSONArray arr = new JSONArray();
            for (Binding b : p.bindings) {
                JSONObject o = new JSONObject();
                o.put("keyCode", b.keyCode);
                o.put("x", (double) b.nx);
                o.put("y", (double) b.ny);
                o.put("label", b.label);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("name", p.name);
            if (p.packageName != null) root.put("package", p.packageName);
            root.put("bindings", arr);
            byte[] out = root.toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream fos = new FileOutputStream(profileFile(p.name))) {
                fos.write(out);
            }
            return true;
        } catch (Throwable t) {
            NativeLog.w("Mac.apk.KeyBind", "save(" + p.name + ") failed: " + t);
            return false;
        }
    }

    // ---- active profile ----------------------------------------------------

    private static volatile Profile sActive;

    /** The profile currently driving key injection, or null when bindings are
     *  off. Read on every key event, so it is a plain volatile read. */
    public static Profile active() { return sActive; }

    /** Name of the active profile, or null. Used by the Controls menu to put
     *  the checkmark on the right row without duplicating selection state. */
    public static String activeName() {
        Profile p = sActive;
        return p == null ? null : p.name;
    }

    public static void setActive(Profile p) {
        sActive = p;
        NativeLog.i("Mac.apk.KeyBind", "active profile -> "
            + (p == null ? "(none)" : p.name + " (" + p.bindings.size() + " bindings)"));
    }

    /** Select by name; null or an unknown name turns bindings off. */
    public static boolean setActiveByName(String name) {
        // A profile decides what a recorded key becomes on replay (keys derive
        // into touches in doDispatchKey): note the switch (InputRecorder).
        if (InputRecorder.ON)
            InputRecorder.unrecorded("key-binding profile -> " + (name == null ? "(none)" : name));
        if (name == null) { setActive(null); return true; }
        Profile p = load(name);
        if (p == null) return false;
        setActive(p);
        return true;
    }

    /** The Controls menu's pick: "None" (null) or a profile. The menu is ONE
     *  group of input modes -- None, Arrow Keys as D-pad, Tilt Control, Space
     *  Bar Taps Center, each profile -- and only one can be on (owner,
     *  2026-10-02: more than one checked "cannot be so"), so a pick here
     *  switches the other modes off. Kept
     *  apart from setActiveByName, which boot and the harness call and which
     *  must not rewrite the remembered Tilt / Arrow-pad choices. */
    public static boolean selectFromMenu(String name) {
        if (TiltControl.isEnabled()) TiltControl.setEnabled(false);
        if (ArrowPad.isEnabled()) ArrowPad.setEnabled(false);
        if (SpaceTap.isEnabled()) SpaceTap.setEnabled(false);
        return setActiveByName(name);
    }

    /**
     * Pick a profile for the package being launched: an exact package match
     * wins, otherwise nothing is activated. Deliberately does NOT fall back to
     * "the first profile on disk" -- silently applying someone else's bindings
     * to an unrelated game would look like broken input, which is exactly the
     * class of bug this session spent its time on.
     */
    public static void autoSelectForPackage(String pkg) {
        if (pkg == null) return;
        for (String n : list()) {
            Profile p = load(n);
            if (p != null && pkg.equals(p.packageName)) { setActive(p); return; }
        }
    }

    // ---- built-in default --------------------------------------------------

    /**
     * v1.0.833: the built-in Hill Climb Racing profile was REMOVED, and with it
     * the seeding that wrote it to disk on first boot.
     *
     * The MECHANISM here is generic and stays: a profile is user data on disk
     * carrying its own `packageName`, and {@link #autoSelectForPackage} matches
     * whatever the user authored. What had to go is the runtime SHIPPING an
     * entry for one specific app -- `new Profile("HCR", "com.fingersoft.hillclimb", ...)`
     * hardcoded that package name plus tap coordinates measured off Hill Climb
     * Racing's gameplay screen, installed it unasked, and auto-activated it on
     * launch. That is app-specific code in the shipped runtime, which the
     * project forbids outright ("No app-specific code... or any package-name
     * branch anywhere in the codebase"); a convenience default does not qualify
     * for the narrow temporary-diagnostic exception.
     *
     * The capability is unchanged for users: authoring a profile with
     * `"package": "com.fingersoft.hillclimb"` and the same two bindings
     * reproduces the old behaviour exactly. The bindings it used, for anyone
     * recreating it, were KEYCODE_DPAD_LEFT -> (0.111, 0.795) "Brake" and
     * KEYCODE_DPAD_RIGHT -> (0.889, 0.795) "Gas"; full derivation of those
     * coordinates is in the v1.0.826 entry in changelog.md.
     */
    public static void installDefaultsIfAbsent() {
        // v1.0.1034: Mac.apk now DOES ship profiles. They live as classpath
        // resources under com/macapk/loader/keybinds/ inside macapk-loader.jar,
        // which is already a member of RuntimeInstall.payload -- so they reach
        // an INSTALLED app (which execs the staged runtime, not the bundle)
        // without a payload change. Shipping them as a loose file next to the
        // jar would have needed one, and forgetting that is the exact
        // two-execution-surfaces trap that has bitten this project repeatedly.
        //
        // WHY THIS EXISTS: a profile authored on one machine lives only in that
        // user's Application Support dir. HCR's gas/brake bindings sat there for
        // a month and reached nobody -- Hill Climb Racing ran on a second Mac
        // with no keyboard controls at all (owner, 2026-08-23).
        //
        // IfAbsent, never overwrite: a user who has tuned a profile keeps their
        // version. The consequence, accepted: deleting a shipped profile
        // restores it on the next launch. Renaming it does not.
        for (String name : bundledProfileNames()) {
            File dest = profileFile(stripJson(name));
            if (dest.exists()) continue;
            byte[] data = readBundled(name);
            if (data == null) continue;
            try {
                File dir = profilesDir();
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    NativeLog.w("Mac.apk.KeyBind", "cannot create " + dir);
                    return;
                }
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    out.write(data);
                }
                NativeLog.i("Mac.apk.KeyBind", "installed shipped profile " + name);
            } catch (IOException e) {
                NativeLog.w("Mac.apk.KeyBind",
                            "could not install " + name + ": " + e);
            }
        }
    }

    /** Filenames listed in the shipped index, or empty when none ship. */
    private static List<String> bundledProfileNames() {
        List<String> out = new ArrayList<>();
        byte[] idx = readBundled("index.txt");
        if (idx == null) return out;
        for (String line : new String(idx, StandardCharsets.UTF_8).split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            out.add(t);
        }
        return out;
    }

    private static byte[] readBundled(String name) {
        try (java.io.InputStream in = KeyBindProfiles.class
                .getResourceAsStream("keybinds/" + name)) {
            if (in == null) return null;
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String stripJson(String name) {
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }
}
