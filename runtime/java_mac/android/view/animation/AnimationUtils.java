// android.view.animation.AnimationUtils -- factory methods for loading
// Animation / Interpolator instances by resource ID.
//
// Mac.apk's framework doesn't run actual animations through a frame
// scheduler -- a static View tree is enough for our render path -- so
// these factories return baseline implementations rather than parsing
// the corresponding XML. The contract callers depend on is "I get back
// a non-null Interpolator / Animation that I can call without an NPE";
// they almost never inspect the values returned (animations rarely
// drive content, only transitions which we don't render anyway).
//
// Load-bearing for v1.0.128: F-Droid's MaterialCardViewHelper calls
// AnimationUtils.loadInterpolator(context, R.interpolator.X) during
// MaterialCardView's <init>. Without this method, every MaterialCardView
// instantiation throws NoSuchMethodError and LayoutInflater swaps in a
// FrameLayout placeholder -- so the ViewCanvasDriver card-chrome
// painter never matches (its `name.endsWith("CardView")` check fails
// against "FrameLayout"). Adding a stub-returning loadInterpolator lets
// MaterialCardView construct, the class name on the View is
// "MaterialCardView", and the painter resumes painting card chrome.
// Generic across every Material widget that uses interpolator XML
// resources for state transitions (CardView, NavigationBarView, FAB,
// AppBarLayout, TextInputLayout, etc.).
//
// AOSP reference:
//   frameworks/base/core/java/android/view/animation/AnimationUtils.java
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/view/animation/AnimationUtils.java
package android.view.animation;

import android.content.Context;

public class AnimationUtils {
    public AnimationUtils() {}

    /** AOSP AnimationUtils.loadInterpolator(Context, int) (android16-release
     *  core/java/android/view/animation/AnimationUtils.java:411-424):
     *
     *      parser = context.getResources().getAnimation(id);
     *      return createInterpolatorFromXml(context.getResources(),
     *                                       context.getTheme(), parser);
     *
     *  i.e. it is a thin adapter over the (Resources, Theme, int) overload.
     *  This body used to be `return new LinearInterpolator()` with a comment
     *  calling the real parse "a future enhancement" -- but that enhancement
     *  landed further down THIS FILE (see PART C), so the wrong-curve-for-every-
     *  resource answer was being given by the overload apps actually call while
     *  a correct parser sat unused two methods away. Every AndroidX/Material
     *  widget that reads an interpolator resource during construction takes this
     *  overload (MaterialCardViewHelper, FAB, AppBarLayout, TextInputLayout),
     *  so all of their motion was linear regardless of what their XML said.
     *
     *  The 3-arg overload keeps this file's never-throws contract (it falls back
     *  to LinearInterpolator instead of AOSP's NotFoundException), so the
     *  pre-existing "callers get a non-null Interpolator, always" guarantee that
     *  made this a stub in the first place is preserved exactly. */
    public static Interpolator loadInterpolator(Context context, int id) {
        if (context == null) return new LinearInterpolator();
        android.content.res.Resources res = context.getResources();
        if (res == null) return new LinearInterpolator();
        android.content.res.Resources.Theme theme = null;
        try { theme = context.getTheme(); } catch (Throwable ignored) {}
        return loadInterpolator(res, theme, id);
    }

    /** AOSP AnimationUtils.loadAnimation(Context, int) (android16-release
     *  core/java/android/view/animation/AnimationUtils.java:216-229):
     *
     *      parser = context.getResources().getAnimation(id);
     *      return createAnimationFromXml(context, parser);
     *
     *  This body used to be `return new Animation() {}` -- the XML was opened
     *  never, and EVERY R.anim resource in every app resolved to the same empty
     *  base Animation. The comment justifying that said "we don't drive
     *  animations through a frame scheduler, so a no-op Animation is correct".
     *  Both halves were wrong by the time it was read: Animation.java is a
     *  faithful AOSP state machine, and View.draw now pumps it (see
     *  View.applyLegacyAnimation). More importantly the claim was never a
     *  RENDERING claim -- an app that calls
     *  `anim.setAnimationListener(...); view.startAnimation(anim)` and swaps a
     *  fragment, re-enables a button or releases a latch in onAnimationEnd
     *  never gets that callback if the animation never runs. That is a HANG,
     *  not a missing fade.
     *
     *  DELIBERATE DEVIATION, same as the interpolator half above: AOSP throws
     *  NotFoundException when the resource cannot be loaded; this file's
     *  contract is never-throws, so we fall back to the historical inert
     *  Animation. An inert animation has duration 0, which the state machine
     *  ends on its first frame -- so even the fallback now fires
     *  onAnimationEnd instead of hanging. */
    public static Animation loadAnimation(Context context, int id) {
        if (context != null) {
            android.content.res.XmlResourceParser parser = null;
            try {
                android.content.res.Resources res = context.getResources();
                if (res != null) {
                    parser = res.getAnimation(id);
                    Animation a = createAnimationFromXml(context, parser, null,
                            android.util.Xml.asAttributeSet(parser));
                    if (a != null) return a;
                }
            } catch (Throwable ignored) {
                // See the never-throws note above.
            } finally {
                if (parser != null) parser.close();
            }
        }
        return new Animation() {};
    }

    /** AOSP-API-compatible: returns the current animation time in ms.
     *  AOSP uses SystemClock.uptimeMillis() so animations stay in
     *  sync across processes; we use System.nanoTime()/1e6 since
     *  we're single-process. Callers compare deltas, so the absolute
     *  origin doesn't matter. */
    public static long currentAnimationTimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    /** AOSP AnimationUtils.loadLayoutAnimation(Context, int)
     *  (AnimationUtils.java:216-229 / createLayoutAnimationFromXml:233-261,
     *  android14-release):
     *      parser = context.getResources().getAnimation(id);
     *      return createLayoutAnimationFromXml(context, parser);
     *  and the inflater dispatches on the root element:
     *      if ("layoutAnimation".equals(name))      controller = new LayoutAnimationController(c, attrs);
     *      else if ("gridLayoutAnimation".equals(name)) controller = new GridLayoutAnimationController(c, attrs);
     *      else throw new InflateException("Unknown layout animation name: " + name);
     *
     *  This body used to be `return new LayoutAnimationController((Animation) null)`
     *  -- the XML was never opened, and the controller it handed back wrapped a
     *  NULL Animation. Any app that reads the animation back off the controller
     *  NPEs; measured on Aegis 3.4.2 (2026-08-27 fleet census), whose
     *  MainActivity.loadEntries does exactly that and took its whole onStart
     *  down with it, so the entry list never populated.
     *
     *  DELIBERATE DEVIATION, matching loadAnimation and loadInterpolator above:
     *  AOSP throws NotFoundException/InflateException on a bad resource; this
     *  file's contract is never-throws. So an unreadable or unrecognised
     *  resource falls back to a controller wrapping the INERT Animation -- NOT
     *  a null one. An inert animation has duration 0 and the state machine ends
     *  it on the first frame, so a caller that inspects or starts it gets a
     *  coherent no-op instead of an NPE. The old behaviour is not reachable any
     *  more: nothing here can return a controller with a null animation. */
    public static android.view.animation.LayoutAnimationController loadLayoutAnimation(
            android.content.Context context, int id) {
        if (context != null) {
            android.content.res.XmlResourceParser parser = null;
            try {
                android.content.res.Resources res = context.getResources();
                if (res != null) {
                    parser = res.getAnimation(id);
                    android.view.animation.LayoutAnimationController c =
                            createLayoutAnimationFromXml(context, parser,
                                    android.util.Xml.asAttributeSet(parser));
                    if (c != null) {
                        if (c.getAnimation() == null) c.setAnimation(new Animation() {});
                        return c;
                    }
                }
            } catch (Throwable ignored) {
                // See the never-throws note above.
            } finally {
                if (parser != null) parser.close();
            }
        }
        return new android.view.animation.LayoutAnimationController(new Animation() {});
    }

    /** AOSP createLayoutAnimationFromXml(Context, XmlPullParser, AttributeSet)
     *  (AnimationUtils.java:233-261). Same walk as createAnimationFromXml
     *  below: descend from the current depth and build on the first START_TAG
     *  we recognise. AOSP throws InflateException on an unknown element; we
     *  return null and let the caller fall back (never-throws contract). */
    private static android.view.animation.LayoutAnimationController
            createLayoutAnimationFromXml(Context c,
                    org.xmlpull.v1.XmlPullParser parser,
                    android.util.AttributeSet attrs) throws Exception {
        android.view.animation.LayoutAnimationController controller = null;
        int type;
        int depth = parser.getDepth();
        while (((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_TAG
                    || parser.getDepth() > depth)
                && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("layoutAnimation".equals(name)) {
                controller = new android.view.animation.LayoutAnimationController(c, attrs);
            } else if ("gridLayoutAnimation".equals(name)) {
                controller = new android.view.animation.GridLayoutAnimationController(c, attrs);
            }
        }
        return controller;
    }
    // ==================================================================
    // PART C -- real interpolator-XML inflation.
    //
    // WHY THIS BECOMES LOAD-BEARING NOW. loadInterpolator(Context,int) above
    // answered LinearInterpolator for EVERY resource id. That was invisible
    // while nothing consumed the result -- but AnimatorInflater now sets the
    // returned interpolator on every animator declaring android:interpolator
    // (1,631 occurrences across the 942 animator XML files in the local
    // corpus), so a wrong answer here turns into wrong motion fleet-wide.
    //
    // Element frequencies measured over the corpus's res/interpolator*/ and
    // res/animator*/ dirs, 2026-08-09 -- every class already exists here:
    //     <pathInterpolator> (dominant), <linearInterpolator> 26,
    //     <overshootInterpolator> 5, <decelerateInterpolator> 4,
    //     <accelerateInterpolator> 4, <accelerateDecelerateInterpolator> 4.
    //
    // SCOPE, deliberately drawn at "what we can answer EXACTLY":
    // every element above is built exactly, and <pathInterpolator> with
    // explicit control points maps straight onto PathInterpolator's cubic
    // ctor. <pathInterpolator android:pathData="..."> still falls back to
    // linear -- i.e. exactly today's behaviour, no regression -- because
    // answering it needs THREE pieces we do not have, and faking it with a
    // partial parser would be the workaround shape CLAUDE.md forbids:
    //   1. android.util.PathParser.createPathFromPathData(String). The parser
    //      EXISTS but is private: VectorDrawable.java:669 parsePathData(String).
    //      Extract it to android.util.PathParser (AOSP's own home for it) and
    //      have VectorDrawable delegate.
    //   2. A real PathInterpolator(Path). Ours (PathInterpolator.java:42) is a
    //      stub that discards the path and returns a linear curve, so even a
    //      correct Path would not help until it samples the path into mX/mY.
    //   3. Then delete the fallback below.
    // Measured cost of leaving it: of the 230 pathData-bearing
    // <pathInterpolator> occurrences, 138 are a single cubic and 92 are not
    // (68 are the multi-segment "M 0,0 l 1,0 l 0,1" progress curve, 23 are a
    // @string/ reference to Material's multi-cubic "emphasized" easing).
    // ==================================================================
    private static final int ATTR_FACTOR       = 0x010101d3;
    private static final int ATTR_CYCLES       = 0x010101d4;
    private static final int ATTR_TENSION      = 0x0101026a;
    private static final int ATTR_EXTRATENSION = 0x0101026b;
    private static final int ATTR_CONTROLX1    = 0x010103fc;
    private static final int ATTR_CONTROLY1    = 0x010103fd;
    private static final int ATTR_CONTROLX2    = 0x010103fe;
    private static final int ATTR_CONTROLY2    = 0x010103ff;
    private static final int ATTR_PATHDATA     = 0x01010405;
    // Ids read out of the API-30 platform resource table (resources.arsc of
    // framework-res.apk, via debug/extract_fw_res.py), not transcribed.

    /** AOSP AnimationUtils.loadInterpolator(Resources, Theme, int) -- the
     *  overload AnimatorInflater calls. */
    public static Interpolator loadInterpolator(android.content.res.Resources res,
            android.content.res.Resources.Theme theme, int id) {
        android.content.res.XmlResourceParser parser = null;
        try {
            parser = res.getAnimation(id);
            Interpolator i = createInterpolatorFromXml(res, theme, parser);
            if (i != null) return i;
        } catch (Throwable ignored) {
            // DELIBERATE DEVIATION: AOSP throws NotFoundException here. This
            // method's pre-existing contract is never-throws, and its callers
            // (MaterialCardViewHelper field init, and now AnimatorInflater's
            // per-animator interpolator read) are not all written for a throw.
            // A wrong CURVE is a far cheaper failure than an aborted inflate.
        } finally {
            if (parser != null) parser.close();
        }
        return new LinearInterpolator();
    }

    /** AOSP AnimationUtils.createInterpolatorFromXml: dispatch purely on
     *  element name, the same shape as AnimatorInflater's. */
    private static Interpolator createInterpolatorFromXml(
            android.content.res.Resources res, android.content.res.Resources.Theme theme,
            org.xmlpull.v1.XmlPullParser parser) throws Exception {
        Interpolator interpolator = null;
        int type;
        int depth = parser.getDepth();
        while (((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_TAG
                    || parser.getDepth() > depth)
                && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            android.util.AttributeSet attrs = android.util.Xml.asAttributeSet(parser);
            String name = parser.getName();
            if (name.equals("linearInterpolator")) {
                interpolator = new LinearInterpolator();
            } else if (name.equals("accelerateInterpolator")) {
                interpolator = new AccelerateInterpolator(f(res, theme, attrs, ATTR_FACTOR, 1.0f));
            } else if (name.equals("decelerateInterpolator")) {
                interpolator = new DecelerateInterpolator(f(res, theme, attrs, ATTR_FACTOR, 1.0f));
            } else if (name.equals("accelerateDecelerateInterpolator")) {
                interpolator = new AccelerateDecelerateInterpolator();
            } else if (name.equals("cycleInterpolator")) {
                interpolator = new CycleInterpolator(f(res, theme, attrs, ATTR_CYCLES, 1.0f));
            } else if (name.equals("anticipateInterpolator")) {
                interpolator = new AnticipateInterpolator(f(res, theme, attrs, ATTR_TENSION, 2.0f));
            } else if (name.equals("overshootInterpolator")) {
                interpolator = new OvershootInterpolator(f(res, theme, attrs, ATTR_TENSION, 2.0f));
            } else if (name.equals("anticipateOvershootInterpolator")) {
                interpolator = new AnticipateOvershootInterpolator(
                        f(res, theme, attrs, ATTR_TENSION, 2.0f)
                                * f(res, theme, attrs, ATTR_EXTRATENSION, 1.5f));
            } else if (name.equals("bounceInterpolator")) {
                interpolator = new BounceInterpolator();
            } else if (name.equals("pathInterpolator")) {
                interpolator = pathInterpolator(res, theme, attrs);
            } else {
                throw new RuntimeException("Unknown interpolator name: " + parser.getName());
            }
        }
        return interpolator;
    }

    private static Interpolator pathInterpolator(android.content.res.Resources res,
            android.content.res.Resources.Theme theme, android.util.AttributeSet attrs) {
        final int[] sa = { ATTR_CONTROLX1, ATTR_CONTROLY1, ATTR_CONTROLX2,
                           ATTR_CONTROLY2, ATTR_PATHDATA };
        android.content.res.TypedArray a = (theme != null)
                ? theme.obtainStyledAttributes(attrs, sa, 0, 0)
                : res.obtainAttributes(attrs, sa);
        try {
            if (a.getString(4) != null) {
                // See the scope note above: needs PathParser + a real
                // PathInterpolator(Path). Same answer as before this change.
                return new LinearInterpolator();
            }
            if (a.hasValue(2) || a.hasValue(3)) {
                return new PathInterpolator(a.getFloat(0, 0f), a.getFloat(1, 0f),
                        a.getFloat(2, 0f), a.getFloat(3, 0f));
            }
            return new PathInterpolator(a.getFloat(0, 0f), a.getFloat(1, 0f));
        } finally {
            a.recycle();
        }
    }

    private static float f(android.content.res.Resources res,
            android.content.res.Resources.Theme theme, android.util.AttributeSet attrs,
            int attrId, float def) {
        final int[] sa = { attrId };
        android.content.res.TypedArray a = (theme != null)
                ? theme.obtainStyledAttributes(attrs, sa, 0, 0)
                : res.obtainAttributes(attrs, sa);
        try { return a.getFloat(0, def); } finally { a.recycle(); }
    }


    // ==================================================================
    // PART D -- real R.anim XML inflation.
    //
    // AOSP builds each element with its (Context, AttributeSet) ctor and reads
    // the attributes through a TypedArray, because on a device aapt has already
    // compiled "50%p" into a TYPE_FRACTION TypedValue carrying
    // COMPLEX_UNIT_FRACTION_PARENT (Animation.Description.parseValue,
    // android16-release Animation.java:1560-1600).
    //
    // OUR PIPELINE DELIVERS TEXT, NOT A COMPILED TypedValue. Resources.getAnimation
    // -> getXml -> ResourceIndex.openXml hands back the apktool-DECODED XML and
    // com.macapk.loader.DomXmlResourceParser reads it as text, so the attribute
    // arrives as the literal String "50%p". parseVal below is therefore the
    // string-form equivalent of Description.parseValue: it reproduces exactly the
    // same four answers (parent fraction / self fraction / dimension in px /
    // plain float) from the pre-aapt spelling. This is the same shape as the
    // interpolator half in PART C, which also reads raw attributes.
    //
    // Reading raw attribute text rather than going through TypedArray also keeps
    // this path off the framework attr-id table: see CLAUDE.md's warning that
    // this dev machine has a stale androguard install silently answering some
    // framework attr lookups that would fail on a properly-provisioned one.
    // ==================================================================
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** The attribute's raw XML text, or null when absent. Namespace handling
     *  mirrors TypedArray.setValueAt: prefer the explicit android namespace,
     *  then the no-namespace form (our decoded XML uses both spellings). */
    static String rawAttr(android.util.AttributeSet attrs, String name) {
        if (attrs == null || name == null) return null;
        String v = null;
        try { v = attrs.getAttributeValue(ANDROID_NS, name); } catch (Throwable ignored) {}
        if (v == null) {
            try { v = attrs.getAttributeValue(null, name); } catch (Throwable ignored) {}
        }
        return v;
    }

    /** AOSP TypedArray.hasValue equivalent for this path. AnimationSet's ctor
     *  needs it to decide which of its properties were declared. */
    static boolean hasAttr(android.util.AttributeSet attrs, String name) {
        return rawAttr(attrs, name) != null;
    }

    /** The string-form equivalent of Animation.Description.parseValue's four
     *  TypedValue branches. `kind` says which branch matched so callers that
     *  care about the difference between "a bare scale factor" and "a dimension"
     *  (ScaleAnimation.resolveScale) can still tell them apart, which a plain
     *  float return could not. */
    static final class Val {
        static final int NONE   = -1; // attribute absent / unparseable
        static final int FLOAT  = 0;  // "0.5"        -> TypedValue.TYPE_FLOAT
        static final int SELF   = 1;  // "50%"        -> FRACTION, unit FRACTION
        static final int PARENT = 2;  // "50%p"       -> FRACTION, unit FRACTION_PARENT
        static final int DIMEN  = 3;  // "12dp"       -> TYPE_DIMENSION (value already px)
        final int kind;
        final float value;
        Val(int k, float v) { kind = k; value = v; }
    }

    // Suffixes in the order AOSP's aapt accepts them. "dip" MUST precede "dp"
    // or "12dip" would be split as "12di" + "p".
    private static final String[] DIMEN_SUFFIX = { "dip", "dp", "sp", "px", "pt", "in", "mm" };
    private static final int[] DIMEN_UNIT = {
        android.util.TypedValue.COMPLEX_UNIT_DIP, android.util.TypedValue.COMPLEX_UNIT_DIP,
        android.util.TypedValue.COMPLEX_UNIT_SP,  android.util.TypedValue.COMPLEX_UNIT_PX,
        android.util.TypedValue.COMPLEX_UNIT_PT,  android.util.TypedValue.COMPLEX_UNIT_IN,
        android.util.TypedValue.COMPLEX_UNIT_MM,
    };

    static Val parseVal(Context c, String raw) {
        if (raw == null) return new Val(Val.NONE, 0f);
        String s = raw.trim();
        if (s.isEmpty()) return new Val(Val.NONE, 0f);
        try {
            if (s.endsWith("%p")) {
                // aapt stores "50%p" as complexToFloat -> 0.5, i.e. a 0..1
                // fraction, NOT 50. Divide here for the same answer.
                return new Val(Val.PARENT, Float.parseFloat(s.substring(0, s.length() - 2)) / 100f);
            }
            if (s.endsWith("%")) {
                return new Val(Val.SELF, Float.parseFloat(s.substring(0, s.length() - 1)) / 100f);
            }
            for (int i = 0; i < DIMEN_SUFFIX.length; i++) {
                String suf = DIMEN_SUFFIX[i];
                if (s.length() > suf.length() && s.endsWith(suf)) {
                    float n = Float.parseFloat(s.substring(0, s.length() - suf.length()));
                    android.util.DisplayMetrics dm = metricsOf(c);
                    // AOSP: Description.parseValue's TYPE_DIMENSION branch is
                    // complexToDimension(data, metrics), i.e. resolved to px.
                    return new Val(Val.DIMEN,
                            android.util.TypedValue.applyDimension(DIMEN_UNIT[i], n, dm));
                }
            }
            return new Val(Val.FLOAT, Float.parseFloat(s));
        } catch (Throwable ignored) {
            return new Val(Val.NONE, 0f);
        }
    }

    private static android.util.DisplayMetrics metricsOf(Context c) {
        try {
            if (c != null && c.getResources() != null
                    && c.getResources().getDisplayMetrics() != null) {
                return c.getResources().getDisplayMetrics();
            }
        } catch (Throwable ignored) {}
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        dm.setToDefaults();
        return dm;
    }

    /** A float-valued animation attribute (fromAlpha, fromDegrees, ...). */
    static float animFloat(Context c, android.util.AttributeSet attrs, String name, float def) {
        Val v = parseVal(c, rawAttr(attrs, name));
        return v.kind == Val.NONE ? def : v.value;
    }

    /** An int-valued animation attribute (duration, startOffset, repeatCount).
     *  Handles the "@integer/..." reference form and repeatCount="infinite". */
    static int animInt(Context c, android.util.AttributeSet attrs, String name, int def) {
        String raw = rawAttr(attrs, name);
        if (raw == null) return def;
        String s = raw.trim();
        if (s.isEmpty()) return def;
        if ("infinite".equals(s)) return Animation.INFINITE;
        if (s.charAt(0) == '@') return resolveIntegerRef(c, s, def);
        try { return (int) Float.parseFloat(s); } catch (Throwable ignored) { return def; }
    }

    /** "@integer/x" / "@android:integer/config_shortAnimTime". App-local ids go
     *  through getIdentifier + Resources.getInteger. The three platform
     *  config_*AnimTime integers are answered from AOSP's own values
     *  (core/res/res/values/config.xml, android16-release: 200 / 400 / 500)
     *  because we do not ship framework-res.apk to resolve them from -- they
     *  are framework constants we reimplement, exactly like an android.R id. */
    private static int resolveIntegerRef(Context c, String ref, int def) {
        try {
            String body = ref.substring(1);
            if (body.startsWith("+")) body = body.substring(1);
            String pkg = null;
            int colon = body.indexOf(':');
            if (colon >= 0) { pkg = body.substring(0, colon); body = body.substring(colon + 1); }
            String type = "integer", name = body;
            int slash = body.indexOf('/');
            if (slash >= 0) { type = body.substring(0, slash); name = body.substring(slash + 1); }
            if ("android".equals(pkg)) {
                if ("config_shortAnimTime".equals(name)) return 200;
                if ("config_mediumAnimTime".equals(name)) return 400;
                if ("config_longAnimTime".equals(name)) return 500;
                return def;
            }
            if (c == null || c.getResources() == null) return def;
            int id = c.getResources().getIdentifier(name, type,
                    pkg != null ? pkg : c.getPackageName());
            if (id == 0) return def;
            return c.getResources().getInteger(id);
        } catch (Throwable ignored) {
            return def;
        }
    }

    static boolean animBool(android.util.AttributeSet attrs, String name, boolean def) {
        String raw = rawAttr(attrs, name);
        if (raw == null) return def;
        String s = raw.trim();
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return def;
    }

    /** An enum-or-int attribute. AOSP's TypedArray.getInt resolves the enum
     *  spelling from the attr declaration; we carry the mapping explicitly. */
    static int animEnum(android.util.AttributeSet attrs, String name,
            String[] names, int[] values, int def) {
        String raw = rawAttr(attrs, name);
        if (raw == null) return def;
        String s = raw.trim();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(s)) return values[i];
        }
        try { return Integer.parseInt(s); } catch (Throwable ignored) { return def; }
    }

    /** android:interpolator="@..." on an <alpha>/<translate>/<set>/... element.
     *  Returns null when absent or unresolvable, so the caller keeps AOSP's
     *  ensureInterpolator() default (AccelerateDecelerateInterpolator). */
    static Interpolator animInterpolator(Context c, android.util.AttributeSet attrs) {
        String raw = rawAttr(attrs, "interpolator");
        if (raw == null || c == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.charAt(0) != '@') return null;
        try {
            String body = s.substring(1);
            if (body.startsWith("+")) body = body.substring(1);
            String pkg = null;
            int colon = body.indexOf(':');
            if (colon >= 0) { pkg = body.substring(0, colon); body = body.substring(colon + 1); }
            String type = "interpolator", name = body;
            int slash = body.indexOf('/');
            if (slash >= 0) { type = body.substring(0, slash); name = body.substring(slash + 1); }
            if ("android".equals(pkg)) return platformInterpolator(name);
            android.content.res.Resources res = c.getResources();
            if (res == null) return null;
            int id = res.getIdentifier(name, type, pkg != null ? pkg : c.getPackageName());
            if (id == 0) return null;
            android.content.res.Resources.Theme theme = null;
            try { theme = c.getTheme(); } catch (Throwable ignored) {}
            return loadInterpolator(res, theme, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The framework's own res/interpolator + res/anim interpolator resources.
     *  We do not ship framework-res.apk, and these are fixed framework
     *  definitions we reimplement like any other android.* surface. The three
     *  path-based curves are transcribed VERBATIM from
     *  core/res/res/interpolator/{fast_out_slow_in,fast_out_linear_in,
     *  linear_out_slow_in}.xml (android16-release), and the *_cubic / *_quint
     *  factors from {accelerate,decelerate}_{cubic,quint}.xml. */
    private static Interpolator platformInterpolator(String name) {
        if (name == null) return null;
        switch (name) {
            case "linear_interpolator":                 return new LinearInterpolator();
            case "accelerate_interpolator":             return new AccelerateInterpolator();
            case "decelerate_interpolator":             return new DecelerateInterpolator();
            case "accelerate_decelerate_interpolator":  return new AccelerateDecelerateInterpolator();
            case "anticipate_interpolator":             return new AnticipateInterpolator();
            case "overshoot_interpolator":              return new OvershootInterpolator();
            case "anticipate_overshoot_interpolator":   return new AnticipateOvershootInterpolator();
            case "bounce_interpolator":                 return new BounceInterpolator();
            case "cycle_interpolator":                  return new CycleInterpolator(1.0f);
            case "accelerate_cubic":                    return new AccelerateInterpolator(1.5f);
            case "accelerate_quint":                    return new AccelerateInterpolator(2.5f);
            case "decelerate_cubic":                    return new DecelerateInterpolator(1.5f);
            case "decelerate_quint":                    return new DecelerateInterpolator(2.5f);
            case "fast_out_slow_in":                    return new PathInterpolator(0.4f, 0f, 0.2f, 1f);
            case "fast_out_linear_in":                  return new PathInterpolator(0.4f, 0f, 1f, 1f);
            case "linear_out_slow_in":                  return new PathInterpolator(0f, 0f, 0.2f, 1f);
            default:                                    return null;
        }
    }

    /** AOSP AnimationUtils.createAnimationFromXml (android16-release
     *  AnimationUtils.java:237-281): dispatch purely on element name, recursing
     *  into <set> and adding each child to it.
     *
     *  DELIBERATE DEVIATIONS, both keeping this file's never-throws contract:
     *  AOSP throws InflateException on an unknown element name; we skip it, so
     *  one unrecognised child does not lose the whole animation. And
     *  <cliprect>/<extend> (window-transition-only elements, never used by app
     *  R.anim resources) are among the skipped -- we have no ClipRectAnimation
     *  / ExtendAnimation, and inventing one would be worse than not answering. */
    private static Animation createAnimationFromXml(Context c,
            org.xmlpull.v1.XmlPullParser parser, AnimationSet parent,
            android.util.AttributeSet attrs) throws Exception {
        Animation anim = null;
        int type;
        int depth = parser.getDepth();
        while (((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_TAG
                    || parser.getDepth() > depth)
                && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("set".equals(name)) {
                anim = new AnimationSet(c, attrs);
                createAnimationFromXml(c, parser, (AnimationSet) anim, attrs);
            } else if ("alpha".equals(name)) {
                anim = new AlphaAnimation(c, attrs);
            } else if ("scale".equals(name)) {
                anim = new ScaleAnimation(c, attrs);
            } else if ("rotate".equals(name)) {
                anim = new RotateAnimation(c, attrs);
            } else if ("translate".equals(name)) {
                anim = new TranslateAnimation(c, attrs);
            } else {
                continue;
            }
            if (parent != null) parent.addAnimation(anim);
        }
        return anim;
    }

    // ---- the four canned window animations + the presentation-time clock -----
    // Source: frameworks/base core/java/android/view/animation/AnimationUtils.java
    // (android16-release). AOSP builds each of these from an AlphaAnimation +
    // TranslateAnimation pair with a DecelerateInterpolator, using the
    // platform's own R.anim resources; the shapes below are those animations
    // written out directly, because this tree has no platform R.anim table to
    // load them from (Resources has no android-namespace anim entries).

    /** AOSP AnimationUtils.makeInAnimation(Context, boolean fromLeft): a
     *  slide-in-from-the-edge plus fade-in, 200ms (config_shortAnimTime). */
    public static Animation makeInAnimation(android.content.Context c, boolean fromLeft) {
        TranslateAnimation slide = fromLeft
                ? new TranslateAnimation(Animation.RELATIVE_TO_SELF, -1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f)
                : new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
        AnimationSet set = new AnimationSet(false);
        set.addAnimation(slide);
        set.addAnimation(new AlphaAnimation(0.0f, 1.0f));
        set.setDuration(200);
        set.setInterpolator(new DecelerateInterpolator());
        set.setStartTime(currentAnimationTimeMillis());
        return set;
    }

    /** AOSP AnimationUtils.makeOutAnimation(Context, boolean toRight): the
     *  slide-out-and-fade twin of makeInAnimation. */
    public static Animation makeOutAnimation(android.content.Context c, boolean toRight) {
        TranslateAnimation slide = toRight
                ? new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f)
                : new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, -1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
        AnimationSet set = new AnimationSet(false);
        set.addAnimation(slide);
        set.addAnimation(new AlphaAnimation(1.0f, 0.0f));
        set.setDuration(200);
        set.setInterpolator(new AccelerateInterpolator());
        set.setStartTime(currentAnimationTimeMillis());
        return set;
    }

    /** AOSP AnimationUtils.makeInChildBottomAnimation(Context): slide up from
     *  the bottom edge plus fade in, used by list/child insert animations. */
    public static Animation makeInChildBottomAnimation(android.content.Context c) {
        AnimationSet set = new AnimationSet(false);
        set.addAnimation(new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f));
        set.addAnimation(new AlphaAnimation(0.0f, 1.0f));
        set.setDuration(200);
        set.setInterpolator(new AccelerateInterpolator());
        set.setStartTime(currentAnimationTimeMillis());
        return set;
    }

    /** AOSP AnimationUtils.getExpectedPresentationTimeNanos() /
     *  ...Millis() (API 34): the time the frame being built is EXPECTED to
     *  reach the display, which AOSP reads from the Choreographer's frame-time
     *  data. Falls back to the current animation time when no frame is in
     *  flight -- which is AOSP's own fallback too (its thread-local
     *  sAnimationState.expectedPresentationTimeNanos starts at 0 and
     *  currentAnimationTimeMillis is what every caller uses instead). */
    public static long getExpectedPresentationTimeNanos() {
        return android.view.Choreographer.getInstance().macapkExpectedPresentationTimeNanos();
    }
    public static long getExpectedPresentationTimeMillis() {
        return getExpectedPresentationTimeNanos() / 1000000L;
    }
}
