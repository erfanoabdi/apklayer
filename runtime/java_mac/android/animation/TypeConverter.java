// android.animation.TypeConverter<T, V> -- converts a property's value type T into
// the type V an evaluator/animator works with (e.g. PointF <-> float[] for path
// animation). Was a non-generic empty stub, so the published abstract convert(T)
// contract was wrong. Mirrors
// frameworks/base/core/java/android/animation/TypeConverter.java.
package android.animation;

public abstract class TypeConverter<T, V> {
    private final Class<T> mFromClass;
    private final Class<V> mToClass;

    public TypeConverter(Class<T> fromClass, Class<V> toClass) {
        mFromClass = fromClass;
        mToClass = toClass;
    }

    Class<T> getFromClass() { return mFromClass; }
    Class<V> getToClass() { return mToClass; }

    // Subclasses implement the forward conversion.
    public abstract V convert(T value);
}
