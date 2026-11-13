package hello.environment;

import android.content.Context;
import android.system.Os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real exec attempts: guest PATH selection, error precedence and post-chdir lookup. */
final class PathChecks {
    private static final String NAME = "apkly-path-command";
    private int failures;
    private interface Launch { Process start() throws Exception; }
    private void equal(Object want, Object actual, String label) {
        boolean ok = Objects.equals(want, actual);
        if (!ok) failures++;
        System.out.println("[environment] " + (ok ? "ok " : "mismatch ") + "PATH " + label
                + " expected=" + want + " actual=" + actual);
    }
    private void probe(String label, String expected, Launch launch) {
        Process child = null;
        try {
            System.out.println("[environment] checking PATH child " + label);
            child = launch.start();
            child.getOutputStream().close();
            boolean exited = child.waitFor(10, TimeUnit.SECONDS);
            equal(true, exited, label + " deadline");
            if (exited) {
                equal(0, child.exitValue(), label + " exit");
                equal(expected, read(child.getInputStream()), label + " stdout");
                equal("", read(child.getErrorStream()), label + " stderr");
            }
        } catch (Throwable failure) {
            failures++;
            System.out.println("[environment] PATH exception " + label + " " + failure);
        } finally {
            if (child != null) try {
                if (child.isAlive()) child.destroyForcibly();
                equal(true, child.waitFor(2, TimeUnit.SECONDS), label + " reaped");
                child.getInputStream().close(); child.getErrorStream().close(); child.getOutputStream().close();
            } catch (Exception failure) { failures++; System.out.println("[environment] PATH cleanup " + failure); }
        }
    }
    private String read(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        for (int count; (count = stream.read(bytes)) >= 0;) {
            if (out.size() + count > 16384) throw new IOException("child output exceeds limit");
            out.write(bytes, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private void rejected(String label, int errno, ProcessBuilder builder) {
        List<String> original = new ArrayList<>(builder.command());
        try {
            Process child = builder.start();
            child.destroyForcibly();
            equal(true, child.waitFor(2, TimeUnit.SECONDS), label + " unexpected child reaped");
            child.getInputStream().close(); child.getErrorStream().close(); child.getOutputStream().close();
            equal(true, false, label + " should fail during start");
        } catch (IOException expected) {
            equal(true, expected.getMessage().matches("(?s).*\\berror\\s*[:=]\\s*" + errno + "(?:\\D.*|$)"),
                    label + " errno: " + expected.getMessage());
            equal(false, expected.getMessage().contains("apklayer-exec-search"), label + " no transport in exception");
        } catch (Exception failure) { failures++; System.out.println("[environment] PATH rejection exception " + failure); }
        equal(original, builder.command(), label + " command restored");
    }
    private Path script(Path directory, String contents, int mode) throws Exception {
        Files.createDirectory(directory);
        Path file = directory.resolve(NAME);
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        Os.chmod(file.toString(), mode);
        return directory;
    }
    private void pipeline() {
        List<Process> children = Collections.emptyList();
        ProcessBuilder first = new ProcessBuilder(NAME);
        try {
            children = ProcessBuilder.startPipeline(Arrays.asList(first,
                    new ProcessBuilder("/system/bin/sh", "-c", "IFS= read -r line; echo -n \"$line\"")));
            Process last = children.get(1);
            probe("pipeline inherited live B", "B", () -> last);
            equal(Collections.singletonList(NAME), first.command(), "pipeline command restored");
        } catch (Throwable failure) { failures++; System.out.println("[environment] PATH pipeline exception " + failure); }
        finally {
            for (Process child : children) try {
                if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly();
                equal(true, child.waitFor(2, TimeUnit.SECONDS), "pipeline child reaped");
                child.getInputStream().close(); child.getErrorStream().close(); child.getOutputStream().close();
            } catch (Exception failure) { failures++; System.out.println("[environment] PATH pipeline cleanup " + failure); }
        }
    }
    static int run(Context context) {
        PathChecks test = new PathChecks();
        test.runChecks(context);
        return test.failures;
    }
    private void runChecks(Context context) {
        String original = Os.getenv("PATH");
        Path root = null;
        try {
            root = Files.createTempDirectory(context.getFilesDir().toPath(), "path-check-");
            Path a = script(root.resolve("a"), "#!/system/bin/sh\necho -n \"A$1\"\n", 0700);
            Path b = script(root.resolve("b"), "#!/system/bin/sh\necho -n \"B$1\"\n", 0700);
            Path denied = script(root.resolve("denied"), "#!/system/bin/sh\necho -n DENIED\n", 0600);
            Path plain = script(root.resolve("plain"), "echo -n PLAIN\n", 0700);
            Os.setenv("PATH", a.toString(), true);
            probe("initialize cached parent A", "INIT", () -> new ProcessBuilder("/system/bin/sh", "-c", "echo -n INIT").start());
            ProcessBuilder capturedA = new ProcessBuilder(NAME); capturedA.environment();
            Os.setenv("PATH", b.toString(), true);
            probe("inherited uses live B", "B", () -> new ProcessBuilder(NAME).start());
            probe("Runtime.exec string live B", "B", () -> Runtime.getRuntime().exec(NAME));
            probe("Runtime.exec string tokenization", "Bone", () -> Runtime.getRuntime().exec(NAME + " one two"));
            probe("builder preserves argument spaces", "B one two", () -> new ProcessBuilder(NAME, " one two").start());
            probe("Runtime.exec null live B", "B", () -> Runtime.getRuntime().exec(new String[]{NAME}, null));
            pipeline();
            probe("captured A", "A", capturedA::start);
            probe("captured B still searches cached A", "A", () -> {
                ProcessBuilder saved = new ProcessBuilder(NAME); saved.environment(); return saved.start();
            });
            probe("explicit child B searches cached A", "A", () -> {
                ProcessBuilder explicit = new ProcessBuilder(NAME);
                explicit.environment().clear(); explicit.environment().put("PATH", b.toString()); return explicit.start();
            });
            probe("Runtime.exec explicit B", "A", () -> Runtime.getRuntime().exec(NAME, new String[]{"PATH=" + b}));
            probe("Runtime.exec explicit empty", "A", () -> Runtime.getRuntime().exec(new String[]{NAME}, new String[0]));
            ProcessBuilder reused = new ProcessBuilder(NAME);
            probe("reused builder B", "B", reused::start);
            equal(Collections.singletonList(NAME), reused.command(), "command restored after success");
            Os.setenv("PATH", a.toString(), true);
            probe("reused builder remains inherited A", "A", reused::start);
            Os.setenv("PATH", "../a", true);
            probe("relative PATH after cwd", "A", () -> new ProcessBuilder(NAME).directory(b.toFile()).start());
            Os.setenv("PATH", "", true);
            probe("empty PATH is cwd", "A", () -> new ProcessBuilder(NAME).directory(a.toFile()).start());
            Os.setenv("PATH", "/apkly-missing-path:", true);
            probe("trailing empty PATH is cwd", "A", () -> new ProcessBuilder(NAME).directory(a.toFile()).start());
            Os.setenv("PATH", denied + ":" + b, true);
            probe("EACCES candidate continues to B", "B", () -> new ProcessBuilder(NAME).start());
            Os.setenv("PATH", denied + ":/apkly-missing-path", true);
            rejected("EACCES beats missing", 13, new ProcessBuilder(NAME));
            Os.setenv("PATH", "/apkly-missing-path", true);
            rejected("missing command", 2, new ProcessBuilder(NAME));
            Os.setenv("PATH", plain.toString(), true);
            probe("no-shebang shell fallback", "PLAIN", () -> new ProcessBuilder(NAME).start());
            probe("no-shebang path shell fallback", "PLAIN", () -> new ProcessBuilder(plain.resolve(NAME).toString()).start());
            // Android's /bin is /system/bin: a #!/bin/sh script and /bin/sh itself are Android's shell (mksh), not the host's
            Path binsh = script(root.resolve("binsh"), "#!/bin/sh\necho -n \"S${KSH_VERSION:+mksh}$1\"\n", 0700);
            Os.setenv("PATH", binsh.toString(), true);
            probe("#!/bin/sh script by PATH", "Smksh", () -> new ProcessBuilder(NAME).start());
            probe("#!/bin/sh script by path", "Smksh1", () -> new ProcessBuilder(binsh.resolve(NAME).toString(), "1").start());
            probe("Runtime.exec /bin/sh", "mksh", () -> Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "echo -n ${KSH_VERSION:+mksh}"}));
        } catch (Throwable failure) {
            failures++; System.out.println("[environment] PATH setup exception " + failure); failure.printStackTrace();
        } finally {
            try {
                if (original == null) Os.unsetenv("PATH"); else Os.setenv("PATH", original, true);
                if (root != null) {
                    for (String dir : new String[]{"a", "b", "denied", "plain", "binsh"}) {
                        Files.deleteIfExists(root.resolve(dir).resolve(NAME)); Files.deleteIfExists(root.resolve(dir));
                    }
                    Files.deleteIfExists(root);
                }
            } catch (Exception failure) { failures++; System.out.println("[environment] PATH restore exception " + failure); }
        }
    }
}
