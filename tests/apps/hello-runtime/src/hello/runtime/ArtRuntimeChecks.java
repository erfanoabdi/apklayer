package hello.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Checks supported VMRuntime contracts, not ART's private object layout. */
public final class ArtRuntimeChecks {
    private ArtRuntimeChecks() {}

    public static void run() throws Exception {
        Class<?> type = Class.forName("dalvik.system.VMRuntime");
        Object runtime = type.getMethod("getRuntime").invoke(null);
        check(runtime == type.getMethod("getRuntime").invoke(null), "runtime singleton");
        String version = (String) type.getMethod("vmVersion").invoke(runtime);
        check("2.1.0".equals(version), "VM version: " + version);
        check(version.equals(System.getProperty("java.vm.version")), "property/VM version agreement");
        check("libart.so".equals(type.getMethod("vmLibrary").invoke(runtime)), "VM library query");
        String isa = (String) type.getMethod("vmInstructionSet").invoke(runtime);
        check(isa.equals(type.getMethod("getCurrentInstructionSet").invoke(null)), "ISA query agreement");
        boolean wide = (Boolean) type.getMethod("is64Bit").invoke(runtime);
        check(wide == ("arm64".equals(isa) || "x86_64".equals(isa)), "ISA width");
        check(wide == android.os.Process.is64Bit(), "Process/VM width agreement");

        Method get = type.getMethod("getTargetHeapUtilization");
        Method set = type.getMethod("setTargetHeapUtilization", float.class);
        float initial = (Float) get.invoke(runtime);
        check(initial > 0 && initial < 1, "initial heap hint: " + initial);
        try {
            check((Float) set.invoke(runtime, 0.7f) == initial, "heap hint previous value");
            check((Float) get.invoke(runtime) == 0.7f, "heap hint update");
            check((Float) set.invoke(runtime, 0.05f) == 0.7f, "heap hint save/restore");
            check((Float) get.invoke(runtime) == 0.1f, "heap hint minimum clamp");
            reject(set, runtime, 0f);
            reject(set, runtime, 1f);
            check((Float) get.invoke(runtime) == 0.1f, "rejected hint preserves value");
        } finally {
            set.invoke(runtime, initial);
        }

        Method allocate = type.getMethod("newUnpaddedArray", Class.class, int.class);
        int[] values = (int[]) allocate.invoke(runtime, int.class, 4);
        check(values.length >= 4 && values[3] == 0, "unpadded primitive allocation");
        String[] refs = (String[]) allocate.invoke(runtime, String.class, 2);
        refs[1] = "kept";
        check(refs.length >= 2 && "kept".equals(refs[1]), "unpadded reference allocation");
        System.out.println("[hello-runtime] ART API PASS version=" + version + " isa=" + isa);
        // Report the boundary explicitly; these checks do not assert a real ART heap or JNI ABI.
        System.out.println("[hello-runtime] ART layout not tested: ArtMethod, JNI internals, pinned arrays, dex loader identity");
    }

    private static void reject(Method setter, Object runtime, float value) throws Exception {
        try {
            setter.invoke(runtime, value);
            throw new AssertionError("accepted invalid heap hint " + value);
        } catch (InvocationTargetException expected) {
            check(expected.getCause() instanceof IllegalArgumentException, "heap hint exception type");
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
