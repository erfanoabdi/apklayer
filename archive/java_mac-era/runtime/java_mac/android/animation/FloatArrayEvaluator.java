// android.animation.FloatArrayEvaluator -- interpolates element-wise between two
// float[] arrays. Was absent. Used with ValueAnimator.ofObject when animating a
// vector of floats (e.g. path coordinates). Mirrors
// frameworks/base/core/java/android/animation/FloatArrayEvaluator.java.
package android.animation;

public class FloatArrayEvaluator implements TypeEvaluator<float[]> {
    private float[] mArray;

    public FloatArrayEvaluator() {}

    // AOSP lets the caller supply a reusable output array to avoid per-frame alloc.
    public FloatArrayEvaluator(float[] reuseArray) { mArray = reuseArray; }

    @Override
    public float[] evaluate(float fraction, float[] startValue, float[] endValue) {
        float[] array = mArray;
        if (array == null) array = new float[startValue.length];
        for (int i = 0; i < array.length; i++) {
            float start = startValue[i];
            float end = endValue[i];
            array[i] = start + (fraction * (end - start));
        }
        return array;
    }
}
