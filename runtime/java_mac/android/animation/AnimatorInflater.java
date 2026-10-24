// android.animation.AnimatorInflater -- a REAL animator-XML inflater.
//
// Was a stub (v1.0.428): loadAnimator returned `new ValueAnimator()` and
// loadStateListAnimator returned an empty StateListAnimator, without ever
// opening the XML. Those fixed an NPE in Material's AppBarLayout lift-on-scroll
// setup and were never revisited, so element name, propertyName, <set>,
// ordering, durations, values and interpolators were ALL discarded. Material's
// MotionSpec.createSpecFromAnimators requires every animator to be an
// ObjectAnimator (it reads getPropertyName()); handed a bare ValueAnimator it
// logs "[W] MotionSpec: Can't load animation resource ID" and returns null.
//
// Mirrors AOSP frameworks/base/core/java/android/animation/AnimatorInflater.java
// (android13-release). Cited line numbers below are that file.
//
// DELIBERATE DEVIATIONS FROM AOSP (each one is a missing platform piece, not a
// shortcut around one we have):
//  1. No ConfigurationBoundResourceCache. AOSP caches the inflated animator and
//     hands back constantState.newInstance() so the cached copy is never used by
//     a caller (AOSP:129-137, :173-179). We have no ConstantState on Animator, so
//     we simply re-inflate per call. Observably identical -- AOSP goes out of its
//     way to return a FRESH instance each time -- only the cost differs.
//  2. No PathParser, so VALUE_TYPE_PATH (android:valueType="pathType") and the
//     android:pathData / propertyXName / propertyYName path-along-a-curve mode
//     (AOSP:508-550) are not built. Both are AnimatedVectorDrawable features.
//     Fleet exposure measured 2026-08-09: 2 of 3689 valueType occurrences and
//     1 of 3676 objectAnimators. Those degrade to a value-less animator rather
//     than throwing, which is strictly better than today's total discard.
//  3. Animator.appendChangingConfigurations()/createConstantState() do not exist
//     on our Animator, so the configuration-tracking calls are dropped.
//
// MACAPK_ANIMATOR_INFLATE=0 restores the pre-v<NEW> stub behaviour. This is a
// labelled bisect gate, not a supported mode: real inflation makes
// loadAnimator/loadStateListAnimator able to THROW Resources.NotFoundException
// where the stub never could (that IS the AOSP contract -- Material's own
// try/catch around MotionSpec.createFromResource proves callers are written for
// it), and the gate is here so a fleet regression can be A/B'd in one run
// instead of re-deriving the boundary.
package android.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.StateSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

public class AnimatorInflater {
    public AnimatorInflater() {}

    /** AOSP:64-65. */
    private static final int TOGETHER = 0;
    private static final int SEQUENTIALLY = 1;

    /** AOSP:70-74. Values are the android:valueType enum ints from
     *  core/res/res/values/attrs.xml (floatType=0 intType=1 pathType=2
     *  colorType=3). VALUE_TYPE_UNDEFINED is AOSP's internal sentinel and is
     *  NOT a declared enum value. */
    private static final int VALUE_TYPE_FLOAT     = 0;
    private static final int VALUE_TYPE_INT       = 1;
    private static final int VALUE_TYPE_PATH      = 2;
    private static final int VALUE_TYPE_COLOR     = 3;
    private static final int VALUE_TYPE_UNDEFINED = 4;

    // ------------------------------------------------------------------
    // Styleables.
    //
    // AOSP reads these through com.android.internal.R.styleable, which we do
    // not have, so the id arrays are inlined. Every id was READ OUT of the real
    // API-30 platform resource table (resources.arsc of
    // /system/framework/framework-res.apk from the android-30 google_apis AVD,
    // extracted by debug/extract_fw_res.py), NOT transcribed from a header --
    // see docs/framework-res-table_2026-08-08.md. Membership of each
    // declare-styleable was checked against
    // aosp-mirror/platform_frameworks_base android13-release
    // core/res/res/values/attrs.xml.
    //
    // Order is ascending by id, which is what aapt emits for a styleable and
    // therefore matches AOSP's own R.styleable_* index constants -- so a reader
    // can cross-check an index against AOSP source directly.
    //
    // NOTE, load-bearing: android:startDelay (0x010103e2) is NOT a member of
    // declare-styleable Animator. Real Android reads the start delay from
    // android:startOffset (AOSP:414) and silently IGNORES android:startDelay on
    // an <objectAnimator>. The fleet has 238 android:startDelay occurrences that
    // a device drops on the floor; honouring them here would be a deviation, not
    // a bug fix.
    // ------------------------------------------------------------------
    private static final int[] STYLEABLE_ANIMATOR = {
        0x01010141, // interpolator
        0x01010198, // duration
        0x010101be, // startOffset
        0x010101bf, // repeatCount
        0x010101c0, // repeatMode
        0x010102de, // valueFrom
        0x010102df, // valueTo
        0x010102e0, // valueType
    };
    private static final int Animator_interpolator = 0;
    private static final int Animator_duration     = 1;
    private static final int Animator_startOffset  = 2;
    private static final int Animator_repeatCount  = 3;
    private static final int Animator_repeatMode   = 4;
    private static final int Animator_valueFrom    = 5;
    private static final int Animator_valueTo      = 6;
    private static final int Animator_valueType    = 7;

    private static final int[] STYLEABLE_PROPERTY_ANIMATOR = {
        0x01010405, // pathData
        0x01010474, // propertyXName
        0x01010475, // propertyYName
        0x010102e1, // propertyName
    };
    // Hand-ordered to keep the index constants readable; the array order only
    // has to agree with these constants (our TypedArray resolves an index by
    // looking attrs[index] up by NAME, so ascending order is cosmetic here).
    private static final int PropertyAnimator_pathData      = 0;
    private static final int PropertyAnimator_propertyXName = 1;
    private static final int PropertyAnimator_propertyYName = 2;
    private static final int PropertyAnimator_propertyName  = 3;

    private static final int[] STYLEABLE_ANIMATOR_SET = {
        0x010102e2, // ordering
    };
    private static final int AnimatorSet_ordering = 0;

    private static final int[] STYLEABLE_PVH = {
        0x010102de, // valueFrom
        0x010102df, // valueTo
        0x010102e0, // valueType
        0x010102e1, // propertyName
    };
    private static final int PropertyValuesHolder_valueFrom    = 0;
    private static final int PropertyValuesHolder_valueTo      = 1;
    private static final int PropertyValuesHolder_valueType    = 2;
    private static final int PropertyValuesHolder_propertyName = 3;

    private static final int[] STYLEABLE_KEYFRAME = {
        0x01010024, // value
        0x01010141, // interpolator
        0x010102e0, // valueType
        0x010104d8, // fraction
    };
    private static final int Keyframe_value        = 0;
    private static final int Keyframe_interpolator = 1;
    private static final int Keyframe_valueType    = 2;
    private static final int Keyframe_fraction     = 3;

    /** android.R.attr.animation, from the API-30 table. Selects the
     *  "this <item> references an animator resource" branch in
     *  createStateListAnimatorFromXml (AOSP:227). */
    private static final int ATTR_ANIMATION = 0x010101cd;

    private static boolean inflateEnabled() {
        return !"0".equals(System.getenv("MACAPK_ANIMATOR_INFLATE"));
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** AOSP:89-92. */
    public static Animator loadAnimator(Context context, int id)
            throws Resources.NotFoundException {
        return loadAnimator(context.getResources(), context.getTheme(), id);
    }

    /** AOSP:104-107. */
    public static Animator loadAnimator(Resources resources, Resources.Theme theme, int id)
            throws Resources.NotFoundException {
        return loadAnimator(resources, theme, id, 1);
    }

    /** AOSP:110-155, minus the ConfigurationBoundResourceCache (deviation 1). */
    public static Animator loadAnimator(Resources resources, Resources.Theme theme, int id,
            float pathErrorScale) throws Resources.NotFoundException {
        if (!inflateEnabled()) return new ValueAnimator();
        XmlResourceParser parser = null;
        try {
            parser = resources.getAnimation(id);
            return createAnimatorFromXml(resources, theme, parser, pathErrorScale);
        } catch (XmlPullParserException | IOException ex) {
            Resources.NotFoundException rnf = new Resources.NotFoundException(
                    "Can't load animation resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    /** AOSP:157-203, minus the cache. */
    public static StateListAnimator loadStateListAnimator(Context context, int id)
            throws Resources.NotFoundException {
        if (!inflateEnabled()) return new StateListAnimator();
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            return createStateListAnimatorFromXml(context, parser, Xml.asAttributeSet(parser));
        } catch (XmlPullParserException | IOException ex) {
            Resources.NotFoundException rnf = new Resources.NotFoundException(
                    "Can't load state list animator resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    // ------------------------------------------------------------------
    // <selector> of state -> animator (AOSP:205-251)
    // ------------------------------------------------------------------
    private static StateListAnimator createStateListAnimatorFromXml(Context context,
            XmlPullParser parser, AttributeSet attributeSet)
            throws IOException, XmlPullParserException {
        int type;
        StateListAnimator stateListAnimator = new StateListAnimator();

        while (true) {
            type = parser.next();
            switch (type) {
                case XmlPullParser.END_DOCUMENT:
                case XmlPullParser.END_TAG:
                    return stateListAnimator;

                case XmlPullParser.START_TAG:
                    Animator animator = null;
                    if ("item".equals(parser.getName())) {
                        int attributeCount = parser.getAttributeCount();
                        int[] states = new int[attributeCount];
                        int stateIndex = 0;
                        for (int i = 0; i < attributeCount; i++) {
                            int attrName = attributeSet.getAttributeNameResource(i);
                            if (attrName == ATTR_ANIMATION) {
                                final int animId = attributeSet.getAttributeResourceValue(i, 0);
                                animator = loadAnimator(context, animId);
                            } else {
                                states[stateIndex++] =
                                        attributeSet.getAttributeBooleanValue(i, false)
                                                ? attrName : -attrName;
                            }
                        }
                        if (animator == null) {
                            animator = createAnimatorFromXml(context.getResources(),
                                    context.getTheme(), parser, 1f);
                        }
                        if (animator == null) {
                            throw new Resources.NotFoundException(
                                    "animation state item must have a valid animation");
                        }
                        stateListAnimator.addState(
                                StateSet.trimStateSet(states, stateIndex), animator);
                    }
                    break;
                default:
                    // Our DomXmlResourceParser emits IGNORABLE_WHITESPACE between
                    // tags (apktool output is pretty-printed; a device's compiled
                    // XmlBlock has no such events). Falling through is correct --
                    // AOSP's switch ignores everything that is not one of the three
                    // cases above -- but the default arm is spelled out so a future
                    // reader does not "tidy" the loop into something that trips on it.
                    break;
            }
        }
    }

    // ------------------------------------------------------------------
    // The element-name dispatch (AOSP:639-714)
    // ------------------------------------------------------------------
    private static Animator createAnimatorFromXml(Resources res, Resources.Theme theme,
            XmlPullParser parser, float pixelSize)
            throws XmlPullParserException, IOException {
        return createAnimatorFromXml(res, theme, parser, Xml.asAttributeSet(parser), null, 0,
                pixelSize);
    }

    private static Animator createAnimatorFromXml(Resources res, Resources.Theme theme,
            XmlPullParser parser, AttributeSet attrs, AnimatorSet parent, int sequenceOrdering,
            float pixelSize) throws XmlPullParserException, IOException {
        Animator anim = null;
        ArrayList<Animator> childAnims = null;

        int type;
        int depth = parser.getDepth();

        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) continue;

            String name = parser.getName();
            boolean gotValues = false;

            if (name.equals("objectAnimator")) {
                anim = loadObjectAnimator(res, theme, attrs, pixelSize);
            } else if (name.equals("animator")) {
                anim = loadAnimator(res, theme, attrs, null, pixelSize);
            } else if (name.equals("set")) {
                anim = new AnimatorSet();
                TypedArray a = obtain(res, theme, attrs, STYLEABLE_ANIMATOR_SET);
                int ordering = a.getInt(AnimatorSet_ordering, TOGETHER);
                createAnimatorFromXml(res, theme, parser, attrs, (AnimatorSet) anim, ordering,
                        pixelSize);
                a.recycle();
            } else if (name.equals("propertyValuesHolder")) {
                PropertyValuesHolder[] values = loadValues(res, theme, parser,
                        Xml.asAttributeSet(parser));
                if (values != null && anim != null && (anim instanceof ValueAnimator)) {
                    ((ValueAnimator) anim).setValues(values);
                }
                gotValues = true;
            } else {
                throw new RuntimeException("Unknown animator name: " + parser.getName());
            }

            if (parent != null && !gotValues) {
                if (childAnims == null) childAnims = new ArrayList<Animator>();
                childAnims.add(anim);
            }
        }
        if (parent != null && childAnims != null) {
            Animator[] animsArray = new Animator[childAnims.size()];
            int index = 0;
            for (Animator a : childAnims) animsArray[index++] = a;
            if (sequenceOrdering == TOGETHER) {
                parent.playTogether(animsArray);
            } else {
                parent.playSequentially(animsArray);
            }
        }
        return anim;
    }

    // ------------------------------------------------------------------
    // <objectAnimator> / <animator> (AOSP:1007-1070)
    // ------------------------------------------------------------------
    private static ObjectAnimator loadObjectAnimator(Resources res, Resources.Theme theme,
            AttributeSet attrs, float pathErrorScale) throws Resources.NotFoundException {
        ObjectAnimator anim = new ObjectAnimator();
        loadAnimator(res, theme, attrs, anim, pathErrorScale);
        return anim;
    }

    private static ValueAnimator loadAnimator(Resources res, Resources.Theme theme,
            AttributeSet attrs, ValueAnimator anim, float pathErrorScale)
            throws Resources.NotFoundException {
        TypedArray arrayAnimator = obtain(res, theme, attrs, STYLEABLE_ANIMATOR);
        TypedArray arrayObjectAnimator = null;
        if (anim != null) {
            arrayObjectAnimator = obtain(res, theme, attrs, STYLEABLE_PROPERTY_ANIMATOR);
        }
        if (anim == null) anim = new ValueAnimator();

        parseAnimatorFromTypeArray(anim, arrayAnimator, arrayObjectAnimator, pathErrorScale);

        final int resID = arrayAnimator.getResourceId(Animator_interpolator, 0);
        if (resID > 0) {
            final Interpolator interpolator =
                    AnimationUtils.loadInterpolator(res, theme, resID);
            if (interpolator != null) anim.setInterpolator(interpolator);
        }

        arrayAnimator.recycle();
        if (arrayObjectAnimator != null) arrayObjectAnimator.recycle();
        return anim;
    }

    /** AOSP:410-444. The `300` duration default is AOSP's and is load-bearing:
     *  get it wrong and every FAB / app-bar transition becomes instant. */
    private static void parseAnimatorFromTypeArray(ValueAnimator anim,
            TypedArray arrayAnimator, TypedArray arrayObjectAnimator, float pixelSize) {
        long duration = arrayAnimator.getInt(Animator_duration, 300);
        long startDelay = arrayAnimator.getInt(Animator_startOffset, 0);

        int valueType = arrayAnimator.getInt(Animator_valueType, VALUE_TYPE_UNDEFINED);
        if (valueType == VALUE_TYPE_UNDEFINED) {
            valueType = inferValueTypeFromValues(arrayAnimator, Animator_valueFrom,
                    Animator_valueTo);
        }
        PropertyValuesHolder pvh = getPVH(arrayAnimator, valueType, Animator_valueFrom,
                Animator_valueTo, "");
        if (pvh != null) anim.setValues(pvh);

        anim.setDuration(duration);
        anim.setStartDelay(startDelay);

        if (arrayAnimator.hasValue(Animator_repeatCount)) {
            anim.setRepeatCount(arrayAnimator.getInt(Animator_repeatCount, 0));
        }
        if (arrayAnimator.hasValue(Animator_repeatMode)) {
            anim.setRepeatMode(arrayAnimator.getInt(Animator_repeatMode, ValueAnimator.RESTART));
        }

        if (arrayObjectAnimator != null) {
            setupObjectAnimator(anim, arrayObjectAnimator, valueType, pixelSize);
        }
    }

    /** AOSP:495-556. The propertyName assignment at :552-554 is the whole reason
     *  Material's MotionSpec works: createSpecFromAnimators casts each child to
     *  ObjectAnimator and reads getPropertyName(). The pathData branch
     *  (AOSP:508-550) is deviation 2 -- we have no PathParser, so we log nothing
     *  and simply take the propertyName path, leaving the animator value-less
     *  rather than throwing. */
    private static void setupObjectAnimator(ValueAnimator anim, TypedArray arrayObjectAnimator,
            int valueType, float pixelSize) {
        ObjectAnimator oa = (ObjectAnimator) anim;
        String pathData = arrayObjectAnimator.getString(PropertyAnimator_pathData);
        if (pathData != null) {
            // Deviation 2: path-along-a-curve is not built. Fall through to the
            // property name so the animator is still typed correctly for
            // MotionSpec; it just carries no values.
            String propertyXName =
                    arrayObjectAnimator.getString(PropertyAnimator_propertyXName);
            String propertyYName =
                    arrayObjectAnimator.getString(PropertyAnimator_propertyYName);
            if (propertyXName == null && propertyYName == null) return;
            oa.setPropertyName(propertyXName != null ? propertyXName : propertyYName);
            return;
        }
        oa.setPropertyName(arrayObjectAnimator.getString(PropertyAnimator_propertyName));
    }

    // ------------------------------------------------------------------
    // <propertyValuesHolder> / <keyframe> (AOSP:716-1005)
    // ------------------------------------------------------------------
    private static PropertyValuesHolder[] loadValues(Resources res, Resources.Theme theme,
            XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        ArrayList<PropertyValuesHolder> values = null;

        int type;
        while ((type = parser.getEventType()) != XmlPullParser.END_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) { parser.next(); continue; }

            String name = parser.getName();
            if (name.equals("propertyValuesHolder")) {
                TypedArray a = obtain(res, theme, attrs, STYLEABLE_PVH);
                String propertyName = a.getString(PropertyValuesHolder_propertyName);
                int valueType = a.getInt(PropertyValuesHolder_valueType, VALUE_TYPE_UNDEFINED);

                PropertyValuesHolder pvh = loadPvh(res, theme, parser, propertyName, valueType);
                if (pvh == null) {
                    pvh = getPVH(a, valueType, PropertyValuesHolder_valueFrom,
                            PropertyValuesHolder_valueTo, propertyName);
                }
                if (pvh != null) {
                    if (values == null) values = new ArrayList<PropertyValuesHolder>();
                    values.add(pvh);
                }
                a.recycle();
            }
            parser.next();
        }

        PropertyValuesHolder[] valuesArray = null;
        if (values != null) {
            int count = values.size();
            valuesArray = new PropertyValuesHolder[count];
            for (int i = 0; i < count; ++i) valuesArray[i] = values.get(i);
        }
        return valuesArray;
    }

    /** AOSP:830-916. */
    private static PropertyValuesHolder loadPvh(Resources res, Resources.Theme theme,
            XmlPullParser parser, String propertyName, int valueType)
            throws XmlPullParserException, IOException {
        PropertyValuesHolder value = null;
        ArrayList<Keyframe> keyframes = null;

        int type;
        while ((type = parser.next()) != XmlPullParser.END_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // AOSP reads parser.getName() unguarded here because a device's
            // compiled XML has no inter-tag text events. Ours does (see the
            // note in createStateListAnimatorFromXml), and for a text node
            // getName() answers "#text" rather than null -- so AOSP's code is
            // merely inert on those events, not broken. Guarding on START_TAG
            // anyway makes that independent of DomXmlResourceParser's getName()
            // contract for non-element events.
            if (type != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if (name.equals("keyframe")) {
                if (valueType == VALUE_TYPE_UNDEFINED) {
                    valueType = inferValueTypeOfKeyframe(res, theme, Xml.asAttributeSet(parser));
                }
                Keyframe keyframe = loadKeyframe(res, theme, Xml.asAttributeSet(parser), valueType);
                if (keyframe != null) {
                    if (keyframes == null) keyframes = new ArrayList<Keyframe>();
                    keyframes.add(keyframe);
                }
                parser.next();
            }
        }

        int count;
        if (keyframes != null && (count = keyframes.size()) > 0) {
            // AOSP:857-908: guarantee keyframes at fraction 0 and 1, then spread
            // any unset fractions evenly across each gap.
            Keyframe firstKeyframe = keyframes.get(0);
            Keyframe lastKeyframe = keyframes.get(count - 1);
            float endFraction = lastKeyframe.getFraction();
            if (endFraction < 1) {
                if (endFraction < 0) {
                    lastKeyframe.setFraction(1);
                } else {
                    keyframes.add(keyframes.size(), createNewKeyframe(lastKeyframe, 1));
                    ++count;
                }
            }
            float startFraction = firstKeyframe.getFraction();
            if (startFraction != 0) {
                if (startFraction < 0) {
                    firstKeyframe.setFraction(0);
                } else {
                    keyframes.add(0, createNewKeyframe(firstKeyframe, 0));
                    ++count;
                }
            }
            Keyframe[] keyframeArray = new Keyframe[count];
            keyframes.toArray(keyframeArray);
            for (int i = 0; i < count; ++i) {
                Keyframe keyframe = keyframeArray[i];
                if (keyframe.getFraction() < 0) {
                    if (i == 0) {
                        keyframe.setFraction(0);
                    } else if (i == count - 1) {
                        keyframe.setFraction(1);
                    } else {
                        int startIndex = i;
                        int endIndex = i;
                        for (int j = startIndex + 1; j < count - 1; ++j) {
                            if (keyframeArray[j].getFraction() >= 0) break;
                            endIndex = j;
                        }
                        float gap = keyframeArray[endIndex + 1].getFraction()
                                - keyframeArray[startIndex - 1].getFraction();
                        distributeKeyframes(keyframeArray, gap, startIndex, endIndex);
                    }
                }
            }
            value = PropertyValuesHolder.ofKeyframe(propertyName, keyframeArray);
            if (valueType == VALUE_TYPE_COLOR) value.setEvaluator(ArgbEvaluator.getInstance());
        }
        return value;
    }

    /** AOSP:918-924. */
    private static Keyframe createNewKeyframe(Keyframe sampleKeyframe, float fraction) {
        return sampleKeyframe.getType() == float.class ? Keyframe.ofFloat(fraction)
                : (sampleKeyframe.getType() == int.class) ? Keyframe.ofInt(fraction)
                : Keyframe.ofObject(fraction);
    }

    /** AOSP:944-951. */
    private static void distributeKeyframes(Keyframe[] keyframes, float gap,
            int startIndex, int endIndex) {
        int count = endIndex - startIndex + 2;
        float increment = gap / count;
        for (int i = startIndex; i <= endIndex; ++i) {
            keyframes[i].setFraction(keyframes[i - 1].getFraction() + increment);
        }
    }

    /** AOSP:953-1005. */
    private static Keyframe loadKeyframe(Resources res, Resources.Theme theme, AttributeSet attrs,
            int valueType) throws XmlPullParserException, IOException {
        TypedArray a = obtain(res, theme, attrs, STYLEABLE_KEYFRAME);
        Keyframe keyframe = null;

        float fraction = a.getFloat(Keyframe_fraction, -1);
        TypedValue keyframeValue = a.peekValue(Keyframe_value);
        boolean hasValue = present(keyframeValue);
        if (valueType == VALUE_TYPE_UNDEFINED) {
            valueType = (hasValue && isColorType(keyframeValue.type))
                    ? VALUE_TYPE_COLOR : VALUE_TYPE_FLOAT;
        }

        if (hasValue) {
            switch (valueType) {
                case VALUE_TYPE_FLOAT:
                    keyframe = Keyframe.ofFloat(fraction, a.getFloat(Keyframe_value, 0));
                    break;
                case VALUE_TYPE_COLOR:
                case VALUE_TYPE_INT:
                    keyframe = Keyframe.ofInt(fraction, a.getInt(Keyframe_value, 0));
                    break;
                default:
                    break;
            }
        } else {
            keyframe = (valueType == VALUE_TYPE_FLOAT) ? Keyframe.ofFloat(fraction)
                    : Keyframe.ofInt(fraction);
        }

        final int resID = a.getResourceId(Keyframe_interpolator, 0);
        if (resID > 0 && keyframe != null) {
            final Interpolator interpolator = AnimationUtils.loadInterpolator(res, theme, resID);
            if (interpolator != null) keyframe.setInterpolator(interpolator);
        }
        a.recycle();
        return keyframe;
    }

    // ------------------------------------------------------------------
    // Value plumbing (AOSP:272-400, :774-813, :1079-1081)
    // ------------------------------------------------------------------
    private static PropertyValuesHolder getPVH(TypedArray styledAttributes, int valueType,
            int valueFromId, int valueToId, String propertyName) {
        TypedValue tvFrom = styledAttributes.peekValue(valueFromId);
        boolean hasFrom = present(tvFrom);
        int fromType = hasFrom ? tvFrom.type : 0;
        TypedValue tvTo = styledAttributes.peekValue(valueToId);
        boolean hasTo = present(tvTo);
        int toType = hasTo ? tvTo.type : 0;

        if (valueType == VALUE_TYPE_UNDEFINED) {
            valueType = ((hasFrom && isColorType(fromType)) || (hasTo && isColorType(toType)))
                    ? VALUE_TYPE_COLOR : VALUE_TYPE_FLOAT;
        }
        if (valueType == VALUE_TYPE_PATH) return null;   // deviation 2

        boolean getFloats = (valueType == VALUE_TYPE_FLOAT);
        PropertyValuesHolder returnValue = null;
        TypeEvaluator evaluator = (valueType == VALUE_TYPE_COLOR)
                ? ArgbEvaluator.getInstance() : null;

        if (getFloats) {
            float valueFrom;
            float valueTo;
            if (hasFrom) {
                valueFrom = (fromType == TypedValue.TYPE_DIMENSION)
                        ? styledAttributes.getDimension(valueFromId, 0f)
                        : styledAttributes.getFloat(valueFromId, 0f);
                if (hasTo) {
                    valueTo = (toType == TypedValue.TYPE_DIMENSION)
                            ? styledAttributes.getDimension(valueToId, 0f)
                            : styledAttributes.getFloat(valueToId, 0f);
                    returnValue = PropertyValuesHolder.ofFloat(propertyName, valueFrom, valueTo);
                } else {
                    returnValue = PropertyValuesHolder.ofFloat(propertyName, valueFrom);
                }
            } else if (hasTo) {
                valueTo = (toType == TypedValue.TYPE_DIMENSION)
                        ? styledAttributes.getDimension(valueToId, 0f)
                        : styledAttributes.getFloat(valueToId, 0f);
                returnValue = PropertyValuesHolder.ofFloat(propertyName, valueTo);
            }
        } else {
            int valueFrom;
            int valueTo;
            if (hasFrom) {
                valueFrom = intAt(styledAttributes, valueFromId, fromType);
                if (hasTo) {
                    valueTo = intAt(styledAttributes, valueToId, toType);
                    returnValue = PropertyValuesHolder.ofInt(propertyName, valueFrom, valueTo);
                } else {
                    returnValue = PropertyValuesHolder.ofInt(propertyName, valueFrom);
                }
            } else if (hasTo) {
                valueTo = intAt(styledAttributes, valueToId, toType);
                returnValue = PropertyValuesHolder.ofInt(propertyName, valueTo);
            }
        }
        if (returnValue != null && evaluator != null) returnValue.setEvaluator(evaluator);
        return returnValue;
    }

    private static int intAt(TypedArray a, int index, int type) {
        if (type == TypedValue.TYPE_DIMENSION) return (int) a.getDimension(index, 0f);
        if (isColorType(type)) return a.getColor(index, 0);
        return a.getInt(index, 0);
    }

    /** AOSP:796-813. */
    private static int inferValueTypeFromValues(TypedArray styledAttributes, int valueFromId,
            int valueToId) {
        TypedValue tvFrom = styledAttributes.peekValue(valueFromId);
        boolean hasFrom = present(tvFrom);
        TypedValue tvTo = styledAttributes.peekValue(valueToId);
        boolean hasTo = present(tvTo);
        return ((hasFrom && isColorType(tvFrom.type)) || (hasTo && isColorType(tvTo.type)))
                ? VALUE_TYPE_COLOR : VALUE_TYPE_FLOAT;
    }

    /** AOSP:774-794. */
    private static int inferValueTypeOfKeyframe(Resources res, Resources.Theme theme,
            AttributeSet attrs) {
        TypedArray a = obtain(res, theme, attrs, STYLEABLE_KEYFRAME);
        TypedValue keyframeValue = a.peekValue(Keyframe_value);
        int valueType = (present(keyframeValue) && isColorType(keyframeValue.type))
                ? VALUE_TYPE_COLOR : VALUE_TYPE_FLOAT;
        a.recycle();
        return valueType;
    }

    /** AOSP:1079-1081. */
    private static boolean isColorType(int type) {
        return (type >= TypedValue.TYPE_FIRST_COLOR_INT) && (type <= TypedValue.TYPE_LAST_COLOR_INT);
    }

    /** AOSP tests `peekValue(...) != null` for presence. TypedArray.peekValue
     *  returns null for a TYPE_NULL slot since v1.0.2269 (it used to answer a
     *  TYPE_NULL record); the type check stays so a subclass that still answers
     *  a record cannot read as present. */
    private static boolean present(TypedValue v) {
        return v != null && v.type != TypedValue.TYPE_NULL;
    }

    /** AOSP obtains through the Theme when it has one and through Resources
     *  otherwise (e.g. :673-677, :1031-1035); the theme path is what makes an
     *  `?attr/`-valued animator attribute resolve. */
    private static TypedArray obtain(Resources res, Resources.Theme theme, AttributeSet attrs,
            int[] styleable) {
        if (theme != null) return theme.obtainStyledAttributes(attrs, styleable, 0, 0);
        return res.obtainAttributes(attrs, styleable);
    }
}
