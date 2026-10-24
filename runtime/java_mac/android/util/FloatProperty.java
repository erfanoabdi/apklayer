package android.util;
/** A Property of float type. Animators use setValue(obj, float) to avoid boxing.
 *  Source: frameworks/base/core/java/android/util/FloatProperty.java */
public abstract class FloatProperty<T> extends Property<T, Float> {
    public FloatProperty(String name) { super(Float.class, name); }
    public abstract void setValue(T object, float value);
    @Override public final void set(T object, Float value) { setValue(object, value.floatValue()); }
}
