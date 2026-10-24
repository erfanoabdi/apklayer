// com.macapk.loader.FrameworkWindowStyle -- the window style of an activity's
// MANIFEST theme: does it hide what is below it (occlude), and does it display
// a window at all. Read-only; nothing here applies a theme.
//
// WHY (window policy phase 3, docs/window-policy-per-activity-orientation_2026-09-25.md
// R5 and its skeptic's correction). On a device every ActivityRecord reads its
// theme's Window style ONCE, when the record is created
// (android16-release ActivityRecord.java:1892-1911):
//     int realTheme = info.getThemeResource();
//     if (realTheme == Resources.ID_NULL) realTheme = ... Theme / Theme_Holo;
//     final WindowStyle style = mAtmService.getWindowStyle(packageName, realTheme, mUserId);
//     mOccludesParent = !(style.isTranslucent() || style.isFloating())
//             || style.showWallpaper();
//     mNoDisplay = style.noDisplay();
// and a translucent or floating activity then inherits the letterbox of the
// opaque activity below (TransparentPolicy). So "does this activity move the
// window?" is a question about the manifest theme's windowIsTranslucent /
// windowIsFloating / windowShowWallpaper / windowNoDisplay -- never about a
// runtime setTheme.
//
// The corpus declares those mostly through PLATFORM themes the app's resource
// table does not contain (Theme.Translucent.NoTitleBar.Fullscreen on Subway's
// Play Games bridge and Anuto's MenuActivity, Theme.Translucent on AdMob's
// AdActivity, Theme.NoDisplay, Theme.Dialog, the Material/DeviceDefault
// dialogs). ResourceIndex's own framework style table (MACAPK_FW_STYLE_BY_ID)
// deliberately names only eight OPAQUE themes, and extending it changes how
// themes are APPLIED -- which is not this question. This class is the
// occlusion-only lookup: an app style is walked through ResourceIndex's style
// table up to its framework terminus, and a framework theme is answered from
// the table below. The native manifest reader carries the SAME table
// (runtime/src/window_traits.cpp kFrameworkWindowStyles), and
// debug/winpolicy_gate.sh fails when the two differ.
package com.macapk.loader;

public final class FrameworkWindowStyle {
    private FrameworkWindowStyle() {}

    /** window_policy.h kStyle* -- the same bit values on both sides. */
    public static final int TRANSLUCENT    = 1;   // windowIsTranslucent
    public static final int FLOATING       = 2;   // windowIsFloating
    public static final int SHOW_WALLPAPER = 4;   // windowShowWallpaper
    public static final int NO_DISPLAY     = 8;   // windowNoDisplay
    private static final int ALL = TRANSLUCENT | FLOATING | SHOW_WALLPAPER | NO_DISPLAY;
    // Row shorthands (the gate reads the rows back by these letters).
    private static final int T = TRANSLUCENT, F = FLOATING, W = SHOW_WALLPAPER, N = NO_DISPLAY;

    private static final java.util.Map<Integer, Integer> BY_ID = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> BY_NAME = new java.util.HashMap<>();

    private static void row(int id, String name, int flags) {
        BY_ID.put(id, flags);
        BY_NAME.put(name, flags);
    }

    // Generated 2026-09-25 from aosp-mirror platform_frameworks_base android14-release
    // core/res/res/values/{themes,themes_holo,themes_material,themes_device_defaults}.xml
    // (+ values-large/ for the DialogWhenLarge rows, which are Dialog.FixedSize on a
    // large screen -- the only screen the window policy runs on): every
    // <public type="style"> Theme* id in public-final.xml, each flag taken from the
    // nearest style in its parent chain (parent=, else the dotted prefix) that
    // declares the item. Only rows with a flag set; every other public theme is
    // opaque. Theme.Translucent sets windowIsTranslucent (themes.xml), Theme.Dialog
    // windowIsFloating, Theme.Wallpaper windowShowWallpaper, Theme.NoDisplay
    // windowIsTranslucent + windowNoDisplay; the rest inherit them.
    static {
        row(0x0103000b, "Theme.Dialog", F);
        row(0x0103000f, "Theme.Translucent", T);
        row(0x01030010, "Theme.Translucent.NoTitleBar", T);
        row(0x01030011, "Theme.Translucent.NoTitleBar.Fullscreen", T);
        row(0x01030054, "Theme.InputMethod", T | F);
        row(0x01030055, "Theme.NoDisplay", T | N);
        row(0x01030059, "Theme.Panel", T | F);
        row(0x0103005a, "Theme.Light.Panel", T | F);
        row(0x0103005e, "Theme.Wallpaper", W);
        row(0x0103005f, "Theme.Wallpaper.NoTitleBar", W);
        row(0x01030060, "Theme.Wallpaper.NoTitleBar.Fullscreen", W);
        row(0x01030061, "Theme.WallpaperSettings", T);
        row(0x01030062, "Theme.Light.WallpaperSettings", T);
        row(0x0103006f, "Theme.Holo.Dialog", F);
        row(0x01030070, "Theme.Holo.Dialog.MinWidth", F);
        row(0x01030071, "Theme.Holo.Dialog.NoActionBar", F);
        row(0x01030072, "Theme.Holo.Dialog.NoActionBar.MinWidth", F);
        row(0x01030073, "Theme.Holo.Light.Dialog", F);
        row(0x01030074, "Theme.Holo.Light.Dialog.MinWidth", F);
        row(0x01030075, "Theme.Holo.Light.Dialog.NoActionBar", F);
        row(0x01030076, "Theme.Holo.Light.Dialog.NoActionBar.MinWidth", F);
        row(0x01030077, "Theme.Holo.DialogWhenLarge", F);  // values-large
        row(0x01030078, "Theme.Holo.DialogWhenLarge.NoActionBar", F);  // values-large
        row(0x01030079, "Theme.Holo.Light.DialogWhenLarge", F);  // values-large
        row(0x0103007a, "Theme.Holo.Light.DialogWhenLarge.NoActionBar", F);  // values-large
        row(0x0103007b, "Theme.Holo.Panel", T | F);
        row(0x0103007c, "Theme.Holo.Light.Panel", T | F);
        row(0x0103007d, "Theme.Holo.Wallpaper", W);
        row(0x0103007e, "Theme.Holo.Wallpaper.NoTitleBar", W);
        row(0x0103007f, "Theme.Holo.InputMethod", T | F);
        row(0x0103012e, "Theme.DeviceDefault.Dialog", F);
        row(0x0103012f, "Theme.DeviceDefault.Dialog.MinWidth", F);
        row(0x01030130, "Theme.DeviceDefault.Dialog.NoActionBar", F);
        row(0x01030131, "Theme.DeviceDefault.Dialog.NoActionBar.MinWidth", F);
        row(0x01030132, "Theme.DeviceDefault.Light.Dialog", F);
        row(0x01030133, "Theme.DeviceDefault.Light.Dialog.MinWidth", F);
        row(0x01030134, "Theme.DeviceDefault.Light.Dialog.NoActionBar", F);
        row(0x01030135, "Theme.DeviceDefault.Light.Dialog.NoActionBar.MinWidth", F);
        row(0x01030136, "Theme.DeviceDefault.DialogWhenLarge", F);  // values-large
        row(0x01030137, "Theme.DeviceDefault.DialogWhenLarge.NoActionBar", F);  // values-large
        row(0x01030138, "Theme.DeviceDefault.Light.DialogWhenLarge", F);  // values-large
        row(0x01030139, "Theme.DeviceDefault.Light.DialogWhenLarge.NoActionBar", F);  // values-large
        row(0x0103013a, "Theme.DeviceDefault.Panel", T | F);
        row(0x0103013b, "Theme.DeviceDefault.Light.Panel", T | F);
        row(0x0103013c, "Theme.DeviceDefault.Wallpaper", W);
        row(0x0103013d, "Theme.DeviceDefault.Wallpaper.NoTitleBar", W);
        row(0x0103013e, "Theme.DeviceDefault.InputMethod", T | F);
        row(0x01030225, "Theme.Material.Dialog", F);
        row(0x01030226, "Theme.Material.Dialog.Alert", F);
        row(0x01030227, "Theme.Material.Dialog.MinWidth", F);
        row(0x01030228, "Theme.Material.Dialog.NoActionBar", F);
        row(0x01030229, "Theme.Material.Dialog.NoActionBar.MinWidth", F);
        row(0x0103022b, "Theme.Material.DialogWhenLarge", F);  // values-large
        row(0x0103022c, "Theme.Material.DialogWhenLarge.NoActionBar", F);  // values-large
        row(0x0103022d, "Theme.Material.InputMethod", T | F);
        row(0x01030232, "Theme.Material.Panel", T | F);
        row(0x01030234, "Theme.Material.Voice", F);
        row(0x01030235, "Theme.Material.Wallpaper", W);
        row(0x01030236, "Theme.Material.Wallpaper.NoTitleBar", W);
        row(0x01030239, "Theme.Material.Light.Dialog", F);
        row(0x0103023a, "Theme.Material.Light.Dialog.Alert", F);
        row(0x0103023b, "Theme.Material.Light.Dialog.MinWidth", F);
        row(0x0103023c, "Theme.Material.Light.Dialog.NoActionBar", F);
        row(0x0103023d, "Theme.Material.Light.Dialog.NoActionBar.MinWidth", F);
        row(0x0103023f, "Theme.Material.Light.DialogWhenLarge", F);  // values-large
        row(0x01030240, "Theme.Material.Light.DialogWhenLarge.NoActionBar", F);  // values-large
        row(0x01030245, "Theme.Material.Light.Panel", T | F);
        row(0x01030246, "Theme.Material.Light.Voice", F);
        row(0x010302d1, "Theme.DeviceDefault.Dialog.Alert", F);
        row(0x010302d2, "Theme.DeviceDefault.Light.Dialog.Alert", F);
        row(0x010302d6, "ThemeOverlay.Material.Dialog", F);
        row(0x010302d7, "ThemeOverlay.Material.Dialog.Alert", F);
    }

    /** The flags of a FRAMEWORK theme id, 0 when the table does not list it. */
    public static int frameworkFlags(int themeId) {
        Integer f = BY_ID.get(themeId);
        return f != null ? f : 0;
    }

    /** The same by name ("Theme.Translucent", with or without an
     *  "@android:style/" / "android:style/" prefix). */
    public static int frameworkFlagsByName(String name) {
        if (name == null) return 0;
        Integer f = BY_NAME.get(stripFrameworkPrefix(name));
        return f != null ? f : 0;
    }

    /** AR16:1901-1904. */
    public static boolean occludesParent(int flags) {
        return (flags & (TRANSLUCENT | FLOATING)) == 0 || (flags & SHOW_WALLPAPER) != 0;
    }

    /** Whether an activity whose manifest theme has these flags owns the window
     *  box: it hides what is below AND it displays a window. */
    public static boolean ownsWindowBox(int flags) {
        return occludesParent(flags) && (flags & NO_DISPLAY) == 0;
    }

    /** The flags of ActivityInfo.getThemeResource() (the activity's theme, else
     *  the application's). 0 = no theme: AOSP then uses Theme / Theme_Holo, both
     *  opaque (AR16:1892-1895). An app style is walked through the app's style
     *  table: at each style the first declaration of each flag wins (a child
     *  overrides its parent, AssetManager2::GetBag's merge), and the chain's
     *  framework terminus answers whatever is still undeclared. Best-effort: a
     *  theme the index cannot name answers 0 (opaque, AOSP's own default when
     *  no style is found, AR16:1910). */
    public static int flagsForTheme(int themeResId) {
        if (themeResId == 0) return 0;
        if ((themeResId >>> 24) == 0x01) return frameworkFlags(themeResId);
        try {
            Object idx = android.content.res.Resources.macapkGetResourceIndex();
            if (!(idx instanceof ResourceIndex)) return 0;
            ResourceIndex ri = (ResourceIndex) idx;
            String name = ri.styleNameForIdRefPublic(String.format("@0x%08x", themeResId));
            return walk(ri, name);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static final String[] ATTRS = {
        "windowIsTranslucent", "windowIsFloating", "windowShowWallpaper", "windowNoDisplay" };
    private static final int[] BITS = { TRANSLUCENT, FLOATING, SHOW_WALLPAPER, NO_DISPLAY };

    static int walk(ResourceIndex ri, String start) {
        int flags = 0, known = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        String cur = start;
        while (cur != null && !cur.isEmpty() && known != ALL && seen.add(cur)) {
            // A framework terminus: the table answers every flag still open.
            Integer fw = frameworkRef(cur);
            if (fw != null) {
                flags |= fw & ~known;
                break;
            }
            String appName = cur;
            if (appName.startsWith("@0x") || appName.startsWith("@0X")) {
                appName = ri.styleNameForIdRefPublic(appName);
                if (appName == null) break;
            } else if (appName.startsWith("@style/")) {
                appName = appName.substring(7);
            }
            ResourceIndex.StyleDef s = ri.getStyleByName(appName);
            if (s == null || s.items == null) break;
            for (int i = 0; i < ATTRS.length; ++i) {
                if ((known & BITS[i]) != 0) continue;
                String v = s.items.get("android:" + ATTRS[i]);
                if (v == null) v = s.items.get(ATTRS[i]);
                if (v == null) continue;
                v = v.trim();
                if ("true".equalsIgnoreCase(v)) { known |= BITS[i]; flags |= BITS[i]; }
                else if ("false".equalsIgnoreCase(v)) { known |= BITS[i]; }
                // A reference / ?attr value: undeclared here, the parent decides.
            }
            cur = s.parent;
        }
        return flags;
    }

    /** A parent reference that names a FRAMEWORK style, or null for an app one. */
    private static Integer frameworkRef(String ref) {
        String r = ref.trim();
        if (r.startsWith("@android:style/") || r.startsWith("android:style/")
                || r.startsWith("@android:")) {
            return frameworkFlagsByName(r);
        }
        if (r.startsWith("@0x01") || r.startsWith("@0X01")) {
            try { return frameworkFlags((int) Long.parseLong(r.substring(3), 16)); }
            catch (NumberFormatException e) { return 0; }
        }
        return null;
    }

    private static String stripFrameworkPrefix(String n) {
        String s = n.trim();
        if (s.startsWith("@android:style/")) return s.substring(15);
        if (s.startsWith("android:style/")) return s.substring(14);
        if (s.startsWith("@android:")) return s.substring(9);
        return s;
    }
}
