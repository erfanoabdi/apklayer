package apklayer.host;

import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import javax.management.ObjectName;

/**
 * The instance's class data sharing archive (cli/env.cpp cdsArgs): a JVM started without one (-Dapklayer.cdsdump=file, -XX:+RecordDynamicDumpInfo)
 * writes the classes it loaded, the JDK's, Robolectric's and the framework's (its sandbox loader's classes are matched by their bytes), a few seconds
 * after its launch is up. In process (jcmd VM.cds's own command through the DiagnosticCommand bean): attaching jcmd to app JVMs ended Fennec, and the
 * dump at exit never runs with the native exit. One JVM at a time (a file lock); a failure leaves no archive, and the launch runs on.
 * Every custom loader's classes are linked first (linkCustomClasses), and a JVM that began to exit keeps no archive.
 */
final class CdsDump {
    private CdsDump() {}

    private static final long DELAY_MS = 3000;  // the first frames' classes too, and the dump's pause away from the start

    private static boolean asked;

    /** The first activity is up (ActivityTask): a component host or the hub never writes the archive, it would lack every window class. */
    static synchronized void afterStart() {
        String file = System.getProperty("apklayer.cdsdump");
        if (file == null || asked || file.endsWith(".aot")) return;  // an AOT cache is trained by the idle spare only (trainSpare)
        asked = true;
        Thread t = new Thread(() -> dump(Path.of(file), false), "cds-dump");
        t.setDaemon(true);
        t.start();
    }

    /** JDK 25: the idle spare (AppSpare) trains the AOT cache headless, so no user launch has to: it links its classes and ends (the configuration is
     *  written at its exit). A launch that takes the spare meanwhile just runs (it records for nothing, no more). One attempt per instance (an earlier
     *  create's log stays). The spare the hub starts is not sandboxed, so a sandboxed instance gets its cache too (cli/sandbox.cpp strips the recording
     *  from sandboxed JVMs). */
    static void trainSpare() {
        String file = System.getProperty("apklayer.cdsdump");
        if (file == null || !file.endsWith(".aot") || !ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:AOTMode=record")) return;
        try (DirectoryStream<Path> tried = Files.newDirectoryStream(Path.of(file).getParent(), "*.aot.log")) {
            if (tried.iterator().hasNext()) return;
        } catch (Exception noDirectoryYet) { /* none made */ }
        dump(Path.of(file), true);
    }

    private static void dump(Path file, boolean spare) {
        try {
            Thread.sleep(DELAY_MS);
            for (String entry : System.getProperty("java.class.path").split(":")) {  // a non-empty directory makes the dump exit the VM
                Path p = Path.of(entry);
                if (Files.isDirectory(p)) try (DirectoryStream<Path> d = Files.newDirectoryStream(p)) { if (d.iterator().hasNext()) return; }
            }
            Path dir = file.getParent();
            Files.createDirectories(dir);
            try (FileChannel channel = FileChannel.open(dir.resolve("dump.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null || Files.exists(file) || exiting()) return;
                Path tmp = dir.resolve(file.getFileName() + ".tmp");
                try (DirectoryStream<Path> left = Files.newDirectoryStream(dir, "*.tmp*")) {  // a dump ended with its JVM (HotSpot's .tmp.temp)
                    for (Path p : left) Files.deleteIfExists(p);
                }
                long start = System.nanoTime();
                int linked = linkCustomClasses();
                if (exiting()) { System.out.println("[apklayer] cds: no archive: the JVM is ending"); return; }
                if (spare && AppSpare.taken()) return;  // a launch took the spare: it trains
                if (file.getFileName().toString().endsWith(".aot")) { aotCreateAndExit(file, linked); return; }
                ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "vmCds",
                        new Object[] {new String[] {"dynamic_dump", tmp.toString()}}, new String[] {String[].class.getName()});
                if (exiting()) {  // SIGTERM or System.exit during the dump: the app was being torn down
                    Files.deleteIfExists(tmp);
                    System.out.println("[apklayer] cds: no archive: the JVM began to end during the dump");
                    return;
                }
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
                try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "*.jsa")) {  // a former runtime's
                    for (Path p : old) if (!p.equals(file)) Files.deleteIfExists(p);
                }
                System.out.println("[apklayer] cds: archive " + file + " written in " + (System.nanoTime() - start) / 1_000_000 + " ms (" + linked + " classes linked first)");
            }
        } catch (Exception | LinkageError e) {
            System.out.println("[apklayer] cds: no archive: " + e);
        }
    }

    /** JDK 25 (cli/env.cpp cdsArgs): this JVM runs -XX:AOTMode=record, whose configuration HotSpot writes at a normal exit only (not with the native exit). A detached
     *  script waits for this process to end, then makes the cache with the same JVM options and class path in a JVM of its own (a record run's JVM cannot create);
     *  the app of this first launch ends here (shortcut: the first launch is the training run; a headless one at install would keep it). */
    private static void aotCreateAndExit(Path aot, int linked) throws Exception {
        // the JVM's own option list (the launcher's argv is overwritten by the process title): no main class, the create run does not start the app
        java.util.List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String conf = args.stream().filter(a -> a.startsWith("-XX:AOTConfiguration=")).findFirst().orElseThrow().substring(21);
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("setsid", "sh", "-c",
                "p=$1 c=$2 o=$3; shift 3; while kill -0 $p 2>/dev/null; do sleep 0.3; done; [ -s \"$c\" ] && \"$@\" -XX:AOTMode=create -XX:AOTConfiguration=\"$c\" "
                + "-XX:AOTCache=\"$o.tmp\" >\"$o.log\" 2>&1 && mv \"$o.tmp\" \"$o\"; rm -f \"$o\"conf.* \"$o.tmp\"; for f in \"${o%/*}\"/*.aot; do [ \"$f\" = \"$o\" ] || rm -f \"$f\"; done",
                "sh", String.valueOf(ProcessHandle.current().pid()), conf, aot.toString(), System.getProperty("java.home") + "/bin/java"));
        for (String arg : args)
            if (!arg.startsWith("-XX:AOTMode=") && !arg.startsWith("-XX:AOTConfiguration=") && !arg.startsWith("-Dapklayer.cdsdump=")) cmd.add(arg);
        cmd.addAll(java.util.List.of("-cp", System.getProperty("java.class.path")));
        new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        System.out.println("[apklayer] cds: training done (" + linked + " classes linked first), the JVM ends to write the AOT configuration");
        System.exit(0);
    }

    /** The JVM's shutdown has begun (a hook can no longer be added). */
    private static boolean exiting() {
        Thread probe = new Thread(() -> {});
        try { Runtime.getRuntime().addShutdownHook(probe); Runtime.getRuntime().removeShutdownHook(probe); return false; }
        catch (IllegalStateException e) { return true; }
    }

    /** Links (verifies, no initialization) every class of every loader but the JDK's builtin ones (JVMTI's list: the app's own ClassLoader subclasses
     *  too), until linking loads no more. HotSpot (JDK 21)
     *  links only the builtin loaders' classes before a dynamic dump and archives a custom loader's unlinked class with its constant pool as it
     *  is: a nest host's member entry that a nestmate check resolved (InstanceKlass::has_nest_member) stays resolved, and a JVM that maps the
     *  host from the archive without having loaded the member crashes on its first `new` of it (Scroller$ViscousFluidInterpolator: SIGSEGV in
     *  InstanceKlass::init_lock). Linked, the host's entries are cleared at dump time as HotSpot does for every linked class. */
    static int linkCustomClasses() {
        ClassLoader system = ClassLoader.getSystemClassLoader(), platform = ClassLoader.getPlatformClassLoader();
        java.util.Set<Class<?>> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (boolean more = true; more; ) {
            more = false;
            Class<?>[] all = NativeLoader.loadedClasses();
            if (all == null) throw new IllegalStateException("JVMTI gives no loaded classes");
            for (Class<?> c : all) {
                ClassLoader l = c.getClassLoader();
                if (l == null || l == system || l == platform || c.isArray() || !done.add(c)) continue;
                more = true;
                try { c.getDeclaredFields(); } catch (LinkageError e) {}  // JVM_GetClassDeclaredFields links the class; a refused one stays as it is
            }
        }
        return done.size();
    }
}
