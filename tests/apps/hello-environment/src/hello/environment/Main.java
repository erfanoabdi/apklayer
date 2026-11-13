package hello.environment;

import android.app.Activity;
import android.os.Bundle;
import android.system.Os;
import android.widget.TextView;
import java.util.Map;
import java.util.Objects;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Main extends Activity {
    private static final String KEY = "APKLY_ENV_TEST_BRIDGE";
    private static final String PUT = "APKLY_ENV_TEST_PUTENV";
    private static final String LOCAL = "APKLY_ENV_TEST_BUILDER";
    private static native String read(String key, boolean scanEnviron);
    private static native int set(String key, String value, boolean overwrite);
    private static native int unset(String key);
    private static native int put(String key, String value);
    private static native void mutatePut(String value);
    private static native void releasePut();
    private static native byte[] procEnviron(boolean buffered);
    private static native void activityManagerCheck();
    private int failures;
    private interface Step { void run() throws Throwable; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Checking Java/native environment");
        setContentView(text);
        new Thread(() -> {
            try {
                System.loadLibrary("hello");
                step("native ActivityManager private control", Main::activityManagerCheck);
                tests();
            } catch (Throwable failure) {
                failures++;
                System.out.println("[environment] startup exception " + failure);
                failure.printStackTrace();
            }
            String result = failures == 0 ? "PASS" : "FAIL comparisons=" + failures;
            System.out.println("[environment] " + result);
            runOnUiThread(() -> text.setText("Environment " + result));
        }, "environment-check").start();
    }

    private void check(Object expected, Object actual, String label) {
        boolean ok = Objects.equals(expected, actual);
        if (!ok) failures++;
        System.out.println("[environment] " + (ok ? "ok " : "mismatch ") + label
                + " expected=" + expected + " actual=" + actual);
    }

    private void step(String label, Step action) {
        try { action.run(); }
        catch (Throwable failure) {
            failures++;
            System.out.println("[environment] exception " + label + " " + failure);
            failure.printStackTrace();
        }
    }

    private void views(String key, String value, String label) {
        step(label + " System.getenv", () -> check(value, System.getenv(key), label + " System.getenv(name)"));
        step(label + " getenv map", () -> check(value, System.getenv().get(key), label + " System.getenv map"));
        step(label + " ProcessBuilder", () -> check(value, new ProcessBuilder("unused").environment().get(key), label + " new builder"));
        step(label + " Os.getenv", () -> check(value, Os.getenv(key), label + " Os.getenv"));
        step(label + " libc getenv", () -> check(value, read(key, false), label + " libc getenv"));
        step(label + " environ", () -> check(value, read(key, true), label + " libc environ"));
        step(label + " reflection", () -> {
            check(value, System.class.getMethod("getenv", String.class).invoke(null, key), label + " reflected getenv");
            Map<?, ?> snapshot = (Map<?, ?>) System.class.getMethod("getenv").invoke(null);
            check(value, snapshot.get(key), label + " reflected getenv map");
        });
        step(label + " MethodHandle", () -> {
            String actual = (String) MethodHandles.lookup().findStatic(System.class, "getenv",
                    MethodType.methodType(String.class, String.class)).invokeExact(key);
            check(value, actual, label + " MethodHandle getenv");
            Map<?, ?> snapshot = (Map<?, ?>) MethodHandles.lookup().findStatic(System.class, "getenv",
                    MethodType.methodType(Map.class)).invokeExact();
            check(value, snapshot.get(key), label + " MethodHandle getenv map");
        });
        step(label + " method reference", () -> {
            Function<String, String> getter = System::getenv;
            Supplier<Map<String, String>> snapshot = System::getenv;
            check(value, getter.apply(key), label + " method-reference getenv");
            check(value, snapshot.get().get(key), label + " method-reference map");
        });
    }

    private String[] command() {
        return new String[]{"/system/bin/sh", "-c", "echo -n \"$APKLY_ENV_TEST_BRIDGE|$HOME\""};
    }

    private Thread drain(InputStream stream, ByteArrayOutputStream result) {
        Thread reader = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                for (int count; (count = stream.read(buffer)) != -1; )
                    if (result.size() < 16384) result.write(buffer, 0, Math.min(count, 16384 - result.size()));
            } catch (java.io.IOException closed) { /* Parent closes pipes during cleanup. */ }
        }, "environment-child-pipe");
        reader.start();
        return reader;
    }

    private void child(Process process, String expected, String label) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Thread out = drain(process.getInputStream(), output);
        Thread err = drain(process.getErrorStream(), errors);
        System.out.println("[environment] checking child " + label);
        try {
            process.getOutputStream().close();
            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            check(true, exited, label + " exits within deadline");
            if (exited) {
                out.join(500);
                err.join(500);
                check(0, process.exitValue(), label + " exit code");
                check(expected, output.toString("UTF-8"), label + " stdout");
                check("", errors.toString("UTF-8"), label + " stderr");
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            check(true, process.waitFor(2, TimeUnit.SECONDS), label + " reaped");
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
            out.join(500);
            err.join(500);
            check(false, out.isAlive() || err.isAlive(), label + " pipe readers stopped");
        }
    }

    private void children(String home) {
        step("child setup", () -> Os.setenv(KEY, "child-inherited", true));
        step("inherited builder", () -> child(new ProcessBuilder(command()).start(), "child-inherited|" + home, "inherited builder"));
        step("inherited Runtime.exec", () -> child(Runtime.getRuntime().exec(command()), "child-inherited|" + home, "inherited Runtime.exec"));
        step("snapshot builder", () -> {
            ProcessBuilder saved = new ProcessBuilder(command());
            saved.environment();
            Os.setenv(KEY, "after-snapshot", true);
            child(saved.start(), "child-inherited|" + home, "snapshot builder");
        });
        step("explicit builder", () -> {
            ProcessBuilder explicit = new ProcessBuilder(command());
            explicit.environment().clear();
            explicit.environment().put(KEY, "child-explicit");
            explicit.environment().put("HOME", "/data/local/tmp/explicit-home");
            child(explicit.start(), "child-explicit|/data/local/tmp/explicit-home", "explicit builder");
        });
        step("explicit Runtime.exec", () -> child(Runtime.getRuntime().exec(command(),
                new String[]{KEY + "=exec-explicit", "HOME=/data/local/tmp/exec-home"}),
                "exec-explicit|/data/local/tmp/exec-home", "explicit Runtime.exec"));
        views(KEY, "after-snapshot", "children leave parent intact");
        views("HOME", home, "children preserve parent HOME");
    }

    private byte[] javaProcEnviron() throws Exception {
        return Files.readAllBytes(Paths.get("/proc/self/environ"));
    }

    private void initialProc(byte[][] initial) {
        step("initial Java proc environ", () -> {
            initial[0] = javaProcEnviron();
            check(true, initial[0].length > 0, "initial proc environ is nonempty");
            for (String entry : new String(initial[0], StandardCharsets.UTF_8).split("\u0000")) {
                String name = entry.split("=", 2)[0];
                check(false, name.startsWith("APKLAYER_") || name.startsWith("BIONIC_")
                        || name.equals("JAVA_HOME") || name.equals("HOME"), "initial proc excludes host key " + name);
            }
        });
        step("initial JNI fread proc environ", () -> {
            initial[1] = procEnviron(true);
            check(true, Arrays.equals(initial[0], initial[1]), "Java/JNI fread initial proc bytes agree");
        });
        step("initial JNI open proc environ", () -> {
            initial[2] = procEnviron(false);
            check(true, Arrays.equals(initial[0], initial[2]), "Java/JNI open initial proc bytes agree");
        });
    }

    private void tests() {
        byte[][] initial = new byte[3][];
        initialProc(initial);
        failures += PathChecks.run(this);
        try {
            step("initial cleanup", () -> { unset(KEY); unset(PUT); unset(LOCAL); });
            views(KEY, null, "absent");
            step("null getenv rejected", () -> {
                boolean rejected = false;
                try { System.getenv((String) null); } catch (NullPointerException expected) { rejected = true; }
                check(true, rejected, "null getenv throws NPE");
            });
            step("empty value", () -> Os.setenv(KEY, "", true));
            views(KEY, "", "empty differs from absent");
            step("empty cleanup", () -> Os.unsetenv(KEY));
            final Map<String, String> before = System.getenv();
            final Map<String, String> builder = new ProcessBuilder("unused").environment();
            step("builder invalid key rejected", () -> {
                boolean rejected = false;
                try { builder.put("APKLY_ENV_TEST_INVALID=KEY", "value"); }
                catch (IllegalArgumentException expected) { rejected = true; }
                check(true, rejected, "builder rejects equals in key");
            });
            step("unmodifiable getenv", () -> {
                boolean rejected = false;
                try { before.put(LOCAL, "forbidden"); }
                catch (UnsupportedOperationException expected) { rejected = true; }
                check(true, rejected, "getenv map rejects mutation");
            });
            step("native setenv", () -> check(0, set(KEY, "native-one", true), "setenv return"));
            views(KEY, "native-one", "native mutation");
            check(null, before.get(KEY), "prior getenv map is snapshot");
            check(null, builder.get(KEY), "prior builder is snapshot");
            step("builder independence", () -> {
                builder.put(KEY, "builder-only");
                builder.put(LOCAL, "builder-local");
                check("builder-only", builder.get(KEY), "explicit builder value");
            });
            views(KEY, "native-one", "builder leaves process intact");
            views(LOCAL, null, "builder variable stays local");
            step("Os.setenv", () -> Os.setenv(KEY, "os-two", true));
            views(KEY, "os-two", "Os mutation");
            check("builder-only", builder.get(KEY), "process leaves explicit builder intact");
            step("no overwrite", () -> check(0, set(KEY, "not-written", false), "setenv no-overwrite return"));
            views(KEY, "os-two", "no overwrite");
            step("Os.unsetenv", () -> Os.unsetenv(KEY));
            views(KEY, null, "Os unset");
            step("putenv", () -> check(0, put(PUT, "first"), "putenv return"));
            views(PUT, "first", "putenv initial");
            step("putenv storage edit", () -> mutatePut("second"));
            views(PUT, "second", "putenv mutable storage");
            step("native unsetenv", () -> check(0, unset(PUT), "unsetenv return"));
            views(PUT, null, "native unset");
            step("same HOME", () -> {
                String original = read("HOME", true);
                String home = original != null ? original : "/data/local/tmp/apkly-env-home";
                try {
                    Os.setenv("HOME", home, true);
                    views("HOME", home, "explicit HOME");
                    Os.setenv("HOME", home, true);
                    views("HOME", home, "same HOME value again");
                    children(home);
                } finally {
                    if (original == null) Os.unsetenv("HOME");
                    else Os.setenv("HOME", original, true);
                }
            });
            step("Java proc snapshot after mutation", () -> check(true,
                    initial[0] != null && Arrays.equals(initial[0], javaProcEnviron()), "Java proc initial bytes unchanged"));
            step("JNI fread proc snapshot after mutation", () -> check(true,
                    initial[1] != null && Arrays.equals(initial[1], procEnviron(true)), "JNI fread proc initial bytes unchanged"));
            step("JNI open proc snapshot after mutation", () -> check(true,
                    initial[2] != null && Arrays.equals(initial[2], procEnviron(false)), "JNI open proc initial bytes unchanged"));
        } finally {
            step("final cleanup", () -> { unset(KEY); unset(PUT); unset(LOCAL); releasePut(); });
        }
    }
}
