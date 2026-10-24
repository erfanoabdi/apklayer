// StatusBarDriver -- paints an Android-style system status bar on the
// Mac.apk canvas. The bar lives at the top of the surface (the
// rootView is inset down by `heightPx(root)` so the app's own content
// lays out below it), shows the macOS host's wall-clock time on the
// left and battery percentage on the right, and matches the activity's
// declared M3 theme colors so it blends with the app.
//
// AOSP-correct layering: this paint runs AFTER rootView.draw(canvas)
// from inside ViewCanvasDriver.macapkDrawFrame -- on real Android
// the status bar is owned by SystemUI, not the app, and is drawn on
// TOP of every Activity decor by the WindowManager compositor. We
// mirror that by painting last so app-side overlays don't poke
// through, then the OS-side strip overlays them.
//
// Generic across every APK: keys exclusively on theme attributes
// (`?attr/colorBackground`, `?attr/colorOnBackground`) and a stock
// `pmset -g batt` parse. Nothing here mentions any package name; an
// APK with no theme cascade falls back to white-on-dark-gray.
//
// The pmset(8) man page on macOS 26 documents the line format for
// `-g batt` as:
//     Now drawing from '<source>'
//      -InternalBattery-0 (id=...)\t<pct>%; <state>; <ETA>; ...
// On a Mac with no battery (Mac mini / iMac without UPS) the second
// line is missing or replaced with "No batteries available". Both
// branches are tolerated -- if we can't parse a percentage we fall
// back to rendering "--" on the right side so the strip layout stays
// stable.
package com.macapk.loader;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class StatusBarDriver {
    // Process-environment snapshots for the per-draw / per-frame / per-layout /
    // per-event sites below (debug/w9textdraw_verify/hoist_getenv.py). A
    // System.getenv call encodes the name and hashes it on every call; the
    // environment it reads is copied once at JVM start (JDK ProcessEnvironment,
    // "subsequent calls to putenv/setenv from C will not be visible from Java
    // code"), so one read at class init is the same value. First in the body, so
    // no other static initializer of this class can observe them unset.
    private static final String ENV_MACAPK_STATUSBAR_BLEND = System.getenv("MACAPK_STATUSBAR_BLEND");
    private static final String ENV_MACAPK_TRACE_BARCOLOR = System.getenv("MACAPK_TRACE_BARCOLOR");

    private static final String TAG = "Mac.apk.StatusBar";

    private StatusBarDriver() {}

    // ----------------------------------------------------------------------
    // THE SYSTEM BARS ARE OFF BY DEFAULT (v1.0.1627).
    //
    // Owner, 2026-09-22: "remove the status bar + bottom nav bar -- it makes
    // it feel like an emulator." A Mac.apk app is a macOS app in a macOS
    // window; a painted Android clock/wifi/battery strip and a row of
    // back/home/recents glyphs are chrome for a phone, and on a Mac they
    // announce "this is an emulator" while costing 24dp + 48dp of the app's
    // own content area.
    //
    // WHAT REPLACED THE AFFORDANCE. Only BACK ever meant anything here: HOME
    // and RECENTS have no referent in a one-window Mac app. BACK now has a
    // NATIVE home -- the title-bar chevron installed by
    // InstallBackTitlebarAccessory (cocoa_bridge.mm, v1.0.1624-1625), which
    // routes to the same KEYCODE_BACK -> Activity.onKeyUp(BACK) ->
    // onBackPressed() path as Esc and Cmd+[. So this removes chrome, it does
    // not remove a capability.
    //
    // HOW IT IS EXPRESSED, and why that shape. This is NOT a new fourth state:
    // it makes the runtime answer every question exactly as it already does for
    // an app that set FLAG_FULLSCREEN and called
    // WindowInsetsController.hide(navigationBars()) -- both of which are
    // ordinary AOSP immersive behaviour that this file has honoured since
    // v1.0.891/939 and that the whole inset pipeline is already exercised
    // against. heightPx and navBarInsetPx return 0, so every consumer (measure,
    // layout, WindowInsets, the painters, the hit rects, the touch offset) sees
    // one consistent answer and no call site needed editing.
    //
    // heightPxRaw is DELIBERATELY UNCHANGED. It is the DISPLAY dimension --
    // AOSP's DisplayContent.computeScreenConfiguration subtracts
    // status_bar_height for every rotation regardless of the foreground
    // window's flags -- so it keeps feeding Configuration.screenHeightDp and
    // WindowInsets.getInsetsIgnoringVisibility(statusBars()), exactly as it
    // does today for a fullscreen window. Same split, same two answers.
    //
    // MACAPK_SYSTEM_BARS=1 paints them again, for an A/B. Note that an
    // emulator-vs-us pixel comparison is NOT valid across this flag: the
    // reference AVD draws both bars, and debug/imgdiff.py's --safe-area crops
    // OUR side by a CONSTANT 48/48 inset that this change invalidates. Pass
    // MACAPK_IMGDIFF_OUR_INSET=0,0 (or MACAPK_SYSTEM_BARS=1 for the capture)
    // before quoting a fleet number across it.
    private static volatile Boolean sBarsOn;
    private static final boolean sPhoneAppWindow =
        !"0".equals(System.getenv("MACAPK_PHONE_APP_WINDOW"));
    public static boolean systemBarsEnabled() {
        Boolean b = sBarsOn;
        if (b == null) {
            b = Boolean.valueOf("1".equals(System.getenv("MACAPK_SYSTEM_BARS")));
            sBarsOn = b;
        }
        return b.booleanValue();
    }

    /** Bar height in host pixels = 24dp scaled by the View's
     *  DisplayMetrics density. AOSP `system_bar_height` is 24dp; we
     *  match that exactly so the inset matches what the APK's
     *  fitSystemWindows / WindowInsets-aware layouts expect. */
    public static int heightPx(View root) {
        // v1.0.1627: the bar is removed by default (see systemBarsEnabled).
        // Same answer FLAG_FULLSCREEN already produces, for the same reason:
        // there is no bar, so this window gets no top inset.
        if (!systemBarsEnabled()) return 0;
        // v1.0.891: FLAG_FULLSCREEN means the app hid the status bar, so the
        // window frame is the whole display and there is NO top content inset.
        // AOSP pairs the flag with FLAG_LAYOUT_IN_SCREEN in PhoneWindow for
        // exactly this. Returning 0 here covers every consumer at once --
        // ViewCanvasDriver's measure, its layout, and the bar painter itself --
        // rather than four separate call-site edits that could drift apart.
        if (android.view.Window.macapkFullscreen()) return 0;
        // v1.0.939: WindowInsetsController.hide(statusBars()) means the same
        // thing FLAG_FULLSCREEN does -- the bar is gone, so it costs no inset.
        // Two APIs, one state, one answer.
        if (android.view.Window.macapkBarHidden(
                android.view.WindowInsets.Type.statusBars())) return 0;
        return heightPxRaw(root);
    }

    /** The status_bar_height DIMENSION, with no window-flag gate.
     *
     *  v1.0.939 split this out of heightPx because the two are different
     *  questions and AOSP answers them from different places. The dimension is
     *  a property of the DISPLAY (DisplayPolicy reads
     *  com.android.internal.R.dimen.status_bar_height once per rotation, and
     *  Configuration.screenHeightDp / DisplayInfo's nominal app sizes subtract
     *  it whether or not the foreground window happens to be fullscreen);
     *  heightPx above is the inset THIS window gets, which a fullscreen window
     *  legitimately drops to zero.
     *
     *  The same split is what lets WindowInsets report insets.statusBars = 0
     *  while insetsIgnoringVisibility.statusBars stays 24dp, which is exactly
     *  the distinction those two APIs exist to draw. */
    public static int heightPxRaw(View root) {
        // v1.0.1646: with no status bar at all (the default since v1.0.1627) the
        // display has none to subtract. AOSP: DisplayPolicy sets
        // mStatusBarHeightForRotation[*] = 0 when !hasStatusBar()
        // (android13 DisplayPolicy.java:1855-1869; android14 builds the decor insets
        // only from windows that exist, :1312-1327 / :1862-1880). Reporting 24dp for a
        // bar that is not there made Configuration.screenHeightDp and the stable
        // insets disagree with the window. MACAPK_PHONE_APP_WINDOW=0 restores it.
        if (!systemBarsEnabled() && sPhoneAppWindow) return 0;
        float density = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            if (root != null && root.getResources() != null
                && root.getResources().getDisplayMetrics() != null
                && root.getResources().getDisplayMetrics().density > 0f) {
                density = root.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}
        return Math.round(24f * density);
    }

    /** Bottom system-navigation strip height in host pixels. This is
     *  the reserved area at the very bottom of the surface where the
     *  Android gesture-nav pill handle is painted. Activity content
     *  (rootView) is sized to EXCLUDE this strip -- rootView.bottom =
     *  surfaceH - navBarInsetPx -- so the Activity's
     *  BottomNavigationView anchors to the strip's top edge, not the
     *  screen's.
     *
     *  AOSP-correct WindowInsets layering: the OS owns the bar regions
     *  at the top (status) and bottom (system nav); the activity's
     *  window lives between them.
     *
     *  v1.0.867 (owner call): THREE-BUTTON nav, matching the emulator --
     *  "I want the 3 buttons at the bottom like the emulator, not the
     *  nav pill." So this is AOSP's `navigation_bar_height` = **48dp**
     *  (core/res/res/values/dimens.xml:62, fetched 2026-08-03), not the
     *  24dp gesture bar the 2026-06-14 owner pick used.
     *
     *  MEASURED against the reference AVD (808x1796 @ density 2.0, so
     *  48dp = 96px): the emulator's nav glyphs have their vertical centre
     *  at y=1747.5, which places the bar at 1700..1796 -- exactly 96px.
     *  That confirms 48dp rather than leaving it to recall.
     *
     *  NOTE the knock-on the 2026-06-14 pick was avoiding: a 48dp strip
     *  pushes the app's own bottom nav up by 48px versus the pill. That is
     *  what a real 3-button device does, and it is what the emulator
     *  references we score against show. */
    public static int navBarInsetPx(View root) {
        // v1.0.1627: removed by default (see systemBarsEnabled). Identical to
        // the WindowInsetsController.hide(navigationBars()) answer below --
        // the bar is GONE, not merely covered, so it reserves nothing and
        // nothing slides under anything. The BACK affordance lives in the
        // native title bar now.
        if (!systemBarsEnabled()) return 0;
        float density = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            if (root != null && root.getResources() != null
                && root.getResources().getDisplayMetrics() != null
                && root.getResources().getDisplayMetrics().density > 0f) {
                density = root.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}
        // v1.0.939: hidden by WindowInsetsController.hide(navigationBars()) is
        // genuinely GONE, so it reserves nothing. This is NOT the v1.0.876
        // edge-to-edge trap: there the bars stay VISIBLE and the app is expected
        // to re-apply the insets it now owns, which we cannot deliver because we
        // dispatch no WindowInsets, so dropping the strip slid content under a
        // bar that was still painted. Here the bar is removed, so there is
        // nothing to slide under.
        if (android.view.Window.macapkBarHidden(
                android.view.WindowInsets.Type.navigationBars())) return 0;
        // 48dp = AOSP navigation_bar_height (3-button). The 24dp value is
        // the GESTURE bar, which we no longer draw.
        return Math.round(48f * density);
    }

    /** The display density (px per dp) every bar dimension in this file scales
     *  by. Reads the View's own DisplayMetrics when there is one, else the
     *  device density (DisplayMetrics.macapkDeviceDensity: the launch screen's
     *  backing scale, 2.0 on Retina, 1.0 on a 1x monitor). Factored
     *  out for the position predicate below, which needs a density with no View
     *  in hand; the older accessors keep their own inline copies so this change
     *  cannot perturb them. */
    private static float macapkDensity(View root) {
        try {
            if (root != null && root.getResources() != null
                && root.getResources().getDisplayMetrics() != null
                && root.getResources().getDisplayMetrics().density > 0f) {
                return root.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}
        return android.util.DisplayMetrics.macapkDeviceDensity();
    }

    // ----------------------------------------------------------------------
    // WHICH EDGE THE NAVIGATION BAR OCCUPIES.
    //
    // AOSP does NOT keep the navigation bar at the bottom in landscape on a
    // phone -- it moves it to the display's RIGHT edge (or LEFT at
    // ROTATION_270), where it costs the app WIDTH instead of HEIGHT:
    //
    //   DisplayPolicy.navigationBarPosition (aosp-mirror/platform_frameworks_base
    //   android11-release, services/core/java/com/android/server/wm/
    //   DisplayPolicy.java:3164-3173, fetched 2026-08-19):
    //       if (navigationBarCanMove() && displayWidth > displayHeight) {
    //           if (displayRotation == Surface.ROTATION_270) return NAV_BAR_LEFT;
    //           else if (displayRotation == Surface.ROTATION_90) return NAV_BAR_RIGHT;
    //       }
    //       return NAV_BAR_BOTTOM;
    //
    //   ...and the frame it produces (:1859-1877):
    //       final int left = cutoutSafeUnrestricted.right
    //               - getNavigationBarWidth(rotation, uiMode);
    //       navigationFrame.set(left, 0, displayFrames.mUnrestricted.right, displayHeight);
    //       dockFrame.right = displayFrames.mRestricted.right = left;
    //
    // MEASURED on the live AVD (API 30 resized to our canvas, 808x1796 @ 320dpi,
    // PlayTube foreground, rotated to landscape, 2026-08-19):
    //       cur=1796x808  app=1700x808
    //       mNavigationBarCanMove=true  mNavigationBarPosition=2 (NAV_BAR_RIGHT)
    //       ITYPE_NAVIGATION_BAR frame=[1700,0][1796,808] visible=true
    //       PlayTube window content=[0,60][1700,808]
    // The app keeps the FULL 808px height and loses 96px of WIDTH; nothing is
    // taken off the bottom. We took 96px off the HEIGHT, so a rotated video --
    // the one thing on screen that is height-bound in landscape -- lost 12.6%
    // of its height and 27% of its area (owner, 2026-08-19: "playtube still
    // shows status bar and nav bar when rotated ... it covers up the video").
    //
    // The BAR IS NOT HIDDEN, and hiding it would be the wrong fix: PlayTube
    // asks for no immersive/fullscreen bit anywhere (0 of its 2,039 classes
    // reference setSystemUiVisibility / WindowInsetsController.hide /
    // setDecorFitsSystemWindows, and the device reports
    // mSystemUiVisibility=0x2110 = LIGHT_STATUS|LIGHT_NAV|LAYOUT_STABLE), so a
    // real device shows both bars too. Only the EDGE is wrong.
    //
    // THICKNESS IS UNCHANGED. core/res/res/values/dimens.xml (android11-release)
    // :49 navigation_bar_height=48dp and :53 navigation_bar_width=48dp are the
    // same number, so navBarInsetPx answers for either edge.
    //
    // THE STATUS BAR DOES NOT MOVE. Same source, :38/41:
    // status_bar_height_landscape aliases status_bar_height_portrait, and the
    // device measurement above has ITYPE_STATUS_BAR frame=[0,0][1796,60] --
    // full width, at the top, in landscape. contentInsetTopPx is untouched.
    // ----------------------------------------------------------------------

    /** AOSP WindowManagerPolicyConstants:53-56 (android11-release). */
    public static final int NAV_BAR_INVALID = -1;
    public static final int NAV_BAR_LEFT    = 1 << 0;
    public static final int NAV_BAR_RIGHT   = 1 << 1;
    public static final int NAV_BAR_BOTTOM  = 1 << 2;

    /** BISECT GATE for the side-navigation-bar work, default ON.
     *
     *  `MACAPK_NAV_BAR_SIDE=0` forces navBarPosition() back to NAV_BAR_BOTTOM,
     *  which restores the pre-change geometry EXACTLY in one run (every
     *  consumer routes through that one predicate). CLAUDE.md prefers a
     *  labelled gate to a revert, and this change moves every pixel a
     *  LANDSCAPE app draws -- hcr181 in the protected sweep included -- so the
     *  next session needs a one-run A/B rather than a re-derived boundary. */
    private static volatile Boolean sNavSideEnabled;
    public static boolean macapkNavSideEnabled() {
        Boolean v = sNavSideEnabled;
        if (v == null) {
            boolean on = true;
            try {
                String s = System.getenv("MACAPK_NAV_BAR_SIDE");
                if (s != null && (s.equals("0") || s.equalsIgnoreCase("false"))) {
                    on = false;
                    NativeLog.i(TAG, "MACAPK_NAV_BAR_SIDE=0 -- navigation bar "
                        + "pinned to the BOTTOM in every orientation "
                        + "(pre-v1.0.997 geometry; AOSP moves it to the right "
                        + "edge in landscape on a phone-class display)");
                }
            } catch (Throwable ignored) {}
            v = Boolean.valueOf(on);
            sNavSideEnabled = v;
        }
        return v.booleanValue();
    }

    /** The DISPLAY's density, deliberately not a View's.
     *
     *  It is the device density (DENSITY_DEVICE_STABLE / 160, fixed for the
     *  process at the launch screen's backing scale since v1.0.2187; it was a
     *  literal 2.0), the same number DisplayMetrics.setToDefaults and Boot's
     *  Configuration carry.
     *
     *  Read from here rather than from a caller's View because
     *  mNavigationBarCanMove is a DisplayContent field in AOSP, and because a
     *  View-derived density would let the PAINTER (which is handed a root) and
     *  Display's decor arithmetic (which passes null) reach different answers
     *  about which edge the bar is on. That is precisely the
     *  two-sources-disagree failure this whole change exists to avoid. */
    private static final float MACAPK_DISPLAY_DENSITY =
        android.util.DisplayMetrics.macapkDeviceDensity();

    /** AOSP DisplayPolicy.navigationBarCanMove (:724-725), whose value is set in
     *  updateConfigurationAndScreenSizeDependentBehaviors (:2898-2900):
     *      mNavigationBarCanMove = mBaseDisplayWidth != mBaseDisplayHeight
     *              && res.getBoolean(R.bool.config_navBarCanMove);
     *
     *  The resource is a QUALIFIED boolean, and the qualifier IS the
     *  phone-vs-tablet test -- there is nothing to derive here:
     *      core/res/res/values/config.xml:3232        true
     *      core/res/res/values-sw600dp/config.xml:47  false
     *  (both fetched from aosp-mirror android11-release, 2026-08-19). So the
     *  predicate is smallestScreenWidthDp < 600, expressed against the PHYSICAL
     *  display because that is what mBaseDisplayWidth/Height are.
     *
     *  Deliberately NOT read from Configuration.smallestScreenWidthDp: Boot
     *  derives that from screenHeightDp, which already subtracts the navigation
     *  decor -- reading it here would make the bar's position depend on the
     *  bar's own inset. */
    public static boolean navBarCanMove() {
        try {
            final int wpx = android.view.Display.macapkRealWidthPx();
            final int hpx = android.view.Display.macapkRealHeightPx();
            if (wpx <= 0 || hpx <= 0 || wpx == hpx) return false;
            final int swDp = Math.round(Math.min(wpx, hpx) / MACAPK_DISPLAY_DENSITY);
            return swDp < 600;
        } catch (Throwable t) {
            return false;
        }
    }


    /** Which display edge the navigation bar occupies. ONE source of truth: the
     *  window box, the painter, the hit rects, Display's decor arithmetic and
     *  the WindowInsets we report all ask this and nothing derives it again.
     *  That matters here specifically -- v1.0.996 was two size publications
     *  disagreeing, and a painter that computed "landscape" from a surface
     *  width while the layout computed it from the display would reproduce that
     *  bug exactly.
     *
     *  NAV_BAR_LEFT IS DECLARED BUT NEVER RETURNED, and that is deliberate.
     *  AOSP maps ROTATION_270 to LEFT, but Display.getRotation() cannot
     *  distinguish 90 from 270 (it has only a width>height test -- see its own
     *  comment at Display.java:26-36, and cocoa_bridge's g_phone_w/g_phone_h
     *  simply swap), so we have no honest way to answer 270. Inventing one
     *  would be a comfortable lie; consumers that would need an x-origin shift
     *  for a left bar say so at their site. */
    public static int navBarPosition(View root) {
        // `root` is UNUSED today, and deliberately so: the answer is a property
        // of the DISPLAY, not of any window. It is in the signature to match
        // every other accessor in this file, so a caller cannot be tempted to
        // cache the answer per-view. Do not start reading a density or a
        // Configuration off it -- see MACAPK_DISPLAY_DENSITY.
        if (!macapkNavSideEnabled()) return NAV_BAR_BOTTOM;
        try {
            if (!navBarCanMove()) return NAV_BAR_BOTTOM;
            // The same width>height test Display.getRotation() uses to answer
            // ROTATION_90, and the same one AOSP applies here.
            if (android.view.Display.macapkRealWidthPx()
                    > android.view.Display.macapkRealHeightPx()) {
                return NAV_BAR_RIGHT;
            }
        } catch (Throwable ignored) {}
        return NAV_BAR_BOTTOM;
    }

    /** navBarInsetPx split onto the BOTTOM edge -- the raw dimension with no
     *  decorFitsSystemWindows gate, matching what navBarInsetPx itself has
     *  always been. 0 when the bar sits on a side (or is hidden). */
    public static int navBarInsetBottomPx(View root) {
        return navBarPosition(root) == NAV_BAR_BOTTOM ? navBarInsetPx(root) : 0;
    }
    /** navBarInsetPx split onto the RIGHT edge. AOSP takes it out of the app's
     *  WIDTH here (DisplayPolicy.java:1859-1877). */
    public static int navBarInsetRightPx(View root) {
        return navBarPosition(root) == NAV_BAR_RIGHT ? navBarInsetPx(root) : 0;
    }
    /** navBarInsetPx split onto the LEFT edge. Always 0 today -- navBarPosition
     *  never returns NAV_BAR_LEFT (see its javadoc). Declared so the four-edge
     *  arithmetic below is written once and correctly, not so a caller can
     *  pretend seascape works. */
    public static int navBarInsetLeftPx(View root) {
        return navBarPosition(root) == NAV_BAR_LEFT ? navBarInsetPx(root) : 0;
    }

    // ----------------------------------------------------------------------
    // THE APP WINDOW FRAME (v1.0.996). One place answers "where does the app's
    // window live inside the display surface", because four consumers need the
    // SAME answer and they used to derive it separately: ViewCanvasDriver's
    // measure, its layout, the ANGLE render layer's geometry, and the surface
    // size we publish to a SurfaceView's renderer.
    //
    // AOSP CONTRACT, measured on the live AVD (API 30, 1080x2280 @ 320dpi,
    // 2026-08-17 -- see docs/fullscreen-game-window-geometry_2026-08-17.md):
    //
    //     init=1080x2280 320dpi cur=1080x2280 app=1080x2184
    //     InsetsSource ITYPE_NAVIGATION_BAR frame=[0,2184][1080,2280] visible=true
    //
    // `app=` is 96px shorter than `cur=`: the app window frame EXCLUDES the
    // visible navigation bar. A real device NEVER composites the nav bar over
    // an app's own surface -- the bar's window sits BESIDE the app's, and the
    // app is handed a surface that stops where the bar starts. (The status bar
    // OVERLAYS, which is why the top inset is flag-gated by heightPx above and
    // the bottom one is not.)
    //
    // WHAT THIS FIXES. We handed GL games the FULL drawable and then painted
    // the 96px 3-button strip on the overlay CALayer on top of the bottom 96
    // rows the game had legitimately rendered into (owner, 2026-08-17: "HCR
    // 1.8 runs great but ... the android nav bar is showing at the bottom even
    // though its a full screen app"). Two size publications disagreed -- native
    // drawable 3244x1796 vs laid-out window box 3244x1700 -- the game trusted
    // one and the painter the other. There is no app-side request to honour
    // here and none to blame: hillclimb's build contains ZERO references to
    // setSystemUiVisibility (targetSdk 16, immersive did not exist), so it
    // never asks for anything. The window frame is the display's business.
    // ----------------------------------------------------------------------

    /** The app window's TOP content inset in host px.
     *
     *  Alias of heightPx, kept as its own name because the window-geometry
     *  consumers are asking about the WINDOW FRAME rather than about the
     *  status bar as such -- and a future top-anchored inset source (a display
     *  cutout, a caption bar) belongs here rather than inside the status bar's
     *  own dimension. */
    public static int contentInsetTopPx(View root) { return heightPx(root); }

    /** The app window's BOTTOM content inset in host px.
     *
     *  Same `decorFitsSystemWindows` gate ViewCanvasDriver's measure/layout
     *  uses (v1.0.865): AOSP reserves the bar space by default and only lets
     *  the app draw behind it when the app asks -- `WindowCompat
     *  .setDecorFitsSystemWindows(window, false)`, i.e. what
     *  `EdgeToEdge.enable()` calls. An edge-to-edge app therefore keeps the
     *  FULL surface and the bar composites over it, which is exactly what a
     *  device does for it. navBarInsetPx itself already returns 0 when the app
     *  hid the bar outright (immersive / WindowInsetsController.hide), so every
     *  immersive game -- gdlite, mindustry, unciv, subway, crossy -- gets a
     *  zero inset here and is byte-identical to the pre-v1.0.996 geometry. */
    public static int contentInsetBottomPx(View root) {
        return android.view.Window.macapkDecorFitsSystemWindows()
                ? navBarInsetBottomPx(root) : 0;
    }

    /** The app window's RIGHT content inset in host px.
     *
     *  Non-zero exactly when navBarPosition() == NAV_BAR_RIGHT, i.e. a
     *  landscape phone-class display with a visible navigation bar. AOSP takes
     *  the bar out of the app's WIDTH there:
     *      dockFrame.right = displayFrames.mRestricted.right = left;
     *  (DisplayPolicy.java:1859-1877, android11-release), and the live AVD
     *  duly reports app=1700x808 inside cur=1796x808 with the app's content
     *  frame ending at x=1700 and running to the BOTTOM edge.
     *
     *  Same decorFitsSystemWindows gate as the bottom inset, and navBarInsetPx
     *  still returns 0 for a hidden bar, so every immersive app gets 0 on all
     *  four edges and is byte-identical to the pre-change geometry. */
    public static int contentInsetRightPx(View root) {
        return android.view.Window.macapkDecorFitsSystemWindows()
                ? navBarInsetRightPx(root) : 0;
    }

    /** The app window's LEFT content inset in host px. Always 0 today --
     *  navBarPosition never returns NAV_BAR_LEFT because we cannot tell
     *  ROTATION_270 from ROTATION_90. */
    public static int contentInsetLeftPx(View root) {
        return android.view.Window.macapkDecorFitsSystemWindows()
                ? navBarInsetLeftPx(root) : 0;
    }

    /** BISECT GATE for the v1.0.996 window-box work, default ON.
     *
     *  `MACAPK_WINDOW_BOX=0` makes the three NEW consumers -- the ANGLE render
     *  layer's geometry, the surface size published to a SurfaceView's
     *  renderer, and the renderer-direct touch shift -- behave exactly as they
     *  did before v1.0.996: full display drawable, no window box. It does NOT
     *  touch ViewCanvasDriver's measure/layout, which has laid the rootView out
     *  inside the same box since v1.0.865 and is not part of this change. So
     *  gate-OFF is the honest "before" state and one run can A/B it, which
     *  beats a revert (CLAUDE.md, "prefer a labelled bisect gate").
     *
     *  Falsifier for a regression report: with the gate ON, hcr181's GL surface
     *  is 3244x1700 and the 96px nav strip lands on window background; with it
     *  OFF, the surface is 3244x1796 and the strip is painted over the game's
     *  own bottom rows (the owner's 2026-08-17 report). */
    private static volatile Boolean sWindowBoxEnabled;
    public static boolean macapkWindowBoxEnabled() {
        Boolean v = sWindowBoxEnabled;
        if (v == null) {
            boolean on = true;
            try {
                String s = System.getenv("MACAPK_WINDOW_BOX");
                if (s != null && (s.equals("0") || s.equalsIgnoreCase("false"))) {
                    on = false;
                    NativeLog.i(TAG, "MACAPK_WINDOW_BOX=0 -- app-window box "
                        + "DISABLED; GL surfaces get the full display drawable "
                        + "and the nav strip composites over them (pre-v1.0.996)");
                }
            } catch (Throwable ignored) {}
            v = Boolean.valueOf(on);
            sWindowBoxEnabled = v;
        }
        return v.booleanValue();
    }

    /** The gated top inset -- what the WINDOW-BOX consumers read. */
    public static int windowBoxInsetTopPx(View root) {
        return macapkWindowBoxEnabled() ? contentInsetTopPx(root) : 0;
    }
    /** The gated bottom inset -- what the WINDOW-BOX consumers read. */
    public static int windowBoxInsetBottomPx(View root) {
        return macapkWindowBoxEnabled() ? contentInsetBottomPx(root) : 0;
    }
    /** The gated left inset -- what the WINDOW-BOX consumers read. */
    public static int windowBoxInsetLeftPx(View root) {
        return macapkWindowBoxEnabled() ? contentInsetLeftPx(root) : 0;
    }
    /** The gated right inset -- what the WINDOW-BOX consumers read. */
    public static int windowBoxInsetRightPx(View root) {
        return macapkWindowBoxEnabled() ? contentInsetRightPx(root) : 0;
    }

    /** All FOUR insets packed as `(top << 48) | (bottom << 32) | (left << 16)
     *  | right`, for the single JNI call the Cocoa compositor makes per frame
     *  to size the ANGLE render layer.
     *
     *  WIDENED from an int (top/bottom only) when the navigation bar learned to
     *  sit on a side edge: a two-field answer physically cannot carry a
     *  horizontal inset, and the failure mode of leaving it at two is precisely
     *  the v1.0.996 bug -- the render layer would stay full-width while the view
     *  tree was laid out narrower, i.e. two size publications disagreeing.
     *  There is deliberately no int variant left behind: two accessors for one
     *  question is how they drift.
     *
     *  PULLED, not pushed, deliberately. The inset state is a pure function of
     *  the display density and the app's own window flags, but WHEN those flags
     *  are set is up to the app: ActivityHost runs the activity lifecycle on
     *  `macapk-activity` while the Cocoa runloop is already spinning
     *  (Boot.run step 5/6), so an Activity can set FLAG_FULLSCREEN before,
     *  during, or after the first drawInMTKView. A pull can never be stale by
     *  a frame and needs no invalidation protocol; a push would need both. */
    public static long macapkContentInsetsPacked4() {
        int top = 0, bottom = 0, left = 0, right = 0;
        try { top    = windowBoxInsetTopPx(null);    } catch (Throwable ignored) {}
        try { bottom = windowBoxInsetBottomPx(null); } catch (Throwable ignored) {}
        try { left   = windowBoxInsetLeftPx(null);   } catch (Throwable ignored) {}
        try { right  = windowBoxInsetRightPx(null);  } catch (Throwable ignored) {}
        if (top    < 0 || top    > 0xffff) top    = 0;
        if (bottom < 0 || bottom > 0xffff) bottom = 0;
        if (left   < 0 || left   > 0xffff) left   = 0;
        if (right  < 0 || right  > 0xffff) right  = 0;
        return ((long) top    << 48)
             | ((long) bottom << 32)
             | ((long) left   << 16)
             |  (long) right;
    }

    /** The app window's height in host px inside a `surfaceHpx`-tall display
     *  surface -- i.e. the height a SurfaceView's renderer must be told.
     *
     *  AOSP's own mechanism is the VIEW's laid-out size: SurfaceView.updateSurface
     *  (core/java/android/view/SurfaceView.java:1217-1220, aosp-mirror/master,
     *  fetched 2026-08-17) reads
     *
     *      int myWidth = mRequestedWidth;
     *      if (myWidth <= 0) myWidth = getWidth();
     *      int myHeight = mRequestedHeight;
     *      if (myHeight <= 0) myHeight = getHeight();
     *
     *  and hands exactly that to `c.surfaceChanged(mSurfaceHolder, mFormat,
     *  myWidth, myHeight)` (:1369). For a window-filling surface -- which is
     *  the entire GL population here -- the laid-out size IS this window box,
     *  because ViewCanvasDriver lays the rootView out to (0, top)-(w, bottom)
     *  from the very same two insets. Measured on hillclimb: `root=0,0-3244,1700`
     *  and the app's own Cocos2dxGLSurfaceView.onSizeChanged pushed
     *  `setScreenWidthAndHeight(3244x1700)` unprompted.
     *
     *  We take the WINDOW FRAME rather than reading getWidth()/getHeight()
     *  because the frame cannot be corrupted by a bad layout pass and the
     *  laid-out size can: v1.0.742 ("HCR renders in the bottom-left corner")
     *  was precisely a wrong overlay size re-laying-out the GL view and
     *  shrinking the engine's viewport to a corner rect. Same value, one fewer
     *  way to be wrong.
     *
     *  Fails SAFE: any inset arithmetic that would leave a zero or negative
     *  surface returns the full height instead. A guest handed a 0-height
     *  surface runs nativeInit(w, 0) -> zero GL viewport -> black at 60fps. */
    public static int contentBoxHeightPx(View root, int surfaceHpx) {
        if (surfaceHpx <= 0) return surfaceHpx;
        int top = windowBoxInsetTopPx(root);
        int bottom = windowBoxInsetBottomPx(root);
        if (top < 0) top = 0;
        if (bottom < 0) bottom = 0;
        int h = surfaceHpx - top - bottom;
        return h > 0 ? h : surfaceHpx;
    }

    /** The app window's WIDTH in host px inside a `surfaceWpx`-wide display
     *  surface -- the horizontal half of contentBoxHeightPx, and required for
     *  exactly the same reason.
     *
     *  Before the navigation bar could sit on a side edge this was always
     *  `surfaceWpx` and the renderer was told so. Once it can, telling the
     *  engine the full display width while the ANGLE layer is 96px narrower
     *  reproduces the v1.0.742 failure directly: the guest sets a viewport
     *  wider than its backbuffer and renders into a rect that does not exist.
     *  Both publications must come from this one accessor.
     *
     *  Fails SAFE to the full width for the same reason the height does. */
    public static int contentBoxWidthPx(View root, int surfaceWpx) {
        if (surfaceWpx <= 0) return surfaceWpx;
        int left = windowBoxInsetLeftPx(root);
        int right = windowBoxInsetRightPx(root);
        if (left < 0) left = 0;
        if (right < 0) right = 0;
        int w = surfaceWpx - left - right;
        return w > 0 ? w : surfaceWpx;
    }

    /** Left-edge gesture-back hot-zone width (host px): a touch that
     *  starts within this many px of the left edge and drags right is a
     *  back swipe. ~20dp matches AOSP's EdgeBackGestureHandler inset. */
    public static int edgeBackInsetPx(View root) {
        float density = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            if (root != null && root.getResources() != null
                && root.getResources().getDisplayMetrics() != null
                && root.getResources().getDisplayMetrics().density > 0f) {
                density = root.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}
        return Math.round(20f * density);
    }

    // ----------------------------------------------------------------------
    // AOSP DecorView.calculateBarColor -- the ONE mechanism that decides both
    // system bars' colours. Everything else in this file is painting; the
    // COLOUR question is answered here and nowhere else.
    //
    // WHAT THIS REPLACES (the v1.0.134 "blend"). paint() used to sample the
    // dominant colour of the content row 2px below the bar and copy it up.
    // That has no AOSP counterpart, and measurement on 2026-08-08 showed it was
    // not a fallback but the ONLY live path: our bar colour equalled the sampled
    // row on 15 of 15 fleet apps, so the `colorBackground` branch below it never
    // executed. It painted anuto WHITE where the device paints BLACK,
    // tobsolitaire INDIGO, sfsolitaire GREEN and fdroid PINK -- in each case the
    // app's own content, faithfully reproduced in the wrong place. The device
    // does the opposite: on fdroid the AVD paints a solid #f9f9ff bar down to
    // y=59 and the pink #ffb9b9 content begins at y=60.
    // ----------------------------------------------------------------------

    // WindowManager.LayoutParams flags, public API constants.
    private static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
    private static final int FLAG_TRANSLUCENT_STATUS           = 0x04000000;
    private static final int FLAG_TRANSLUCENT_NAVIGATION       = 0x08000000;
    /** DecorView.SCRIM_LIGHT = 0xe6ffffff (90% white). */
    private static final int MACAPK_SCRIM_LIGHT = 0xe6ffffff;
    /** @color/system_bar_background_semi_transparent. UNVERIFIED against source:
     *  DecorView only names the resource. Reachable ONLY from branch 1
     *  (FLAG_TRANSLUCENT_*) and branch 3's dark case, and no fleet app takes
     *  either, so nothing measured today depends on it. Pin it before relying. */
    private static final int MACAPK_SEMI_TRANSPARENT_BAR = 0x66000000;
    private static final int MACAPK_APPEARANCE_LIGHT_STATUS_BARS     = 0x8;
    private static final int MACAPK_APPEARANCE_LIGHT_NAVIGATION_BARS = 0x10;
    /** Tells "resolved to X" from "fell back": the resolvers return the fallback
     *  on failure and are otherwise indistinguishable. Not a colour any theme
     *  declares. */
    private static final int MACAPK_UNSET_COLOR = 0x00000001;
    /** Same flag as android.app.Activity's ActivityWindow bar-colour gate. */
    private static final boolean MACAPK_WINDOW_BAR_COLORS =
            !"0".equals(System.getenv("MACAPK_WINDOW_BAR_COLORS"));

    /** AOSP DecorView.calculateBarColor, ported verbatim (Color.BLACK spelled
     *  0xFF000000, Color.alpha(c) spelled c >>> 24). Kept as its own method with
     *  AOSP's exact parameter order so a future reader can diff it line by line
     *  against the source. */
    static int macapkCalculateBarColor(int flags, int translucentFlag,
            int semiTransparentBarColor, int barColor, int appearance,
            int lightAppearanceFlag, boolean scrimTransparent) {
        if ((flags & translucentFlag) != 0) {
            return semiTransparentBarColor;
        } else if ((flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0) {
            return 0xFF000000;
        } else if (scrimTransparent && ((barColor >>> 24) == 0)) {
            boolean light = (appearance & lightAppearanceFlag) != 0;
            return light ? MACAPK_SCRIM_LIGHT : semiTransparentBarColor;
        } else {
            return barColor;
        }
    }

    /** One theme COLOUR attribute, preferring the view's OWN Context theme (the
     *  setTheme() cascade) over the static manifest-pinned anchor. This is the
     *  v1.0.897 two-anchor fix applied to the bars, and it is not optional here:
     *  5 of the 15 fleet apps have a splash or trampoline theme in the manifest,
     *  so the static anchor never enters the cascade that holds the answer. */
    private static int macapkThemeAttrColor(View v, String attr, int fallback) {
        try {
            android.content.Context c = (v != null) ? v.getContext() : null;
            android.content.res.Resources.Theme th = (c != null) ? c.getTheme() : null;
            if (th != null) {
                int r = th.resolveAttrColor(attr, fallback);
                if (r != fallback) return r;
            }
        } catch (Throwable ignored) {}
        try {
            return android.content.res.Resources.macapkThemeColor(attr, fallback);
        } catch (Throwable ignored) {}
        return fallback;
    }

    /** One theme BOOLEAN attribute, same anchor order. No new resolver is added:
     *  Resources.Theme.resolveAttrRaw already returns the raw cascade value with
     *  exactly the anchor semantics we want, so a boolean is a parse rather than
     *  new resolution logic. AOSP's TypedValue treats only the literal "true"
     *  (case-insensitive) as true for a boolean attribute. */
    private static boolean macapkThemeAttrBool(View v, String attr, boolean fallback) {
        try {
            android.content.Context c = (v != null) ? v.getContext() : null;
            android.content.res.Resources.Theme th = (c != null) ? c.getTheme() : null;
            if (th != null) {
                String raw = th.resolveAttrRaw(attr);
                if (raw != null && !raw.isEmpty()) return "true".equalsIgnoreCase(raw.trim());
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    /** What is BEHIND a bar whose colour is not fully opaque. On AOSP the bar
     *  colour views live inside the DecorView, so a transparent bar composites
     *  over the WINDOW BACKGROUND, not over the app's laid-out content (which is
     *  inset below the bar). Measured on two apps that are already correct:
     *  deskclock and fdroid both declare a transparent statusBarColor and the
     *  device paints their windowBackground (#fefbff, #f9f9ff), NOT the content
     *  beneath (fdroid's content there is pink #ffb9b9). */
    private static int macapkDecorBg(View root) {
        for (String attr : new String[]{"windowBackground", "colorBackground"}) {
            int c = macapkThemeAttrColor(root, attr, MACAPK_UNSET_COLOR);
            if (c != MACAPK_UNSET_COLOR && (c >>> 24) == 0xFF) return c;
        }
        return 0xFFFFFFFF;
    }

    /** Porter-Duff SrcOver of an ARGB source onto an opaque destination. */
    private static int macapkOver(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 0xFF) return src;
        if (sa == 0)    return 0xFF000000 | (dst & 0x00FFFFFF);
        float a = sa / 255f, ia = 1f - a;
        int r = Math.round(((src >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * ia);
        int g = Math.round(((src >>  8) & 0xFF) * a + ((dst >>  8) & 0xFF) * ia);
        int b = Math.round(( src        & 0xFF) * a + ( dst        & 0xFF) * ia);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The ARGB actually painted for one system bar. `statusBar` picks which of
     *  AOSP's two argument sets calculateBarColor gets. */
    static int macapkSystemBarColor(View root, boolean statusBar) {
        // PhoneWindow.generateLayout sets the flag only when the theme says so,
        // defaulting FALSE. The framework themes supply it (Material true, legacy
        // Theme/Holo false), which is what makes a legacy-themed app take branch
        // 2 and come out BLACK whatever colour it declares.
        int flags = 0;
        if (macapkThemeAttrBool(root, "windowDrawsSystemBarBackgrounds", false)) {
            flags |= FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        int translucentFlag = statusBar ? FLAG_TRANSLUCENT_STATUS
                                        : FLAG_TRANSLUCENT_NAVIGATION;
        if (macapkThemeAttrBool(root, statusBar ? "windowTranslucentStatus"
                                                : "windowTranslucentNavigation", false)) {
            flags |= translucentFlag;
        }
        int lightFlag = statusBar ? MACAPK_APPEARANCE_LIGHT_STATUS_BARS
                                  : MACAPK_APPEARANCE_LIGHT_NAVIGATION_BARS;
        // v1.0.939: WindowInsetsController.setSystemBarsAppearance wins over the
        // theme attribute when the app has actually called it, which is AOSP's
        // own precedence (generateLayout reads windowStatusBarColor only
        // `if (!mForcedStatusBarColor)`; the appearance bits work the same way).
        // -1 means "the app never set this bit", so the theme answers.
        int forcedLight = android.view.Window.macapkBarAppearance(lightFlag);
        boolean light = (forcedLight >= 0)
                ? (forcedLight == 1)
                : macapkThemeAttrBool(root, statusBar ? "windowLightStatusBar"
                                                      : "windowLightNavigationBar", false);
        int appearance = light ? lightFlag : 0;
        // Both PhoneWindow defaults are opaque BLACK: statusBarColor's getColor
        // default is Color.BLACK outright, and navigationBarColor's default
        // resolves through navigation_bar_default (#00808080) to
        // navigation_bar_compatible (@android:color/black). That second one is
        // why the AVD shows a black nav bar on every app that leaves the
        // attribute alone, while an app that EXPLICITLY writes
        // @android:color/transparent keeps alpha 0 and shows what is behind --
        // two cases the old code collapsed into one.
        // AOSP's mForcedStatusBarColor gate first: generateLayout reads the THEME
        // value only `if (!mForcedStatusBarColor)`, so an app that set the colour
        // in code outranks its own theme. This is not an edge case on a modern
        // fleet -- Material's own EdgeToEdgeUtils calls setStatusBarColor, and
        // AntennaPod, Mastodon and AnkiDroid all reach it.
        int declared = statusBar ? android.view.Window.macapkForcedStatusBarColor()
                                 : android.view.Window.macapkForcedNavigationBarColor();
        if (declared == android.view.Window.MACAPK_BAR_COLOR_UNSET) {
            declared = macapkThemeAttrColor(root, statusBar ? "statusBarColor"
                                                            : "navigationBarColor",
                                            MACAPK_UNSET_COLOR);
            // The comment above DESCRIBES the navigation_bar_default ->
            // navigation_bar_compatible mapping, but this read predates the API-36
            // table, whose themes all declare the #00808080 sentinel itself, so it
            // painted alpha 0 where a device paints black. Map it with the same
            // helper the Window getters use (Activity.macapkResolveNavigationBarColor,
            // android14 PhoneWindow.java:2516-2527), so painted and reported agree.
            // Sentinel half ONLY: the edge-to-edge TRANSPARENT answer stays
            // getter-only until the E2E layout half (decorFitsSystemWindows=false)
            // exists -- painting a transparent strip over a decor that still fits
            // system windows would show window background where AOSP shows app
            // content. Only visible with MACAPK_SYSTEM_BARS=1. Gate:
            // MACAPK_WINDOW_BAR_COLORS=0 restores the verbatim theme colour.
            if (!statusBar && declared != MACAPK_UNSET_COLOR && MACAPK_WINDOW_BAR_COLORS
                    && root != null) {
                int[] t = android.app.Activity.macapkNavigationBarTable(root.getContext());
                declared = android.app.Activity.macapkResolveNavigationBarColor(declared,
                        t[0] != 0, t[1], t[2], t[3] != 0, false);
            }
        }
        if (declared == MACAPK_UNSET_COLOR) declared = 0xFF000000;
        boolean scrimTransparent = macapkThemeAttrBool(root, statusBar
            ? "enforceStatusBarContrast" : "enforceNavigationBarContrast", false);

        int c = macapkCalculateBarColor(flags, translucentFlag, MACAPK_SEMI_TRANSPARENT_BAR,
                                        declared, appearance, lightFlag, scrimTransparent);
        if (ENV_MACAPK_TRACE_BARCOLOR != null) {
            android.util.Log.i("Mac.apk.BarColor", (statusBar ? "status" : "nav")
                + " flags=0x" + Integer.toHexString(flags)
                + " declared=0x" + Integer.toHexString(declared)
                + " scrimT=" + scrimTransparent
                + " -> 0x" + Integer.toHexString(c)
                + " decorBg=0x" + Integer.toHexString(macapkDecorBg(root))
                + " dim=0x" + Integer.toHexString(macapkActiveDim()));
        }
        // Not opaque: the window background shows through it.
        if ((c >>> 24) != 0xFF) c = macapkOver(c, macapkDecorBg(root));
        // ... and then the app's own dim, if a dialog is up. The bar colour views
        // are part of the DecorView, so FLAG_DIM_BEHIND dims them too; only
        // SystemUI's glyphs stay bright, because SystemUI is a separate window
        // above the dim layer. Measured on the AVD: newpipe's band is #5c1715 =
        // light_youtube_primary x 0.4, and unitconverter's is its own
        // colorPrimaryDark x 0.4.
        int dim = macapkActiveDim();
        if (dim != 0) c = macapkOver(dim, c);
        return c;
    }

    /** The active FLAG_DIM_BEHIND scrim, or 0. Uses WindowOverlayStack's own
     *  snapshot/topmostDimIndex/dimColorFor so the bar dims by EXACTLY what the
     *  content dimmed by, and so the "there is one dim, not one per dialog" rule
     *  is obeyed here for free rather than re-derived. */
    private static int macapkActiveDim() {
        try {
            java.util.List<WindowOverlayStack.Layer> ls = WindowOverlayStack.snapshot();
            int at = WindowOverlayStack.topmostDimIndex(ls);
            if (at >= 0) return WindowOverlayStack.dimColorFor(ls.get(at));
        } catch (Throwable ignored) {}
        return 0;
    }

    /** Paint the 3-button system nav strip at canvas y =
     *  (surfaceH - navBarInsetPx) to surfaceH, with three FILLED glyphs
     *  centred at 1/4, 1/2 and 3/4 of the width:
     *  ◀ (left triangle = back), ● (circle = home), ■ (rounded square =
     *  recents). Filled, not stroked -- that is what the reference AVD
     *  draws (verified by cropping its nav bar, 2026-08-03).
     *
     *  Matches the AOSP `SystemBars` 3-button layout. Generic for every
     *  APK -- no per-app skin. Bar colour comes from the theme's
     *  `navigationBarColor`; the glyph colour is derived from that bar's
     *  luminance so it can never come out invisible. */
    public static void paintSystemNavStrip(Canvas canvas, View root,
                                            int surfaceWidth, int surfaceHeight) {
        // v1.0.867 (owner call): THREE BUTTONS, like the emulator -- back
        // triangle, home circle, recents square. Replaces the v1.0.865 gesture
        // pill.
        //
        // Skipped when the app opted into edge-to-edge: there is no reserved
        // strip to paint into, and drawing over the app's own content is what
        // "edge-to-edge" explicitly asks us not to do.
        //
        // v1.0.997: the strip is not always at the bottom. On a landscape
        // phone-class display AOSP puts it on the RIGHT edge (see
        // navBarPosition), and the LAYOUT now reserves it there -- so painting
        // it at the bottom would put OS chrome over the app's own pixels while
        // leaving the reserved right column showing window background. Paint
        // and layout must read the same predicate.
        if (canvas == null || surfaceWidth <= 0) return;
        if (!android.view.Window.macapkDecorFitsSystemWindows()) return;
        // navigation_bar_height and navigation_bar_width are both 48dp
        // (core/res/res/values/dimens.xml:49/53, android11-release), so one
        // dimension serves either edge.
        int stripH = navBarInsetPx(root);
        if (stripH <= 0) return;
        final int navPos = navBarPosition(root);
        if (navPos == NAV_BAR_BOTTOM) { if (surfaceHeight <= stripH) return; }
        else                          { if (surfaceWidth  <= stripH) return; }
        float density = macapkDensity(root);
        int top = surfaceHeight - stripH;
        try {
            // Bar colour = the theme's `android:navigationBarColor` when it
            // declares an OPAQUE one, else BLACK.
            //
            // Black is not a guess. AOSP's default is
            //   themes.xml:194  navigationBarColor -> @color/navigation_bar_default
            //   colors.xml:589  navigation_bar_default = #00808080
            // i.e. fully TRANSPARENT (alpha 0) -- the colour is a no-op and
            // SystemUI paints the bar itself. We have no SystemUI, so we have to
            // pick what it actually renders, and the reference AVD renders solid
            // black on every app that leaves it alone (verified across wikipedia,
            // tobsolitaire, anuto, bobball, frozenbubble, sfsolitaire). An app
            // that DOES declare one gets it honoured -- opencalculator declares
            // `android:navigationBarColor = @color/background_color` (#fff) and
            // the emulator duly shows it a LIGHT bar.
            //
            // A sentinel is needed to tell "resolved to X" from "fell back",
            // because macapkThemeColor returns the fallback on failure and
            // cannot otherwise be distinguished. 0x00000001 is not a colour any
            // theme declares.
            // v1.0.900: AOSP DecorView.calculateBarColor (see
            // macapkSystemBarColor). This REPLACES a two-branch approximation
            // ("opaque declared colour, else BLACK") that reached the right
            // answer for the wrong reason. Its own comment called black "not a
            // guess" and cited the AVD rendering black on every app that leaves
            // the attribute alone -- correct observation, missing mechanism.
            // PhoneWindow.generateLayout maps an UNSET navigationBarColor through
            // navigation_bar_default (#00808080) to navigation_bar_compatible
            // (@android:color/black), so "unset" really is opaque black on a
            // device, while an app that EXPLICITLY declares
            // @android:color/transparent keeps alpha 0 and shows the window
            // background. The old test collapsed those two cases into one and
            // painted black for both, which is why deskclock / fdroid /
            // antennapod got a black strip against the AVD's light one.
            int barColor = macapkSystemBarColor(root, false);
            Paint bg = new Paint();
            bg.setAntiAlias(false);
            bg.setColor(barColor);
            if (navPos == NAV_BAR_BOTTOM) {
                canvas.drawRect(0, top, surfaceWidth, surfaceHeight, bg);
            } else {
                // NAV_BAR_RIGHT: [displayWidth - width, 0, displayWidth,
                // displayHeight], mirroring AOSP's navigationFrame.set(left, 0,
                // mUnrestricted.right, displayHeight) (DisplayPolicy:1861-1862).
                // The reference AVD's strip is x1700..1796 over the FULL height.
                canvas.drawRect(surfaceWidth - stripH, 0, surfaceWidth, surfaceHeight, bg);
            }

            // Glyph colour by BAR LUMINANCE, not by the app theme's polarity.
            // AOSP lets an app ask for dark glyphs via
            // `android:windowLightNavigationBar`, and modern SystemUI ALSO
            // enforces contrast when the app gets it wrong. Deriving it from the
            // bar we just painted gets the same result without depending on an
            // attribute most apps never set -- and it cannot produce the one
            // unacceptable outcome, invisible glyphs on a same-coloured bar.
            int r = (barColor >> 16) & 0xFF, g = (barColor >> 8) & 0xFF, b = barColor & 0xFF;
            // Rec.709 luma, the standard perceptual weighting.
            double luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
            int glyphColor = luma < 0.5 ? 0xFFFFFFFF : 0xFF1F1F1F;

            Paint gp = new Paint();
            gp.setAntiAlias(true);
            gp.setStyle(Paint.Style.FILL);
            gp.setColor(glyphColor);

            // Geometry measured off the reference AVD (see navBarInsetPx): the
            // three glyphs sit at 1/4, 1/2 and 3/4 of the bar's LONG axis,
            // ~30px wide and ~32px tall at density 2 -- i.e. ~15dp x 16dp --
            // centred across its thickness. Written in dp so it holds at any
            // density.
            float triW = 14f * density, triH = 16f * density;
            float dia   = 15f * density;
            float sq    = 15f * density, sqR = 2f * density;

            if (navPos == NAV_BAR_BOTTOM) {
                float cy = top + stripH / 2f;
                // BACK: filled triangle pointing LEFT.
                float bx = surfaceWidth * 0.25f;
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(bx - triW / 2f, cy);
                tri.lineTo(bx + triW / 2f, cy - triH / 2f);
                tri.lineTo(bx + triW / 2f, cy + triH / 2f);
                tri.close();
                canvas.drawPath(tri, gp);

                // HOME: filled circle.
                canvas.drawCircle(surfaceWidth * 0.5f, cy, dia / 2f, gp);

                // RECENTS: filled, slightly-rounded square.
                float rx = surfaceWidth * 0.75f;
                canvas.drawRoundRect(rx - sq / 2f, cy - sq / 2f,
                                     rx + sq / 2f, cy + sq / 2f, sqR, sqR, gp);
            } else {
                // NAV_BAR_RIGHT. The order is NOT a naive rotation of the row
                // above, and the BACK glyph is NOT rotated with it. Both facts
                // are MEASURED off the reference AVD capture
                // (scratchpad/avd_playtube_landscape_1796x808.png, right 96
                // columns of an 1796x808 landscape screen, re-measured
                // independently 2026-08-19):
                //
                //   y189..218  ink width a constant 30px for 26 of 30 rows,
                //              corners clipped by 2px  -> ROUNDED SQUARE (recents)
                //   y390..417  ink width 8 -> 28 -> 8, symmetric  -> CIRCLE (home)
                //   y587..618  ink RIGHT edge pinned at x=1759 for every row
                //              while the LEFT edge sweeps 1757 -> 1732 -> 1757
                //              -> TRIANGLE, flat edge vertical on the RIGHT,
                //              apex pointing LEFT (the same shape the bottom bar
                //              draws, unrotated)
                //
                // So top-to-bottom is RECENTS, HOME, BACK: the horizontal row
                // [back, home, recents] rotates with the display, which puts
                // what was leftmost at the BOTTOM.
                float cx = surfaceWidth - stripH / 2f;
                // RECENTS at 1/4 height. Measured centre 203.5 vs 808*0.25=202.
                float ry = surfaceHeight * 0.25f;
                canvas.drawRoundRect(cx - sq / 2f, ry - sq / 2f,
                                     cx + sq / 2f, ry + sq / 2f, sqR, sqR, gp);
                // HOME at 1/2. Measured centre 403.5 vs 808*0.50=404.
                canvas.drawCircle(cx, surfaceHeight * 0.5f, dia / 2f, gp);
                // BACK at 3/4, apex still LEFT. Measured centre 602.5 vs
                // 808*0.75=606.
                float by = surfaceHeight * 0.75f;
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(cx - triW / 2f, by);
                tri.lineTo(cx + triW / 2f, by - triH / 2f);
                tri.lineTo(cx + triW / 2f, by + triH / 2f);
                tri.close();
                canvas.drawPath(tri, gp);
            }
        } catch (Throwable ignored) {}
    }

    /** Hit rect (surface px, top-origin) of one nav-bar glyph, or null when
     *  there is no reserved strip. `slot` is 0=back, 1=home, 2=recents.
     *
     *  The touch target is the full third of the bar, not the glyph bounds --
     *  AOSP's nav buttons fill their slot so a near-miss still registers, and a
     *  15dp visual target would be far below the 48dp minimum. */
    public static int[] navButtonHitRect(View root, int surfaceW, int surfaceH, int slot) {
        if (surfaceW <= 0 || slot < 0 || slot > 2) return null;
        if (!android.view.Window.macapkDecorFitsSystemWindows()) return null;
        int stripH = navBarInsetPx(root);
        if (stripH <= 0) return null;
        // v1.0.997: MIRRORS the painter's branch, and must keep doing so. If
        // paint moves and the hit rects do not, BACK becomes an invisible
        // tap-eating box in the old place -- the exact defect v1.0.865 retired
        // from the status bar.
        final int navPos = navBarPosition(root);
        if (navPos == NAV_BAR_BOTTOM) {
            if (surfaceH <= stripH) return null;
            int third = surfaceW / 3;
            return new int[]{ slot * third, surfaceH - stripH,
                              (slot == 2 ? surfaceW : (slot + 1) * third), surfaceH };
        }
        // NAV_BAR_RIGHT: a vertical strip whose glyph order, top-to-bottom, is
        // RECENTS, HOME, BACK (measured -- see paintSystemNavStrip). So the
        // slot index counts UP from the bottom: slot 0 (back) is the BOTTOM
        // third, slot 2 (recents) the TOP one.
        if (surfaceW <= stripH || surfaceH <= 0) return null;
        int third = surfaceH / 3;
        int band = 2 - slot;                       // 0 = top band
        int y0 = band * third;
        int y1 = (band == 2) ? surfaceH : (band + 1) * third;
        return new int[]{ surfaceW - stripH, y0, surfaceW, y1 };
    }

    /** v1.0.406: the tappable hit rect (surface px, top-origin) for the
     *  "< Back" button painted at the LEFT of the STATUS BAR by paint(), or
     *  null when there's no status bar. TouchDispatcher consumes a tap inside
     *  it and dispatches KEYCODE_BACK -> onBackPressed. The box spans the left
     *  ~80dp of the bar (chevron + "Back" label) without reaching the centered
     *  clock. */
    public static int[] backButtonHitRect(View root, int surfaceW, int surfaceH) {
        // v1.0.865 RETIRED the status-bar back button (an invisible tap-eating
        // box over the top-left 80dp of every app).
        // v1.0.867 REPOINTS this at the real thing: the BACK glyph in the
        // 3-button nav bar. The name and TouchDispatcher's arm/disarm tap logic
        // are unchanged -- only the rect it describes moved from a Mac.apk
        // invention in the status bar to an actual painted button, so a tap
        // there now lands on something the user can see.
        return navButtonHitRect(root, surfaceW, surfaceH, 0);
    }

    // Re-used for every frame -- SimpleDateFormat is NOT thread-safe
    // but `macapkDrawFrame` is the only caller and it always runs on
    // the metal-callback thread. A per-call allocation would be a wash
    // but a shared singleton is cleaner.
    // 12-hour clock (owner preference), e.g. "9:15" -- no AM/PM, matching the
    // compact Android status-bar clock.
    private static final java.text.SimpleDateFormat sTimeFormat =
        new java.text.SimpleDateFormat("h:mm");

    // Battery cache: pmset is a process-spawn + parse and burns ~5ms
    // wall time on a quiet system. At 60fps a per-frame call would
    // round-trip 300 spawns per second; the actual battery level
    // doesn't move that fast. Refresh once every 30s.
    private static final long BATTERY_CACHE_MS = 30_000L;
    private static volatile int sCachedBatteryPct = Integer.MIN_VALUE; // sentinel
    private static volatile long sCachedBatteryAtMs = 0L;
    private static volatile boolean sBatteryWarned = false;

    /** Paint the status bar at canvas (0, 0, surfaceW, barHeight).
     *  Must be called with the canvas in its NATIVE (untranslated)
     *  state so the strip sits at the top of the screen, not at the
     *  current canvas translation. The driver wraps the rootView.draw
     *  in a save/translate/restore so this method is safe to call
     *  with the same canvas immediately after. */
    public static void paint(Canvas canvas, View root, int surfaceWidth, int barHeight) {
        paint(canvas, root, surfaceWidth, barHeight, 0);
    }

    /** Paint variant that BLENDS the bar with the content beneath it.
     *  `bgUnder` is the dominant ARGB of the canvas row just below the bar
     *  (sampled by ViewCanvasDriver). When opaque, the bar is painted that
     *  color and the glyphs flip to light/dark for contrast -- mimicking a
     *  transparent status bar over the app's content (what the emulator
     *  shows over F-Droid's orange feature-graphic header). When bgUnder is
     *  0 (transparent / unavailable) it falls back to the themed
     *  colorBackground strip (the prior behavior). Env MACAPK_STATUSBAR_BLEND=0
     *  forces the plain colorBackground strip. */
    public static void paint(Canvas canvas, View root, int surfaceWidth,
                             int barHeight, int bgUnder) {
        if (canvas == null || surfaceWidth <= 0 || barHeight <= 0) return;

        boolean blend = ((bgUnder >>> 24) != 0)
            && !"0".equals(ENV_MACAPK_STATUSBAR_BLEND);

        // -- Background --
        // ?attr/colorBackground blends with the activity's window bg.
        // F-Droid declares it as md_theme_background = #F9F9FF (lavender)
        // so the bar disappears into the activity; Material baseline
        // light theme = #FFFBFE; falls back to white when no theme.
        // v1.0.900: AOSP DecorView.calculateBarColor (see macapkSystemBarColor).
        // The blend is RETIRED. It had no AOSP counterpart, and it was not a
        // fallback but the ONLY live path -- measured 2026-08-08, our bar colour
        // equalled the sampled row on 15 of 15 fleet apps, so the colorBackground
        // branch never once executed. MACAPK_STATUSBAR_BLEND=1 restores it (note
        // the sense is now opt-IN where it used to be opt-OUT with =0) so the two
        // can be A/B'd on one build if an app regresses.
        int bgColor = (blend && "1".equals(ENV_MACAPK_STATUSBAR_BLEND))
            ? (0xFF000000 | (bgUnder & 0x00FFFFFF))
            : macapkSystemBarColor(root, true);
        Paint bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, surfaceWidth, barHeight, bgPaint);

        // -- Foreground (text) --
        // ?attr/colorOnBackground = legible-against-background ink.
        // Falls back to opaque-ish dark gray when no theme so we still
        // get readable digits on a white default bg. When blending, pick
        // light/dark glyphs by the bar background's luminance (AOSP's
        // light-status-bar appearance contract).
        // Glyph polarity from the PAINTED bar's luminance, on every path.
        // The old else-branch answered colorOnBackground, i.e. ink chosen for the
        // app's BACKGROUND rather than for the bar. That was survivable while the
        // bar was always a copy of the content underneath; it is not survivable
        // now that branch 2 paints BLACK on legacy-themed apps, where dark ink on
        // a black bar would vanish. Deriving it here cannot produce that outcome,
        // and it matches what modern SystemUI does: it enforces bar contrast even
        // when the app's windowLightStatusBar says otherwise.
        //
        // Full opacity on purpose even when the bar is dimmed: SystemUI's window
        // sits ABOVE the FLAG_DIM_BEHIND layer, so its glyphs stay bright while
        // the app-drawn bar behind them darkens. The AVD shows exactly that on
        // newpipe (bar #5c1715 dimmed, glyphs #ffffff).
        int fgColor;
        {
            int r = (bgColor >> 16) & 0xFF, g = (bgColor >> 8) & 0xFF, b = bgColor & 0xFF;
            double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
            fgColor = (lum >= 0.5) ? 0xDD000000 : 0xFFFFFFFF;
        }

        float density = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            if (root != null && root.getResources() != null
                && root.getResources().getDisplayMetrics() != null
                && root.getResources().getDisplayMetrics().density > 0f) {
                density = root.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}

        // 14sp text size matches AOSP `Text.StatusBar.Clock` style.
        // Using a slightly bolder weight to mirror SystemUI's clock.
        float textSizePx = 14f * density;
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(fgColor);
        textPaint.setTextSize(textSizePx);
        try { textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); }
        catch (Throwable ignored) {}

        // Vertical centering: AWT's drawString places the baseline at
        // the given Y; baseline = (barH + (ascent.abs - descent)) / 2
        // approximates a visually-centered run. ascent in Android
        // Paint.FontMetrics is negative.
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float ascent = fm.ascent;   // negative
        float descent = fm.descent; // positive
        float textHeight = -ascent + descent;
        float baselineY = (barHeight - textHeight) / 2f - ascent;

        int padPx = Math.round(16f * density); // 16dp from each side

        // v1.0.865 (owner call): the "< Back" button that used to be painted
        // here is GONE. It was a Mac.apk invention -- its own comment said so:
        // "AOSP has no status-bar back, but macOS users need a discoverable
        // system back, so we add one here." With the bottom nav strip now
        // reserved AOSP-faithfully again, BACK is delivered the way real
        // Android delivers it under gesture nav: the LEFT-EDGE SWIPE
        // (TouchDispatcher), plus Esc and Cmd+[ as host-keyboard equivalents.
        // Nothing app-facing changed -- the action chain is still
        // Activity.onKeyUp(KEYCODE_BACK) -> onBackPressed.

        // -- Center: clock (12-hour) --
        String timeText;
        try { timeText = sTimeFormat.format(new java.util.Date()); }
        catch (Throwable t) { timeText = ""; }
        if (!timeText.isEmpty()) {
            try {
                float tw = textPaint.measureText(timeText);
                canvas.drawText(timeText, (surfaceWidth - tw) / 2f, baselineY, textPaint);
            } catch (Throwable ignored) {}
        }

        // -- Right: battery --
        int pct = getBatteryPct();
        String batText;
        if (pct < 0) batText = "--";
        else batText = pct + "%";
        float batWidth;
        try { batWidth = textPaint.measureText(batText); }
        catch (Throwable t) { batWidth = batText.length() * textSizePx * 0.55f; }

        // Battery outline icon: a small rounded rect with a tiny cap
        // on the right side, sitting just inside the text on its left.
        // 22dp wide x 11dp tall body + 2dp cap. Drawn first so the
        // percentage glyph sits to the LEFT of it (right-edge aligned).
        int iconW = Math.round(22f * density);
        int iconH = Math.round(11f * density);
        int capW  = Math.round(2f * density);
        int capH  = Math.round(5f * density);
        int iconRight = surfaceWidth - padPx;
        int iconLeft  = iconRight - iconW;
        int iconTop   = (barHeight - iconH) / 2;
        int iconBot   = iconTop + iconH;
        Paint iconPaint = new Paint();
        iconPaint.setAntiAlias(true);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(Math.max(1f, density)); // 1dp stroke
        iconPaint.setColor(fgColor);
        try {
            canvas.drawRoundRect(
                new android.graphics.RectF(iconLeft, iconTop, iconRight, iconBot),
                2f * density, 2f * density, iconPaint);
            // Cap on the right side of the body.
            iconPaint.setStyle(Paint.Style.FILL);
            int capL = iconRight;
            int capR = iconRight + capW;
            int capT = (barHeight - capH) / 2;
            int capB = capT + capH;
            canvas.drawRect(capL, capT, capR, capB, iconPaint);
            // Fill the inside proportional to charge level when known.
            if (pct >= 0) {
                int inset = Math.max(1, Math.round(1.5f * density));
                int innerL = iconLeft + inset;
                int innerT = iconTop + inset;
                int innerB = iconBot - inset;
                int innerR = iconRight - inset;
                int innerW = innerR - innerL;
                if (innerW > 0) {
                    int fillR = innerL + Math.round(innerW * (pct / 100f));
                    Paint fillPaint = new Paint();
                    fillPaint.setStyle(Paint.Style.FILL);
                    fillPaint.setAntiAlias(true);
                    fillPaint.setColor(fgColor);
                    canvas.drawRect(innerL, innerT, fillR, innerB, fillPaint);
                }
            }
        } catch (Throwable ignored) {}

        // Percentage text sits just LEFT of the icon.
        int batTextRight = iconLeft - Math.round(6f * density);
        float batTextX = batTextRight - batWidth;
        try { canvas.drawText(batText, batTextX, baselineY, textPaint); }
        catch (Throwable ignored) {}
    }

    /** Read battery % from the macOS host. Cached for 30s -- the actual
     *  level doesn't change at 60 fps and `pmset` is a process spawn
     *  costing ~5ms per call. Returns -1 when the host has no battery
     *  (desktops) or pmset itself fails (sandbox blocks exec etc.).
     *
     *  Format reference (macOS 26 pmset(8)):
     *      Now drawing from 'Battery Power'
     *       -InternalBattery-0 (id=...)\t87%; discharging; 5:32 remaining present: true
     *  We don't depend on the exact whitespace or surrounding tokens --
     *  the first \b<digits>%\b on any output line wins. */
    private static int getBatteryPct() {
        long now = System.currentTimeMillis();
        if (sCachedBatteryPct != Integer.MIN_VALUE
            && (now - sCachedBatteryAtMs) < BATTERY_CACHE_MS) {
            return sCachedBatteryPct;
        }
        sCachedBatteryAtMs = now;
        int pct = -1;
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"pmset", "-g", "batt"});
            java.util.regex.Pattern re =
                java.util.regex.Pattern.compile("\\b(\\d{1,3})\\s*%");
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    java.util.regex.Matcher m = re.matcher(line);
                    if (m.find()) {
                        try {
                            int v = Integer.parseInt(m.group(1));
                            if (v >= 0 && v <= 100) { pct = v; break; }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            try { proc.waitFor(); } catch (InterruptedException ignored) {}
        } catch (Throwable t) {
            if (!sBatteryWarned) {
                sBatteryWarned = true;
                NativeLog.w(TAG, "pmset failed (battery shown as --): " + t);
            }
        } finally {
            if (proc != null) {
                try { proc.destroy(); } catch (Throwable ignored) {}
            }
        }
        sCachedBatteryPct = pct;
        return pct;
    }
}
