package apklayer.host;

/**
 * The app spare (`apklayer app-spare`, cli/spare.cpp), zygote's role for window JVMs: a JVM that has built its sandbox and loaded the native runtime (what every
 * app start shares, ~0.7 s) before the launch it becomes is known. The next `apklayer run` of the same runtime and environment hands it, through the holder,
 * its own JVM options and files and its stdout and stderr.
 */
public final class AppSpare {
    private AppSpare() {}

    /** Prepares, then blocks until the holder hands over a launch (exits when the holder is gone first); returns the APK and the classes jar. */
    public static String[] awaitTarget() {
        AppEnvironment.loadNativeRuntime();
        StartTrace.mark("spare prepared");
        System.out.flush();
        System.err.flush();
        String target = NativeLoader.receiveTarget(Integer.parseInt(System.getenv("APKLAYER_SPARE_FD")));  // the launch's stdout and stderr become this process's
        if (target == null) { System.out.println("[apklayer] app spare: the holder is gone"); System.exit(0); }
        StartTrace.mark("spare target");
        return ComponentSpare.apply(target.lines().toList());
    }
}
