/** Run as a class on HotSpot or as DEX with dalvikvm to compare public runtime identity. */
public final class RuntimeIdentity {
    public static void main(String[] args) {
        String[] properties = {"java.vm.name", "java.vm.vendor", "java.vm.version", "java.vm.specification.name",
            "java.vm.specification.vendor", "java.vm.specification.version", "java.runtime.name", "java.runtime.version",
            "java.version", "java.vendor", "java.specification.name", "java.specification.vendor", "java.specification.version",
            "java.home", "java.class.version", "java.class.path", "java.boot.class.path", "java.library.path", "java.io.tmpdir",
            "os.name", "os.arch", "os.version", "user.name", "user.home"};
        for (String key : properties) System.out.println("property " + key + "=" + System.getProperty(key));
        String[] classes = {"dalvik.system.VMRuntime", "dalvik.system.BaseDexClassLoader", "java.lang.Daemons",
            "java.lang.AndroidHardcodedSystemProperties", "java.lang.management.ManagementFactory",
            "com.sun.management.HotSpotDiagnosticMXBean", "org.robolectric.RuntimeEnvironment", "apklayer.host.NativeLoader"};
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name, false, RuntimeIdentity.class.getClassLoader());
                System.out.println("class " + name + "=" + type.getClassLoader());
            } catch (ClassNotFoundException absent) { System.out.println("class " + name + "=absent"); }
        }
        System.out.println("loader app=" + RuntimeIdentity.class.getClassLoader());
        try { throw new Exception("runtime identity trace"); }
        catch (Exception trace) { for (StackTraceElement frame : trace.getStackTrace()) System.out.println("frame " + frame); }
    }
}
