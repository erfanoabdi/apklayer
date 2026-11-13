package hello.environment;

import android.system.Os;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Native code calling java.lang.System's property and environment methods through JNI sees Android's values (jni_system.c). */
final class JniSystemChecks {
    private static final String[] FORMS = {"varargs", "V", "A", "FromReflectedMethod", "Method.invoke", "attached-thread"};
    private static final int VARARGS = 0, REFLECTED = 3, INVOKE = 4, THREAD = 5;
    private static final int GET_PROPERTY = 0, GETENV = 1, GET_PROPERTIES = 2, GETENV_MAP = 3, SET_PROPERTY = 4,
            CLEAR_PROPERTY = 5, GET_PROPERTY_DEFAULT = 6, GET_BOOLEAN = 7, SET_PROPERTIES = 8;
    private static final String ENV_KEY = "APKLY_ENV_TEST_JNI", HOST_ENV = "APKLAYER_NO_SYSTEM_SERVER", HOST_PROPERTY = "apklayer.package";
    private static native Object call(int form, int op, String first, String second, Object method);
    private static native Object registered(int form, int op, String first, String second, Object method);
    private static native Object tailGetProperty(String key);
    private static native Object[] onloadValues();
    private int failures;

    private void equal(Object want, Object actual, String label) {
        boolean ok = Objects.equals(want, actual);
        if (!ok) failures++;
        System.out.println("[environment] " + (ok ? "ok " : "mismatch ") + "JNI " + label + " expected=" + want + " actual=" + actual);
    }

    private interface Probe { void run() throws Throwable; }
    private void probe(String label, Probe probe) {
        try { probe.run(); }
        catch (Throwable failure) {
            failures++;
            System.out.println("[environment] mismatch JNI " + label + " exception " + failure);
        }
    }

    private static Method method(int op) throws NoSuchMethodException {
        switch (op) {
            case GET_PROPERTY: case CLEAR_PROPERTY: return System.class.getMethod(op == GET_PROPERTY ? "getProperty" : "clearProperty", String.class);
            case GETENV: return System.class.getMethod("getenv", String.class);
            case GET_PROPERTIES: return System.class.getMethod("getProperties");
            case GETENV_MAP: return System.class.getMethod("getenv");
            case SET_PROPERTY: return System.class.getMethod("setProperty", String.class, String.class);
            case GET_PROPERTY_DEFAULT: return System.class.getMethod("getProperty", String.class, String.class);
            case GET_BOOLEAN: return Boolean.class.getMethod("getBoolean", String.class);
            default: return null;
        }
    }

    private Object run(int form, int op, String first, String second) throws Throwable {
        return call(form, op, first, second, method(op));
    }

    private void form(int form) {
        String f = FORMS[form];
        probe(f + " getProperty", () -> {
            equal("2.1.0", run(form, GET_PROPERTY, "java.vm.version", null), f + " System.getProperty(java.vm.version)");
            equal(null, run(form, GET_PROPERTY, HOST_PROPERTY, null), f + " System.getProperty(" + HOST_PROPERTY + ")");
        });
        probe(f + " getProperty default", () -> equal("fallback", run(form, GET_PROPERTY_DEFAULT, HOST_PROPERTY, "fallback"), f + " System.getProperty(key, default)"));
        probe(f + " getenv", () -> {
            equal("jni-guest", run(form, GETENV, ENV_KEY, null), f + " System.getenv(guest key)");
            equal(null, run(form, GETENV, HOST_ENV, null), f + " System.getenv(host control)");
        });
        probe(f + " getProperties", () -> {
            Object p = run(form, GET_PROPERTIES, null, null);
            equal("2.1.0", p instanceof Properties ? ((Properties) p).getProperty("java.vm.version") : p, f + " System.getProperties()");
        });
        probe(f + " getenv map", () -> {
            Object m = run(form, GETENV_MAP, null, null);
            equal("jni-guest", m instanceof Map ? ((Map<?, ?>) m).get(ENV_KEY) : m, f + " System.getenv() guest key");
            equal(false, m instanceof Map && ((Map<?, ?>) m).containsKey(HOST_ENV), f + " System.getenv() host control");
        });
        probe(f + " setProperty", () -> {
            String key = "apkly.jni.set." + form;
            equal(null, run(form, SET_PROPERTY, key, "native"), f + " System.setProperty previous");
            equal("native", System.getProperty(key), f + " System.setProperty seen by app Java");
            System.clearProperty(key);
        });
        probe(f + " clearProperty", () -> {
            String key = "apkly.jni.clear." + form;
            System.setProperty(key, "java");
            equal("java", run(form, CLEAR_PROPERTY, key, null), f + " System.clearProperty previous");
            equal(null, System.getProperty(key), f + " System.clearProperty seen by app Java");
            System.clearProperty(key);
        });
        probe(f + " Boolean.getBoolean", () -> {
            System.setProperty("apkly.jni.bool", "true");
            equal("true", run(form, GET_BOOLEAN, "apkly.jni.bool", null), f + " Boolean.getBoolean");
        });
        if (form != REFLECTED && form != INVOKE) probe(f + " setProperties", () -> {
            Object p = run(form, GET_PROPERTIES, null, null);
            call(form, SET_PROPERTIES, null, null, p);
            equal("2.1.0", System.getProperty("java.vm.version"), f + " System.setProperties(getProperties()) keeps app bag");
        });
    }

    static int run() {
        JniSystemChecks checks = new JniSystemChecks();
        checks.probe("setup", () -> Os.setenv(ENV_KEY, "jni-guest", true));
        checks.probe("JNI_OnLoad", () -> {
            Object[] values = onloadValues();
            checks.equal("2.1.0", values[0], "JNI_OnLoad System.getProperty(java.vm.version)");
            checks.equal(null, values[1], "JNI_OnLoad System.getenv(host control)");
        });
        for (int form = VARARGS; form <= THREAD; form++) checks.form(form);
        checks.probe("RegisterNatives callback", () -> {
            checks.equal("2.1.0", registered(2, GET_PROPERTY, "java.vm.version", null, null), "RegisterNatives callback System.getProperty");
            checks.equal(null, registered(0, GETENV, HOST_ENV, null, null), "RegisterNatives callback System.getenv(host control)");
        });
        checks.probe("tail call", () -> checks.equal("2.1.0", tailGetProperty("java.vm.version"), "tail-call System.getProperty"));
        checks.probe("Java views intact", () -> {
            checks.equal("2.1.0", System.getProperty("java.vm.version"), "app Java System.getProperty after JNI");
            checks.equal("jni-guest", System.getenv(ENV_KEY), "app Java System.getenv after JNI");
        });
        checks.probe("cleanup", () -> { Os.unsetenv(ENV_KEY); System.clearProperty("apkly.jni.bool"); });
        System.out.println("[environment] JNI System checks: " + (checks.failures == 0 ? "PASS" : checks.failures + " mismatches"));
        return checks.failures;
    }
}
