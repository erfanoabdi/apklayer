package android.widget;
import android.content.Context;
// android.widget.Button -- a TextView whose default style centers its label
// (AOSP's Widget.Button sets gravity=center_vertical|center_horizontal). Without
// setting it here the label drew top-left (FrozenBubble's menu buttons, any
// button-driven UI). Apps can still override via setGravity / android:gravity.
public class Button extends TextView {
    public Button(Context c) { super(c); init(); macapkApplyDefaultButtonStyle(c); }
    public Button(Context c, android.util.AttributeSet a) { super(c, a); init(); }
    public Button(Context c, android.util.AttributeSet a, int s) { super(c, a, s); init(); }
    private void init() { setGravity(android.view.Gravity.CENTER); }

    /** AOSP's `Button(Context)` chains to
     *  `this(context, null, com.android.internal.R.attr.buttonStyle)`, so a
     *  button built IN CODE gets the theme's button style exactly as an inflated
     *  one does. Ours did not: the widget-default-style tier exists only in
     *  `LayoutInflater.applyWidgetDefaultStyle`, and the inflater never runs for
     *  `new Button(ctx)`.
     *
     *  MEASURED victim: frozenbubble's `HomeScreen.addHomeButtons` builds all
     *  four menu buttons with `new Button(this)` and sets only text / size /
     *  width / typeface / shadow, so every pixel of chrome AND the 48dip minimum
     *  height have to come from the style. The emulator draws four 324x96 buttons
     *  at a 126px pitch; we drew bare 43px-tall labels at a 73px pitch, which puts
     *  three of the four rows 52 / 26 / 52px from where Android puts them and
     *  shrinks every touch target to text height.
     *
     *  WHY NOT the literal AOSP shape (`this(c, null, 0x01010048)` and let View's
     *  4-arg constructor resolve it): that route goes through
     *  `Theme.obtainStyledAttributes`, and two things on it answer null for a
     *  theme-only probe today -- `TypedArray.setValueAt` returns null whenever
     *  `attributeSet == null`, and `Resources.Theme.resolveDefStyleName` ends with
     *  `startsWith("Widget.Material.")`, which rejects the classic and Holo button
     *  styles outright. So resolve through the same ResourceIndex route the
     *  inflater's tier 3 already proves works, and read the SAME rows it reads so
     *  the two paths cannot disagree. Collapse this into the constructor chain the
     *  day both are fixed.
     *
     *  Only the 1-arg constructor takes this: the inflater always finds
     *  `(Context, AttributeSet)` first, so no inflated view can double-apply. */
    /** Memoized so the trace below costs one map lookup per PROCESS rather than
     *  one per Button construction. */
    private static Boolean sTraceStyle;
    private static boolean macapkTraceStyle() {
        if (sTraceStyle == null) sTraceStyle = System.getenv("MACAPK_TRACE_WINDOW_FLAGS") != null;
        return sTraceStyle;
    }

    private void macapkApplyDefaultButtonStyle(Context c) {
        try {
            if (c == null) return;
            Object ri = android.content.res.Resources.macapkGetResourceIndex();
            if (ri == null) return;
            java.lang.reflect.Method resolve = ri.getClass()
                .getMethod("resolveAttrInTheme", String.class, String.class);
            Object appTheme = ri.getClass().getMethod("getApplicationThemeName").invoke(ri);
            if (!(appTheme instanceof String)) return;
            Object sr = resolve.invoke(ri, appTheme, "buttonStyle");
            // MEASUREMENT ONLY (v1.0.1025, no behaviour change): does the
            // Context's own theme cascade answer buttonStyle differently from
            // the process-wide anchor? This is the style-tier twin of the
            // colour bug Stage A fixed at v1.0.1016, but before changing a path
            // that runs for every programmatic Button, find out whether the two
            // answers EVER diverge on a real app. Behind MACAPK_TRACE_WINDOW_FLAGS.
            if (macapkTraceStyle()) {
                try {
                    android.content.res.Resources.Theme th = c.getTheme();
                    String viaCtx = null;
                    if (th != null) viaCtx = th.macapkResolveAttrRawHonest("buttonStyle");
                    String viaGlobal = (sr instanceof String) ? ((String) sr).trim() : null;
                    if (viaCtx != null) viaCtx = viaCtx.trim();
                    boolean differ = (viaCtx == null) ? (viaGlobal != null)
                                                      : !viaCtx.equals(viaGlobal);
                    com.macapk.loader.NativeLog.i("Mac.apk.Button",
                        "buttonStyle viaContext=" + viaCtx + " viaGlobal=" + viaGlobal
                        + " ctx=" + c.getClass().getName()
                        + (differ ? "   <== DIVERGES" : ""));
                } catch (Throwable ignored) {}
            }
            if (!(sr instanceof String)) return;
            String styleName = ((String) sr).trim();
            if (styleName.startsWith("@style/"))              styleName = styleName.substring(7);
            else if (styleName.startsWith("@android:style/")) styleName = styleName.substring(15);
            else if (styleName.startsWith("?"))               return;  // unresolved delegation
            android.util.DisplayMetrics dm = null;
            try { dm = c.getResources().getDisplayMetrics(); } catch (Throwable ignored) {}
            // Background first: setBackgroundDrawable applies the drawable's own
            // padding, and AOSP's constructor order is background-then-attrs.
            Object bg = resolve.invoke(ri, styleName, "background");
            if (bg instanceof String && ((String) bg).startsWith("@android:drawable/")) {
                android.graphics.drawable.Drawable d = c.getResources()
                    .macapkFrameworkDrawableByName(((String) bg).substring(18));
                if (d != null) setBackgroundDrawable(d);
            }
            Object tc = resolve.invoke(ri, styleName, "textColor");
            if (tc instanceof String) {
                Integer col = android.content.res.Resources
                    .macapkResolveColorExpr((String) tc);
                if (col != null) setTextColor(col.intValue());
            }
            int mh = macapkStyleDimenPx(resolve.invoke(ri, styleName, "minHeight"), dm);
            if (mh > 0) setMinHeight(mh);
            int mw = macapkStyleDimenPx(resolve.invoke(ri, styleName, "minWidth"), dm);
            if (mw > 0) setMinWidth(mw);
            // Widget.Button's focusable / clickable (styles.xml): what lets a
            // button built in code take D-pad / Tab focus, as on a device.
            if ("true".equals(resolve.invoke(ri, styleName, "focusable"))) setFocusable(true);
            if ("true".equals(resolve.invoke(ri, styleName, "clickable"))) setClickable(true);
        } catch (Throwable ignored) {}
    }

    /** "48dip" / "48dp" / "14sp" / "12px" to pixels, per AOSP
     *  TypedValue.applyDimension. Returns 0 for anything unparseable, so an
     *  unrecognised unit leaves the minimum alone rather than inventing one. */
    private static int macapkStyleDimenPx(Object raw, android.util.DisplayMetrics dm) {
        if (!(raw instanceof String)) return 0;
        String s = ((String) raw).trim();
        int unit;
        if (s.endsWith("dip"))     { unit = android.util.TypedValue.COMPLEX_UNIT_DIP; s = s.substring(0, s.length() - 3); }
        else if (s.endsWith("dp")) { unit = android.util.TypedValue.COMPLEX_UNIT_DIP; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("sp")) { unit = android.util.TypedValue.COMPLEX_UNIT_SP;  s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("px")) { unit = android.util.TypedValue.COMPLEX_UNIT_PX;  s = s.substring(0, s.length() - 2); }
        else return 0;
        try {
            float v = Float.parseFloat(s.trim());
            if (dm == null) return Math.round(v);
            return Math.round(android.util.TypedValue.applyDimension(unit, v, dm));
        } catch (Throwable t) { return 0; }
    }

    public Button(Context c, android.util.AttributeSet a, int defStyleAttr, int defStyleRes) {
        super(c, a, defStyleAttr); init();
    }
}

