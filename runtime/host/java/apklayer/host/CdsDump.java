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
 * The custom loaders' classes are linked first (linkCustomClasses), and a JVM that began to exit keeps no archive.
 */
final class CdsDump {
    private CdsDump() {}

    private static final long DELAY_MS = 3000;  // the first frames' classes too, and the dump's pause away from the start

    private static boolean asked;

    /** The first activity is up (ActivityTask): a component host or the hub never writes the archive, it would lack every window class. */
    static synchronized void afterStart() {
        String file = System.getProperty("apklayer.cdsdump");
        if (file == null || asked) return;
        asked = true;
        ClassLoader loader = AppClassLoader.app != null ? AppClassLoader.app : Thread.currentThread().getContextClassLoader();
        Thread t = new Thread(() -> dump(Path.of(file), loader), "cds-dump");
        t.setDaemon(true);
        t.start();
    }

    private static void dump(Path file, ClassLoader loader) {
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
                int linked = linkCustomClasses(loader);
                if (exiting()) { System.out.println("[apklayer] cds: no archive: the JVM is ending"); return; }
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

    /** The JVM's shutdown has begun (a hook can no longer be added). */
    private static boolean exiting() {
        Thread probe = new Thread(() -> {});
        try { Runtime.getRuntime().addShutdownHook(probe); Runtime.getRuntime().removeShutdownHook(probe); return false; }
        catch (IllegalStateException e) { return true; }
    }

    /** Links (verifies, no initialization) every class of the loader chain up to the system loader, until linking loads no more. HotSpot (JDK 21)
     *  links only the builtin loaders' classes before a dynamic dump and archives a custom loader's unlinked class with its constant pool as it
     *  is: a nest host's member entry that a nestmate check resolved (InstanceKlass::has_nest_member) stays resolved, and a JVM that maps the
     *  host from the archive without having loaded the member crashes on its first `new` of it (Scroller$ViscousFluidInterpolator: SIGSEGV in
     *  InstanceKlass::init_lock). Linked, the host's entries are cleared at dump time as HotSpot does for every linked class. */
    @SuppressWarnings("unchecked")
    static int linkCustomClasses(ClassLoader loader) throws ReflectiveOperationException {
        // what HotSpot's define adds each class to; reflection filters ClassLoader's fields, a lookup does not (java.lang is opened to the host)
        java.lang.invoke.VarHandle classes = java.lang.invoke.MethodHandles.privateLookupIn(ClassLoader.class, java.lang.invoke.MethodHandles.lookup())
                .findVarHandle(ClassLoader.class, "classes", java.util.ArrayList.class);
        java.util.Set<Class<?>> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (boolean more = true; more; ) {
            more = false;
            for (ClassLoader l = loader; l != null && l != ClassLoader.getSystemClassLoader() && l != ClassLoader.getPlatformClassLoader(); l = l.getParent()) {
                java.util.List<Class<?>> list = (java.util.List<Class<?>>) (Object) classes.get(l);
                Class<?>[] now;
                synchronized (list) { now = list.toArray(new Class<?>[0]); }
                for (Class<?> c : now) {
                    if (!done.add(c)) continue;
                    more = true;
                    try { c.getDeclaredFields(); } catch (LinkageError e) {}  // JVM_GetClassDeclaredFields links the class; a refused one stays as it is
                }
            }
        }
        return done.size();
    }
}
