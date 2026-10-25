// android.animation.BidirectionalTypeConverter<T, V> -- a TypeConverter that also
// converts back (V -> T), required when ObjectAnimator must both read the start
// value from and write the animated value to a property of type T via an
// intermediate type V. Was absent. Mirrors
// frameworks/base/core/java/android/animation/BidirectionalTypeConverter.java.
package android.animation;

public abstract class BidirectionalTypeConverter<T, V> extends TypeConverter<T, V> {
    private BidirectionalTypeConverter<V, T> mInvertedConverter;

    public BidirectionalTypeConverter(Class<T> fromClass, Class<V> toClass) {
        super(fromClass, toClass);
    }

    // The reverse conversion subclasses must implement.
    public abstract T convertBack(V value);

    // Returns a converter that runs this one in reverse, lazily created.
    public BidirectionalTypeConverter<V, T> invert() {
        if (mInvertedConverter == null) {
            mInvertedConverter = new InvertedConverter<V, T>(this);
        }
        return mInvertedConverter;
    }

    private static class InvertedConverter<F, S> extends BidirectionalTypeConverter<F, S> {
        private final BidirectionalTypeConverter<S, F> mConverter;
        InvertedConverter(BidirectionalTypeConverter<S, F> converter) {
            super(converter.getToClass(), converter.getFromClass());
            mConverter = converter;
        }
        @Override public S convert(F value) { return mConverter.convertBack(value); }
        @Override public F convertBack(S value) { return mConverter.convert(value); }
    }
}
