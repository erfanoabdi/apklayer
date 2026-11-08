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
 */
final class CdsDump {
    private CdsDump() {}

    private static final long DELAY_MS = 3000;  // the first frames' classes too, and the dump's pause away from the start

    static void afterStart() {
        String file = System.getProperty("apklayer.cdsdump");
        if (file == null) return;
        Thread t = new Thread(() -> dump(Path.of(file)), "cds-dump");
        t.setDaemon(true);
        t.start();
    }

    private static void dump(Path file) {
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
                if (lock == null || Files.exists(file)) return;
                Path tmp = dir.resolve(file.getFileName() + ".tmp");
                long start = System.nanoTime();
                ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "vmCds",
                        new Object[] {new String[] {"dynamic_dump", tmp.toString()}}, new String[] {String[].class.getName()});
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
                try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "*.jsa")) {  // a former runtime's
                    for (Path p : old) if (!p.equals(file)) Files.deleteIfExists(p);
                }
                System.out.println("[apklayer] cds: archive " + file + " written in " + (System.nanoTime() - start) / 1_000_000 + " ms");
            }
        } catch (Exception | LinkageError e) {
            System.out.println("[apklayer] cds: no archive: " + e);
        }
    }
}
