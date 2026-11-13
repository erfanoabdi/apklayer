package hello.environment;

import android.app.Activity;
import android.os.Bundle;
import android.system.Os;
import android.widget.TextView;
import java.util.Map;
import java.util.Objects;

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
    private int failures;
    private interface Step { void run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Checking Java/native environment");
        setContentView(text);
        new Thread(() -> {
            try {
                System.loadLibrary("hello");
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
    }

    private void tests() {
        try {
            step("initial cleanup", () -> { unset(KEY); unset(PUT); unset(LOCAL); });
            views(KEY, null, "absent");
            final Map<String, String> before = System.getenv();
            final Map<String, String> builder = new ProcessBuilder("unused").environment();
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
                } finally {
                    if (original == null) Os.unsetenv("HOME");
                    else Os.setenv("HOME", original, true);
                }
            });
        } finally {
            step("final cleanup", () -> { unset(KEY); unset(PUT); unset(LOCAL); releasePut(); });
        }
    }
}
