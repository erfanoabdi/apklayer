// android.animation.Keyframe -- a (fraction, value) control point used to build a
// multi-stop animation via PropertyValuesHolder.ofKeyframe. Was absent even though
// PropertyValuesHolder.ofKeyframe existed (it accepted raw Object values), so any
// app constructing explicit Keyframe.ofFloat(0f, ...)/ofInt(...) NoClassDefFound'd.
// Mirrors frameworks/base/core/java/android/animation/Keyframe.java: an abstract
// base with Int/Float/Object subclasses created via the static factories.
package android.animation;

public abstract class Keyframe implements Cloneable {
    float mFraction;
    Class<?> mValueType;
    private TimeInterpolator mInterpolator = null;
    boolean mHasValue = false;

    public static Keyframe ofInt(float fraction, int value) {
        return new IntKeyframe(fraction, value);
    }
    public static Keyframe ofInt(float fraction) {
        return new IntKeyframe(fraction);
    }
    public static Keyframe ofFloat(float fraction, float value) {
        return new FloatKeyframe(fraction, value);
    }
    public static Keyframe ofFloat(float fraction) {
        return new FloatKeyframe(fraction);
    }
    public static Keyframe ofObject(float fraction, Object value) {
        return new ObjectKeyframe(fraction, value);
    }
    public static Keyframe ofObject(float fraction) {
        return new ObjectKeyframe(fraction, null);
    }

    public boolean hasValue() { return mHasValue; }

    public abstract Object getValue();
    public abstract void setValue(Object value);

    public float getFraction() { return mFraction; }
    public void setFraction(float fraction) { mFraction = fraction; }

    public TimeInterpolator getInterpolator() { return mInterpolator; }
    public void setInterpolator(TimeInterpolator interpolator) { mInterpolator = interpolator; }

    public Class<?> getType() { return mValueType; }

    @Override
    public abstract Keyframe clone();

    static class IntKeyframe extends Keyframe {
        int mValue;
        IntKeyframe(float fraction, int value) {
            mFraction = fraction; mValue = value; mValueType = int.class; mHasValue = true;
        }
        IntKeyframe(float fraction) { mFraction = fraction; mValueType = int.class; }
        public int getIntValue() { return mValue; }
        @Override public Object getValue() { return mValue; }
        @Override public void setValue(Object value) {
            if (value instanceof Integer) { mValue = (Integer) value; mHasValue = true; }
        }
        @Override public IntKeyframe clone() {
            IntKeyframe kfClone = mHasValue ? new IntKeyframe(mFraction, mValue)
                                            : new IntKeyframe(mFraction);
            kfClone.setInterpolator(getInterpolator());
            return kfClone;
        }
    }

    static class FloatKeyframe extends Keyframe {
        float mValue;
        FloatKeyframe(float fraction, float value) {
            mFraction = fraction; mValue = value; mValueType = float.class; mHasValue = true;
        }
        FloatKeyframe(float fraction) { mFraction = fraction; mValueType = float.class; }
        public float getFloatValue() { return mValue; }
        @Override public Object getValue() { return mValue; }
        @Override public void setValue(Object value) {
            if (value instanceof Float) { mValue = (Float) value; mHasValue = true; }
        }
        @Override public FloatKeyframe clone() {
            FloatKeyframe kfClone = mHasValue ? new FloatKeyframe(mFraction, mValue)
                                              : new FloatKeyframe(mFraction);
            kfClone.setInterpolator(getInterpolator());
            return kfClone;
        }
    }

    static class ObjectKeyframe extends Keyframe {
        Object mValue;
        ObjectKeyframe(float fraction, Object value) {
            mFraction = fraction; mValue = value; mHasValue = (value != null);
            mValueType = (value != null) ? value.getClass() : Object.class;
        }
        @Override public Object getValue() { return mValue; }
        @Override public void setValue(Object value) { mValue = value; mHasValue = (value != null); }
        @Override public ObjectKeyframe clone() {
            ObjectKeyframe kfClone = new ObjectKeyframe(mFraction, mHasValue ? mValue : null);
            kfClone.setInterpolator(getInterpolator());
            return kfClone;
        }
    }
}
