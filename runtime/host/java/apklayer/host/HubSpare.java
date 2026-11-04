package apklayer.host;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * One component host JVM started ahead (`apklayer component-host --spare`: the JVM, its sandbox and the native runtime, what every package's
 * process shares, ComponentSpare), handed its package when the hub needs a host and replaced at once, as zygote forks the next app process.
 * Its output goes to the log of the host it becomes (what it printed before is written there first).
 */
final class HubSpare {
    private static final int EARLY_LIMIT = 1 << 20;  // what a spare prints before its target (native runtime registration lines: some 30 KB)
    private final Path logs;
    private Process spare;
    private Output output;

    HubSpare(Path logs) { this.logs = logs; }

    /** Starts a spare when there is none (after the hub is up, and after each take). */
    synchronized void start() {
        if (spare != null && spare.isAlive()) return;
        try {
            Process process = new ProcessBuilder(System.getProperty("apklayer.cli"), "component-host", "--spare").redirectErrorStream(true).start();
            Output out = new Output(process.getInputStream());
            Thread pump = new Thread(out, "hub-spare-output-" + process.pid());
            pump.setDaemon(true); pump.start();
            spare = process; output = out;
            System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " spare component host launcherPid=" + process.pid());
        } catch (IOException failure) { System.out.println("[apklayer-hub] cannot start a spare component host: " + failure); }
    }

    /** The spare becomes the host of package (and isolated service instance, or null), logging to log; null when there is none alive. */
    synchronized Process take(String pkg, String instance, Path log) {
        Process process = spare;
        Output out = output;
        spare = null; output = null;
        if (process == null || !process.isAlive()) return null;
        try {
            out.to(log);
            OutputStream in = process.getOutputStream();
            in.write(((instance == null ? pkg : pkg + " " + instance) + "\n").getBytes(StandardCharsets.UTF_8));
            in.flush();
            return process;
        } catch (IOException gone) { process.destroyForcibly(); return null; }
    }

    synchronized void stop() { if (spare != null) spare.destroyForcibly(); spare = null; }

    /** Copies the spare's output: into memory until it has a target, then into the target's log. */
    private final class Output implements Runnable {
        private final InputStream in;
        private ByteArrayOutputStream early = new ByteArrayOutputStream();
        private OutputStream sink;
        Output(InputStream in) { this.in = in; }

        @Override public void run() {
            byte[] buffer = new byte[8192];
            try { for (int n; (n = in.read(buffer)) > 0; ) write(buffer, n); }
            catch (IOException ended) { /* the host is gone */ }
            synchronized (this) {
                try {
                    if (sink == null) to(logs.resolve("component-spare.log"));  // a spare that ended unused: its output, for the reason
                    sink.close();
                } catch (IOException ignored) { /* nothing left to log to */ }
            }
        }

        private synchronized void write(byte[] buffer, int n) {
            try {
                if (sink != null) sink.write(buffer, 0, n);
                else if (early.size() < EARLY_LIMIT) early.write(buffer, 0, n);
            } catch (IOException lost) { /* keeps draining: a host blocked on a full pipe would hang */ }
        }

        synchronized void to(Path log) throws IOException {
            sink = new FileOutputStream(log.toFile(), true);
            early.writeTo(sink);
            early = null;
        }
    }
}
