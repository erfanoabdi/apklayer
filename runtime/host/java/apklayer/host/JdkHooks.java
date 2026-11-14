package apklayer.host;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Template of {@code java.lang.ApklayerHooks}, which JdkIdentityPatch copies into java.base (the class renamed): the seams through which the
 * patched Throwable, Thread and exception classes give an app Android-shaped answers. The host fills the hooks at startup (StackFrames,
 * ExceptionMessages); empty (the hub, tools, {@code -Dapklayer.rawtraces=true}) everything stays the JDK's. Only java.base API here: the class
 * runs in the boot loader, also before the JVM has finished starting (no regex, no String.format).
 */
final class JdkHooks {
    private JdkHooks() {}

    /** Frames of a Throwable (on its first read) and of another thread's stack. */
    public static volatile UnaryOperator<StackTraceElement[]> frames;
    /** Thread.getAllStackTraces: the threads an app may see, their frames. */
    public static volatile UnaryOperator<Map<Thread, StackTraceElement[]>> threads;
    /** NullPointerException.getMessage: HotSpot's helpful message in ART's words. */
    public static volatile UnaryOperator<String> npe;

    /** Class members an app may not see (hidden-API policy; Class.getMethod/getDeclaredMethod throw NoSuchMethodException for them). */
    public static volatile java.util.function.BiPredicate<Class<?>, String> methodHidden;
    /** Class.getDeclaredFields for app callers (the fields of the loader classes, Robolectric's). */
    public static volatile java.util.function.BiFunction<Class<?>, java.lang.reflect.Field[], java.lang.reflect.Field[]> fields;

    static void checkMethod(Class<?> owner, String name) throws NoSuchMethodException {
        java.util.function.BiPredicate<Class<?>, String> hook = methodHidden;
        if (hook == null) return;
        boolean hidden;
        try { hidden = hook.test(owner, name); } catch (Throwable keep) { return; }
        if (hidden) throw new NoSuchMethodException(owner.getName() + "." + name + " []");
    }

    static java.lang.reflect.Field[] declaredFields(Class<?> owner, java.lang.reflect.Field[] all) {
        java.util.function.BiFunction<Class<?>, java.lang.reflect.Field[], java.lang.reflect.Field[]> hook = fields;
        if (hook == null) return all;
        try { return hook.apply(owner, all); } catch (Throwable keep) { return all; }
    }

    static StackTraceElement[] frames(StackTraceElement[] trace) {
        UnaryOperator<StackTraceElement[]> hook = frames;
        if (hook == null || trace == null) return trace;
        try { return hook.apply(trace); } catch (Throwable keep) { return trace; }
    }

    static Map<Thread, StackTraceElement[]> threads(Map<Thread, StackTraceElement[]> all) {
        UnaryOperator<Map<Thread, StackTraceElement[]>> hook = threads;
        if (hook == null || all == null) return all;
        try { return hook.apply(all); } catch (Throwable keep) { return all; }
    }

    static String npe(String message) {
        UnaryOperator<String> hook = npe;
        if (hook == null || message == null) return message;
        try { return hook.apply(message); } catch (Throwable keep) { return message; }
    }

    static final int INDEX = 1, CAST = 2, ARITHMETIC = 3, METHOD = 4, LIST = 5;

    private static boolean fromArrayList() {
        return StackWalker.getInstance().walk(f -> f.limit(8).anyMatch(x -> { String c = x.getClassName(); return c.equals("java.util.ArrayList") || c.equals("java.util.ArrayList$SubList"); }));
    }

    /** The message HotSpot gives an exception it throws itself, as ART words it (art/runtime/common_throws.cc); any other message unchanged. */
    static String message(String m, int kind) {
        if (m == null) return null;
        try {
            if (kind == INDEX) {  // "Index 5 out of bounds for length 3" -> "length=3; index=5"
                String head = "Index ", mid = " out of bounds for length ";
                int at = m.indexOf(mid);
                if (m.startsWith(head) && at > head.length()) {
                    String index = m.substring(head.length(), at), length = m.substring(at + mid.length());
                    if (number(index) && number(length)) return "length=" + length + "; index=" + index;
                }
            } else if (kind == LIST && fromArrayList()) {  // ArrayList.get / set / remove: libcore's "Index: 5, Size: 3"; other collections, arrays and app-made exceptions keep their text
                String head = "Index ", mid = " out of bounds for length ";
                int at = m.indexOf(mid);
                if (m.startsWith(head) && at > head.length()) {
                    String index = m.substring(head.length(), at), length = m.substring(at + mid.length());
                    if (number(index) && number(length)) return "Index: " + index + ", Size: " + length;
                }
            } else if (kind == CAST) {  // "class A cannot be cast to class B (A and B are in ...)" -> "A cannot be cast to B"
                String head = "class ", mid = " cannot be cast to class ";
                int at = m.indexOf(mid);
                if (m.startsWith(head) && at > head.length()) {
                    String to = m.substring(at + mid.length());
                    int space = to.indexOf(' ');
                    return m.substring(head.length(), at) + " cannot be cast to " + (space < 0 ? to : to.substring(0, space));
                }
            } else if (kind == METHOD) {  // "a.B.m(int,java.lang.String)" -> "a.B.m [int, class java.lang.String]"
                int open = m.indexOf('('), close = m.lastIndexOf(')');
                if (open > 0 && close == m.length() - 1 && m.indexOf(' ') < 0) {
                    String args = m.substring(open + 1, close);
                    StringBuilder b = new StringBuilder(m.substring(0, open)).append(" [");
                    if (!args.isEmpty()) {
                        boolean first = true;
                        for (String a : args.split(",")) {
                            if (!first) b.append(", ");
                            first = false;
                            boolean prim = a.equals("int") || a.equals("long") || a.equals("boolean") || a.equals("byte") || a.equals("char") || a.equals("short") || a.equals("float") || a.equals("double");
                            b.append(prim ? "" : "class ").append(a);
                        }
                    }
                    return b.append(']').toString();
                }
            } else if (kind == ARITHMETIC && m.equals("/ by zero")) return "divide by zero";
        } catch (Throwable keep) { /* the JDK's message */ }
        return m;
    }

    private static boolean number(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!(Character.isDigit(s.charAt(i)) || (i == 0 && s.charAt(i) == '-'))) return false;
        return true;
    }
}
