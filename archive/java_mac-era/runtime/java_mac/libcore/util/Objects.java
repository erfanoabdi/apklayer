// libcore.util.Objects -- libcore's reflective toString() for its plain struct
// classes (android.system.StructPollfd, StructTimeval, Int64Ref, ...). Hidden
// on a device (not in the SDK); it exists here because the android.system
// structs this lane added call it exactly the way AOSP's do, so their
// toString() output is the device's output.
//   AOSP: libcore/luni/src/main/java/libcore/util/Objects.java (android14-release)
//
// Two deliberate details, both to match what an app observes on ART:
//  * FIELD ORDER. AOSP iterates Class.getDeclaredFields(). On ART that returns
//    instance fields in dex field_ids order, and dex sorts field_ids by NAME
//    (dex-format "field_ids ... sorted by defining type, name, type"), so the
//    device prints fields alphabetically. HotSpot returns declaration order,
//    which would print StructPollfd as [fd=..,events=..] where a device prints
//    [events=..,fd=..]. We sort by name to reproduce ART.
//  * A NULL FIELD THROWS. AOSP calls value.getClass() without a null check, so
//    new StructPollfd().toString() is a NullPointerException on a device (fd
//    and userData start null). Mirrored, not "fixed": an app that works on a
//    device cannot depend on the friendlier behaviour, and one that crashes
//    there should crash here the same way.
package libcore.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

public final class Objects {
    private Objects() {}

    /** Returns "SimpleName[field=value,...]" over the non-static, non-transient fields. */
    public static String toString(Object o) {
        Class<?> c = o.getClass();
        StringBuilder sb = new StringBuilder();
        sb.append(c.getSimpleName()).append('[');
        int i = 0;
        Field[] fields = c.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (Field f : fields) {
            if ((f.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) != 0) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object value = f.get(o);

                if (i++ > 0) {
                    sb.append(',');
                }

                sb.append(f.getName());
                sb.append('=');

                if (value.getClass().isArray()) {
                    if (value.getClass() == boolean[].class) {
                        sb.append(Arrays.toString((boolean[]) value));
                    } else if (value.getClass() == byte[].class) {
                        sb.append(Arrays.toString((byte[]) value));
                    } else if (value.getClass() == char[].class) {
                        sb.append(Arrays.toString((char[]) value));
                    } else if (value.getClass() == double[].class) {
                        sb.append(Arrays.toString((double[]) value));
                    } else if (value.getClass() == float[].class) {
                        sb.append(Arrays.toString((float[]) value));
                    } else if (value.getClass() == int[].class) {
                        sb.append(Arrays.toString((int[]) value));
                    } else if (value.getClass() == long[].class) {
                        sb.append(Arrays.toString((long[]) value));
                    } else if (value.getClass() == short[].class) {
                        sb.append(Arrays.toString((short[]) value));
                    } else {
                        sb.append(Arrays.toString((Object[]) value));
                    }
                } else if (value.getClass() == Character.class) {
                    sb.append('\'').append(value).append('\'');
                } else if (value.getClass() == String.class) {
                    sb.append('"').append(value).append('"');
                } else {
                    sb.append(value);
                }
            } catch (IllegalAccessException unexpected) {
                throw new AssertionError(unexpected);
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
