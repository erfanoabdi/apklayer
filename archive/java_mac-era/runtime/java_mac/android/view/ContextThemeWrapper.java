// ContextThemeWrapper -- intermediate Context layer between
// ContextWrapper and Activity in the real Android hierarchy. We don't
// model themes; this is a pass-through.
package android.view;

import android.content.Context;
import android.content.ContextWrapper;

public class ContextThemeWrapper extends ContextWrapper {

    /** MACAPK_VIEW_THEME (default OFF) -- the single gate for View-level
     *  theming: `android:theme` on a layout element, ContextThemeWrapper
     *  actually OWNING a theme, and Resources.Theme.setTo actually copying one.
     *
     *  Default OFF is load-bearing, not caution theatre. `android.app.Activity
     *  extends ContextThemeWrapper`, so the ONE decision below -- whether
     *  getTheme() returns this wrapper's own theme or forwards to the base
     *  context's -- changes theme resolution for EVERY activity in every app in
     *  the corpus. Flipping it needs the protected sweep. With the flag off the
     *  default path is byte-identical to the pre-existing pass-through.
     *
     *  KNOWN INCOMPLETE even when ON: XML-drawable `?attr/` COLOURS do not go
     *  through Resources.Theme.resolveAttribute at all -- they go through the
     *  STATIC Resources.macapkThemeColor -> ResourceIndex
     *  .resolveApplicationThemeColor, which is hardcoded to the APPLICATION
     *  theme and takes no Context. So a View-level overlay is honoured by
     *  Context.getTheme().resolveAttribute (the path AppCompat/Material widget
     *  constructors use) but NOT yet by a <shape><solid android:color="?attr/X">
     *  inside a background drawable. Closing that needs the resolving Theme
     *  threaded into drawable/TypedArray colour resolution, the way AOSP passes
     *  Resources.Theme down Drawable.inflate(Resources, XmlPullParser,
     *  AttributeSet, Theme). See docs/newapps-a_2026-07-30.md. */
    // lane layout, 2026-08-01: DEFAULT FLIPPED ON (env MACAPK_VIEW_THEME=0
    // opts out). This was the staged, deliberate decision from the theme
    // saga (docs/newapps-a_2026-07-30.md parts (a)-(d), the last part landed
    // v1.0.842-era in docs/attr-theme-threading_2026-07-31.md) whose gate
    // was "needs the protected sweep" -- this session runs that sweep. With
    // the flag OFF, Theme.setTo was a no-op and wrapper themes never carried
    // the activity cascade, so a runtime setTheme() was INVISIBLE to every
    // attr resolution: resolveDefStyleName walked an empty cascade + the
    // MANIFEST theme (AntennaPod: the Splash theme) and every Material3
    // token (bottomNavigationStyle -> Widget.Material3.BottomNavigationView,
    // toolbarStyle, itemIconTint, minHeight 80dp...) resolved null --
    // measured as defStyle=null on ~670 of ~700 4-arg TypedArray parses in
    // one AntennaPod boot (MACAPK_TRACE_DEFSTYLE, 2026-08-01), the M2-shaped
    // 63dp bottom bar, and the unstyled toolbar title.
    public static final boolean MACAPK_VIEW_THEME =
        macapkFlagOn(System.getenv("MACAPK_VIEW_THEME"), true);

    private static boolean macapkFlagOn(String v, boolean dflt) {
        if (v == null || v.isEmpty()) return dflt;
        return !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    /** The style resource this wrapper was constructed with (AOSP's
     *  mThemeResource). 0 means "no overlay", which is the Activity case. */
    private int macapkThemeResource;

    public ContextThemeWrapper(Context base, int themeResId) {
        super(base);
        this.macapkThemeResource = themeResId;
    }

    /** AOSP ContextThemeWrapper(Context, Resources.Theme)
     *  (frameworks/base/core/java/android/view/ContextThemeWrapper.java:79-82,
     *  aosp-mirror/platform_frameworks_base android14-release), verbatim:
     *
     *      public ContextThemeWrapper(Context base, Resources.Theme theme) {
     *          super(base);
     *          mTheme = theme;
     *      }
     *
     *  AOSP's own javadoc states the semantic difference from the int form:
     *  "Unlike ContextThemeWrapper(Context, int), the theme passed to this
     *  constructor will COMPLETELY REPLACE the base context's theme." That is
     *  the whole behaviour -- it assigns mTheme (this tree's macapkOwnTheme)
     *  and does NOT touch mThemeResource, so:
     *    - getTheme() returns exactly the Theme handed in. The unconditional
     *      `if (macapkOwnTheme != null) return macapkOwnTheme;` at the top of
     *      getTheme() below already implements AOSP's `if (mTheme != null)
     *      return mTheme;`, so no change was needed there. Crucially there is
     *      NO inheritance from the base context: initializeTheme's
     *      `mTheme.setTo(getBaseContext().getTheme())` seed runs only on the
     *      `first = mTheme == null` path, which this constructor has already
     *      falsified. Seeding from the base here would be WRONG -- it is the
     *      documented difference between the two constructors.
     *    - getThemeResId() stays 0, and a later setTheme(int) therefore
     *      applyStyle()s onto the CALLER'S Theme object rather than a fresh
     *      one. That is AOSP's behaviour too, and it is why the Theme is
     *      shared, not copied: the caller can keep mutating it afterwards.
     *    - getResources() is untouched -- AOSP's Resources selection keys off
     *      the override Configuration, never off the theme.
     *
     *  MEASURED need: com.zhiliaoapp.musically 46.4.3 constructs one of these
     *  in InternalPublishServiceImpl (reached from ToolsActivityAssem.onCreate
     *  <- MainActivity.afterCallSceneActivityCreated) and threw
     *  `NoSuchMethodError: 'void android.view.ContextThemeWrapper.<init>
     *  (android.content.Context, android.content.res.Resources$Theme)'` from
     *  INSIDE MainActivity.onCreate -- aborting the rest of onCreate. */
    public ContextThemeWrapper(Context base,
                               android.content.res.Resources.Theme theme) {
        super(base);
        this.macapkOwnTheme = theme;
    }

    public ContextThemeWrapper() { super(null); }

    /** AOSP ContextThemeWrapper.setTheme(int) (frameworks/base/core/java/
     *  android/view/ContextThemeWrapper.java, aosp-mirror
     *  platform_frameworks_base@main, fetched 2026-07-31):
     *      public void setTheme(int resid) {
     *          if (mThemeResource != resid) { mThemeResource = resid; initializeTheme(); }
     *      }
     *  Ported UNCONDITIONALLY -- no MACAPK_VIEW_THEME gate here, unlike
     *  getTheme() below. The flag exists to guard the RISKIER, narrower
     *  per-View `android:theme="..."` cascade (see the flag's own doc
     *  comment above); it was never meant to gate the decades-old contract
     *  that a Context calling its OWN setTheme() sees the new theme take
     *  effect. The OLD body here only re-applied onto an ALREADY-
     *  materialized macapkOwnTheme, and only when the flag was on
     *  (`if (MACAPK_VIEW_THEME && macapkOwnTheme != null)`) -- for a fresh
     *  Activity that is never true (nothing had called getTheme() under the
     *  flag yet), so the call was silently swallowed.
     *
     *  MEASURED gap (NewPipe bring-up, 2026-07-30/31, docs/newapps-a_2026-
     *  07-30.md): NewPipe uses the standard pre-androidx.core.splashscreen
     *  idiom -- a transparent/red-watermarked splash theme declared on
     *  `<application>` in the manifest, then `setTheme(R.style.DarkTheme)`
     *  as the first line of `MainActivity.onCreate()`, matching how every
     *  app using this idiom (`android.app.Activity extends
     *  ContextThemeWrapper`) has always relied on setTheme() working. With
     *  the call swallowed, MainActivity stayed on its manifest splash theme
     *  forever: `colorPrimary`/`colorAccent` stayed transparent and
     *  `windowBackground` stayed the splash drawable (a red field + logo),
     *  so the nav drawer and every dialog painted with no surface
     *  underneath. Generic: any app that calls Activity.setTheme() -- not
     *  just a splash-theme switch, also a runtime day/night or per-flavor
     *  theme toggle -- hit the identical silent no-op. */
    public void setTheme(int resId) {
        if (macapkThemeResource != resId) {
            macapkThemeResource = resId;
            macapkInitializeTheme();
        }
    }

    /** AOSP ContextThemeWrapper.setTheme(Resources.Theme) (same file, lines
     *  155-157), verbatim `mTheme = theme;`. AOSP's javadoc: "If null is
     *  provided then the default Theme is returned on the next call to
     *  getTheme()" -- which falls out of getTheme()'s null check, so no extra
     *  handling is needed. The counterpart of the (Context, Theme)
     *  constructor: same assignment, after construction. */
    public void setTheme(android.content.res.Resources.Theme theme) {
        macapkOwnTheme = theme;
    }

    /** AOSP ContextThemeWrapper.getThemeResId() (same file, lines 159-164).
     *  @hide on AOSP, so guest bytecode compiled against a public android.jar
     *  cannot call it directly -- but it IS present on a real device and is
     *  reachable by reflection, which support libraries do use. Returns the
     *  CONSTRUCTOR/setTheme(int) style id, which stays 0 for a wrapper built
     *  from the (Context, Theme) constructor. */
    public int getThemeResId() { return macapkThemeResource; }

    /** AOSP ContextThemeWrapper.initializeTheme():
     *      final boolean first = mTheme == null;
     *      if (first) { mTheme = getResources().newTheme();
     *                   Resources.Theme theme = getBaseContext().getTheme();
     *                   if (theme != null) mTheme.setTo(theme); }
     *      onApplyThemeResource(mTheme, mThemeResource, first);
     *  with the default onApplyThemeResource doing theme.applyStyle(resId, true).
     *  Previously this early-returned the cached macapkOwnTheme WITHOUT
     *  re-applying on a second call -- harmless for the sole caller that
     *  existed then (getTheme()'s per-View branch, which only ever runs
     *  once per synthesized per-element wrapper instance), but wrong for
     *  setTheme() above, which must re-applyStyle EVERY time the resId
     *  changes (an Activity switching splash -> real theme is exactly a
     *  second call). Now mirrors AOSP's first/not-first split precisely:
     *  the Theme object and its `setTo(base)` seed are built ONCE, but
     *  applyStyle runs on every call. */
    private android.content.res.Resources.Theme macapkOwnTheme;

    private android.content.res.Resources.Theme macapkInitializeTheme() {
        boolean first = (macapkOwnTheme == null);
        if (first) {
            android.content.res.Resources res = null;
            try { res = getResources(); } catch (Throwable ignored) {}
            if (res == null) return null;
            android.content.res.Resources.Theme t = res.newTheme();
            if (t == null) return null;
            Context b = getBaseContext();
            if (b != null) {
                try {
                    android.content.res.Resources.Theme base = b.getTheme();
                    if (base != null) t.setTo(base);
                } catch (Throwable ignored) {}
            }
            macapkOwnTheme = t;
        }
        if (macapkThemeResource != 0) {
            try { macapkOwnTheme.applyStyle(macapkThemeResource, true); }
            catch (Throwable ignored) {}
        }
        return macapkOwnTheme;
    }

    /** v1.0.345: mirrors AOSP ContextThemeWrapper.applyOverrideConfiguration
     *  (frameworks/base/core/java/android/view/ContextThemeWrapper.java):
     *  stores a COPY of the override configuration and enforces the
     *  call-once / before-getResources contract via the same
     *  IllegalStateException. Activities override attachBaseContext and
     *  call this for per-activity locale/density overrides (Plague Inc's
     *  MainActivity does) -- without the method, starting the activity
     *  dies in NoSuchMethodError before onCreate. AOSP's getResources()
     *  builds a createConfigurationContext(mOverrideConfiguration)
     *  context; Mac.apk's single-density Resources model has no
     *  per-configuration variants yet, so the stored override currently
     *  has no consumer -- the load-bearing part is the API surface and
     *  its state contract. */
    private android.content.res.Configuration macapkOverrideConfiguration;

    public void applyOverrideConfiguration(
            android.content.res.Configuration overrideConfiguration) {
        if (macapkOverrideConfiguration != null) {
            throw new IllegalStateException(
                "Override configuration has already been set");
        }
        if (overrideConfiguration != null) {
            macapkOverrideConfiguration =
                new android.content.res.Configuration(overrideConfiguration);
        }
    }
    /** v1.0.75: return non-null Theme. AppCompat's AppBarLayout ctor
     *  calls Context.getTheme().resolveAttribute(...) during view
     *  inflation -- with null, NPE on the very first inflate. Forward
     *  to base context's Theme (which lazily allocates a stub one if
     *  the base also has none). */
    public android.content.res.Resources.Theme getTheme() {
        // AOSP ContextThemeWrapper.getTheme(): "if (mTheme != null) return
        // mTheme;" -- unconditional, no flag. Checked FIRST, ahead of the
        // MACAPK_VIEW_THEME gate below: once this wrapper has its own
        // materialized Theme -- built by an explicit setTheme() call above,
        // which is unconditional -- that Theme is authoritative regardless
        // of the flag. An explicit setTheme() call is a strictly stronger,
        // narrower signal than "this wrapper happens to carry a nonzero
        // CONSTRUCTOR resId" (the per-View android:theme="..." case the
        // flag actually guards, see MACAPK_VIEW_THEME's doc comment above),
        // so it must win even when the flag is off. This is the fix for
        // NewPipe-shaped apps (setTheme() in onCreate); it does not touch
        // the flag-gated branch immediately below, which stays exactly as
        // untested/gated as before.
        if (macapkOwnTheme != null) return macapkOwnTheme;
        // THE gate for View-level theming (see MACAPK_VIEW_THEME above). AOSP
        // ALWAYS returns the wrapper's own theme here; we only do so when the
        // flag is on, because Activity extends this class and the corpus has
        // never run with a non-forwarding getTheme(). A wrapper carrying no
        // overlay (themeResource 0, the Activity case) still falls through to
        // the base so the flag-on path stays as close to today as possible.
        if (MACAPK_VIEW_THEME && macapkThemeResource != 0) {
            android.content.res.Resources.Theme own = macapkInitializeTheme();
            if (own != null) return own;
        }
        Context b = getBaseContext();
        if (b != null) {
            android.content.res.Resources.Theme t = b.getTheme();
            if (t != null) return t;
        }
        if (macapkLocalTheme == null) {
            // v1.0.110: Theme is now a non-static inner class of
            // Resources; allocate a backing Resources for it.
            android.content.res.Resources r =
                new android.content.res.Resources(null, null, null);
            macapkLocalTheme = r.newTheme();
        }
        return macapkLocalTheme;
    }
    private android.content.res.Resources.Theme macapkLocalTheme;

    /** AOSP ContextThemeWrapper.onApplyThemeResource(Resources.Theme, int,
     *  boolean): the hook a subclass overrides to change how the style is
     *  applied to the freshly-created Theme. AOSP's own body is
     *      theme.applyStyle(resid, true);
     *  (the `first` argument is only used by subclasses such as
     *  android.app.Activity, which decides whether to also re-apply the base
     *  theme). Reproduced exactly -- getTheme() below calls it. */
    protected void onApplyThemeResource(android.content.res.Resources.Theme theme,
            int resid, boolean first) {
        theme.applyStyle(resid, true);
    }

    /** Gate MACAPK_CTW_INFLATER_SERVICE (default ON; =0 restores the old
     *  forward-to-base answer byte for byte). */
    static final boolean MACAPK_CTW_INFLATER_SERVICE =
        macapkFlagOn(System.getenv("MACAPK_CTW_INFLATER_SERVICE"), true);

    /** AOSP ContextThemeWrapper.getSystemService (android14-release
     *  core/java/android/view/ContextThemeWrapper.java:181-189):
     *      if (LAYOUT_INFLATER_SERVICE.equals(name)) {
     *          if (mInflater == null) {
     *              mInflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
     *          }
     *          return mInflater;
     *      }
     *      return getBaseContext().getSystemService(name);
     *  and Activity.getSystemService (android14 Activity.java:7353-7366) only
     *  special-cases WINDOW/SEARCH before `return super.getSystemService(name)`.
     *  So on a device the inflater an Activity (or any theme wrapper) hands out
     *  is bound to THAT wrapper -- its theme, its setTheme() -- and, because
     *  LayoutInflater.from(ctx) IS ctx.getSystemService(LAYOUT_INFLATER_SERVICE)
     *  (LayoutInflater.java:286-293), it is the very instance AppCompat installed
     *  its Factory2 on.
     *
     *  Ours inherited ContextWrapper's plain forward, so
     *  activity.getSystemService("layout_inflater") returned the BASE context's
     *  inflater: AppCompat's attachBaseContext wrapper (theme
     *  Theme.AppCompat.Empty) or the ContextImpl (manifest theme), never the
     *  activity's own. Everything an app inflated through that door missed the
     *  activity's setTheme() theme. Measured 2026-09-29 on Open Sudoku 4.7.0:
     *  the game screen's number pad (NumberButton extends MaterialButton) is
     *  inflated via getSystemService("layout_inflater"); the cascade was
     *  [ThemeOverlay.Material3.Button, Theme.AppCompat.Empty,
     *  Theme.DeviceDefault.Light.DarkActionBar] with no AppTheme.AMOLED, so
     *  MDC's ThemeEnforcement found no isMaterialTheme and threw, every button
     *  became a FrameLayout placeholder, and the app died with a
     *  ClassCastException -- "numbers cannot be inputted".
     *
     *  LayoutInflater.from(this), not a fresh clone, is the faithful answer
     *  HERE: our LayoutInflater.from does not route through getSystemService
     *  (it keeps a per-Context instance, or macapkOwnInflater() where that is
     *  modelled), so returning it keeps the AOSP identity
     *  from(w) == w.getSystemService(LAYOUT_INFLATER_SERVICE). The other half of
     *  the AOSP answer (`from(base).cloneInContext(this)`: the base's factories
     *  are inherited) lives in LayoutInflater.from(), which builds the
     *  per-Context instance of a NON-Activity wrapper as exactly that clone
     *  (review 2026-09-29: from(w) alone was a factory-less fresh inflater, so
     *  `new ContextThemeWrapper(activity, style)` lost AppCompat's Factory2).
     *  An Activity's inflater starts fresh: AppCompat installs its Factory2 on
     *  from(activity) afterwards. from() never calls back into this method, so
     *  there is no recursion. */
    @Override public Object getSystemService(String name) {
        if (MACAPK_CTW_INFLATER_SERVICE && Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
            return android.view.LayoutInflater.from(this);
        }
        return super.getSystemService(name);
    }

    /** @hide Mac.apk. The inflater LayoutInflater.from(this) must return, for a
     *  wrapper that models AOSP's getSystemService(LAYOUT_INFLATER_SERVICE)
     *  answer (android14 ContextThemeWrapper.java:181-189: the base's inflater
     *  cloned into this wrapper, factories kept). Null, the default, means "not
     *  modelled": LayoutInflater.from keeps its per-Context cache, exactly as
     *  before, so Activity (a subclass) and every other wrapper are unchanged.
     *  android.app.Dialog's dialog context overrides it. See
     *  LayoutInflater.MACAPK_INFLATER_FROM_OWN. An override must not call
     *  LayoutInflater.from(this): from() calls this first. */
    protected android.view.LayoutInflater macapkOwnInflater() { return null; }
}
