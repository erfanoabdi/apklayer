package apklayer.host;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * What an app's reflection may not see, as Android's hidden-API policy (art/runtime/hidden_api.cc) shows it to an app of target SDK 28 or later.
 * Scope: the runtime's own classes (dalvik.system.VMRuntime and VMDebug members Android 13 blocks for apps: the probes of tests/apps/hello-identity),
 * the fields of the loader classes, Robolectric's `__robo_data__`, and `Runtime.version()` (libcore removed it). This is not the framework's
 * hiddenapi-flags database: the rest of the framework's non-SDK members stay reachable (docs/runtime-identity-matrix.md). The caller must be the app:
 * code of an app's PathClassLoader; the framework and the host see everything. Like ART, `VMRuntime.setHiddenApiExemptions` (which an app only
 * reaches by a bypass) turns the policy off for the signatures it names ("L" for everything), see {@link #exempt}.
 */
@DoNotInstrument
public final class GuestHiddenApi {
    private GuestHiddenApi() {}

    private static final Set<String> VMRUNTIME = Set.of("vmVersion", "isCheckJniEnabled", "isNativeDebuggable", "isJavaDebuggable", "getTargetSdkVersion",
            "getTargetHeapUtilization", "setTargetHeapUtilization", "properties", "bootClassPath", "classPath", "setHiddenApiExemptions",
            "setTargetSdkVersion", "registerNativeAllocation", "registerNativeFree", "clearGrowthLimit", "startJitCompilation", "disableJitCompilation");
    private static final Set<String> VMDEBUG = Set.of("getVmFeatureList", "dumpHprofData", "startMethodTracing", "stopMethodTracing", "getRuntimeStat", "getRuntimeStats");

    private static final List<String> exemptions = new CopyOnWriteArrayList<>();
    private static volatile int target = 10000;

    /** The app's target SDK (VMRuntime.setTargetSdkVersion); below 28 the policy lets everything through. */
    static void target(int sdk) { target = sdk; }

    /** VMRuntime.setHiddenApiExemptions: signature prefixes, "L" matches every member. */
    static void exempt(String[] prefixes) {
        exemptions.clear();
        if (prefixes != null) exemptions.addAll(Arrays.asList(prefixes));
    }

    private static boolean exempted(String signature) {
        for (String prefix : exemptions) if (signature.startsWith(prefix)) return true;
        return false;
    }

    private static final StackWalker WALK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** True when the calling code is the app's (a class of an app PathClassLoader), skipping the reflection frames. */
    private static boolean fromApp() {
        return WALK.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(c -> !c.getName().startsWith("java.lang.Class") && !c.getName().startsWith("java.lang.reflect.") && !c.getName().startsWith("jdk.internal.")
                        && !c.getName().startsWith("apklayer.host.Guest") && !c.getName().equals("apklayer.host.Reflect") && !c.getName().equals("java.lang.ApklayerHooks"))
                .findFirst().map(c -> isAppLoader(c.getClassLoader())).orElse(false));
    }

    private static boolean isAppLoader(ClassLoader loader) {
        for (; loader != null; loader = loader.getParent()) if (loader instanceof dalvik.system.PathClassLoader || loader instanceof AppClassLoader) return true;
        return false;
    }

    static boolean hidden(Class<?> owner, String member) {
        Set<String> blocked = owner == Runtime.class ? Set.of("version")
                : owner.getName().equals("dalvik.system.VMRuntime") ? VMRUNTIME : owner.getName().equals("dalvik.system.VMDebug") ? VMDEBUG : null;
        if (blocked == null || !blocked.contains(member)) return false;
        if (owner != Runtime.class && target < 28) return false;
        if (exempted("L" + owner.getName().replace('.', '/') + ";->" + member)) return false;
        return fromApp();
    }

    /** Android shows an app the loader classes' `pathList` and `parent` only; Robolectric's marker field never. */
    static Field[] fields(Class<?> owner, Field[] all) {
        String name = owner.getName();
        boolean loaders = name.equals("dalvik.system.BaseDexClassLoader") || name.equals("java.lang.ClassLoader") || name.equals("dalvik.system.PathClassLoader");
        boolean marker = false;
        for (Field f : all) if (f.getName().equals("__robo_data__")) marker = true;
        if (!loaders && !marker) return all;
        if (!fromApp()) return all;
        return Arrays.stream(all).filter(f -> !f.getName().equals("__robo_data__") && !f.getName().startsWith("apklayer")
                && (!loaders || f.getName().equals("pathList") || f.getName().equals("parent"))).toArray(Field[]::new);
    }
}
