// dalvik.system.VMStack -- Android-internal stack-introspection API
// (AOSP libcore/libart/src/main/java/dalvik/system/VMStack.java).
// Google's Flogger logging library (shipped obfuscated inside most
// Google apps, e.g. Calculator) probes for this class by reflection
// at Platform init and, when present, derives every logger's name
// via getStackClass2(). Without it BOTH of Flogger's caller-finder
// strategies fail on Mac.apk -- the VMStack path (class missing) and
// the StackTraceElement path (gated on Build.FINGERPRINT being null
// or "robolectric") -- so findLoggingClass returns null and the
// first FluentLogger.forEnclosingClass() dies with an NPE inside a
// <clinit> (Calculator: ExceptionInInitializerError constructing the
// activity).
//
// Frame contracts (AOSP javadoc, quoted):
//   getStackClass1()          "Returns the class of the caller's caller."
//   getStackClass2()          "Returns the class of the caller's caller's caller."
//   getCallingClassLoader()   "Returns the defining class loader of the caller's caller."
//   getClosestUserClassLoader() "Returns the first ClassLoader on the
//                              call stack that isn't the bootstrap class loader."
//
// The JVM-native equivalent is StackWalker. Counting from this
// method's own frame as 0 (StackWalker's stream starts at the method
// that invoked walk()), getStackClass2 returns frame 3. Flogger's own
// capability probe verifies this math at init: it calls
// getStackClass2 through two helper frames and requires the result to
// be the probing class -- i.e. probe(frame3) == clz -- so the skip
// counts below are load-bearing, not stylistic. getStackClass1
// delegates to getStackClass2 exactly like AOSP does; the extra
// delegation frame shifts the math by one, which is what makes its
// "caller's caller" contract come out right.
package dalvik.system;

public final class VMStack {
    private VMStack() {}

    private static final StackWalker WALKER =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** AOSP: "Returns the defining class loader of the caller's caller." */
    @Deprecated
    public static ClassLoader getCallingClassLoader() {
        return WALKER.walk(s -> s.skip(2).findFirst())
            .map(f -> f.getDeclaringClass().getClassLoader())
            .orElse(null);
    }

    /** AOSP: "Returns the class of the caller's caller." */
    @Deprecated
    public static Class<?> getStackClass1() {
        return getStackClass2();
    }

    /** AOSP: "Returns the class of the caller's caller's caller." */
    public static Class<?> getStackClass2() {
        return WALKER.walk(s -> s.skip(3).findFirst())
            .map(StackWalker.StackFrame::getDeclaringClass)
            .orElse(null);
    }

    /** AOSP: "Returns the first ClassLoader on the call stack that
     *  isn't the bootstrap class loader." On HotSpot the bootstrap
     *  loader is represented as null. */
    public static ClassLoader getClosestUserClassLoader() {
        return WALKER.walk(s -> s
            .map(f -> f.getDeclaringClass().getClassLoader())
            .filter(cl -> cl != null)
            .findFirst()).orElse(null);
    }

    /** AOSP: "Retrieves the stack trace from the specified thread."
     *  Internal callers (Thread.getStackTrace on ART) use this; on
     *  the JVM the Thread API serves directly. */
    public static StackTraceElement[] getThreadStackTrace(Thread t) {
        return t.getStackTrace();
    }
}
