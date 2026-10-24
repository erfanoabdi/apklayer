// android.animation.ObjectAnimator -- Tier 3 M7: a REAL property animator on top
// of the now-Choreographer-driven ValueAnimator. Each vsync frame the parent
// computes the interpolated value and calls applyAnimatedValue(), which we push
// onto the target's property (an android.util.Property if given, else a
// reflective set<PropertyName>() setter). Material widgets / RecyclerView
// ItemAnimator animate translationX/Y, alpha, scaleX/Y, rotation, and color
// properties through this. Gated by ValueAnimator on MACAPK_REAL_LOOPER: flag-OFF
// keeps the snap-to-end-and-set-once behavior.
package android.animation;

import android.graphics.Path;
import android.graphics.PointF;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ObjectAnimator extends ValueAnimator {
    private Object target;
    private String propertyName;
    private android.util.Property property;
    private Method cachedSetter;
    private boolean setterResolved;
    // v1.0.426: holders for ofPropertyValuesHolder (multi-property animators).
    private PropertyValuesHolder[] holders;

    // AOSP setAutoCancel: the documented idiom for a control that re-animates on
    // every tap ("a button that translates position every time it is pressed").
    // When a RUNNING animator opted in and a NEW animator on the same target and
    // the same property names starts, the running one is cancelled instead of the
    // two writing the same property every frame. AOSP keeps the running set in
    // AnimationHandler; our ValueAnimator posts itself straight onto the
    // Choreographer and has no AnimationHandler, so the equivalent weak registry
    // lives here.
    private boolean mAutoCancel = false;
    private static final ArrayList<WeakReference<ObjectAnimator>> sStarted = new ArrayList<>();

    // Path-driven modes. AOSP routes ofFloat/ofInt/ofObject(...,Path) through
    // PropertyValuesHolder -> PathKeyframes, which flattens the Path ONCE via
    // Path.approximate(0.5f) into [fraction, x, y] triples and looks the point up
    // by animation fraction. We keep exactly that table.
    private float[] mPathData;
    private String mXPropertyName, mYPropertyName;
    private android.util.Property mXProperty, mYProperty;
    private boolean mPathIsInt;
    // TypeConverter applied to the evaluated value before it reaches the property
    // (PointF -> V for the Path forms, V -> P for ofObject with a converter).
    private TypeConverter mConverter;

    public static ObjectAnimator ofFloat(Object target, String propertyName, float... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        a.setFloatValues(values);
        return a;
    }
    public static ObjectAnimator ofInt(Object target, String propertyName, int... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        a.setIntValues(values);
        return a;
    }
    public static ObjectAnimator ofArgb(Object target, String propertyName, int... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        a.setArgbValues(values);   // ARGB color interpolation in the parent
        return a;
    }
    public static ObjectAnimator ofPropertyValuesHolder(Object target, PropertyValuesHolder... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.holders = values;
        return a;
    }

    // --- AOSP multi-parameter setters (ofMultiInt / ofMultiFloat). ------------
    // Every one of these is `ObjectAnimator.ofPropertyValuesHolder(target,
    // PropertyValuesHolder.ofMulti*(...))` on AOSP too -- see
    // frameworks/base/core/java/android/animation/ObjectAnimator.java
    // (android16-release), which is exactly how each of these is written there.
    // They animate a setter taking SEVERAL parameters, e.g.
    //     ObjectAnimator.ofMultiFloat(view, "setPosition", path)
    // driving `setPosition(float x, float y)`. All six were missing, which is a
    // NoSuchMethodError at the CALL, before any animation runs.

    public static ObjectAnimator ofMultiInt(Object target, String propertyName, int[][] values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt(propertyName, values);
        return ofPropertyValuesHolder(target, pvh);
    }

    public static ObjectAnimator ofMultiInt(Object target, String propertyName,
            android.graphics.Path path) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt(propertyName, path);
        return ofPropertyValuesHolder(target, pvh);
    }

    @SafeVarargs
    public static <T> ObjectAnimator ofMultiInt(Object target, String propertyName,
            TypeConverter<T, int[]> converter, TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder pvh =
                PropertyValuesHolder.ofMultiInt(propertyName, converter, evaluator, values);
        return ofPropertyValuesHolder(target, pvh);
    }

    public static ObjectAnimator ofMultiFloat(Object target, String propertyName,
            float[][] values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat(propertyName, values);
        return ofPropertyValuesHolder(target, pvh);
    }

    public static ObjectAnimator ofMultiFloat(Object target, String propertyName,
            android.graphics.Path path) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat(propertyName, path);
        return ofPropertyValuesHolder(target, pvh);
    }

    @SafeVarargs
    public static <T> ObjectAnimator ofMultiFloat(Object target, String propertyName,
            TypeConverter<T, float[]> converter, TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder pvh =
                PropertyValuesHolder.ofMultiFloat(propertyName, converter, evaluator, values);
        return ofPropertyValuesHolder(target, pvh);
    }
    public static <T> ObjectAnimator ofObject(T target, android.util.Property<T, ?> property,
                                                android.animation.TypeEvaluator evaluator,
                                                Object... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.property = property;
        // Route through the parent's object-evaluator path.
        a.setObjectEvaluator(evaluator, values);
        return a;
    }
    // AOSP's property-BY-NAME ofObject. This is the most common form in real app
    // code (the type-safe Property<T,V> form is mostly an AndroidX-internal
    // idiom), and its absence was a hard NoSuchMethodError at the call site.
    public static ObjectAnimator ofObject(Object target, String propertyName,
                                                android.animation.TypeEvaluator evaluator,
                                                Object... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        a.setObjectEvaluator(evaluator, values);
        return a;
    }
    // AOSP ofObject with a TypeConverter: the animator interpolates in the
    // evaluator's type V and the converter maps each frame's V onto the
    // property's own type P before it is written.
    @SafeVarargs
    public static <T, V, P> ObjectAnimator ofObject(T target, android.util.Property<T, P> property,
                                                android.animation.TypeConverter<V, P> converter,
                                                android.animation.TypeEvaluator<V> evaluator,
                                                V... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.property = property;
        a.mConverter = converter;
        a.setObjectEvaluator(evaluator, values);
        return a;
    }
    // ---- Path-driven factories (AOSP) ----------------------------------
    // The animated value is the point at `fraction` along the Path. With a
    // converter the PointF is mapped onto the property's type; the two-property
    // forms drive an x property and a y property from the same point.
    public static ObjectAnimator ofObject(Object target, String propertyName,
                                                android.animation.TypeConverter<PointF, ?> converter,
                                                Path path) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        a.mConverter = converter;
        a.setPathValues(path);
        return a;
    }
    public static <T, V> ObjectAnimator ofObject(T target, android.util.Property<T, V> property,
                                                android.animation.TypeConverter<PointF, V> converter,
                                                Path path) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.property = property;
        a.mConverter = converter;
        a.setPathValues(path);
        return a;
    }
    public static ObjectAnimator ofFloat(Object target, String xPropertyName,
                                                String yPropertyName, Path path) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.mXPropertyName = xPropertyName;
        a.mYPropertyName = yPropertyName;
        a.setPathValues(path);
        return a;
    }
    public static <T> ObjectAnimator ofFloat(T target, android.util.Property<T, Float> xProperty,
                                                android.util.Property<T, Float> yProperty, Path path) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.mXProperty = xProperty;
        a.mYProperty = yProperty;
        a.setPathValues(path);
        return a;
    }
    public static ObjectAnimator ofInt(Object target, String xPropertyName,
                                                String yPropertyName, Path path) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.mXPropertyName = xPropertyName;
        a.mYPropertyName = yPropertyName;
        a.mPathIsInt = true;
        a.setPathValues(path);
        return a;
    }
    public static <T> ObjectAnimator ofInt(T target, android.util.Property<T, Integer> xProperty,
                                                android.util.Property<T, Integer> yProperty, Path path) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.mXProperty = xProperty;
        a.mYProperty = yProperty;
        a.mPathIsInt = true;
        a.setPathValues(path);
        return a;
    }

    /** AOSP PathKeyframes rejects a null/empty path outright rather than
     *  silently animating nothing. */
    private void setPathValues(Path path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("The path must not be null or empty");
        }
        mPathData = path.approximate(0.5f);
    }
    public static <T> ObjectAnimator ofArgb(T target, android.util.Property<T, Integer> property, int... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.property = property;
        a.setArgbValues(values);
        return a;
    }
    public static <T> ObjectAnimator ofFloat(T target, android.util.Property<T, Float> property, float... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.property = property;
        a.setFloatValues(values);
        return a;
    }
    public static <T> ObjectAnimator ofInt(T target, android.util.Property<T, Integer> property, int... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.property = property;
        a.setIntValues(values);
        return a;
    }

    @Override public ObjectAnimator setDuration(long duration) {
        super.setDuration(duration); return this;
    }
    // AOSP: when only an END value is supplied (the dominant form --
    // ofFloat(v,"alpha",1f), ViewPropertyAnimator, Material state transitions),
    // capture the START value from the target's current property at start() so
    // the animation runs from the current value, not 0. Without this an alpha-in
    // flashes from fully transparent, a translationY slide-in jumps from 0, etc.
    // v1.0.423.
    //
    // AOSP runs AnimationHandler.getInstance().autoCancelBasedOn(this) as start()'s
    // very FIRST statement, before any value is captured, so a prior animator still
    // writing the property is stopped before we read the current value as our start
    // point; keep that ordering.
    @Override public void start() {
        autoCancelBasedOn(this);
        captureStartValue();
        super.start();
        registerStarted();
    }

    /** AOSP ObjectAnimator.setAutoCancel. */
    public void setAutoCancel(boolean cancel) { mAutoCancel = cancel; }

    /** AOSP AnimationHandler.autoCancelBasedOn: cancel every still-running
     *  animator that opted into auto-cancel and shares this one's target and
     *  property names. */
    private static void autoCancelBasedOn(ObjectAnimator starting) {
        ArrayList<ObjectAnimator> doomed = null;
        synchronized (sStarted) {
            for (int i = sStarted.size() - 1; i >= 0; i--) {
                ObjectAnimator other = sStarted.get(i).get();
                if (other == null || !other.isRunning()) { sStarted.remove(i); continue; }
                if (other == starting) continue;
                if (starting.shouldAutoCancel(other)) {
                    if (doomed == null) doomed = new ArrayList<>();
                    doomed.add(other);
                }
            }
        }
        // cancel() fires listeners that may start further animators, so it must
        // run outside the registry lock.
        if (doomed != null) {
            for (ObjectAnimator o : doomed) {
                try { o.cancel(); } catch (Throwable ignored) {}
            }
        }
    }

    /** Only animators that are actually pumping frames can be auto-cancel
     *  victims. On the snap-to-end path start() completes inside super.start(),
     *  so nothing is ever registered and auto-cancel is inert -- which is
     *  correct, there is no running animation to cancel. */
    private void registerStarted() {
        if (!isRunning()) return;
        synchronized (sStarted) {
            for (int i = sStarted.size() - 1; i >= 0; i--) {
                ObjectAnimator o = sStarted.get(i).get();
                if (o == null || !o.isRunning()) { sStarted.remove(i); continue; }
                if (o == this) return;
            }
            sStarted.add(new WeakReference<ObjectAnimator>(this));
        }
    }

    /** AOSP: the flag that matters is the RUNNING animator's, not the starting
     *  one's -- an animator opts in to being cancelled by newcomers. */
    private boolean shouldAutoCancel(ObjectAnimator anim) {
        return anim != null && anim.mAutoCancel && hasSameTargetAndProperties(anim);
    }

    private boolean hasSameTargetAndProperties(ObjectAnimator anim) {
        if (anim == null || anim.getTarget() != getTarget() || getTarget() == null) return false;
        String[] mine = animatedPropertyNames();
        String[] theirs = anim.animatedPropertyNames();
        if (mine == null || theirs == null || mine.length != theirs.length) return false;
        for (int i = 0; i < mine.length; i++) {
            // AOSP bails out when a name is unknown rather than guessing a match.
            if (mine[i] == null || !mine[i].equals(theirs[i])) return false;
        }
        return true;
    }

    /** The property names this animator writes, in AOSP's PropertyValuesHolder
     *  order. Null means "cannot be determined", which never matches. */
    private String[] animatedPropertyNames() {
        if (holders != null && holders.length > 0) {
            String[] n = new String[holders.length];
            for (int i = 0; i < holders.length; i++) {
                n[i] = (holders[i] == null) ? null : holders[i].getPropertyName();
            }
            return n;
        }
        PropertyValuesHolder[] vals = getValues();
        if (vals != null && vals.length > 0) {
            String[] n = new String[vals.length];
            for (int i = 0; i < vals.length; i++) {
                n[i] = (vals[i] == null) ? null : vals[i].getPropertyName();
            }
            return n;
        }
        if (mXPropertyName != null || mYPropertyName != null) {
            return new String[]{ mXPropertyName, mYPropertyName };
        }
        if (mXProperty != null || mYProperty != null) {
            return new String[]{ mXProperty == null ? null : mXProperty.getName(),
                                 mYProperty == null ? null : mYProperty.getName() };
        }
        if (propertyName != null) return new String[]{ propertyName };
        if (property != null) return new String[]{ property.getName() };
        return null;
    }
    /**
     * AOSP ObjectAnimator.setupStartValues() (ObjectAnimator.java:933-943):
     * initialize, then ask each PropertyValuesHolder to take its START value off
     * the target. captureStartValue() below already IS that operation for this
     * animator (it is what start() calls), so this is the public entry point on
     * to the same path -- with one difference AOSP shares: setupStartValues()
     * overwrites the first value even when the spec already had two ends, which
     * is the whole point of the call (LayoutTransition invokes it after moving
     * the view, to re-anchor a canned animator to where the view actually is).
     */
    @Override
    public void setupStartValues() {
        if (target == null) return;
        captureStartValue();
        if (holders != null) {
            for (PropertyValuesHolder h : holders) {
                if (h == null || h.macapkNeedsStartValue()) continue;  // just handled above
                Object start = readPropertyOf(h.getProperty(), h.getPropertyName());
                if (start != null) h.macapkOverwriteStartValue(start);
            }
        }
    }

    /**
     * AOSP ObjectAnimator.setupEndValues() (ObjectAnimator.java:945-956) -- the
     * mirror image: each holder's LAST value is taken off the target.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void setupEndValues() {
        if (target == null) return;
        int vt = macapkValueType();
        float[] fv = macapkFloatValues();
        int[] iv = macapkIntValues();
        Number cur = readProperty();
        if (cur != null) {
            if (vt == 1 && fv != null && fv.length > 0) {              // T_FLOAT
                float[] v = fv.clone();
                v[v.length - 1] = cur.floatValue();
                setFloatValues(v);
            } else if (vt == 2 && iv != null && iv.length > 0) {       // T_INT
                int[] v = iv.clone();
                v[v.length - 1] = cur.intValue();
                setIntValues(v);
            } else if (vt == 3 && iv != null && iv.length > 0) {       // T_ARGB
                int[] v = iv.clone();
                v[v.length - 1] = cur.intValue();
                setArgbValues(v);
            }
        }
        if (holders != null) {
            for (PropertyValuesHolder h : holders) {
                if (h == null) continue;
                Object end = readPropertyOf(h.getProperty(), h.getPropertyName());
                if (end != null) h.macapkSetEndValue(end);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void captureStartValue() {
        if (target == null) return;
        int vt = macapkValueType();
        float[] fv = macapkFloatValues();
        int[] iv = macapkIntValues();
        Number cur = readProperty();
        if (cur != null) {
            if (vt == 1 && fv != null && fv.length == 1) {            // T_FLOAT
                setFloatValues(cur.floatValue(), fv[0]);
            } else if (vt == 2 && iv != null && iv.length == 1) {     // T_INT
                setIntValues(cur.intValue(), iv[0]);
            } else if (vt == 3 && iv != null && iv.length == 1) {     // T_ARGB
                setArgbValues(new int[]{ cur.intValue(), iv[0] });
            }
        }
        // The ofPropertyValuesHolder spelling of the same thing. AOSP fills a
        // one-ended holder's missing start keyframe from the target in
        // ObjectAnimator.initAnimation -> PropertyValuesHolder
        // .setupSetterAndGetter -> setupValue; the holder path here never did,
        // so PropertyValuesHolder.ofFloat(View.ALPHA, 0f) evaluated as the
        // constant 0 and the view snapped instead of fading. See
        // PropertyValuesHolder.macapkNeedsStartValue.
        //
        // THIS MUST RUN ON THE CLONE, AFTER setTarget. It does: start() calls
        // captureStartValue(), and LayoutTransition-style usage clones the
        // template and calls setTarget(child) before start(). Reading the
        // template's (usually null) target would capture the wrong start value
        // or none at all.
        if (holders != null) {
            for (PropertyValuesHolder h : holders) {
                if (h == null || !h.macapkNeedsStartValue()) continue;
                Object start = readPropertyOf(h.getProperty(), h.getPropertyName());
                if (start != null) h.macapkSetStartValue(start);
            }
        }
    }

    /** readProperty() for an arbitrary property handle / name rather than this
     *  animator's own. Returns the raw value (not narrowed to Number) so an
     *  ofObject holder can take a non-numeric start too. */
    @SuppressWarnings("unchecked")
    private Object readPropertyOf(android.util.Property p, String name) {
        try {
            if (p != null) return p.get(target);
            if (name == null || name.isEmpty()) return null;
            String g = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return target.getClass().getMethod(g).invoke(target);
        } catch (Throwable ignored) { return null; }
    }
    @SuppressWarnings("unchecked")
    private Number readProperty() {
        try {
            if (property != null) {
                Object r = property.get(target);
                return (r instanceof Number) ? (Number) r : null;
            }
            if (propertyName == null) return null;
            String g = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            Object r = target.getClass().getMethod(g).invoke(target);
            return (r instanceof Number) ? (Number) r : null;
        } catch (Throwable ignored) { return null; }
    }

    // AOSP ObjectAnimator.clone() is a one-liner ("final ObjectAnimator anim =
    // (ObjectAnimator) super.clone(); return anim;") because AOSP funnels
    // ofPropertyValuesHolder() through the base class's mValues field (already
    // deep-copied by ValueAnimator.clone(), which this inherits unchanged -- see
    // that method's comment for the mFrameCallback fix it also carries), and
    // resolves+caches each property's setter INSIDE the PropertyValuesHolder
    // object itself, which travels with that same deep copy. Our simpler design
    // (see the class header) keeps ofPropertyValuesHolder's array in a SEPARATE
    // `holders` field here, and caches the reflective setter directly on this
    // class via `cachedSetter`/`setterResolved` (keyed by the CURRENT target's
    // class). Both therefore need the equivalent fixup at this level:
    //   - `holders`: without a deep copy, two started clones sharing one
    //     PropertyValuesHolder array element would both call its mutators
    //     (setFloatValues/setIntValues) and fight over its cached value type.
    //   - `cachedSetter`: LayoutTransition-style usage always calls
    //     anim.setTarget(child) on a FRESH clone with a target the template
    //     never had (often null), so any setter cached against the template's
    //     old target class would be a Method the new target may not even
    //     declare; applyAnimatedValue's invoke would then silently no-op (the
    //     call site swallows the resulting Throwable), so the property never
    //     updates on that frame or ever. Clearing the cache makes it re-resolve
    //     against whatever target the clone is actually given.
    @Override
    public ObjectAnimator clone() {
        ObjectAnimator anim = (ObjectAnimator) super.clone();
        if (holders != null) {
            PropertyValuesHolder[] copy = new PropertyValuesHolder[holders.length];
            for (int i = 0; i < holders.length; i++) {
                copy[i] = (holders[i] != null) ? holders[i].clone() : null;
            }
            anim.holders = copy;
        }
        anim.setterResolved = false;
        anim.cachedSetter = null;
        anim.mMultiSetter = null;
        return anim;
    }

    @Override public void setTarget(Object target) { this.target = target; }
    public Object getTarget() { return target; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; this.setterResolved = false; }
    public String getPropertyName() { return propertyName; }
    /** AOSP ObjectAnimator.setProperty: when the animator already has value holders
     *  (ofPropertyValuesHolder, or ofFloat/ofInt then setProperty), the Property goes
     *  onto the FIRST holder -- `mValues[0].setProperty(property)` -- so that holder
     *  writes through Property.set from then on. Material's ExtendedFloatingActionButton
     *  relies on exactly this: its shrink/extend MotionSpec builds a "labelOpacity"
     *  holder from XML and then calls setProperty(LABEL_OPACITY_PROPERTY); with no
     *  setLabelOpacity() method to find by name, the label never faded and a shrunk FAB
     *  kept drawing its text past its own edge (ytdlnis 1.9.1's paste-link FAB,
     *  2026-10-19). MACAPK_ANIM_HOLDER_PROPERTY=0 restores the field-only store. */
    public void setProperty(android.util.Property property) {
        if (HOLDER_PROPERTY && holders != null && holders.length > 0 && holders[0] != null) {
            holders[0].setProperty(property);
        }
        this.property = property;
    }
    static final boolean HOLDER_PROPERTY =
        !"0".equals(System.getenv("MACAPK_ANIM_HOLDER_PROPERTY"));

    /** Tier 3 M7: push the per-frame value onto the target property. */
    @Override @SuppressWarnings("unchecked")
    protected void applyAnimatedValue(Object value, float fraction) {
        if (target == null) return;
        // ofPropertyValuesHolder: apply EACH holder's value-at-fraction to its
        // own property (the single `value` is null in this mode). v1.0.426.
        if (holders != null) {
            for (PropertyValuesHolder h : holders) {
                if (h == null) continue;
                Object v = h.macapkValueAt(fraction);
                if (v == null) continue;
                // A multi-parameter holder evaluates to an int[]/float[] whose
                // elements are the setter's ARGUMENTS, not a single value.
                if (h.macapkIsMultiValue()) { setByNameMulti(h.getPropertyName(), v); continue; }
                // A holder built on (or given) a Property writes through Property.set,
                // as AOSP PropertyValuesHolder.setAnimatedValue does (`if (mProperty !=
                // null) mProperty.set(target, getAnimatedValue())`), before any setter
                // lookup by name: an app-defined Property has no JavaBean setter.
                android.util.Property hp = HOLDER_PROPERTY ? h.getProperty() : null;
                if (hp != null) {
                    try { hp.set(target, v); continue; } catch (Throwable ignored) {}
                }
                setByName(h.getPropertyName(), v);
            }
            return;
        }
        // Path mode: the value is the point at `fraction` along the flattened
        // path. Either it feeds two coupled scalar properties, or it is mapped
        // through the TypeConverter onto a single property.
        if (mPathData != null) {
            PointF p = pointAtFraction(fraction);
            if (mXPropertyName != null || mYPropertyName != null
                    || mXProperty != null || mYProperty != null) {
                Object xv = mPathIsInt ? (Object) Integer.valueOf(Math.round(p.x))
                                       : (Object) Float.valueOf(p.x);
                Object yv = mPathIsInt ? (Object) Integer.valueOf(Math.round(p.y))
                                       : (Object) Float.valueOf(p.y);
                applyTo(mXProperty, mXPropertyName, xv);
                applyTo(mYProperty, mYPropertyName, yv);
            } else {
                applyTo(property, propertyName,
                        (mConverter != null) ? convertValue(p) : p);
            }
            return;
        }
        if (value == null) return;
        if (mConverter != null) {
            value = convertValue(value);
            if (value == null) return;
        }
        if (property != null) {
            try { property.set(target, value); return; } catch (Throwable ignored) {}
        }
        if (propertyName == null) return;
        Method setter = resolveSetter(value);
        if (setter == null) return;
        try { setter.invoke(target, value); }
        catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(Object v) {
        try { return mConverter.convert(v); } catch (Throwable ignored) { return null; }
    }

    /** Write one value through a Property if we have one, else by setter name. */
    @SuppressWarnings("unchecked")
    private void applyTo(android.util.Property prop, String name, Object value) {
        if (value == null) return;
        if (prop != null) {
            try { prop.set(target, value); return; } catch (Throwable ignored) {}
        }
        if (name == null) return;
        if (applyViaConverterType(name, value)) return;
        setByName(name, value);
    }

    /** AOSP resolves the setter for a converted value by the CONVERTER's
     *  declared target type (PropertyValuesHolder.setupSetter ->
     *  TypeConverter.getTargetType()), not by the runtime class of the value.
     *  A converter declared to produce a supertype therefore still finds its
     *  setter, which a runtime-class lookup would miss. */
    private boolean applyViaConverterType(String name, Object value) {
        if (mConverter == null || name.isEmpty()) return false;
        Class<?> to = mConverter.getToClass();
        if (to == null) return false;
        try {
            String n = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method m = target.getClass().getMethod(n, to);
            m.invoke(target, value);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    /** Read the [fraction, x, y] triple table Path.approximate() produced,
     *  interpolating between the two samples bracketing `fraction`. Mirrors
     *  AOSP PathKeyframes.getValue(fraction). */
    private PointF pointAtFraction(float fraction) {
        final float[] d = mPathData;
        final int n = d.length / 3;
        if (n == 0) return new PointF(0f, 0f);
        if (n == 1) return new PointF(d[1], d[2]);
        if (fraction <= d[0]) return new PointF(d[1], d[2]);
        final int last = (n - 1) * 3;
        if (fraction >= d[last]) return new PointF(d[last + 1], d[last + 2]);
        int lo = 0, hi = n - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            float f = d[mid * 3];
            if (fraction < f) hi = mid - 1;
            else if (fraction > f) lo = mid + 1;
            else return new PointF(d[mid * 3 + 1], d[mid * 3 + 2]);
        }
        // Loop exit invariant: hi == lo - 1, and d[hi] < fraction < d[lo].
        int i0 = Math.max(0, hi), i1 = Math.min(n - 1, lo);
        float f0 = d[i0 * 3], f1 = d[i1 * 3];
        float t = (f1 > f0) ? (fraction - f0) / (f1 - f0) : 0f;
        return new PointF(d[i0 * 3 + 1] + t * (d[i1 * 3 + 1] - d[i0 * 3 + 1]),
                          d[i0 * 3 + 2] + t * (d[i1 * 3 + 2] - d[i0 * 3 + 2]));
    }

    /** Resolve and invoke set<Name>(value) on the target, preferring the
     *  primitive-typed overload. Used by the PVH per-holder application; each
     *  holder caches nothing (different names), so this resolves per call. */
    /**
     * Invoke a MULTI-parameter setter with the elements of an int[]/float[]
     * spread as arguments.
     *
     * AOSP MultiFloatValuesHolder.setupSetter (PropertyValuesHolder.java:1509-
     * 1546) resolves it in this order: first `set<Name>` taking N float/int
     * parameters, then the raw property name taking N parameters -- because the
     * ofMulti* javadoc says the name "can also be the case-sensitive name of the
     * entire setter method". AOSP does the lookup in JNI for speed; the search
     * ORDER, which is the observable part, is reproduced here.
     */
    private void setByNameMulti(String name, Object value) {
        if (name == null || name.isEmpty() || target == null) return;
        final boolean isInt = value instanceof int[];
        final int n = isInt ? ((int[]) value).length : ((float[]) value).length;
        if (n == 0) return;
        Class<?>[] params = new Class<?>[n];
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            params[i] = isInt ? int.class : float.class;
            args[i] = isInt ? (Object) Integer.valueOf(((int[]) value)[i])
                            : (Object) Float.valueOf(((float[]) value)[i]);
        }
        java.lang.reflect.Method m = mMultiSetter;
        if (m == null) {
            Class<?> tc = target.getClass();
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            try { m = tc.getMethod(setterName, params); } catch (NoSuchMethodException ignored) {}
            if (m == null) {
                try { m = tc.getMethod(name, params); } catch (NoSuchMethodException ignored) {}
            }
            if (m == null) return;
            mMultiSetter = m;
        }
        try { m.invoke(target, args); } catch (Throwable ignored) {}
    }

    /** Cached resolution for setByNameMulti. An ObjectAnimator built by an
     *  ofMulti* factory carries exactly one holder, so one slot suffices; it is
     *  cleared by clone() along with the other per-target setter caches. */
    private transient java.lang.reflect.Method mMultiSetter;

    private void setByName(String name, Object value) {
        if (name == null || name.isEmpty()) return;
        try {
            String n = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Class<?> tc = target.getClass();
            Class<?> prim = (value instanceof Float) ? float.class
                          : (value instanceof Integer) ? int.class : null;
            Method m = null;
            if (prim != null) { try { m = tc.getMethod(n, prim); } catch (NoSuchMethodException ignored) {} }
            if (m == null)    { try { m = tc.getMethod(n, value.getClass()); } catch (NoSuchMethodException ignored) {} }
            if (m != null) m.invoke(target, value);
        } catch (Throwable ignored) {}
    }

    private Method resolveSetter(Object value) {
        if (setterResolved) return cachedSetter;
        setterResolved = true;
        cachedSetter = null;
        try {
            String n = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            Class<?> tc = target.getClass();
            // Prefer the primitive-typed setter matching the value box.
            Class<?> prim = (value instanceof Float) ? float.class
                          : (value instanceof Integer) ? int.class : null;
            if (prim != null) {
                try { cachedSetter = tc.getMethod(n, prim); } catch (NoSuchMethodException ignored) {}
            }
            if (cachedSetter == null) {
                try { cachedSetter = tc.getMethod(n, value.getClass()); } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        return cachedSetter;
    }
}
