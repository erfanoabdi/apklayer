// android.animation.IntArrayEvaluator -- interpolates element-wise between two
// int[] arrays. Was absent. Mirrors
// frameworks/base/core/java/android/animation/IntArrayEvaluator.java.
package android.animation;

public class IntArrayEvaluator implements TypeEvaluator<int[]> {
    private int[] mArray;

    public IntArrayEvaluator() {}
    public IntArrayEvaluator(int[] reuseArray) { mArray = reuseArray; }

    @Override
    public int[] evaluate(float fraction, int[] startValue, int[] endValue) {
        int[] array = mArray;
        if (array == null) array = new int[startValue.length];
        for (int i = 0; i < array.length; i++) {
            int start = startValue[i];
            int end = endValue[i];
            array[i] = (int) (start + (fraction * (end - start)));
        }
        return array;
    }
}
