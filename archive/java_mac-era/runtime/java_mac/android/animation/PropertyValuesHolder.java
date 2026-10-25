// android.animation.PropertyValuesHolder -- pairs a property name with a
// sequence of values for animation. v1.0.426 Tier-2: was a pure stub (stored
// only the name); multi-property ObjectAnimators declared via PVH (animate
// translationX AND alpha together, Material motion, MotionLayout) ran their
// timeline but applied NO value to the target. Now it stores the values and
// evaluates them at the master fraction; ObjectAnimator.ofPropertyValuesHolder
// pushes each holder's value onto the target every frame.
package android.animation;

import android.graphics.Path;
import android.graphics.PointF;

// AOSP fidelity: the unchecked usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("unchecked")
public class PropertyValuesHolder implements Cloneable {
    static final int T_NONE = 0, T_FLOAT = 1, T_INT = 2, T_OBJECT = 3, T_KEYFRAME = 4, T_PATH = 5;
    // The MULTI-PARAMETER forms (ofMultiInt / ofMultiFloat). Unlike every type
    // above, the evaluated value is an int[] / float[] whose elements are the
    // PARAMETERS of a multi-argument setter -- `setSize(int w, int h)`, not
    // `setSize(int[] wh)`. ObjectAnimator spreads them at apply time; see
    // macapkIsMultiValue() below and ObjectAnimator.setByNameMulti.
    static final int T_MULTI_INT = 6, T_MULTI_FLOAT = 7;
    private String propertyName;
    private float[] floatValues;
    private int[] intValues;
    private Object[] objectValues;
    private Keyframe[] keyframes;
    private TypeEvaluator evaluator;
    private int type = T_NONE;
    // AndroidX-preferred Property<T,V> idiom (View.ALPHA, View.TRANSLATION_X,
    // ...). Stored alongside propertyName (= property.getName(), which matches
    // the JavaBean setter name for every AOSP-declared Property constant, e.g.
    // "alpha" -> setAlpha) so today's reflective ObjectAnimator.setByName()
    // apply path keeps working unchanged; a Property-aware caller can also read
    // it back via getProperty() to call property.set() directly.
    private android.util.Property property;
    // Converter for the Path-driven ofObject(Property,TypeConverter,Path) form:
    // maps the PointF sampled off the path onto the property's real value type.
    private TypeConverter converter;
    // Path.approximate()'s flattened [fraction,x,y, ...] triple table, used
    // only when type == T_PATH.
    private float[] pathData;

    public static PropertyValuesHolder ofInt(String name, int... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = name; p.setIntValues(values);
        return p;
    }
    public static PropertyValuesHolder ofFloat(String name, float... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = name; p.setFloatValues(values);
        return p;
    }
    public static PropertyValuesHolder ofObject(String name, TypeEvaluator evaluator, Object... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = name; p.evaluator = evaluator; p.setObjectValues(values);
        return p;
    }
    // AOSP signature: ofKeyframe(String, Keyframe...). Each Keyframe carries a
    // fraction + value; we evaluate the value for the master fraction by lerping
    // between the bracketing keyframes (matching AOSP's KeyframeSet for the
    // un-interpolated, evenly-or-explicitly-spaced case).
    public static PropertyValuesHolder ofKeyframe(String name, Keyframe... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = name;
        p.keyframes = values;
        if (values != null && values.length > 0) p.type = T_KEYFRAME;
        return p;
    }

    // --- AOSP Property<T,V>-based factory overloads. AndroidX/Material motion
    // code (and any app following the AndroidX-recommended idiom) builds a
    // PropertyValuesHolder via View.ALPHA/View.TRANSLATION_X/a custom Property
    // constant rather than a String name; without these overloads the
    // CONSTRUCTION call itself is a NoSuchMethodError (wrong erased descriptor)
    // before any animation code runs. Signatures verified against AOSP
    // core/java/android/animation/PropertyValuesHolder.java. ---

    public static PropertyValuesHolder ofInt(android.util.Property<?, Integer> property, int... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.property = property;
        p.propertyName = (property != null) ? property.getName() : null;
        p.setIntValues(values);
        return p;
    }
    public static PropertyValuesHolder ofFloat(android.util.Property<?, Float> property, float... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.property = property;
        p.propertyName = (property != null) ? property.getName() : null;
        p.setFloatValues(values);
        return p;
    }
    // AOSP declares `property` raw (not Property<T,V>) here too.
    @SafeVarargs
    public static <V> PropertyValuesHolder ofObject(android.util.Property property,
            TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.property = property;
        p.propertyName = (property != null) ? property.getName() : null;
        p.evaluator = evaluator;
        p.setObjectValues(values);
        return p;
    }
    // Path-driven form: the animated value is the point at the master fraction
    // along the flattened path, converted onto the property's real type.
    public static <V> PropertyValuesHolder ofObject(android.util.Property<?, V> property,
            TypeConverter<PointF, V> converter, Path path) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.property = property;
        p.propertyName = (property != null) ? property.getName() : null;
        p.converter = converter;
        // Mirrors ObjectAnimator's own Path-driven factories: Path.approximate
        // flattens the curve ONCE into [fraction,x,y] triples looked up by
        // animation fraction, rather than re-walking the Path every frame.
        p.pathData = (path != null) ? path.approximate(0.5f) : new float[0];
        p.type = T_PATH;
        return p;
    }
    public static PropertyValuesHolder ofKeyframe(android.util.Property property, Keyframe... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.property = property;
        p.propertyName = (property != null) ? property.getName() : null;
        p.keyframes = values;
        if (values != null && values.length > 0) p.type = T_KEYFRAME;
        return p;
    }

    // --- AOSP multi-parameter value holders (ofMultiInt / ofMultiFloat). ------
    // These animate a setter that takes SEVERAL parameters -- `setSize(int w,
    // int h)`, `setPosition(float x, float y)`. AOSP models them as
    // MultiIntValuesHolder / MultiFloatValuesHolder, which evaluate to an
    // int[]/float[] and then call the N-argument setter with the elements
    // SPREAD as arguments (never as a single array argument). Every one of these
    // was missing, so `ObjectAnimator.ofMultiFloat(v, "setPos", path)` -- the
    // idiom AndroidX MotionLayout and animated VectorDrawables use for
    // two-parameter setters -- was a NoSuchMethodError at construction time.
    // Source: frameworks/base/core/java/android/animation/PropertyValuesHolder.java
    // :185-405 and :1444-1660 (android16-release).

    public static PropertyValuesHolder ofMultiInt(String propertyName, int[][] values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("At least 2 values must be supplied");
        }
        int numParameters = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("values must not be null");
            }
            int length = values[i].length;
            if (i == 0) {
                numParameters = length;
            } else if (length != numParameters) {
                throw new IllegalArgumentException("Values must all have the same length");
            }
        }
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.evaluator = new IntArrayEvaluator(new int[numParameters]);
        p.objectValues = values;
        p.type = T_MULTI_INT;
        return p;
    }

    public static PropertyValuesHolder ofMultiInt(String propertyName, Path path) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.converter = new PointFToIntArray();
        p.pathData = (path != null) ? path.approximate(0.5f) : new float[0];
        p.type = T_MULTI_INT;
        return p;
    }

    @SafeVarargs
    public static <V> PropertyValuesHolder ofMultiInt(String propertyName,
            TypeConverter<V, int[]> converter, TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.converter = converter;
        p.evaluator = evaluator;
        p.objectValues = values;
        p.type = T_MULTI_INT;
        return p;
    }

    public static <T> PropertyValuesHolder ofMultiInt(String propertyName,
            TypeConverter<T, int[]> converter, TypeEvaluator<T> evaluator, Keyframe... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.converter = converter;
        p.evaluator = evaluator;
        p.keyframes = values;
        p.type = T_MULTI_INT;
        return p;
    }

    public static PropertyValuesHolder ofMultiFloat(String propertyName, float[][] values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("At least 2 values must be supplied");
        }
        int numParameters = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("values must not be null");
            }
            int length = values[i].length;
            if (i == 0) {
                numParameters = length;
            } else if (length != numParameters) {
                throw new IllegalArgumentException("Values must all have the same length");
            }
        }
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.evaluator = new FloatArrayEvaluator(new float[numParameters]);
        p.objectValues = values;
        p.type = T_MULTI_FLOAT;
        return p;
    }

    public static PropertyValuesHolder ofMultiFloat(String propertyName, Path path) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.converter = new PointFToFloatArray();
        p.pathData = (path != null) ? path.approximate(0.5f) : new float[0];
        p.type = T_MULTI_FLOAT;
        return p;
    }

    @SafeVarargs
    public static <V> PropertyValuesHolder ofMultiFloat(String propertyName,
            TypeConverter<V, float[]> converter, TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.converter = converter;
        p.evaluator = evaluator;
        p.objectValues = values;
        p.type = T_MULTI_FLOAT;
        return p;
    }

    public static <T> PropertyValuesHolder ofMultiFloat(String propertyName,
            TypeConverter<T, float[]> converter, TypeEvaluator<T> evaluator, Keyframe... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.propertyName = propertyName;
        p.converter = converter;
        p.evaluator = evaluator;
        p.keyframes = values;
        p.type = T_MULTI_FLOAT;
        return p;
    }

    /**
     * AOSP PropertyValuesHolder.ofObject(Property, TypeConverter, TypeEvaluator,
     * T...) -- animate over values of type T, then CONVERT each evaluated value
     * onto the property's own type V. This was the one ofObject overload
     * missing; the three-argument (no converter) forms already existed.
     */
    @SafeVarargs
    public static <T, V> PropertyValuesHolder ofObject(android.util.Property<?, V> property,
            TypeConverter<T, V> converter, TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder p = new PropertyValuesHolder();
        p.property = property;
        p.propertyName = (property != null) ? property.getName() : null;
        p.converter = converter;
        p.evaluator = evaluator;
        p.setObjectValues((Object[]) values);
        return p;
    }

    /** AOSP PropertyValuesHolder.setConverter(TypeConverter):676. */
    public void setConverter(TypeConverter converter) { this.converter = converter; }

    /** AOSP PropertyValuesHolder.setKeyframes(Keyframe...):634. AOSP pads a
     *  single supplied keyframe out to two; we keep the array as given and let
     *  keyframeValueAt() handle the one-keyframe case by returning its value,
     *  which is the same observable result. */
    public void setKeyframes(Keyframe... values) {
        this.keyframes = values;
        if (values != null && values.length > 0 && !macapkIsMultiValue()) type = T_KEYFRAME;
    }

    /** AOSP PropertyValuesHolder.setProperty(Property):1048. Keeps propertyName
     *  in step, because our apply path resolves the setter by name. */
    public void setProperty(android.util.Property property) {
        this.property = property;
        if (property != null) this.propertyName = property.getName();
    }

    /** AOSP PropertyValuesHolder.PointFToIntArray (private static there) --
     *  the converter the ofMultiInt(String, Path) form installs: the sampled
     *  point becomes the setter's two int parameters. */
    private static class PointFToIntArray extends TypeConverter<PointF, int[]> {
        private final int[] mCoordinates = new int[2];
        PointFToIntArray() { super(PointF.class, int[].class); }
        @Override public int[] convert(PointF value) {
            mCoordinates[0] = Math.round(value.x);
            mCoordinates[1] = Math.round(value.y);
            return mCoordinates;
        }
    }

    /** AOSP PropertyValuesHolder.PointFToFloatArray (private static there). */
    private static class PointFToFloatArray extends TypeConverter<PointF, float[]> {
        private final float[] mCoordinates = new float[2];
        PointFToFloatArray() { super(PointF.class, float[].class); }
        @Override public float[] convert(PointF value) {
            mCoordinates[0] = value.x;
            mCoordinates[1] = value.y;
            return mCoordinates;
        }
    }

    /** The Property this holder was built with, or null when built from a
     *  String name. A Property-aware caller (e.g. a future ObjectAnimator
     *  enhancement) can call property.set(target, value) directly instead of
     *  going through the reflective setByName(getPropertyName(), value) path. */
    public android.util.Property getProperty() { return property; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String name) { this.propertyName = name; }
    public void setIntValues(int... values)    { this.intValues = values;    if (values != null) type = T_INT; }
    public void setFloatValues(float... values){ this.floatValues = values;  if (values != null) type = T_FLOAT; }
    public void setObjectValues(Object... values){ this.objectValues = values; if (values != null) type = T_OBJECT; }
    public void setEvaluator(TypeEvaluator evaluator) { this.evaluator = evaluator; }

    /** Evaluate this holder's value at the animation's master fraction (the
     *  values are treated as evenly spaced keyframes, matching AOSP's default). */
    Object macapkValueAt(float fraction) {
        switch (type) {
            case T_FLOAT:  return (floatValues != null && floatValues.length > 0) ? lerpFloat(floatValues, fraction) : null;
            case T_INT:    return (intValues != null && intValues.length > 0) ? Math.round(lerpIntAsFloat(intValues, fraction)) : null;
            case T_OBJECT: {
                if (objectValues == null || objectValues.length == 0) return null;
                Object v = (evaluator != null)
                        ? evaluator.evaluate(fraction, objectValues[0],
                                objectValues[objectValues.length - 1])
                        : objectValues[objectValues.length - 1];
                // AOSP calculateValue() runs every evaluated value through the
                // converter when one is installed. Only the new
                // ofObject(Property, TypeConverter, TypeEvaluator, T...) form
                // sets one on a T_OBJECT holder, so this is inert for every
                // holder that existed before.
                if (converter != null && v != null) {
                    try { return converter.convert(v); } catch (Throwable ignored) { return null; }
                }
                return v;
            }
            case T_KEYFRAME:
                return keyframeValueAt(fraction);
            case T_PATH:
                return pathValueAt(fraction);
            case T_MULTI_INT:
            case T_MULTI_FLOAT:
                return multiValueAt(fraction);
            default: return null;
        }
    }

    /** True when macapkValueAt() yields an int[]/float[] of SETTER PARAMETERS
     *  rather than a single property value. */
    boolean macapkIsMultiValue() {
        return type == T_MULTI_INT || type == T_MULTI_FLOAT;
    }

    /**
     * AOSP PropertyValuesHolder.calculateValue():
     *     Object value = mKeyframes.getValue(fraction);
     *     mAnimatedValue = mConverter == null ? value : mConverter.convert(value);
     * -- evaluate the keyframes, then run the result through the converter. All
     * four ofMulti* spellings funnel through that one shape; only WHICH keyframe
     * source is populated differs (a Path, explicit Keyframes, or the evenly
     * spaced value array).
     */
    @SuppressWarnings("unchecked")
    private Object multiValueAt(float fraction) {
        Object raw;
        if (pathData != null) {
            raw = pointAtPathFraction(fraction);
        } else if (keyframes != null && keyframes.length > 0) {
            raw = keyframeValueAt(fraction);
        } else if (objectValues != null && objectValues.length > 0) {
            raw = evenlySpacedObjectValueAt(fraction);
        } else {
            return null;
        }
        if (converter != null) {
            try { return converter.convert(raw); } catch (Throwable ignored) { return null; }
        }
        return raw;
    }

    /**
     * Bracket `fraction` between two of N evenly spaced values and interpolate
     * with the evaluator -- AOSP's KeyframeSet built from a plain value array
     * spaces its keyframes at i/(N-1). Used only by the multi-parameter forms;
     * the older T_OBJECT path deliberately keeps its own two-ended behaviour.
     */
    @SuppressWarnings("unchecked")
    private Object evenlySpacedObjectValueAt(float fraction) {
        Object[] v = objectValues;
        if (v.length == 1) return v[0];
        fraction = Math.max(0f, Math.min(1f, fraction));
        float scaled = fraction * (v.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= v.length - 1) return v[v.length - 1];
        float t = scaled - i;
        if (evaluator != null) {
            try { return evaluator.evaluate(t, v[i], v[i + 1]); } catch (Throwable ignored) {}
        }
        return v[i + 1];
    }

    // Value for the Path-driven ofObject(Property,TypeConverter,Path) form:
    // sample the flattened path at `fraction`, then hand the PointF through the
    // converter (if any) onto the property's real value type.
    @SuppressWarnings("unchecked")
    private Object pathValueAt(float fraction) {
        PointF p = pointAtPathFraction(fraction);
        if (converter != null) {
            try { return converter.convert(p); } catch (Throwable ignored) { return null; }
        }
        return p;
    }

    // Interpolate the [fraction,x,y] triple table Path.approximate() produced.
    // Mirrors ObjectAnimator.pointAtFraction (same algorithm, duplicated here
    // because PropertyValuesHolder must evaluate its own path independent of
    // whichever animator applies it).
    private PointF pointAtPathFraction(float fraction) {
        final float[] d = pathData;
        if (d == null || d.length < 3) return new PointF(0f, 0f);
        final int n = d.length / 3;
        if (n == 1) return new PointF(d[1], d[2]);
        fraction = Math.max(0f, Math.min(1f, fraction));
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

    // Find the two keyframes bracketing `fraction` and interpolate between their
    // values. Numeric keyframes lerp; object keyframes use the holder's evaluator
    // if set, else snap to the later keyframe.
    private Object keyframeValueAt(float fraction) {
        if (keyframes == null || keyframes.length == 0) return null;
        if (keyframes.length == 1) return keyframes[0].getValue();
        fraction = Math.max(0f, Math.min(1f, fraction));
        Keyframe prev = keyframes[0];
        for (int i = 1; i < keyframes.length; i++) {
            Keyframe next = keyframes[i];
            if (fraction <= next.getFraction() || i == keyframes.length - 1) {
                float span = next.getFraction() - prev.getFraction();
                float t = span <= 0 ? 0f : (fraction - prev.getFraction()) / span;
                Object pv = prev.getValue();
                Object nv = next.getValue();
                if (evaluator != null) return evaluator.evaluate(t, pv, nv);
                if (pv instanceof Number && nv instanceof Number) {
                    double a = ((Number) pv).doubleValue();
                    double b = ((Number) nv).doubleValue();
                    double r = a + t * (b - a);
                    if (pv instanceof Integer || nv instanceof Integer) return (int) Math.round(r);
                    return (float) r;
                }
                return nv;
            }
            prev = next;
        }
        return keyframes[keyframes.length - 1].getValue();
    }

    private static float lerpFloat(float[] v, float f) {
        if (v.length == 1) return v[0];
        f = Math.max(0f, Math.min(1f, f));
        float scaled = f * (v.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= v.length - 1) return v[v.length - 1];
        float t = scaled - i;
        return v[i] + t * (v[i + 1] - v[i]);
    }
    private static float lerpIntAsFloat(int[] v, float f) {
        if (v.length == 1) return v[0];
        f = Math.max(0f, Math.min(1f, f));
        float scaled = f * (v.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= v.length - 1) return v[v.length - 1];
        float t = scaled - i;
        return v[i] + t * (v[i + 1] - v[i]);
    }

    // ------------------------------------------------------------------
    // The SINGLE-VALUE value spec.
    //
    // AOSP: `PropertyValuesHolder.ofFloat("alpha", 0f)` does NOT mean "hold
    // alpha at 0". KeyframeSet.ofFloat(values) with numKeyframes == 1 builds
    // TWO keyframes:
    //     keyframes[0] = Keyframe.ofFloat(0f);            // fraction 0, NO value
    //     keyframes[1] = Keyframe.ofFloat(1f, values[0]); // fraction 1, the value
    // and the missing first value is filled from the TARGET when the animator
    // starts (PropertyValuesHolder.setupValue -> mProperty.get(target), reached
    // from ObjectAnimator.initAnimation -> setupSetterAndGetter). So the
    // declared value is the END and the start is wherever the property is now.
    //
    // Ours evaluated the single value as a CONSTANT, which silently breaks the
    // standard Material idiom
    //     ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat(View.ALPHA, 0f))
    // -- the view jumps to alpha 0 on frame one and stays there, instead of
    // fading out from its current alpha. ObjectAnimator.captureStartValue
    // already did this for the animator's OWN value arrays (the
    // ObjectAnimator.ofFloat(v, "alpha", 0f) spelling); it did not reach the
    // holders, so only the PVH spelling was wrong.
    // ------------------------------------------------------------------

    /** True when this holder carries a one-ended value spec that still needs
     *  its start value read off the target. */
    boolean macapkNeedsStartValue() {
        switch (type) {
            case T_FLOAT:  return floatValues  != null && floatValues.length  == 1;
            case T_INT:    return intValues    != null && intValues.length    == 1;
            // A CONVERTED holder cannot take its start value off the target:
            // the target reads back the property's own type V while the value
            // array is in the evaluator's type T. AOSP requires a
            // BidirectionalTypeConverter to close that gap and throws
            // otherwise, so we simply decline instead of storing a V where a T
            // belongs.
            case T_OBJECT: return converter == null
                    && objectValues != null && objectValues.length == 1;
            default:       return false;
        }
    }

    /** Turn a one-ended spec into start -> declared. The arrays are REPLACED,
     *  never mutated in place, because PropertyValuesHolder.clone() is a shallow
     *  Object.clone: two clones of one template share the array reference, and
     *  writing through it would make the first started clone rewrite the
     *  second's value spec. Returns false when `start` is not usable for this
     *  holder's type, leaving the holder exactly as it was. */
    boolean macapkSetStartValue(Object start) {
        if (start == null) return false;
        switch (type) {
            case T_FLOAT:
                if (!(start instanceof Number)) return false;
                floatValues = new float[] { ((Number) start).floatValue(), floatValues[0] };
                return true;
            case T_INT:
                if (!(start instanceof Number)) return false;
                intValues = new int[] { ((Number) start).intValue(), intValues[0] };
                return true;
            case T_OBJECT:
                objectValues = new Object[] { start, objectValues[0] };
                return true;
            default:
                return false;
        }
    }

    /**
     * AOSP PropertyValuesHolder.setupEndValue(Object target) -- writes the
     * target's CURRENT property value into the LAST keyframe, so the animation
     * ends wherever the property is now. LayoutTransition uses it to make a
     * canned animator land on the freshly-laid-out value.
     *
     * The arrays are REPLACED rather than mutated, for the same reason
     * macapkSetStartValue does it: clone() is a shallow Object.clone, so two
     * clones of one template share the array reference.
     */
    boolean macapkSetEndValue(Object end) {
        if (end == null) return false;
        switch (type) {
            case T_FLOAT: {
                if (!(end instanceof Number) || floatValues == null) return false;
                float[] v = floatValues.clone();
                v[v.length - 1] = ((Number) end).floatValue();
                floatValues = v;
                return true;
            }
            case T_INT: {
                if (!(end instanceof Number) || intValues == null) return false;
                int[] v = intValues.clone();
                v[v.length - 1] = ((Number) end).intValue();
                intValues = v;
                return true;
            }
            case T_OBJECT: {
                if (objectValues == null || converter != null) return false;
                Object[] v = objectValues.clone();
                v[v.length - 1] = end;
                objectValues = v;
                return true;
            }
            case T_KEYFRAME: {
                if (keyframes == null || keyframes.length == 0) return false;
                Keyframe[] k = keyframes.clone();
                Keyframe last = k[k.length - 1].clone();
                last.setValue(end);
                k[k.length - 1] = last;
                keyframes = k;
                return true;
            }
            default:
                return false;
        }
    }

    /** The start-value counterpart of macapkSetEndValue, for a holder whose
     *  value spec already has both ends (macapkNeedsStartValue() is the
     *  one-ended case, handled separately). */
    boolean macapkOverwriteStartValue(Object start) {
        if (start == null) return false;
        switch (type) {
            case T_FLOAT: {
                if (!(start instanceof Number) || floatValues == null) return false;
                float[] v = floatValues.clone();
                v[0] = ((Number) start).floatValue();
                floatValues = v;
                return true;
            }
            case T_INT: {
                if (!(start instanceof Number) || intValues == null) return false;
                int[] v = intValues.clone();
                v[0] = ((Number) start).intValue();
                intValues = v;
                return true;
            }
            case T_OBJECT: {
                if (objectValues == null || converter != null) return false;
                Object[] v = objectValues.clone();
                v[0] = start;
                objectValues = v;
                return true;
            }
            case T_KEYFRAME: {
                if (keyframes == null || keyframes.length == 0) return false;
                Keyframe[] k = keyframes.clone();
                Keyframe first = k[0].clone();
                first.setValue(start);
                k[0] = first;
                keyframes = k;
                return true;
            }
            default:
                return false;
        }
    }

    @Override public PropertyValuesHolder clone() {
        try { return (PropertyValuesHolder) super.clone(); }
        catch (CloneNotSupportedException e) { throw new AssertionError(e); }
    }
}
