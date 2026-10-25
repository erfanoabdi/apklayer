// android.util.NoSuchPropertyException -- thrown by Property.of() when the
// named property does not exist on the target class. AOSP:
// frameworks/base/core/java/android/util/NoSuchPropertyException.java
package android.util;

/**
 * Thrown by {@link Property#of(Class, Class, String)} when the named property
 * does not exist for the given class.
 */
public class NoSuchPropertyException extends RuntimeException {
    public NoSuchPropertyException(String s) {
        super(s);
    }
}
