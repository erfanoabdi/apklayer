// android.util.DebugUtils -- the ANDROID_OBJECT_FILTER debug selector.
// AOSP: frameworks/base/core/java/android/util/DebugUtils.java
//
// The filter syntax is "<ClassNameRegex>[@prop=valueRegex]...": the class's
// SIMPLE name must match the first selector, then each remaining selector
// names a bean-style getter whose stringified result must match. AOSP's own
// note applies here too -- this is expensive (regex + reflection) and is meant
// for debug builds only.
package android.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

public class DebugUtils {
    /** AOSP has an implicit public default constructor here. */
    public DebugUtils() {}

    public static boolean isObjectSelected(Object object) {
        boolean match = false;
        String s = System.getenv("ANDROID_OBJECT_FILTER");
        if (s != null && s.length() > 0) {
            String[] selectors = s.split("@");
            // first selector == class name
            if (object.getClass().getSimpleName().matches(selectors[0])) {
                // check potential attributes
                for (int i = 1; i < selectors.length; i++) {
                    String[] pair = selectors[i].split("=");
                    Class<?> klass = object.getClass();
                    try {
                        Method declaredMethod = null;
                        Class<?> parent = klass;
                        do {
                            declaredMethod = parent.getDeclaredMethod("get"
                                    + pair[0].substring(0, 1).toUpperCase(Locale.ROOT)
                                    + pair[0].substring(1),
                                    (Class[]) null);
                        } while ((parent = klass.getSuperclass()) != null
                                && declaredMethod == null);

                        if (declaredMethod != null) {
                            Object value = declaredMethod.invoke(object, (Object[]) null);
                            match |= (value != null ? value.toString() : "null")
                                    .matches(pair[1]);
                        }
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return match;
    }
}
