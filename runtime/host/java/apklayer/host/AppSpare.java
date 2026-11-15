package apklayer.host;

import java.util.List;

/**
 * The instance's spare (`apklayer app-spare`, cli/spare.cpp), zygote's role: a JVM that has built its sandbox and loaded the native runtime (what every
 * process start shares, ~0.7 s) before the launch it becomes is known. The next app launch or component host start of the same runtime and environment
 * hands it, through the holder, its own JVM options and files and its stdout and stderr; the name, uid and process kind follow from the options.
 */
public final class AppSpare {
    private AppSpare() {}

    /** Prepares, then blocks until the holder hands over a launch (exits when the holder is gone first); returns the APK and the classes jar. */
    public static String[] awaitTarget(org.robolectric.internal.AndroidSandbox sandbox) {
        AppEnvironment.loadNativeRuntime();
        AppEnvironment.preloadState(sandbox);
        StartTrace.mark("spare prepared");
        System.out.flush();
        System.err.flush();
        Thread preload = new Thread(AppSpare::preload, "spare-preload");
        preload.setDaemon(true);
        preload.setPriority(Thread.MIN_PRIORITY);
        preload.start();
        String target = NativeLoader.receiveTarget(Integer.parseInt(System.getenv("APKLAYER_SPARE_FD")));  // the launch's stdout and stderr become this process's
        taken = true;
        if (target == null) { System.out.println("[apklayer] spare: the holder is gone"); System.exit(0); }
        StartTrace.mark("spare target");
        return apply(target.lines().toList());
    }

    private static volatile boolean taken;

    static boolean taken() { return taken; }

    /** Zygote's preloaded classes: the framework and JDK classes apps load after this point (resources/preloaded-classes, tools/gen/preload-list.py),
     *  loaded, not initialized, while the spare waits; stops when a launch takes it. */
    private static void preload() {
        long t0 = System.nanoTime();
        int loaded = 0, missing = 0;
        ClassLoader sandbox = AppSpare.class.getClassLoader();
        try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(AppSpare.class.getResourceAsStream("/preloaded-classes"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            for (String name; !taken && !"false".equals(System.getProperty("apklayer.sparepreload")) && (name = in.readLine()) != null; ) {
                try { Class.forName(name, false, sandbox); loaded++; }
                catch (Throwable notHere) { missing++; }
            }
        } catch (Exception unreadable) { System.out.println("[apklayer] spare: no preloaded classes: " + unreadable); }
        System.out.println("[apklayer] spare: preloaded " + loaded + " classes (" + missing + " not found) in " + (System.nanoTime() - t0) / 1000000 + " ms"
                + (taken ? ", stopped by the launch" : ""));
        if (!taken) CdsDump.trainSpare();  // JDK 25 without a cache: this spare is the training run
    }

    /** A target's lines: -D options (set as the JVM's properties), then the APK and the classes jar (returned). */
    static String[] apply(List<String> lines) {
        if (lines.size() < 2) throw new IllegalStateException("no APK and classes jar in the target");
        for (String option : lines.subList(0, lines.size() - 2)) {
            int eq = option.indexOf('=');
            if (!option.startsWith("-D") || eq < 0) throw new IllegalStateException("unexpected JVM option " + option);
            System.setProperty(option.substring(2, eq), option.substring(eq + 1));
        }
        return new String[] {lines.get(lines.size() - 2), lines.get(lines.size() - 1)};
    }

    /** A variable of the launch's own environment (cli/spare.cpp kLaunchEnv): a spare got it with its target, a cold JVM in its environment. */
    static String launchEnv(String name) { return System.getProperty("apklayer.env." + name, System.getenv(name)); }

    private static boolean started;

    /** The launch's process is up (an app's first activity, a component host's registration): one byte on the launcher's pipe, upon which the next
     *  spare starts preparing (it would compete with this start). */
    static void launchStarted() {
        if (started) return;
        started = true;
        Integer fd = Integer.getInteger("apklayer.startedfd");  // a spare's (spare_target.c); a plain launch's in the environment
        if (fd == null && System.getenv("APKLAYER_STARTED_FD") != null) fd = Integer.valueOf(System.getenv("APKLAYER_STARTED_FD"));
        if (fd == null) return;
        try (java.io.OutputStream out = new java.io.FileOutputStream("/proc/self/fd/" + fd)) { out.write('1'); }
        catch (java.io.IOException launcherGone) { /* nobody waits for it */ }
    }
}
