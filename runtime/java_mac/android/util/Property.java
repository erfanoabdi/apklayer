// android.util.Property -- abstract pair-of-(target-class, value-class)
// accessor used by AOSP property animations (ObjectAnimator). RecyclerView's
// ItemAnimator, Material widgets, every Compat-style animation reference
// this class.
//
// AOSP source (fetched and mirrored verbatim for of()/the abstract class):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/util/Property.java
// ReflectiveProperty + NoSuchPropertyException are separate top-level
// files in real AOSP (android/util/ReflectiveProperty.java,
// android/util/NoSuchPropertyException.java), fetched from the same repo.
// We nest them inside this file instead of adding new files, since this
// lot's file ownership is scoped to Property.java alone; the only
// observable difference is the exception's fully-qualified name
// (android.util.Property$NoSuchPropertyException instead of a top-level
// android.util.NoSuchPropertyException) -- immaterial to the overwhelming
// majority of callers, who either don't catch this rare exception at all
// or catch it via a plain catch(RuntimeException).
package android.util;

public abstract class Property<T, V> {
    private final String name;
    private final Class<V> type;

    public Property(Class<V> type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getName() { return name; }
    public Class<V> getType() { return type; }

    public boolean isReadOnly() { return false; }

    public abstract V get(T object);
    public void set(T object, V value) {
        throw new UnsupportedOperationException(
            "Property " + getName() + " is read-only");
    }

    /** AOSP: "This factory method creates and returns a Property given the
     *  class and name parameters, where the name parameter represents
     *  either: a public getName()/isName() method on the class which takes
     *  no arguments, plus an optional public setName() method which takes
     *  a value of the same type; or a public name field on the class. If
     *  neither the methods nor the field are found on the class a
     *  NoSuchPropertyException will be thrown." Previously returned null
     *  unconditionally -- see ReflectiveProperty below for the real
     *  reflective resolution this now performs. */
    public static <T, V> Property<T, V> of(Class<T> hostType, Class<V> valueType, String name) {
        return new ReflectiveProperty<T, V>(hostType, valueType, name);
    }

    /** AOSP android/util/NoSuchPropertyException.java, verbatim (nested
     *  here -- see file header for why). Thrown when Property.of() is
     *  asked for a property that hostType exposes no getter/setter/field
     *  pair for. */
    public static class NoSuchPropertyException extends RuntimeException {
        public NoSuchPropertyException(String s) {
            super(s);
        }
    }

    /** AOSP android/util/ReflectiveProperty.java, ported verbatim (nested
     *  here -- see file header for why): resolves get<Name>()/is<Name>()
     *  plus an optional set<Name>(V) method, or failing that a public
     *  field named exactly `name`, via reflection against hostType. */
    private static final class ReflectiveProperty<T, V> extends Property<T, V> {
        private static final String PREFIX_GET = "get";
        private static final String PREFIX_IS = "is";
        private static final String PREFIX_SET = "set";

        private java.lang.reflect.Method mSetter;
        private java.lang.reflect.Method mGetter;
        private java.lang.reflect.Field mField;

        /** For property name 'name', look for getName/isName method or a
         *  'name' field. Also look for an optional setName method (the
         *  property is read-only without one). Failing both throws
         *  NoSuchPropertyException. */
        ReflectiveProperty(Class<T> hostType, Class<V> valueType, String name) {
            super(valueType, name);
            final char firstLetter = Character.toUpperCase(name.charAt(0));
            final String theRest = name.substring(1);
            final String capitalizedName = firstLetter + theRest;
            String getterName = PREFIX_GET + capitalizedName;
            try {
                mGetter = hostType.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                // getName() not available -- try isName() instead.
                getterName = PREFIX_IS + capitalizedName;
                try {
                    mGetter = hostType.getMethod(getterName);
                } catch (NoSuchMethodException e1) {
                    // Try a public field instead.
                    try {
                        mField = hostType.getField(name);
                        final Class<?> fieldType = mField.getType();
                        if (!typesMatch(valueType, fieldType)) {
                            throw new NoSuchPropertyException("Underlying type (" + fieldType
                                    + ") does not match Property type (" + valueType + ")");
                        }
                        return;
                    } catch (NoSuchFieldException e2) {
                        throw new NoSuchPropertyException(
                                "No accessor method or field found for property with name " + name);
                    }
                }
            }
            final Class<?> getterType = mGetter.getReturnType();
            if (!typesMatch(valueType, getterType)) {
                throw new NoSuchPropertyException("Underlying type (" + getterType
                        + ") does not match Property type (" + valueType + ")");
            }
            final String setterName = PREFIX_SET + capitalizedName;
            try {
                mSetter = hostType.getMethod(setterName, getterType);
            } catch (NoSuchMethodException ignored) {
                // Okay to not have a setter -- just a read-only property.
            }
        }

        /** AOSP: "Utility method to check whether the type of the
         *  underlying field/method on the target object matches the type
         *  of the Property. The extra checks for primitive types are
         *  because generics will force the Property type to be a class,
         *  whereas the type of the underlying method/field will probably
         *  be a primitive type instead. Accept float as matching Float,
         *  etc." */
        private static boolean typesMatch(Class<?> valueType, Class<?> underlyingType) {
            if (underlyingType != valueType) {
                if (underlyingType.isPrimitive()) {
                    return (underlyingType == float.class && valueType == Float.class)
                            || (underlyingType == int.class && valueType == Integer.class)
                            || (underlyingType == boolean.class && valueType == Boolean.class)
                            || (underlyingType == long.class && valueType == Long.class)
                            || (underlyingType == double.class && valueType == Double.class)
                            || (underlyingType == short.class && valueType == Short.class)
                            || (underlyingType == byte.class && valueType == Byte.class)
                            || (underlyingType == char.class && valueType == Character.class);
                }
                return false;
            }
            return true;
        }

        @Override
        public void set(T object, V value) {
            if (mSetter != null) {
                try {
                    mSetter.invoke(object, value);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            } else if (mField != null) {
                try {
                    mField.set(object, value);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            } else {
                throw new UnsupportedOperationException("Property " + getName() + " is read-only");
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(T object) {
            if (mGetter != null) {
                try {
                    return (V) mGetter.invoke(object);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            } else if (mField != null) {
                try {
                    return (V) mField.get(object);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
            // Should not get here: the constructor always throws
            // NoSuchPropertyException when neither is resolved.
            throw new AssertionError();
        }

        /** AOSP: "Returns false if there is no setter or public field
         *  underlying this Property." */
        @Override
        public boolean isReadOnly() {
            return mSetter == null && mField == null;
        }
    }
}
